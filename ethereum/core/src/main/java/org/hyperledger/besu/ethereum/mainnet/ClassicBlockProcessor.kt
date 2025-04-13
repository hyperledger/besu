/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.mainnet

import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.core.MutableWorldState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.util.*

class ClassicBlockProcessor(
    transactionProcessor: MainnetTransactionProcessor,
    transactionReceiptFactory: TransactionReceiptFactory,
    blockReward: Wei,
    miningBeneficiaryCalculator: MiningBeneficiaryCalculator,
    skipZeroBlockRewards: Boolean,
    eraLen: OptionalLong,
    protocolSchedule: ProtocolSchedule
) : AbstractBlockProcessor(
    transactionProcessor,
    transactionReceiptFactory,
    blockReward,
    miningBeneficiaryCalculator,
    skipZeroBlockRewards,
    protocolSchedule
) {
    private val eraLength: Long

    init {
        eraLength = eraLen.orElse(DEFAULT_ERA_LENGTH)
    }

    override fun rewardCoinbase(
        worldState: MutableWorldState?,
        header: BlockHeader?,
        ommers: List<BlockHeader>?,
        skipZeroBlockRewards: Boolean
    ): Boolean {
        if (skipZeroBlockRewards && blockReward.isZero) {
            return true
        }
        val coinbaseReward = getCoinbaseReward(blockReward, header!!.number, ommers!!.size)
        val updater = worldState!!.updater()
        val coinbase = updater.getOrCreate(header.coinbase)

        coinbase.incrementBalance(coinbaseReward)
        for (ommerHeader in ommers) {
            if (ommerHeader.number - header.number > MAX_GENERATION) {
                LOG.warn(
                    "Block processing error: ommer block number {} more than {} generations current block number {}",
                    ommerHeader.number,
                    MAX_GENERATION,
                    header.number
                )
                return false
            }

            val ommerCoinbase = updater.getOrCreate(ommerHeader.coinbase)
            val ommerReward =
                getOmmerReward(blockReward, header.number, ommerHeader.number)
            ommerCoinbase.incrementBalance(ommerReward)
        }

        updater.commit()
        return true
    }

    // getUncleInclusionReword return reward for including
    //  an uncle block
    private fun calculateOmmerReward(era: Int, distance: Long): Wei {
        val winnerReward = getBlockWinnerRewardByEra(era)
        if (era < 1) {
            return winnerReward.subtract(winnerReward.multiply(distance).divide(8))
        }
        return winnerReward.divide(32)
    }

    // GetBlockEra gets which "Era" a given block is within, given an era length (ecip-1017 has
    // era=5,000,000 blocks)
    // Returns a zero-index era number, so "Era 1": 0, "Era 2": 1, "Era 3": 2 ...
    private fun getBlockEra(blockNumber: Long, eraLength: Long): Int {
        // if genesis block or impossible nagative-numbered block, return zero
        if (blockNumber < 0) return 0
        val remainder = (blockNumber - 1) % eraLength
        val base = blockNumber - remainder
        val d = base / eraLength
        return Math.toIntExact(d)
    }

    // getRewardByEra gets a block reward at disinflation rate.
    // Constants MaxBlockReward, DisinflationRateQuotient, and DisinflationRateDivisor assumed.
    private fun getBlockWinnerRewardByEra(era: Int): Wei {
        if (era == 0) {
            return this.blockReward
        }

        // MaxBlockReward _r_ * (4/5)**era == MaxBlockReward * (4**era) / (5**era)
        // since (q/d)**n == q**n / d**n
        // qed
        val disinflationRateQuotient = BigInteger.valueOf(4)
        val q = disinflationRateQuotient.pow(era)

        val disinflationRateDivisor = BigInteger.valueOf(5)
        val d = disinflationRateDivisor.pow(era)

        val maximumBlockReward = blockReward.toBigInteger()
        var r = maximumBlockReward.multiply(q)

        r = r.divide(d)
        return Wei.of(r)
    }

    override fun getOmmerReward(
        blockReward: Wei, blockNumber: Long, ommerBlockNumber: Long
    ): Wei {
        val blockEra = getBlockEra(blockNumber, eraLength)
        val distance = blockNumber - ommerBlockNumber
        return calculateOmmerReward(blockEra, distance)
    }

    override fun getCoinbaseReward(
        blockReward: Wei, blockNumber: Long, ommersSize: Int
    ): Wei {
        val blockEra = getBlockEra(blockNumber, eraLength)
        val winnerReward = getBlockWinnerRewardByEra(blockEra)
        return winnerReward.plus(winnerReward.multiply(ommersSize.toLong()).divide(32))
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(ClassicBlockProcessor::class.java)

        private const val DEFAULT_ERA_LENGTH = 5000000L
    }
}

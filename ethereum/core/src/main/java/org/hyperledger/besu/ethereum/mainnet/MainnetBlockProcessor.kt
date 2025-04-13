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

open class MainnetBlockProcessor(
    transactionProcessor: MainnetTransactionProcessor,
    transactionReceiptFactory: TransactionReceiptFactory,
    blockReward: Wei,
    miningBeneficiaryCalculator: MiningBeneficiaryCalculator,
    skipZeroBlockRewards: Boolean,
    protocolSchedule: ProtocolSchedule
) : AbstractBlockProcessor(
    transactionProcessor,
    transactionReceiptFactory,
    blockReward,
    miningBeneficiaryCalculator,
    skipZeroBlockRewards,
    protocolSchedule
) {
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
        val miningBeneficiary = miningBeneficiaryCalculator.calculateBeneficiary(header)
        val miningBeneficiaryAccount = updater.getOrCreate(miningBeneficiary)

        miningBeneficiaryAccount.incrementBalance(coinbaseReward)
        for (ommerHeader in ommers) {
            if (ommerHeader.number - header.number > MAX_GENERATION) {
                LOG.info(
                    "Block processing error: ommer block number {} more than {} generations. Block {}",
                    ommerHeader.number,
                    MAX_GENERATION,
                    header.hash.toHexString()
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

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MainnetBlockProcessor::class.java)
    }
}

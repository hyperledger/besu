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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.List;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassicBlockProcessor extends AbstractBlockProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(ClassicBlockProcessor.class);

  private static final long DEFAULT_ERA_LENGTH = 5_000_000L;

  private final long eraLength;

  public ClassicBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final OptionalLong eraLen) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards);
    eraLength = eraLen.orElse(DEFAULT_ERA_LENGTH);
  }

  @Override
  boolean rewardCoinbase(
      final MutableWorldState worldState,
      final BlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards) {
    if (skipZeroBlockRewards && blockReward.isZero()) {
      return true;
    }
    final Wei coinbaseReward = getCoinbaseReward(blockReward, header.getNumber(), ommers.size());
    final WorldUpdater updater = worldState.updater();
    final MutableAccount coinbase = updater.getOrCreate(header.getCoinbase()).getMutable();

    coinbase.incrementBalance(coinbaseReward);
    for (final BlockHeader ommerHeader : ommers) {
      if (ommerHeader.getNumber() - header.getNumber() > MAX_GENERATION) {
        LOG.warn(
            "Block processing error: ommer block number {} more than {} generations current block number {}",
            ommerHeader.getNumber(),
            MAX_GENERATION,
            header.getNumber());
        return false;
      }

      final MutableAccount ommerCoinbase =
          updater.getOrCreate(ommerHeader.getCoinbase()).getMutable();
      final Wei ommerReward =
          getOmmerReward(blockReward, header.getNumber(), ommerHeader.getNumber());
      ommerCoinbase.incrementBalance(ommerReward);
    }

    updater.commit();
    return true;
  }

  // getUncleInclusionReword return reward for including
  //  an uncle block
  private Wei calculateOmmerReward(final int era, final long distance) {
    Wei winnerReward = getBlockWinnerRewardByEra(era);
    if (era < 1) {
      return winnerReward.subtract(winnerReward.multiply(distance).divide(8));
    }
    return winnerReward.divide(32);
  }

  // GetBlockEra gets which "Era" a given block is within, given an era length (ecip-1017 has
  // era=5,000,000 blocks)
  // Returns a zero-index era number, so "Era 1": 0, "Era 2": 1, "Era 3": 2 ...
  private int getBlockEra(final long blockNumber, final long eraLength) {
    // if genesis block or impossible nagative-numbered block, return zero
    if (blockNumber < 0) return 0;
    long remainder = (blockNumber - 1) % eraLength;
    long base = blockNumber - remainder;
    long d = base / eraLength;
    return Math.toIntExact(d);
  }

  // getRewardByEra gets a block reward at disinflation rate.
  // Constants MaxBlockReward, DisinflationRateQuotient, and DisinflationRateDivisor assumed.
  private Wei getBlockWinnerRewardByEra(final int era) {
    if (era == 0) {
      return this.blockReward;
    }

    // MaxBlockReward _r_ * (4/5)**era == MaxBlockReward * (4**era) / (5**era)
    // since (q/d)**n == q**n / d**n
    // qed

    BigInteger disinflationRateQuotient = BigInteger.valueOf(4);
    BigInteger q;
    q = disinflationRateQuotient.pow(era);

    BigInteger disinflationRateDivisor = BigInteger.valueOf(5);
    BigInteger d;
    d = disinflationRateDivisor.pow(era);

    BigInteger maximumBlockReward = this.blockReward.toBigInteger();
    BigInteger r;
    r = maximumBlockReward.multiply(q);

    r = r.divide(d);
    return Wei.of(r);
  }

  @Override
  public Wei getOmmerReward(
      final Wei blockReward, final long blockNumber, final long ommerBlockNumber) {
    final int blockEra = getBlockEra(blockNumber, eraLength);
    final long distance = blockNumber - ommerBlockNumber;
    return calculateOmmerReward(blockEra, distance);
  }

  @Override
  public Wei getCoinbaseReward(
      final Wei blockReward, final long blockNumber, final int ommersSize) {
    final int blockEra = getBlockEra(blockNumber, eraLength);
    final Wei winnerReward = getBlockWinnerRewardByEra(blockEra);
    return winnerReward.plus(winnerReward.multiply(ommersSize).divide(32));
  }
}

/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.util.uint.UInt256;

import java.math.BigInteger;

import com.google.common.primitives.Ints;

/** Provides the various difficultly calculates used on mainnet hard forks. */
public abstract class MainnetDifficultyCalculators {

  private static final BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(2_048L);

  private static final BigInteger MINIMUM_DIFFICULTY = BigInteger.valueOf(131_072L);

  private static final long EXPONENTIAL_DIFF_PERIOD = 100_000L;

  private static final int DURATION_LIMIT = 13;

  private static final BigInteger BIGINT_2 = BigInteger.valueOf(2L);

  private static final long BYZANTIUM_FAKE_BLOCK_OFFSET = 2_999_999L;
  private static final long CONSTANTINOPLE_FAKE_BLOCK_OFFSET = 4_999_999L;

  private MainnetDifficultyCalculators() {}

  public static DifficultyCalculator<Void> FRONTIER =
      (time, parent, protocolContext) -> {
        final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
        final BigInteger adjust = parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR);
        BigInteger difficulty;
        if (time - parent.getTimestamp() < DURATION_LIMIT) {
          difficulty = adjust.add(parentDifficulty);
        } else {
          difficulty = parentDifficulty.subtract(adjust);
        }
        difficulty = ensureMinimumDifficulty(difficulty);
        final long periodCount = (parent.getNumber() + 1) / EXPONENTIAL_DIFF_PERIOD;
        return periodCount > 1 ? adjustForPeriod(periodCount, difficulty) : difficulty;
      };

  public static DifficultyCalculator<Void> HOMESTEAD =
      (time, parent, protocolContext) -> {
        final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
        final BigInteger difficulty =
            ensureMinimumDifficulty(
                BigInteger.valueOf(Math.max(1 - (time - parent.getTimestamp()) / 10, -99L))
                    .multiply(parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR))
                    .add(parentDifficulty));
        final long periodCount = (parent.getNumber() + 1) / EXPONENTIAL_DIFF_PERIOD;
        return periodCount > 1 ? adjustForPeriod(periodCount, difficulty) : difficulty;
      };

  public static DifficultyCalculator<Void> BYZANTIUM =
      (time, parent, protocolContext) ->
          calculateByzantiumDifficulty(time, parent, BYZANTIUM_FAKE_BLOCK_OFFSET);

  public static DifficultyCalculator<Void> CONSTANTINOPLE =
      (time, parent, protocolContext) ->
          calculateByzantiumDifficulty(time, parent, CONSTANTINOPLE_FAKE_BLOCK_OFFSET);

  private static BigInteger calculateByzantiumDifficulty(
      final long time, final BlockHeader parent, final long fakeBlockOffset) {
    final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
    final boolean hasOmmers = !parent.getOmmersHash().equals(Hash.EMPTY_LIST_HASH);
    final BigInteger difficulty =
        ensureMinimumDifficulty(
            BigInteger.valueOf(byzantiumX(time, parent.getTimestamp(), hasOmmers))
                .multiply(parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR))
                .add(parentDifficulty));
    final long periodCount =
        fakeBlockNum(parent.getNumber(), fakeBlockOffset) / EXPONENTIAL_DIFF_PERIOD;
    return periodCount > 1 ? adjustForPeriod(periodCount, difficulty) : difficulty;
  }

  private static long fakeBlockNum(final long parentNum, final long fakeBlockOffset) {
    final long fakeBlockNumber;
    if (Long.compareUnsigned(parentNum, fakeBlockOffset) >= 0) {
      fakeBlockNumber = parentNum - fakeBlockOffset;
    } else {
      fakeBlockNumber = 0L;
    }
    return fakeBlockNumber;
  }

  private static long byzantiumX(
      final long blockTime, final long parentTime, final boolean hasOmmers) {
    long x = (blockTime - parentTime) / 9L;
    if (hasOmmers) {
      x = 2 - x;
    } else {
      x = 1 - x;
    }
    return Math.max(x, -99L);
  }

  private static BigInteger adjustForPeriod(final long periodCount, final BigInteger difficulty) {
    return difficulty.add(BIGINT_2.pow(Ints.checkedCast(periodCount - 2)));
  }

  private static BigInteger ensureMinimumDifficulty(final BigInteger difficulty) {
    return difficulty.compareTo(MINIMUM_DIFFICULTY) < 0 ? MINIMUM_DIFFICULTY : difficulty;
  }

  private static BigInteger difficulty(final UInt256 value) {
    return new BigInteger(1, value.getBytes().extractArray());
  }
}

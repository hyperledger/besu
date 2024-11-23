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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.ethereum.core.BlockHeader;

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
  private static final long MUIR_GLACIER_FAKE_BLOCK_OFFSET = 8_999_999L;
  private static final long LONDON_FAKE_BLOCK_OFFSET = 9_699_999L;
  private static final long ARROW_GLACIER_FAKE_BLOCK_OFFSET = 10_699_999L;
  private static final long GRAY_GLACIER_FAKE_BLOCK_OFFSET = 11_399_999L;

  private MainnetDifficultyCalculators() {}

  static final DifficultyCalculator FRONTIER =
      (time, parent) -> {
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

  static final DifficultyCalculator HOMESTEAD =
      (time, parent) -> {
        final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
        final BigInteger difficulty =
            ensureMinimumDifficulty(
                BigInteger.valueOf(Math.max(1 - (time - parent.getTimestamp()) / 10, -99L))
                    .multiply(parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR))
                    .add(parentDifficulty));
        final long periodCount = (parent.getNumber() + 1) / EXPONENTIAL_DIFF_PERIOD;
        return periodCount > 1 ? adjustForPeriod(periodCount, difficulty) : difficulty;
      };

  static final DifficultyCalculator BYZANTIUM =
      (time, parent) -> calculateThawedDifficulty(time, parent, BYZANTIUM_FAKE_BLOCK_OFFSET);

  static final DifficultyCalculator CONSTANTINOPLE =
      (time, parent) -> calculateThawedDifficulty(time, parent, CONSTANTINOPLE_FAKE_BLOCK_OFFSET);

  static final DifficultyCalculator MUIR_GLACIER =
      (time, parent) -> calculateThawedDifficulty(time, parent, MUIR_GLACIER_FAKE_BLOCK_OFFSET);

  // As per https://eips.ethereum.org/EIPS/eip-3554
  static final DifficultyCalculator LONDON =
      (time, parent) -> calculateThawedDifficulty(time, parent, LONDON_FAKE_BLOCK_OFFSET);

  // As per https://eips.ethereum.org/EIPS/eip-4345
  static final DifficultyCalculator ARROW_GLACIER =
      (time, parent) -> calculateThawedDifficulty(time, parent, ARROW_GLACIER_FAKE_BLOCK_OFFSET);

  // As per https://eips.ethereum.org/EIPS/eip-5133
  static final DifficultyCalculator GRAY_GLACIER =
      (time, parent) -> calculateThawedDifficulty(time, parent, GRAY_GLACIER_FAKE_BLOCK_OFFSET);

  // Proof-of-Stake difficulty must not be altered
  static final DifficultyCalculator PROOF_OF_STAKE_DIFFICULTY = (time, parent) -> BigInteger.ZERO;

  private static BigInteger calculateThawedDifficulty(
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

  private static BigInteger difficulty(final Quantity value) {
    return value.getAsBigInteger();
  }
}

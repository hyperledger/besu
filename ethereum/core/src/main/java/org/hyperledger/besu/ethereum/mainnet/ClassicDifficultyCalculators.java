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
import org.hyperledger.besu.plugin.data.Quantity;

import java.math.BigInteger;

import com.google.common.primitives.Ints;

public abstract class ClassicDifficultyCalculators {
  private static final BigInteger MINIMUM_DIFFICULTY = BigInteger.valueOf(131_072L);
  private static final BigInteger DIFFICULTY_BOUND_DIVISOR = BigInteger.valueOf(2_048L);
  private static final BigInteger BIGINT_2 = BigInteger.valueOf(2L);
  private static final long EXPONENTIAL_DIFF_PERIOD = 100_000L;
  private static final long PAUSE_BLOCK = 3_000_000L;
  private static final long FIXED_DIFF = PAUSE_BLOCK / EXPONENTIAL_DIFF_PERIOD;
  private static final long CONTINUE_BLOCK = 5_000_000L;
  private static final long DELAY = (CONTINUE_BLOCK - PAUSE_BLOCK) / EXPONENTIAL_DIFF_PERIOD;

  public static DifficultyCalculator DIFFICULTY_BOMB_PAUSED =
      (time, parent, protocolContext) -> {
        final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
        final BigInteger difficulty =
            ensureMinimumDifficulty(
                BigInteger.valueOf(Math.max(1 - (time - parent.getTimestamp()) / 10, -99L))
                    .multiply(parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR))
                    .add(parentDifficulty));
        return adjustForDifficultyPause(FIXED_DIFF, difficulty);
      };

  public static DifficultyCalculator DIFFICULTY_BOMB_DELAYED =
      (time, parent, protocolContext) -> {
        final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
        final BigInteger difficulty =
            ensureMinimumDifficulty(
                BigInteger.valueOf(Math.max(1 - (time - parent.getTimestamp()) / 10, -99L))
                    .multiply(parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR))
                    .add(parentDifficulty));
        final long periodCount = (parent.getNumber() + 1) / EXPONENTIAL_DIFF_PERIOD;
        return adjustForDifficultyDelay(periodCount, difficulty);
      };

  public static DifficultyCalculator DIFFICULTY_BOMB_REMOVED =
      (time, parent, protocolContext) -> {
        final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
        final BigInteger difficulty =
            ensureMinimumDifficulty(
                BigInteger.valueOf(Math.max(1 - (time - parent.getTimestamp()) / 10, -99L))
                    .multiply(parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR))
                    .add(parentDifficulty));
        return difficulty;
      };

  public static DifficultyCalculator EIP100 =
      (time, parent, protocolContext) -> {
        final BigInteger parentDifficulty = difficulty(parent.getDifficulty());
        final boolean hasOmmers = !parent.getOmmersHash().equals(Hash.EMPTY_LIST_HASH);
        final BigInteger difficulty =
            ensureMinimumDifficulty(
                BigInteger.valueOf(byzantiumX(time, parent.getTimestamp(), hasOmmers))
                    .multiply(parentDifficulty.divide(DIFFICULTY_BOUND_DIVISOR))
                    .add(parentDifficulty));
        return difficulty;
      };

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

  private static BigInteger adjustForDifficultyDelay(
      final long periodCount, final BigInteger difficulty) {
    return difficulty.add(BIGINT_2.pow(Ints.checkedCast(periodCount - DELAY - 2)));
  }

  private static BigInteger adjustForDifficultyPause(
      final long periodCount, final BigInteger difficulty) {
    return difficulty.add(BIGINT_2.pow(Ints.checkedCast(periodCount - 2)));
  }

  private static BigInteger ensureMinimumDifficulty(final BigInteger difficulty) {
    return difficulty.compareTo(MINIMUM_DIFFICULTY) < 0 ? MINIMUM_DIFFICULTY : difficulty;
  }

  private static BigInteger difficulty(final Quantity value) {
    return value.getAsBigInteger();
  }
}

/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;

import org.junit.Test;

public class GasLimitCalculatorTest {
  @Test
  public void verifyGasLimitIsIncreasedWithinLimits() {
    Function<Long, Long> gasLimitCalculator = new GasLimitCalculator(Optional.of(10_000_000L));
    assertThat(gasLimitCalculator.apply(8_000_000L))
        .isEqualTo(8_000_000L + GasLimitCalculator.ADJUSTMENT_FACTOR);
  }

  @Test
  public void verifyGasLimitIsDecreasedWithinLimits() {
    Function<Long, Long> gasLimitCalculator = new GasLimitCalculator(Optional.of(10_000_000L));
    assertThat(gasLimitCalculator.apply(12_000_000L))
        .isEqualTo(12_000_000L - GasLimitCalculator.ADJUSTMENT_FACTOR);
  }

  @Test
  public void verifyGasLimitReachesTarget() {
    final long target = 10_000_000L;
    final long offset = GasLimitCalculator.ADJUSTMENT_FACTOR / 2;
    Function<Long, Long> gasLimitCalculator = new GasLimitCalculator(Optional.of(target));
    assertThat(gasLimitCalculator.apply(target - offset)).isEqualTo(target);
    assertThat(gasLimitCalculator.apply(target + offset)).isEqualTo(target);
  }

  @Test
  public void verifyNoNegative() {
    final long target = 0L;
    final long offset = GasLimitCalculator.ADJUSTMENT_FACTOR / 2;
    Function<Long, Long> gasLimitCalculator = new GasLimitCalculator(Optional.of(target));
    assertThat(gasLimitCalculator.apply(target + offset)).isEqualTo(target);
  }

  @Test
  public void verifyNoOverflow() {
    final long target = Long.MAX_VALUE;
    final long offset = GasLimitCalculator.ADJUSTMENT_FACTOR / 2;
    Function<Long, Long> gasLimitCalculator = new GasLimitCalculator(Optional.of(target));
    assertThat(gasLimitCalculator.apply(target - offset)).isEqualTo(target);
  }
}

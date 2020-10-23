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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class TargetingGasLimitCalculatorTest {
  @Test
  public void verifyGasLimitIsIncreasedWithinLimits() {
    TargetingGasLimitCalculator targetingGasLimitCalculator =
        new TargetingGasLimitCalculator(10_000_000L);
    assertThat(targetingGasLimitCalculator.nextGasLimit(8_000_000L))
        .isEqualTo(8_000_000L + TargetingGasLimitCalculator.ADJUSTMENT_FACTOR);
  }

  @Test
  public void verifyGasLimitIsDecreasedWithinLimits() {
    TargetingGasLimitCalculator targetingGasLimitCalculator =
        new TargetingGasLimitCalculator(10_000_000L);
    assertThat(targetingGasLimitCalculator.nextGasLimit(12_000_000L))
        .isEqualTo(12_000_000L - TargetingGasLimitCalculator.ADJUSTMENT_FACTOR);
  }

  @Test
  public void verifyGasLimitReachesTarget() {
    final long target = 10_000_000L;
    final long offset = TargetingGasLimitCalculator.ADJUSTMENT_FACTOR / 2;
    TargetingGasLimitCalculator targetingGasLimitCalculator =
        new TargetingGasLimitCalculator(target);
    assertThat(targetingGasLimitCalculator.nextGasLimit(target - offset)).isEqualTo(target);
    assertThat(targetingGasLimitCalculator.nextGasLimit(target + offset)).isEqualTo(target);
  }

  @Test
  public void verifyNoNegative() {
    final long target = 0L;
    final long offset = TargetingGasLimitCalculator.ADJUSTMENT_FACTOR / 2;
    TargetingGasLimitCalculator targetingGasLimitCalculator =
        new TargetingGasLimitCalculator(target);
    assertThat(targetingGasLimitCalculator.nextGasLimit(target + offset)).isEqualTo(target);
  }

  @Test
  public void verifyNoOverflow() {
    final long target = Long.MAX_VALUE;
    final long offset = TargetingGasLimitCalculator.ADJUSTMENT_FACTOR / 2;
    TargetingGasLimitCalculator targetingGasLimitCalculator =
        new TargetingGasLimitCalculator(target);
    assertThat(targetingGasLimitCalculator.nextGasLimit(target - offset)).isEqualTo(target);
  }

  @Test
  public void changeTargetGasLimit() {
    final long targetGasLimit = 10_000_000L;
    final TargetingGasLimitCalculator targetingGasLimitCalculator =
        new TargetingGasLimitCalculator(targetGasLimit);

    assertThat(targetingGasLimitCalculator.nextGasLimit(targetGasLimit)).isEqualTo(targetGasLimit);
    targetingGasLimitCalculator.changeTargetGasLimit(8_000_000L);
    assertThat(targetingGasLimitCalculator.nextGasLimit(targetGasLimit))
        .isEqualTo(targetGasLimit - TargetingGasLimitCalculator.ADJUSTMENT_FACTOR);
  }

  @Test
  public void changeTargetGasLimitInvalidValue() {
    assertThatThrownBy(() -> new TargetingGasLimitCalculator(-1L))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new TargetingGasLimitCalculator(0L).changeTargetGasLimit(-1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

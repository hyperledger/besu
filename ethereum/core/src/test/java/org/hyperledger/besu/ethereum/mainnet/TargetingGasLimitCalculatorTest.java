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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MAX_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification.DEFAULT_MIN_GAS_LIMIT;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.headervalidationrules.GasLimitRangeAndDeltaValidationRule;

import org.junit.Test;

public class TargetingGasLimitCalculatorTest {
  private static final long ADJUSTMENT_FACTOR = 1024L;

  @Test
  public void verifyGasLimitIsIncreasedWithinLimits() {
    FrontierTargetingGasLimitCalculator targetingGasLimitCalculator =
        new FrontierTargetingGasLimitCalculator();
    assertThat(targetingGasLimitCalculator.nextGasLimit(8_000_000L, 10_000_000L, 1L))
        .isEqualTo(8_000_000L + ADJUSTMENT_FACTOR);
  }

  @Test
  public void verifyGasLimitIsDecreasedWithinLimits() {
    FrontierTargetingGasLimitCalculator targetingGasLimitCalculator =
        new FrontierTargetingGasLimitCalculator();
    assertThat(targetingGasLimitCalculator.nextGasLimit(12_000_000L, 10_000_000L, 1L))
        .isEqualTo(12_000_000L - ADJUSTMENT_FACTOR);
  }

  @Test
  public void verifyGasLimitReachesTarget() {
    final long target = 10_000_000L;
    final long offset = ADJUSTMENT_FACTOR / 2;
    FrontierTargetingGasLimitCalculator targetingGasLimitCalculator =
        new FrontierTargetingGasLimitCalculator();
    assertThat(targetingGasLimitCalculator.nextGasLimit(target - offset, target, 1L))
        .isEqualTo(target);
    assertThat(targetingGasLimitCalculator.nextGasLimit(target + offset, target, 1L))
        .isEqualTo(target);
  }

  @Test
  public void verifyMinGasLimit() {
    assertThat(AbstractGasLimitSpecification.isValidTargetGasLimit(DEFAULT_MIN_GAS_LIMIT - 1))
        .isFalse();
  }

  @Test
  public void verifyWithinGasLimitDelta() {
    final long targetGasLimit = 10_000_000L;
    final long currentGasLimit = 1024L * 1024L;
    final FrontierTargetingGasLimitCalculator targetingGasLimitCalculator =
        new FrontierTargetingGasLimitCalculator();
    final GasLimitRangeAndDeltaValidationRule rule =
        new GasLimitRangeAndDeltaValidationRule(DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT);

    // parent
    final BlockHeader parent = new BlockHeaderTestFixture().gasLimit(currentGasLimit).buildHeader();

    // current
    final long nextGasLimit =
        targetingGasLimitCalculator.nextGasLimit(parent.getGasLimit(), targetGasLimit, 1L);
    final BlockHeader header = new BlockHeaderTestFixture().gasLimit(nextGasLimit).buildHeader();

    assertThat(rule.validate(header, parent)).isTrue();
  }
}

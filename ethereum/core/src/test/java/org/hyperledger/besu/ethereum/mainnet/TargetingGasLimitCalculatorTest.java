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

import org.junit.jupiter.api.Test;

public class TargetingGasLimitCalculatorTest {
  private static final long ADJUSTMENT_FACTOR = 1024L;

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
  public void verifyAdjustmentDeltas() {
    assertDeltas(20000000L, 20019530L, 19980470L);
    assertDeltas(40000000L, 40039061L, 39960939L);
  }

  private void assertDeltas(
      final long gasLimit, final long expectedIncrease, final long expectedDecrease) {
    FrontierTargetingGasLimitCalculator targetingGasLimitCalculator =
        new FrontierTargetingGasLimitCalculator();
    // increase
    assertThat(targetingGasLimitCalculator.nextGasLimit(gasLimit, gasLimit * 2, 1L))
        .isEqualTo(expectedIncrease);
    // decrease
    assertThat(targetingGasLimitCalculator.nextGasLimit(gasLimit, 0, 1L))
        .isEqualTo(expectedDecrease);
    // small decrease
    assertThat(targetingGasLimitCalculator.nextGasLimit(gasLimit, gasLimit - 1, 1L))
        .isEqualTo(gasLimit - 1);
    // small increase
    assertThat(targetingGasLimitCalculator.nextGasLimit(gasLimit, gasLimit + 1, 1L))
        .isEqualTo(gasLimit + 1);
    // no change
    assertThat(targetingGasLimitCalculator.nextGasLimit(gasLimit, gasLimit, 1L))
        .isEqualTo(gasLimit);
  }

  @Test
  public void verifyMinGasLimit() {
    assertThat(AbstractGasLimitSpecification.isValidTargetGasLimit(DEFAULT_MIN_GAS_LIMIT - 1))
        .isFalse();
  }

  @Test
  public void verifyMaxGasLimit() {
    assertThat(AbstractGasLimitSpecification.isValidTargetGasLimit(Long.MAX_VALUE - 1)).isTrue();
    assertThat(AbstractGasLimitSpecification.isValidTargetGasLimit(Long.MAX_VALUE)).isTrue();
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

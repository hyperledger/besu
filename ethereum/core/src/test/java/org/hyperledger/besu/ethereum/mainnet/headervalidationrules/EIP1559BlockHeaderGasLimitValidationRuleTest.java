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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.mainnet.headervalidationrules.EIP1559Helper.blockHeader;
import static org.hyperledger.besu.ethereum.mainnet.headervalidationrules.EIP1559Helper.disableEIP1559;
import static org.hyperledger.besu.ethereum.mainnet.headervalidationrules.EIP1559Helper.enableEIP1559;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.FeeMarket;

import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EIP1559BlockHeaderGasLimitValidationRuleTest {

  private static final long FORK_BLOCK = 800L;
  private final EIP1559 eip1559 = new EIP1559(FORK_BLOCK);
  private EIP1559BlockHeaderGasLimitValidationRule validationRule;
  private final FeeMarket feeMarket = FeeMarket.eip1559();
  private long finalizedForkBlock;

  @Before
  public void setUp() {
    validationRule = new EIP1559BlockHeaderGasLimitValidationRule(eip1559);
    finalizedForkBlock = FORK_BLOCK + feeMarket.getDecayRange();
  }

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void eipActivationShouldBeGuardedProperly() {
    disableEIP1559();
    assertThatThrownBy(
            () -> validationRule.validate(blockHeader(FORK_BLOCK - 1, 0, Optional.empty()), null))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("EIP-1559 is not enabled");
  }

  @Test
  public void shouldReturnTrueBeforeFork() {
    enableEIP1559();
    assertThat(validationRule.validate(blockHeader(FORK_BLOCK - 1, 0, Optional.empty()), null))
        .isTrue();
  }

  @Test
  public void shouldReturnTrueIfMaxGasLimitAfterFinalizedFork() {
    enableEIP1559();
    assertThat(
            validationRule.validate(
                blockHeader(finalizedForkBlock + 1, 0, Optional.empty(), feeMarket.getMaxGas()),
                null))
        .isTrue();
  }

  @Test
  public void shouldReturnFalseIfNotMaxGasLimitAfterFinalizedFork() {
    enableEIP1559();
    assertThat(
            validationRule.validate(
                blockHeader(finalizedForkBlock + 1, 0, Optional.empty(), feeMarket.getMaxGas() - 1),
                null))
        .isFalse();
  }

  @Test
  public void shouldReturnTrueIfValidGasLimitAfterFork() {
    enableEIP1559();
    assertThat(
            validationRule.validate(
                blockHeader(
                    FORK_BLOCK + 1,
                    0,
                    Optional.empty(),
                    (feeMarket.getMaxGas() / 2) + feeMarket.getGasIncrementAmount()),
                null))
        .isTrue();
  }

  @Test
  public void shouldReturnFalseIfInvalidGasLimitAfterFork() {
    enableEIP1559();
    assertThat(
            validationRule.validate(
                blockHeader(FORK_BLOCK + 1, 0, Optional.empty(), feeMarket.getMaxGas() - 1), null))
        .isFalse();
  }
}

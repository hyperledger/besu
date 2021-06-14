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
import static org.hyperledger.besu.ethereum.mainnet.headervalidationrules.EIP1559Helper.blockHeader;

import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.FeeMarket;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class EIP1559BlockHeaderGasPriceValidationRuleTest {

  private static final long FORK_BLOCK = 800L;
  private final EIP1559 eip1559 = new EIP1559(FORK_BLOCK);
  private EIP1559BlockHeaderGasPriceValidationRule validationRule;
  private final FeeMarket feeMarket = FeeMarket.eip1559();

  @Before
  public void setUp() {
    validationRule = new EIP1559BlockHeaderGasPriceValidationRule(eip1559);
  }

  @Test
  public void shouldReturnTrueBeforeFork() {
    assertThat(validationRule.validate(blockHeader(FORK_BLOCK - 1, 0, Optional.empty()), null))
        .isTrue();
  }

  @Test
  public void shouldReturnTrueIfInitialBaseFeeAtForkBlock() {
    assertThat(
            validationRule.validate(
                blockHeader(FORK_BLOCK, 0, Optional.of(feeMarket.getInitialBasefee())), null))
        .isTrue();
  }

  @Test
  public void shouldReturnFalseIfNotInitialBaseFeeAtForkBlock() {
    assertThat(
            validationRule.validate(
                blockHeader(FORK_BLOCK, 0, Optional.of(feeMarket.getInitialBasefee() - 1)), null))
        .isFalse();
  }

  @Test
  public void shouldReturnIfNoBaseFeeAfterForkBlock() {
    assertThat(
            validationRule.validate(
                blockHeader(FORK_BLOCK + 2, 0, Optional.empty()),
                blockHeader(FORK_BLOCK + 1, 0, Optional.empty())))
        .isFalse();
  }
}

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
package org.hyperledger.besu.ethereum.core.fees;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EIP1559Test {

  private static final long FORK_BLOCK = 783L;
  private final EIP1559 eip1559 = new EIP1559(FORK_BLOCK);
  private final FeeMarket feeMarket = FeeMarket.eip1559();
  private static final long TARGET_GAS_USED = 10000000L;

  @Before
  public void setUp() {
    ExperimentalEIPs.eip1559Enabled = true;
  }

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void assertThatBaseFeeDecreasesWhenBelowTargetGasUsed() {
    assertThat(
            eip1559.computeBaseFee(
                FORK_BLOCK,
                feeMarket.getInitialBasefee(),
                TARGET_GAS_USED - 1000000L,
                TARGET_GAS_USED))
        .isLessThan(feeMarket.getInitialBasefee())
        .isEqualTo(987500000L);
  }

  @Test
  public void assertThatBaseFeeIncreasesWhenAboveTargetGasUsed() {
    assertThat(
            eip1559.computeBaseFee(
                FORK_BLOCK,
                feeMarket.getInitialBasefee(),
                TARGET_GAS_USED + 1000000L,
                TARGET_GAS_USED))
        .isGreaterThan(feeMarket.getInitialBasefee())
        .isEqualTo(1012500000L);
  }

  @Test
  public void givenBlockAfterFork_whenIsEIP1559_returnsTrue() {
    assertThat(eip1559.isEIP1559(FORK_BLOCK + 1)).isTrue();
  }

  @Test
  public void givenBlockABeforeFork_whenIsEIP1559_returnsFalse() {
    assertThat(eip1559.isEIP1559(FORK_BLOCK - 1)).isFalse();
  }

  @Test
  public void givenForkBlock_whenIsForkBlock_thenReturnsTrue() {
    assertThat(eip1559.isForkBlock(FORK_BLOCK)).isTrue();
  }

  @Test
  public void givenNotForkBlock_whenIsForkBlock_thenReturnsFalse() {
    assertThat(eip1559.isForkBlock(FORK_BLOCK + 1)).isFalse();
  }

  @Test
  public void getForkBlock() {
    assertThat(eip1559.getForkBlock()).isEqualTo(FORK_BLOCK);
  }
}

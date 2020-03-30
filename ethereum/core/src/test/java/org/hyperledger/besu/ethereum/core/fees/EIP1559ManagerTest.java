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

import org.junit.Test;

public class EIP1559ManagerTest {

  private static final long FORK_BLOCK = 783L;
  private final EIP1559Manager eip1559 = new EIP1559Manager(FORK_BLOCK);

  @Test
  public void assertThatBaseFeeDecreasesWhenBelowTargetGasUsed() {
    assertThat(
            eip1559.computeBaseFee(
                EIP1559Config.INITIAL_BASEFEE, EIP1559Config.TARGET_GAS_USED - 1000000L))
        .isLessThan(EIP1559Config.INITIAL_BASEFEE)
        .isEqualTo(987500000L);
  }

  @Test
  public void assertThatBaseFeeIncreasesWhenAboveTargetGasUsed() {
    assertThat(
            eip1559.computeBaseFee(
                EIP1559Config.INITIAL_BASEFEE, EIP1559Config.TARGET_GAS_USED + 1000000L))
        .isGreaterThan(EIP1559Config.INITIAL_BASEFEE)
        .isEqualTo(1012500000L);
  }

  @Test
  public void assertThatBaseFeeDoesNotChangeWhenAtTargetGasUsed() {
    assertThat(eip1559.computeBaseFee(EIP1559Config.INITIAL_BASEFEE, EIP1559Config.TARGET_GAS_USED))
        .isEqualTo(EIP1559Config.INITIAL_BASEFEE);
  }

  @Test
  public void isValidBaseFee() {
    assertThat(eip1559.isValidBaseFee(EIP1559Config.INITIAL_BASEFEE, 1012500000L)).isTrue();
  }

  @Test
  public void isNotValidBaseFee() {
    assertThat(
            eip1559.isValidBaseFee(
                EIP1559Config.INITIAL_BASEFEE, EIP1559Config.INITIAL_BASEFEE * 15L / 10L))
        .isFalse();
  }

  @Test
  public void eip1559GasPool() {}

  @Test
  public void legacyGasPool() {}

  @Test
  public void givenBlockAfterFork_whenIsEIP1559_returnsTrue() {
    assertThat(eip1559.isEIP1559(FORK_BLOCK + 1)).isTrue();
  }

  @Test
  public void givenBlockABeforeFork_whenIsEIP1559_returnsFalse() {
    assertThat(eip1559.isEIP1559(FORK_BLOCK - 1)).isFalse();
  }

  @Test
  public void givenBlockAfterEIPFinalized_whenIsEIP1559Finalized_returnsTrue() {
    assertThat(eip1559.isEIP1559Finalized(FORK_BLOCK + EIP1559Config.EIP1559_DECAY_RANGE)).isTrue();
  }

  @Test
  public void givenBlockBeforeEIPFinalized_whenIsEIP1559Finalized_returnsFalse() {
    assertThat(eip1559.isEIP1559Finalized(FORK_BLOCK + EIP1559Config.EIP1559_DECAY_RANGE - 1))
        .isFalse();
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

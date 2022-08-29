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
package org.hyperledger.besu.ethereum.core.feemarket;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import org.junit.Test;

public class BaseFeeMarketTest {

  private static final long FORK_BLOCK = 783L;
  private final BaseFeeMarket baseFeeMarket = FeeMarket.london(FORK_BLOCK);
  private static final long TARGET_GAS_USED = 10000000L;

  @Test
  public void assertThatBaseFeeDecreasesWhenBelowTargetGasUsed() {
    assertThat(
            baseFeeMarket.computeBaseFee(
                FORK_BLOCK + 1,
                baseFeeMarket.getInitialBasefee(),
                TARGET_GAS_USED - 1000000L,
                TARGET_GAS_USED))
        .isLessThan(baseFeeMarket.getInitialBasefee())
        .isEqualTo(Wei.of(987500000L));
  }

  @Test
  public void assertThatBaseFeeIncreasesWhenAboveTargetGasUsed() {
    assertThat(
            baseFeeMarket.computeBaseFee(
                FORK_BLOCK + 1,
                baseFeeMarket.getInitialBasefee(),
                TARGET_GAS_USED + 1000000L,
                TARGET_GAS_USED))
        .isGreaterThan(baseFeeMarket.getInitialBasefee())
        .isEqualTo(Wei.of(1012500000L));
  }

  @Test
  public void givenForkBlock_whenBaseFeeValidationMode_thenReturnsInitial() {
    assertThat(baseFeeMarket.baseFeeValidationMode(FORK_BLOCK))
        .isEqualTo(BaseFeeMarket.ValidationMode.INITIAL);
  }

  @Test
  public void givenNotForkBlock_whenBaseFeeValidationMode_thenReturnsOngoing() {
    assertThat(baseFeeMarket.baseFeeValidationMode(FORK_BLOCK + 1))
        .isEqualTo(BaseFeeMarket.ValidationMode.ONGOING);
  }

  @Test
  public void givenForkBlock_whenGasLimitValidationMode_thenReturnsInitial() {
    assertThat(baseFeeMarket.gasLimitValidationMode(FORK_BLOCK))
        .isEqualTo(BaseFeeMarket.ValidationMode.INITIAL);
  }

  @Test
  public void givenNotForkBlock_whenGasLimitValidationMode_thenReturnsOngoing() {
    assertThat(baseFeeMarket.gasLimitValidationMode(FORK_BLOCK + 1))
        .isEqualTo(BaseFeeMarket.ValidationMode.ONGOING);
  }
}

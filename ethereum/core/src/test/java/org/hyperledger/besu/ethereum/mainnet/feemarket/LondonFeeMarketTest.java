/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet.feemarket;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;

import java.util.Optional;

import org.junit.jupiter.api.Test;

public class LondonFeeMarketTest {

  private static final KeyPair KEY_PAIR1 =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();

  @Test
  public void satisfiesFloorTxCost() {
    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.FRONTIER)
            .gasPrice(Wei.of(7))
            .createTransaction(KEY_PAIR1);

    final BaseFeeMarket londonFeeMarket = FeeMarket.london(0);
    assertThat(londonFeeMarket.satisfiesFloorTxFee(transaction)).isTrue();
  }

  @Test
  public void maxFeePerGasLessThanMinimumBaseFee() {
    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .maxFeePerGas(Optional.of(Wei.of(6)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(0)))
            .gasPrice(null)
            .createTransaction(KEY_PAIR1);

    final BaseFeeMarket londonFeeMarket = FeeMarket.london(0);
    assertThat(londonFeeMarket.satisfiesFloorTxFee(transaction)).isFalse();
  }

  @Test
  public void satisfiesFloorTxCostWhenBaseFeeInitialValueIsZero() {
    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .maxFeePerGas(Optional.of(Wei.ZERO))
            .maxPriorityFeePerGas(Optional.of(Wei.ZERO))
            .gasPrice(null)
            .createTransaction(KEY_PAIR1);

    final BaseFeeMarket londonFeeMarket = FeeMarket.london(0, Optional.of(Wei.ZERO));
    assertThat(londonFeeMarket.satisfiesFloorTxFee(transaction)).isTrue();
  }
}

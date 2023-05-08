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
import static org.hyperledger.besu.plugin.data.TransactionType.ACCESS_LIST;
import static org.hyperledger.besu.plugin.data.TransactionType.EIP1559;
import static org.hyperledger.besu.plugin.data.TransactionType.FRONTIER;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TransactionPriceCalculatorTest {

  private static final TransactionPriceCalculator FRONTIER_CALCULATOR =
      TransactionPriceCalculator.frontier();
  private static final TransactionPriceCalculator EIP_1559_CALCULATOR =
      TransactionPriceCalculator.eip1559();

  private final TransactionPriceCalculator transactionPriceCalculator;
  private final TransactionType transactionType;
  private final Wei gasPrice;
  private final Wei maxPriorityFeePerGas;
  private final Wei maxFeePerGas;
  private final Optional<Wei> baseFee;
  private final Wei expectedPrice;

  public TransactionPriceCalculatorTest(
      final TransactionPriceCalculator transactionPriceCalculator,
      final TransactionType transactionType,
      final Wei gasPrice,
      final Wei maxPriorityFeePerGas,
      final Wei maxFeePerGas,
      final Optional<Wei> baseFee,
      final Wei expectedPrice) {
    this.transactionPriceCalculator = transactionPriceCalculator;
    this.transactionType = transactionType;
    this.gasPrice = gasPrice;
    this.maxPriorityFeePerGas = maxPriorityFeePerGas;
    this.maxFeePerGas = maxFeePerGas;
    this.baseFee = baseFee;
    this.expectedPrice = expectedPrice;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          // legacy transaction must return gas price
          {FRONTIER_CALCULATOR, FRONTIER, Wei.of(578L), null, null, Optional.empty(), Wei.of(578L)},
          // legacy transaction zero price
          {FRONTIER_CALCULATOR, FRONTIER, Wei.ZERO, null, null, Optional.empty(), Wei.ZERO},
          // ACCESSLIST transaction must return gas price
          {
            FRONTIER_CALCULATOR,
            ACCESS_LIST,
            Wei.of(578L),
            null,
            null,
            Optional.empty(),
            Wei.of(578L)
          },
          // legacy transaction must return gas price
          {
            EIP_1559_CALCULATOR,
            FRONTIER,
            Wei.of(578L),
            null,
            null,
            Optional.of(Wei.of(150L)),
            Wei.of(578L)
          },
          // london legacy transaction zero price
          {EIP_1559_CALCULATOR, FRONTIER, Wei.ZERO, null, null, Optional.of(Wei.ZERO), Wei.ZERO},
          // ACCESSLIST transaction must return gas price
          {
            EIP_1559_CALCULATOR,
            ACCESS_LIST,
            Wei.of(578L),
            null,
            null,
            Optional.of(Wei.of(150L)),
            Wei.of(578L)
          },
          // EIP-1559 must return maxPriorityFeePerGas + base fee
          {
            EIP_1559_CALCULATOR,
            EIP1559,
            null,
            Wei.of(100L),
            Wei.of(300L),
            Optional.of(Wei.of(150L)),
            Wei.of(250L)
          },
          // EIP-1559 must return fee cap
          {
            EIP_1559_CALCULATOR,
            EIP1559,
            null,
            Wei.of(100L),
            Wei.of(300L),
            Optional.of(Wei.of(250L)),
            Wei.of(300L)
          },
          // EIP-1559 transaction zero price
          {EIP_1559_CALCULATOR, EIP1559, null, Wei.ZERO, Wei.ZERO, Optional.of(Wei.ZERO), Wei.ZERO}
        });
  }

  @Test
  public void assertThatCalculatorWorks() {
    assertThat(
            transactionPriceCalculator.price(
                Transaction.builder()
                    .type(transactionType)
                    .accessList(transactionType == ACCESS_LIST ? Collections.emptyList() : null)
                    .gasPrice(gasPrice)
                    .maxPriorityFeePerGas(maxPriorityFeePerGas)
                    .maxFeePerGas(maxFeePerGas)
                    .chainId(BigInteger.ONE)
                    .build(),
                baseFee))
        .isEqualByComparingTo(expectedPrice);
  }
}

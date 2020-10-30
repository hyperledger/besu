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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
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
  private final Wei gasPrice;
  private final Wei gasPremium;
  private final Wei feeCap;
  private final Optional<Long> baseFee;
  private final Wei expectedPrice;

  public TransactionPriceCalculatorTest(
      final TransactionPriceCalculator transactionPriceCalculator,
      final Wei gasPrice,
      final Wei gasPremium,
      final Wei feeCap,
      final Optional<Long> baseFee,
      final Wei expectedPrice) {
    this.transactionPriceCalculator = transactionPriceCalculator;
    this.gasPrice = gasPrice;
    this.gasPremium = gasPremium;
    this.feeCap = feeCap;
    this.baseFee = baseFee;
    this.expectedPrice = expectedPrice;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          // legacy transaction must return gas price
          {FRONTIER_CALCULATOR, Wei.of(578L), null, null, Optional.empty(), Wei.of(578L)},
          // EIP-1559 must return gas premium + base fee
          {EIP_1559_CALCULATOR, null, Wei.of(100L), Wei.of(300L), Optional.of(150L), Wei.of(250L)},
          // EIP-1559 must return fee cap
          {EIP_1559_CALCULATOR, null, Wei.of(100L), Wei.of(300L), Optional.of(250L), Wei.of(300L)}
        });
  }

  @Test
  public void assertThatCalculatorWorks() {
    assertThat(
            transactionPriceCalculator.price(
                Transaction.builder()
                    .gasPrice(gasPrice)
                    .gasPremium(gasPremium)
                    .feeCap(feeCap)
                    .build(),
                baseFee))
        .isEqualByComparingTo(expectedPrice);
  }

  @Before
  public void setUp() {
    ExperimentalEIPs.eip1559Enabled = true;
  }

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }
}

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
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Wei;

import java.util.Arrays;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CoinbaseFeePriceCalculatorTest {

  private static final CoinbaseFeePriceCalculator FRONTIER_CALCULATOR =
      CoinbaseFeePriceCalculator.frontier();
  private static final CoinbaseFeePriceCalculator EIP_1559_CALCULATOR =
      CoinbaseFeePriceCalculator.eip1559();

  private final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator;
  private final Gas coinbaseFee;
  private final Wei price;
  private final Wei burned;
  private final Wei expectedPrice;

  public CoinbaseFeePriceCalculatorTest(
      final CoinbaseFeePriceCalculator coinbaseFeePriceCalculator,
      final Gas coinbaseFee,
      final Wei price,
      final Wei burned,
      final Wei expectedPrice) {
    this.coinbaseFeePriceCalculator = coinbaseFeePriceCalculator;
    this.coinbaseFee = coinbaseFee;
    this.price = price;
    this.burned = burned;
    this.expectedPrice = expectedPrice;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          // legacy transaction must return gas price * gas
          {FRONTIER_CALCULATOR, Gas.of(100), Wei.of(10L), Wei.ZERO, Wei.of(1000L)},
          // EIP-1559 must return gas * (gas price - base fee)
          {EIP_1559_CALCULATOR, Gas.of(100), Wei.of(10L), Wei.of(4L), Wei.of(600L)}
        });
  }

  @Test
  public void assertThatCalculatorWorks() {
    assertThat(coinbaseFeePriceCalculator.price(coinbaseFee, price, burned))
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

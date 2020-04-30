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

import java.util.Arrays;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EIP1559BaseFeeTest {

  private static final long MARKER_BASE_FEE = 1049238967;
  private final EIP1559 eip1559 = new EIP1559(0L);
  private static final FeeMarket FEE_MARKET = FeeMarket.eip1559();

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            FEE_MARKET.getInitialBasefee(),
            FEE_MARKET.getTargetGasUsed(),
            FEE_MARKET.getInitialBasefee()
          },
          {FEE_MARKET.getInitialBasefee(), 7000000, 962500000},
          {1100000000, FEE_MARKET.getTargetGasUsed(), 1100000000},
          {1100000000, 9000000, 1086250000},
          {1086250000, 9000000, 1072671875},
          {1072671875, 9000000, 1059263476},
          {1059263476, 10001000, 1059276716},
          {1059276716, 16000000, 1138722469},
          {MARKER_BASE_FEE, 0, 918084097},
          {MARKER_BASE_FEE, 5, 918084161},
          {MARKER_BASE_FEE, 5000, 918149673},
          {MARKER_BASE_FEE, 500000, 924641839},
          {MARKER_BASE_FEE, FEE_MARKET.getTargetGasUsed(), MARKER_BASE_FEE},
          {MARKER_BASE_FEE, FEE_MARKET.getMaxGas(), 1180393837}
        });
  }

  private final long parentBaseFee;
  private final long parentGasUsed;
  private final long expectedBaseFee;

  public EIP1559BaseFeeTest(
      final long parentBaseFee, final long parentGasUsed, final long expectedBaseFee) {
    this.parentBaseFee = parentBaseFee;
    this.parentGasUsed = parentGasUsed;
    this.expectedBaseFee = expectedBaseFee;
  }

  @Before
  public void setUp() {
    ExperimentalEIPs.eip1559Enabled = true;
  }

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void assertThatBaseFeeIsCorrect() {
    assertThat(eip1559.computeBaseFee(parentBaseFee, parentGasUsed)).isEqualTo(expectedBaseFee);
  }
}

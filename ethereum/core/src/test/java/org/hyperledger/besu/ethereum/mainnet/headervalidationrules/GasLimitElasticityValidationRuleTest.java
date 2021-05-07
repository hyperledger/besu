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

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;

import java.util.Arrays;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GasLimitElasticityValidationRuleTest {

  private static final EIP1559 eip1559 = new EIP1559(10);

  @Parameterized.Parameter public long headerGasLimit;

  @Parameterized.Parameter(1)
  public long parentGasLimit;

  @Parameterized.Parameter(2)
  public long headerNumber;

  @Parameterized.Parameter(3)
  public boolean expectedResult;

  public GasLimitElasticityValidationRule uut = new GasLimitElasticityValidationRule(eip1559);

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {8184000, 4096000, 10, true},
          {8200000, 4096000, 10, true},
          {8182000, 4096000, 10, false},
          {8202000, 4096000, 10, false},
          {4092000, 4096000, 11, true},
          {4100000, 4096000, 11, true},
          {4091000, 4096000, 11, false},
          {4101000, 4096000, 11, false}
        });
  }

  @BeforeClass
  public static void initialize() {
    ExperimentalEIPs.eip1559Enabled = true;
  }

  @AfterClass
  public static void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void test() {
    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();

    blockHeaderBuilder.number(headerNumber);
    blockHeaderBuilder.gasLimit(headerGasLimit);
    final BlockHeader header = blockHeaderBuilder.buildHeader();

    blockHeaderBuilder.number(headerNumber);
    blockHeaderBuilder.gasLimit(parentGasLimit);
    final BlockHeader parent = blockHeaderBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isEqualTo(expectedResult);
  }
}

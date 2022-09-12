/*
 * Copyright Hyperledger Besu Contributors.
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

import static java.lang.Long.MAX_VALUE;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.ZeroBaseFeeMarket;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GasLimitElasticityValidationRuleZeroBaseFeeMarketTest {

  private static final Optional<BaseFeeMarket> zeroBaseFeeMarket =
      Optional.of(new ZeroBaseFeeMarket(10));

  @Parameter public long headerGasLimit;

  @Parameter(1)
  public long parentGasLimit;

  @Parameter(2)
  public long headerNumber;

  @Parameter(3)
  public boolean expectedResult;

  public GasLimitRangeAndDeltaValidationRule uut =
      new GasLimitRangeAndDeltaValidationRule(5000, MAX_VALUE, zeroBaseFeeMarket);

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {20000000, 10000000, 10, true},
          {20019530, 10000000, 10, true},
          {20019531, 10000000, 10, false},
          {19980470, 10000000, 10, true},
          {19980469, 10000000, 10, false},
          {20000000, 20000000, 11, true},
          {20019530, 20000000, 11, true},
          {20019531, 20000000, 11, false},
          {19980470, 20000000, 11, true},
          {19980469, 20000000, 11, false},
          {40039061, 40000000, 11, true},
          {40039062, 40000000, 11, false},
          {39960939, 40000000, 11, true},
          {39960938, 40000000, 11, false},
          {4999, 40000000, 11, false}
        });
  }

  @Test
  public void test() {

    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();

    blockHeaderBuilder.number(headerNumber);
    blockHeaderBuilder.gasLimit(headerGasLimit);
    final BlockHeader header = blockHeaderBuilder.buildHeader();

    blockHeaderBuilder.number(headerNumber - 1);
    blockHeaderBuilder.gasLimit(parentGasLimit);
    final BlockHeader parent = blockHeaderBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isEqualTo(expectedResult);
  }
}

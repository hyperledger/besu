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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GasLimitRangeAndDeltaValidationRuleTest {

  @Parameter public long headerGasLimit;

  @Parameter(1)
  public long parentGasLimit;

  @Parameter(2)
  public GasLimitRangeAndDeltaValidationRule uut;

  @Parameter(3)
  public boolean expectedResult;

  @Parameter(4)
  public Optional<Wei> optionalBaseFee;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {4096, 4096, new GasLimitRangeAndDeltaValidationRule(4095, 4097), true, Optional.empty()},
          // In Range, no change = valid,
          {
            4096, 4096, new GasLimitRangeAndDeltaValidationRule(4094, 4095), false, Optional.empty()
          },
          // Out of Range, no change = invalid,
          {4099, 4096, new GasLimitRangeAndDeltaValidationRule(4000, 4200), true, Optional.empty()},
          // In Range, <1/1024 change = valid,
          {4093, 4096, new GasLimitRangeAndDeltaValidationRule(4000, 4200), true, Optional.empty()},
          // In Range, ,1/1024 change = valid,
          {
            4092, 4096, new GasLimitRangeAndDeltaValidationRule(4000, 4200), false, Optional.empty()
          },
          // In Range, == 1/1024 change = invalid,
          {
            4100, 4096, new GasLimitRangeAndDeltaValidationRule(4000, 4200), false, Optional.empty()
          },
          // In Range, == 1/1024 change = invalid,
          {
            4099,
            4096,
            new GasLimitRangeAndDeltaValidationRule(4000, 4200),
            false,
            Optional.of(Wei.of(10L))
          }
          // In Range, <1/1024 change, has basefee = invalid,
        });
  }

  @Test
  public void test() {
    final BlockHeaderTestFixture blockHeaderBuilder = new BlockHeaderTestFixture();

    blockHeaderBuilder.gasLimit(headerGasLimit);
    optionalBaseFee.ifPresent(baseFee -> blockHeaderBuilder.baseFeePerGas(baseFee));
    final BlockHeader header = blockHeaderBuilder.buildHeader();

    blockHeaderBuilder.gasLimit(parentGasLimit);
    final BlockHeader parent = blockHeaderBuilder.buildHeader();

    assertThat(uut.validate(header, parent)).isEqualTo(expectedResult);
  }
}

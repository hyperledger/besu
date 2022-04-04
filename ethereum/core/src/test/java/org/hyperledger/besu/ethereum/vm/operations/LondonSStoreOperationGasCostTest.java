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
package org.hyperledger.besu.ethereum.vm.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.TestCodeExecutor;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LondonSStoreOperationGasCostTest {

  private static ProtocolSchedule protocolSchedule;

  @Parameters(name = "Code: {0}, Original: {1}")
  public static Object[][] scenarios() {
    // Tests specified in EIP-3539.
    return new Object[][] {
      {"0x60006000556000600055", 0, 212, 0},
      {"0x60006000556001600055", 0, 20112, 0},
      {"0x60016000556000600055", 0, 20112, 19900},
      {"0x60016000556002600055", 0, 20112, 0},
      {"0x60016000556001600055", 0, 20112, 0},
      {"0x60006000556000600055", 1, 3012, 4800},
      {"0x60006000556001600055", 1, 3012, 2800},
      {"0x60006000556002600055", 1, 3012, 0},
      {"0x60026000556000600055", 1, 3012, 4800},
      {"0x60026000556003600055", 1, 3012, 0},
      {"0x60026000556001600055", 1, 3012, 2800},
      {"0x60026000556002600055", 1, 3012, 0},
      {"0x60016000556000600055", 1, 3012, 4800},
      {"0x60016000556002600055", 1, 3012, 0},
      {"0x60016000556001600055", 1, 212, 0},
      {"0x600160005560006000556001600055", 0, 40118, 19900},
      {"0x600060005560016000556000600055", 1, 5918, 7600},
    };
  }

  private TestCodeExecutor codeExecutor;

  @Parameter public String code;

  @Parameter(value = 1)
  public int originalValue;

  @Parameter(value = 2)
  public int expectedGasUsed;

  @Parameter(value = 3)
  public int expectedGasRefund;

  @Before
  public void setUp() {
    protocolSchedule =
        MainnetProtocolSchedule.fromConfig(
            new StubGenesisConfigOptions().londonBlock(0), EvmConfiguration.DEFAULT);
    codeExecutor = new TestCodeExecutor(protocolSchedule);
  }

  @Test
  public void shouldCalculateGasAccordingToEip3529() {
    final long gasLimit = 1_000_000;
    final MessageFrame frame =
        codeExecutor.executeCode(
            code,
            gasLimit,
            account -> account.setStorageValue(UInt256.ZERO, UInt256.valueOf(originalValue)));
    assertThat(frame.getState()).isEqualTo(State.COMPLETED_SUCCESS);
    assertThat(frame.getRemainingGas()).isEqualTo(gasLimit - (expectedGasUsed + 2100));
    assertThat(frame.getGasRefund()).isEqualTo(expectedGasRefund);
  }
}

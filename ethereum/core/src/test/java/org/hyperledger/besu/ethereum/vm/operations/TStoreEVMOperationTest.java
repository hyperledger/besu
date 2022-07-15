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

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.core.TestCodeExecutor;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class TStoreEVMOperationTest {

  private static ProtocolSchedule protocolSchedule;

  @Parameters(name = "Code: {0}, Original: {1}, Gas usage: {2}")
  public static Object[][] scenarios() {
    // Tests specified in EIP-1153.
    return new Object[][] {
      {"0x60016000b46001b3", 0, 209},
    };
  }

  private TestCodeExecutor codeExecutor;

  @Parameter public String code;

  @Parameter(value = 1)
  public int originalValue;

  @Parameter(value = 2)
  public int expectedGasUsed;

  @Before
  public void setUp() {
    protocolSchedule =
        MainnetProtocolSchedule.fromConfig(
            new StubGenesisConfigOptions().shanghaiBlock(0), EvmConfiguration.DEFAULT);
    codeExecutor = new TestCodeExecutor(protocolSchedule);
  }

  @Test
  public void transientStorageExecutionTest() {
    final long gasLimit = 1_000_000;
    final MessageFrame frame =
        codeExecutor.executeCode(
            code,
            gasLimit,
            account -> account.setStorageValue(UInt256.ZERO, UInt256.valueOf(originalValue)));
    assertThat(frame.getState()).isEqualTo(State.COMPLETED_SUCCESS);
    assertThat(frame.getRemainingGas()).isEqualTo(gasLimit - expectedGasUsed);
    assertThat(frame.getGasRefund()).isEqualTo(0);
  }
}

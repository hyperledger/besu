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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.ByteCodeBuilder;
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
  private static final UInt256 gasLimit = UInt256.valueOf(50_000);
  private static final Address contractAddress = AddressHelpers.ofValue(28499200);

  @Parameters(name = "ByteCodeBuilder: {0}, Original: {1}, Expected Return Value: {2}")
  public static Object[][] scenarios() {
    // Tests specified in EIP-1153.
    return new Object[][] {
            // Can tstore
            {null, new ByteCodeBuilder().tstore(1, 1), 0, 0},
            // Can tload uninitialized
            {null,
                    new ByteCodeBuilder()
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0), 0, 0},
            // Can tload after tstore
            {null,
                    new ByteCodeBuilder()
                    .tstore(1, 2)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0), 0, 2},
            // Can tload after tstore from different location
            {null,
                    new ByteCodeBuilder()
                    .tstore(1, 2)
                    .tload(2)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0), 0, 0},
            // Contracts have separate transient storage
            {new ByteCodeBuilder()
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0),
                    new ByteCodeBuilder()
                    .tstore(1, 2)
                    .call(contractAddress, gasLimit)
                    .returnInnerCallResults(),
                    0,
                    0},
    };
  }

  private TestCodeExecutor codeExecutor;

  @Parameter(value = 0)
  public ByteCodeBuilder contractByteCodeBuilder;

  @Parameter(value = 1)
  public ByteCodeBuilder byteCodeBuilder;

  @Parameter(value = 2)
  public int originalValue;

  @Parameter(value = 3)
  public int expectedReturnValue;


  @Before
  public void setUp() {
    protocolSchedule =
        MainnetProtocolSchedule.fromConfig(
            new StubGenesisConfigOptions().shanghaiBlock(0), EvmConfiguration.DEFAULT);
    codeExecutor = new TestCodeExecutor(protocolSchedule,
            account -> account.setStorageValue(UInt256.ZERO, UInt256.valueOf(originalValue)));
  }

  @Test
  public void transientStorageExecutionTest() {

    if (contractByteCodeBuilder != null) {
      codeExecutor.deployContract(
            contractAddress,
            contractByteCodeBuilder.toString());
    }

    final MessageFrame frame =
        codeExecutor.executeCode(
            byteCodeBuilder.toString(),
            gasLimit.toLong());
    assertThat(frame.getState()).isEqualTo(State.COMPLETED_SUCCESS);
    // assertThat(frame.getRemainingGas()).isEqualTo(gasLimit - byteCodeBuilder.gasCost);
    assertThat(frame.getGasRefund()).isEqualTo(0);
    assertThat(UInt256.fromBytes(frame.getOutputData()).toInt()).isEqualTo(expectedReturnValue);
  }
}

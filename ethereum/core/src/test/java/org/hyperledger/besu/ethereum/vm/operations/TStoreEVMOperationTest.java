/*
 * Copyright Besu Contributors
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
import org.hyperledger.besu.ethereum.core.ByteCodeBuilder.Operation;
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

  private static final UInt256 gasLimit = UInt256.valueOf(50_000);
  private static final Address contractAddress = AddressHelpers.ofValue(28499200);

  @Parameters(name = "ByteCodeBuilder: {0}, Expected Result State: {1}, Expected Return Value: {2}")
  public static Object[][] scenarios() {
    // Tests specified in EIP-1153.
    return new Object[][] {
            // Can tstore
            {null, new ByteCodeBuilder().tstore(1, 1), State.COMPLETED_SUCCESS, 0, 1},
            // Can tload uninitialized
            {null,
                    new ByteCodeBuilder()
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0), State.COMPLETED_SUCCESS, 0, 1},
            // Can tload after tstore
            {null,
                    new ByteCodeBuilder()
                    .tstore(1, 2)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0), State.COMPLETED_SUCCESS, 2, 1},
            // Can tload after tstore from different location
            {null,
                    new ByteCodeBuilder()
                    .tstore(1, 2)
                    .tload(2)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0), State.COMPLETED_SUCCESS, 0, 1},
            // Contracts have separate transient storage
            {new ByteCodeBuilder()
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0),
                    new ByteCodeBuilder()
                            .tstore(1, 2)
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    0,
                    1},
            // Reentrant calls access the same transient storage
            {new ByteCodeBuilder()
                    // check if caller is self
                    .callerIs(contractAddress)
                    .conditionalJump(78)
                    // non-reentrant, call self after tstore
                    .tstore(1, 8)
                    .call(Operation.CALL, contractAddress, gasLimit)
                    .returnInnerCallResults()
                    // reentrant, TLOAD and return value
                    .op(Operation.JUMPDEST)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0),
                    new ByteCodeBuilder()
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Successfully returned calls do not revert transient storage writes
            {new ByteCodeBuilder()
                    // check if caller is self
                    .callerIs(contractAddress)
                    .conditionalJump(77)
                    // non-reentrant, call self after tstore
                    .tstore(1, 8)
                    .call(Operation.CALL, contractAddress, gasLimit)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0)
                    // reentrant, TLOAD and return value
                    .op(Operation.JUMPDEST)
                    .tstore(1, 9),
                    new ByteCodeBuilder()
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    9,
                    1},
            // Revert undoes the transient storage write from the failed call
            {new ByteCodeBuilder()
                    // check if caller is self
                    .callerIs(contractAddress)
                    .conditionalJump(77)
                    // non-reentrant, call self after tstore
                    .tstore(1, 8)
                    .call(Operation.CALL, contractAddress, gasLimit)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0)
                    // reentrant, TLOAD and return value
                    .op(Operation.JUMPDEST)
                    .tstore(1, 9)
                    .op(Operation.REVERT),
                    new ByteCodeBuilder()
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Revert undoes all the transient storage writes to the same key from the failed call
            {new ByteCodeBuilder()
                    // check if caller is self
                    .callerIs(contractAddress)
                    .conditionalJump(77)
                    // non-reentrant, call self after tstore
                    .tstore(1, 8)
                    .call(Operation.CALL, contractAddress, gasLimit)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0)
                    // reentrant, TLOAD and return value
                    .op(Operation.JUMPDEST)
                    .tstore(1, 9)
                    .tstore(1, 10)
                    .op(Operation.REVERT),
                    new ByteCodeBuilder()
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Revert undoes transient storage writes from inner calls that successfully returned
            {new ByteCodeBuilder()
                    // Check call depth
                    .push(0)
                    .op(Operation.CALLDATALOAD)
                    // Store input in mem and reload it to stack
                    .dataOnStackToMemory(5)
                    .push(5)
                    .op(Operation.MLOAD)

                    // See if we're at call depth 1
                    .push(1)
                    .op(Operation.EQ)
                    .conditionalJump(84)

                    // See if we're at call depth 2
                    .push(5)
                    .op(Operation.MLOAD)
                    .push(2)
                    .op(Operation.EQ)
                    .conditionalJump(135)

                    // Call depth = 0, call self after TSTORE 8
                    .tstore(1, 8)

                    // Recursive call with input
                      // Depth++
                      .push(5)
                      .op(Operation.MLOAD)
                      .push(1)
                      .op(Operation.ADD)
                      .callWithInput(Operation.CALL, contractAddress, gasLimit)

                    // TLOAD and return value
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0)

                    // Call depth 1, TSTORE 9 but REVERT after recursion
                    .op(Operation.JUMPDEST)
                    .tstore(1, 9)

                    // Recursive call with input
                      // Depth++
                      .push(5)
                      .op(Operation.MLOAD)
                      .push(1)
                      .op(Operation.ADD)
                      .callWithInput(Operation.CALL, contractAddress, gasLimit)

                    .op(Operation.REVERT)

                    // Call depth 2, TSTORE 10 and complete
                    .op(Operation.JUMPDEST)
                    .tstore(1, 10)
                    ,
                    new ByteCodeBuilder()
                            // Call with input 0
                            .callWithInput(Operation.CALL, contractAddress, gasLimit, UInt256.valueOf(0))
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Transient storage cannot be manipulated in a static context
            {new ByteCodeBuilder()
                    .tstore(1, 8)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0),
                    new ByteCodeBuilder()
                            .call(Operation.STATICCALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_FAILED,
                    0,
                    1},
            // Transient storage cannot be manipulated in a static context when calling self
            {new ByteCodeBuilder()
                    // Check if caller is self
                    .callerIs(contractAddress)
                    .conditionalJump(82)

                    // Non-reentrant, call self after TSTORE 8
                    .tstore(1, 8)
                    .callWithInput(Operation.STATICCALL, contractAddress, gasLimit, UInt256.valueOf(0))
                    // Return the TLOAD value
                    // Should be 8 if call fails, 9 if success
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0)

                    // Reentrant, TSTORE 9
                    .op(Operation.JUMPDEST)
                    .tstore(1, 9),
                    new ByteCodeBuilder()
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Transient storage cannot be manipulated in a nested static context
            {new ByteCodeBuilder()
                    // Check call depth
                    .push(0)
                    .op(Operation.CALLDATALOAD)
                    // Store input in mem and reload it to stack
                    .dataOnStackToMemory(5)
                    .push(5)
                    .op(Operation.MLOAD)

                    // See if we're at call depth 1
                    .push(1)
                    .op(Operation.EQ)
                    .conditionalJump(84)

                    // See if we're at call depth 2
                    .push(5)
                    .op(Operation.MLOAD)
                    .push(2)
                    .op(Operation.EQ)
                    .conditionalJump(140)

                    // Call depth = 0, call self after TSTORE 8
                    .tstore(1, 8)

                    // Recursive call with input
                        // Depth++
                        .push(5)
                        .op(Operation.MLOAD)
                        .push(1)
                        .op(Operation.ADD)
                        .callWithInput(Operation.STATICCALL, contractAddress, gasLimit)

                    // TLOAD and return value
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0)

                    // Call depth 1, TSTORE 9 but REVERT after recursion
                    .op(Operation.JUMPDEST) // 84

                    // Recursive call with input
                        // Depth++
                        .push(5)
                        .op(Operation.MLOAD)
                        .push(1)
                        .op(Operation.ADD)
                        .callWithInput(Operation.CALL, contractAddress, gasLimit)

                        // TLOAD and return value
                        .tload(1)
                        .dataOnStackToMemory(0)
                        .returnValueAtMemory(32, 0)

                    // Call depth 2, TSTORE 10 and complete
                    .op(Operation.JUMPDEST) // 140
                    .tstore(1, 10) // this call will fail
                    ,
                    new ByteCodeBuilder()
                            // Call with input 0
                            .callWithInput(Operation.CALL, contractAddress, gasLimit, UInt256.valueOf(0))
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Delegatecall manipulates transient storage in the context of the current address
            {new ByteCodeBuilder()
                    .tstore(1, 8),
                    new ByteCodeBuilder()
                            .tstore(1, 7)
                            .call(Operation.DELEGATECALL, contractAddress, gasLimit)
                            .tload(1)
                            .dataOnStackToMemory(0)
                            .returnValueAtMemory(32, 0),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Zeroing out a transient storage slot does not result in gas refund
            {null,
                    new ByteCodeBuilder()
                            .tstore(1, 7)
                            .tstore(1, 0),
                    State.COMPLETED_SUCCESS,
                    0,
                    1},
            // Transient storage can be accessed in a static context when calling self
            {new ByteCodeBuilder()
                    // Check if caller is self
                    .callerIs(contractAddress)
                    .conditionalJump(78)

                    // Non-reentrant, call self after TSTORE 8
                    .tstore(1, 8)
                    .call(Operation.STATICCALL, contractAddress, gasLimit)
                    .returnInnerCallResults()

                    // Reentrant, TLOAD and return
                    .op(Operation.JUMPDEST)
                    .tload(1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0),
                    new ByteCodeBuilder()
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    8,
                    1},
            // Transient storage does not persist beyond a single transaction
            // The tstore should not have any impact on the second call
            {new ByteCodeBuilder()
                    .tload(1)
                    .tstore(1, 1)
                    .dataOnStackToMemory(0)
                    .returnValueAtMemory(32, 0),
                    new ByteCodeBuilder()
                            .call(Operation.CALL, contractAddress, gasLimit)
                            .returnInnerCallResults(),
                    State.COMPLETED_SUCCESS,
                    0,
                    2},
    };
  }

  private TestCodeExecutor codeExecutor;

  @Parameter()
  public ByteCodeBuilder contractByteCodeBuilder;

  @Parameter(value = 1)
  public ByteCodeBuilder byteCodeBuilder;

  @Parameter(value = 2)
  public State expectedResultState;

  @Parameter(value = 3)
  public int expectedReturnValue;

  @Parameter(value = 4)
  public int numberOfIterations;


  @Before
  public void setUp() {
    ProtocolSchedule protocolSchedule = MainnetProtocolSchedule.fromConfig(
            new StubGenesisConfigOptions().eip1153Block(0), EvmConfiguration.DEFAULT);
    codeExecutor = new TestCodeExecutor(protocolSchedule,
            account -> account.setStorageValue(UInt256.ZERO, UInt256.valueOf(0)));
  }

  @Test
  public void transientStorageExecutionTest() {
    // Pre-deploy the contract if it's specified
    if (contractByteCodeBuilder != null) {
      codeExecutor.deployContract(
            contractAddress,
            contractByteCodeBuilder.toString());
    }
    for (int i = 0; i < numberOfIterations; i++) {
      final MessageFrame frame =
              codeExecutor.executeCode(
                      byteCodeBuilder.toString(),
                      gasLimit.toLong());
      assertThat(frame.getState()).isEqualTo(expectedResultState);
      assertThat(frame.getGasRefund()).isEqualTo(0);
      assertThat(UInt256.fromBytes(frame.getOutputData()).toInt()).isEqualTo(expectedReturnValue);
    }
  }
}

/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */
package org.hyperledger.besu.evm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hyperledger.besu.evm.EOFTestConstants.INNER_CONTRACT;
import static org.hyperledger.besu.evm.EOFTestConstants.bytesFromPrettyPrint;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class TxCreateOperationTest {

  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final MutableAccount account = mock(MutableAccount.class);
  private final MutableAccount newAccount = mock(MutableAccount.class);

  private static final Bytes CALL_DATA =
      Bytes.fromHexString(
          "cafebaba600dbaadc0de57aff60061e5cafebaba600dbaadc0de57aff60061e5"); // 32 bytes
  public static final String SENDER = "0xdeadc0de00000000000000000000000000000000";

  //  private static final int SHANGHAI_CREATE_GAS = 41240;

  @Test
  void innerContractIsCorrect() {
    Code code = CodeFactory.createCode(INNER_CONTRACT, 1);
    assertThat(code.isValid()).isTrue();

    final MessageFrame messageFrame = testMemoryFrame(code, CALL_DATA);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    final EVM evm = MainnetEVMs.prague(EvmConfiguration.DEFAULT);
    final MessageFrame createFrame = messageFrame.getMessageFrameStack().peek();
    assertThat(createFrame).isNotNull();
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm.getGasCalculator(), evm, false, List.of(), 0, List.of());
    ccp.process(createFrame, OperationTracer.NO_TRACING);

    final Log log = createFrame.getLogs().get(0);
    final Bytes calculatedTopic = log.getTopics().get(0);
    assertThat(calculatedTopic).isEqualTo(CALL_DATA);
  }

  @Test
  void txCreatePassesWithOneInitCode() {
    MessageFrame createFrame = txCreateExecutor(Hash.hash(INNER_CONTRACT), INNER_CONTRACT);

    final Log log = createFrame.getLogs().get(0);
    final String calculatedTopic = log.getTopics().get(0).slice(0, 2).toHexString();
    assertThat(calculatedTopic).isEqualTo("0xc0de");

    assertThat(createFrame.getCreates())
        .containsExactly(Address.fromHexString("0x8c308e96997a8052e3aaab5af624cb827218687a"));
  }

  @Test
  void txCreateFailsWithNoInitcodes() {
    MessageFrame createFrame = txCreateExecutor(Hash.hash(INNER_CONTRACT));

    assertThat(createFrame.getLogs()).isEmpty();
    assertThat(createFrame.getCreates()).isEmpty();
    assertThat(createFrame.getRemainingGas()).isPositive();
    assertThat(createFrame.getExceptionalHaltReason()).isEmpty();
  }

  @Test
  void txCreateFailsWithWrongInitcodes() {
    MessageFrame createFrame = txCreateExecutor(Hash.hash(INNER_CONTRACT), CALL_DATA);

    assertThat(createFrame.getLogs()).isEmpty();
    assertThat(createFrame.getCreates()).isEmpty();
    assertThat(createFrame.getRemainingGas()).isPositive();
    assertThat(createFrame.getExceptionalHaltReason()).isEmpty();
  }

  @Test
  void txCreateSucceedsWithMultipleInitcodes() {
    MessageFrame createFrame =
        txCreateExecutor(Hash.hash(INNER_CONTRACT), CALL_DATA, INNER_CONTRACT, Bytes.random(55));

    final Log log = createFrame.getLogs().get(0);
    final String calculatedTopic = log.getTopics().get(0).slice(0, 2).toHexString();
    assertThat(calculatedTopic).isEqualTo("0xc0de");

    assertThat(createFrame.getCreates())
        .containsExactly(Address.fromHexString("0x8c308e96997a8052e3aaab5af624cb827218687a"));
  }

  @Test
  void txCreateFailsWithBadInitcode() {
    MessageFrame createFrame = txCreateExecutor(Hash.hash(CALL_DATA), CALL_DATA);

    assertThat(createFrame.getLogs()).isEmpty();
    assertThat(createFrame.getCreates()).isEmpty();
    assertThat(createFrame.getRemainingGas()).isPositive();
    assertThat(createFrame.getExceptionalHaltReason()).isEmpty();
  }

  @Test
  void txCreateFailsWithInvalidInitcode() {
    Bytes danglingContract = Bytes.concatenate(INNER_CONTRACT, CALL_DATA);
    MessageFrame createFrame = txCreateExecutor(Hash.hash(danglingContract), danglingContract);

    assertThat(createFrame.getLogs()).isEmpty();
    assertThat(createFrame.getCreates()).isEmpty();
    assertThat(createFrame.getRemainingGas()).isPositive();
    assertThat(createFrame.getExceptionalHaltReason()).isEmpty();
  }

  @Test
  void txCreateSucceedsWithDuplicateInitcodes() {
    MessageFrame createFrame =
        txCreateExecutor(
            Hash.hash(INNER_CONTRACT),
            CALL_DATA,
            CALL_DATA,
            CALL_DATA,
            Bytes.random(55),
            INNER_CONTRACT,
            INNER_CONTRACT);

    final Log log = createFrame.getLogs().get(0);
    final String calculatedTopic = log.getTopics().get(0).slice(0, 2).toHexString();
    assertThat(calculatedTopic).isEqualTo("0xc0de");

    assertThat(createFrame.getCreates())
        .containsExactly(Address.fromHexString("0x8c308e96997a8052e3aaab5af624cb827218687a"));
  }

  MessageFrame txCreateExecutor(final Hash targetHash, final Bytes... initcodes) {
    Bytes outerContract =
        bytesFromPrettyPrint(
            """
                  ef0001 # Magic and Version ( 1 )
                  010004 # Types length ( 4 )
                  020001 # Total code sections ( 1 )
                    000f # Code section 0 , 15 bytes
                  040000 # Data section length(  0 )
                      00 # Terminator (end of header)
                         # Code section 0 types
                      00 # 0 inputs\s
                      80 # 0 outputs  (Non-returning function)
                    0005 # max stack:  5
                         # Code section 0
                  61c0de # [0] PUSH2(0xc0de)
                      5f # [3] PUSH0
                      52 # [4] MSTORE
                      5f # [5] PUSH0
                      35 # [6] CALLDATALOAD
                    6002 # [7] PUSH1(2)
                    601e # [9] PUSH0
                      5f # [11] PUSH0
                      5f # [12] PUSH0
                      ed # [13] TXCREATE
                      00 # [14] STOP
                         # Data section (empty)
                  """);
    Code code = CodeFactory.createCode(outerContract, 1);
    if (!code.isValid()) {
      System.out.println(outerContract);
      fail(((CodeInvalid) code).getInvalidReason());
    }

    final MessageFrame messageFrame = testMemoryFrame(code, targetHash, initcodes);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    final EVM evm = MainnetEVMs.prague(EvmConfiguration.DEFAULT);
    PrecompileContractRegistry precompiles =
        MainnetPrecompiledContracts.prague(evm.getGasCalculator());
    final MessageFrame createFrame = messageFrame.getMessageFrameStack().peek();
    assertThat(createFrame).isNotNull();
    final MessageCallProcessor mcp = new MessageCallProcessor(evm, precompiles);
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm.getGasCalculator(), evm, false, List.of(), 0, List.of());
    while (!createFrame.getMessageFrameStack().isEmpty()) {
      MessageFrame frame = createFrame.getMessageFrameStack().peek();
      (switch (frame.getType()) {
            case CONTRACT_CREATION -> ccp;
            case MESSAGE_CALL -> mcp;
          })
          .process(frame, OperationTracer.NO_TRACING);
    }
    return createFrame;
  }

  private MessageFrame testMemoryFrame(
      final Code code, final Bytes initData, final Bytes... txInitCode) {
    return MessageFrame.builder()
        .type(MessageFrame.Type.MESSAGE_CALL)
        .contract(Address.ZERO)
        .inputData(initData)
        .sender(Address.fromHexString(SENDER))
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(code)
        .completer(__ -> {})
        .address(Address.fromHexString(SENDER))
        .blockHashLookup(n -> Hash.hash(Words.longBytes(n)))
        .blockValues(mock(BlockValues.class))
        .gasPrice(Wei.ZERO)
        .miningBeneficiary(Address.ZERO)
        .originator(Address.ZERO)
        .initialGas(100000L)
        .worldUpdater(worldUpdater)
        .initcodes(
            txInitCode.length == 0 ? Optional.empty() : Optional.of(Arrays.asList(txInitCode)))
        .build();
  }
}

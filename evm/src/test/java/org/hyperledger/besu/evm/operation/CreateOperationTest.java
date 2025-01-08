/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.MainnetEVMs.DEV_NET_CHAIN_ID;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.CODE_TOO_LARGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Deque;
import java.util.List;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class CreateOperationTest {

  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final MutableAccount account = mock(MutableAccount.class);
  private final MutableAccount newAccount = mock(MutableAccount.class);
  private final CreateOperation operation = new CreateOperation(new ConstantinopleGasCalculator());

  private static final String TOPIC =
      "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"; // 32 FFs
  private static final Bytes SIMPLE_CREATE =
      Bytes.fromHexString(
          "0x"
              + "7f" // push32
              + TOPIC
              + "6000" // PUSH1 0x00
              + "6000" // PUSH1 0x00
              + "A1" // LOG1
              + "6000" // PUSH1 0x00
              + "6000" // PUSH1 0x00
              + "F3" // RETURN
          );
  public static final Bytes SIMPLE_EOF =
      Bytes.fromHexString("0xEF00010100040200010001040000000080000000");
  public static final String SENDER = "0xdeadc0de00000000000000000000000000000000";

  private static final int SHANGHAI_CREATE_GAS = 41240;

  @Test
  void createFromMemoryMutationSafe() {

    // Given: Execute a CREATE operation with a contract that logs in the constructor
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final MessageFrame messageFrame = testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(newAccount.isStorageEmpty()).thenReturn(true);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);
    final MessageFrame createFrame = messageFrame.getMessageFrameStack().peek();
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm, false, List.of(), 0, List.of());
    ccp.process(createFrame, OperationTracer.NO_TRACING);

    final Log log = createFrame.getLogs().get(0);
    final String calculatedTopic = log.getTopics().get(0).toUnprefixedHexString();
    assertThat(calculatedTopic).isEqualTo(TOPIC);

    // WHEN the memory that the create operation was executed from is altered.
    messageFrame.writeMemory(
        memoryOffset.trimLeadingZeros().toInt(),
        SIMPLE_CREATE.size(),
        Bytes.random(SIMPLE_CREATE.size()));

    // THEN the logs still have the expected topic
    final String calculatedTopicAfter = log.getTopics().get(0).toUnprefixedHexString();
    assertThat(calculatedTopicAfter).isEqualTo(TOPIC);
  }

  @Test
  void nonceTooLarge() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final MessageFrame messageFrame = testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1);

    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(account.getNonce()).thenReturn(-1L);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);

    assertThat(messageFrame.getStackItem(0).trimLeadingZeros()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void messageFrameStackTooDeep() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final MessageFrame messageFrame =
        testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1025);

    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(account.getNonce()).thenReturn(55L);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);

    assertThat(messageFrame.getStackItem(0).trimLeadingZeros()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void notEnoughValue() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final MessageFrame messageFrame =
        testMemoryFrame(memoryOffset, memoryLength, UInt256.valueOf(1), 1);
    final Deque<MessageFrame> messageFrameStack = messageFrame.getMessageFrameStack();
    for (int i = 0; i < 1025; i++) {
      messageFrameStack.add(messageFrame);
    }

    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(account.getNonce()).thenReturn(55L);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);

    assertThat(messageFrame.getStackItem(0).trimLeadingZeros()).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void shanghaiMaxInitCodeSizeCreate() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.fromHexString("0xc000");
    final MessageFrame messageFrame = testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(newAccount.isStorageEmpty()).thenReturn(true);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    final EVM evm = MainnetEVMs.shanghai(DEV_NET_CHAIN_ID, EvmConfiguration.DEFAULT);
    var result = operation.execute(messageFrame, evm);
    final MessageFrame createFrame = messageFrame.getMessageFrameStack().peek();
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm, false, List.of(), 0, List.of());
    ccp.process(createFrame, OperationTracer.NO_TRACING);

    final Log log = createFrame.getLogs().get(0);
    final String calculatedTopic = log.getTopics().get(0).toUnprefixedHexString();
    assertThat(calculatedTopic).isEqualTo(TOPIC);
    assertThat(result.getGasCost()).isEqualTo(SHANGHAI_CREATE_GAS);
  }

  @Test
  void shanghaiMaxInitCodeSizePlus1Create() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.fromHexString("0xc001");
    final MessageFrame messageFrame = testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    final EVM evm = MainnetEVMs.shanghai(DEV_NET_CHAIN_ID, EvmConfiguration.DEFAULT);
    var result = operation.execute(messageFrame, evm);
    assertThat(result.getHaltReason()).isEqualTo(CODE_TOO_LARGE);
  }

  @Test
  void eofV1CannotCall() {
    final EVM pragueEvm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final MessageFrame messageFrame =
        new TestMessageFrameBuilder()
            .code(pragueEvm.getCodeUncached(SIMPLE_EOF))
            .pushStackItem(memoryLength)
            .pushStackItem(memoryOffset)
            .pushStackItem(Bytes.EMPTY)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.writeMemory(memoryOffset.toLong(), memoryLength.toLong(), SIMPLE_CREATE);

    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);

    final EVM evm = MainnetEVMs.cancun(DEV_NET_CHAIN_ID, EvmConfiguration.DEFAULT);
    var result = operation.execute(messageFrame, evm);
    assertThat(result.getHaltReason()).isEqualTo(INVALID_OPERATION);
    assertThat(messageFrame.getStackItem(0).trimLeadingZeros()).isEqualTo(Bytes.EMPTY);
  }

  @Nonnull
  private MessageFrame testMemoryFrame(
      final UInt256 memoryOffset,
      final UInt256 memoryLength,
      final UInt256 value,
      final int depth) {
    final EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    final MessageFrame messageFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.CONTRACT_CREATION)
            .contract(Address.ZERO)
            .inputData(Bytes.EMPTY)
            .sender(Address.fromHexString(SENDER))
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(evm.getCodeUncached(SIMPLE_CREATE))
            .completer(__ -> {})
            .address(Address.fromHexString(SENDER))
            .blockHashLookup((__, ___) -> Hash.ZERO)
            .blockValues(mock(BlockValues.class))
            .gasPrice(Wei.ZERO)
            .miningBeneficiary(Address.ZERO)
            .originator(Address.ZERO)
            .initialGas(100000L)
            .worldUpdater(worldUpdater)
            .build();
    messageFrame.pushStackItem(memoryLength);
    messageFrame.pushStackItem(memoryOffset);
    messageFrame.pushStackItem(value);
    messageFrame.expandMemory(0, 500);
    messageFrame.writeMemory(
        memoryOffset.trimLeadingZeros().toInt(), SIMPLE_CREATE.size(), SIMPLE_CREATE);
    final Deque<MessageFrame> messageFrameStack = messageFrame.getMessageFrameStack();
    while (messageFrameStack.size() < depth) {
      messageFrameStack.push(messageFrame);
    }
    return messageFrame;
  }
}

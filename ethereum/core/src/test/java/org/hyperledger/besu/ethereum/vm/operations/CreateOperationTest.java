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
package org.hyperledger.besu.ethereum.vm.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.operation.CreateOperation;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.ArrayDeque;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class CreateOperationTest {

  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final WrappedEvmAccount account = mock(WrappedEvmAccount.class);
  private final WrappedEvmAccount newAccount = mock(WrappedEvmAccount.class);
  private final MutableAccount mutableAccount = mock(MutableAccount.class);
  private final MutableAccount newMutableAccount = mock(MutableAccount.class);
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
  public static final String SENDER = "0xdeadc0de00000000000000000000000000000000";

  @Test
  public void createFromMemoryMutationSafe() {

    // Given:  Execute a CREATE operation with a contract that logs in the constructor
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final ArrayDeque<MessageFrame> messageFrameStack = new ArrayDeque<>();
    final MessageFrame messageFrame =
        testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1, messageFrameStack);

    when(account.getMutable()).thenReturn(mutableAccount);
    when(account.getNonce()).thenReturn(55L);
    when(mutableAccount.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getMutable()).thenReturn(newMutableAccount);
    when(newMutableAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);
    final MessageFrame createFrame = messageFrameStack.peek();
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm.getGasCalculator(), evm, false, List.of(), 0, List.of());
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
  public void nonceTooLarge() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final ArrayDeque<MessageFrame> messageFrameStack = new ArrayDeque<>();
    final MessageFrame messageFrame =
        testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1, messageFrameStack);

    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(account.getMutable()).thenReturn(mutableAccount);
    when(mutableAccount.getBalance()).thenReturn(Wei.ZERO);
    when(mutableAccount.getNonce()).thenReturn(-1L);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);

    assertThat(messageFrame.getStackItem(0)).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void messageFrameStackTooDeep() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final ArrayDeque<MessageFrame> messageFrameStack = new ArrayDeque<>();
    final MessageFrame messageFrame =
        testMemoryFrame(memoryOffset, memoryLength, UInt256.ZERO, 1025, messageFrameStack);

    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(account.getMutable()).thenReturn(mutableAccount);
    when(mutableAccount.getBalance()).thenReturn(Wei.ZERO);
    when(mutableAccount.getNonce()).thenReturn(55L);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);

    assertThat(messageFrame.getStackItem(0)).isEqualTo(UInt256.ZERO);
  }

  @Test
  public void notEnoughValue() {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
    final UInt256 memoryLength = UInt256.valueOf(SIMPLE_CREATE.size());
    final ArrayDeque<MessageFrame> messageFrameStack = new ArrayDeque<>();
    final MessageFrame messageFrame =
        testMemoryFrame(memoryOffset, memoryLength, UInt256.valueOf(1), 1, messageFrameStack);
    for (int i = 0; i < 1025; i++) {
      messageFrameStack.add(messageFrame);
    }

    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(account.getMutable()).thenReturn(mutableAccount);
    when(mutableAccount.getBalance()).thenReturn(Wei.ZERO);
    when(mutableAccount.getNonce()).thenReturn(55L);

    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);
    operation.execute(messageFrame, evm);

    assertThat(messageFrame.getStackItem(0)).isEqualTo(UInt256.ZERO);
  }

  @NotNull
  private MessageFrame testMemoryFrame(
      final UInt256 memoryOffset,
      final UInt256 memoryLength,
      final UInt256 value,
      final int depth,
      final ArrayDeque<MessageFrame> messageFrameStack) {
    final MessageFrame messageFrame =
        MessageFrame.builder()
            .type(MessageFrame.Type.CONTRACT_CREATION)
            .contract(Address.ZERO)
            .inputData(Bytes.EMPTY)
            .sender(Address.fromHexString(SENDER))
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(Code.createLegacyCode(SIMPLE_CREATE, Hash.hash(SIMPLE_CREATE)))
            .depth(depth)
            .completer(__ -> {})
            .address(Address.fromHexString(SENDER))
            .blockHashLookup(mock(BlockHashLookup.class))
            .blockValues(mock(ProcessableBlockHeader.class))
            .gasPrice(Wei.ZERO)
            .messageFrameStack(messageFrameStack)
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
    return messageFrame;
  }
}

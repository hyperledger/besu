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
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedToInt;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;

class AbstractCreateOperationTest {

  private final WorldUpdater worldUpdater = mock(WorldUpdater.class);
  private final MutableAccount account = mock(MutableAccount.class);
  private final MutableAccount newAccount = mock(MutableAccount.class);
  private final FakeCreateOperation operation =
      new FakeCreateOperation(new ConstantinopleGasCalculator());

  private static final Bytes SIMPLE_CREATE =
      Bytes.fromHexString(
          "0x"
              + "6000" // PUSH1 0x00
              + "6000" // PUSH1 0x00
              + "F3" // RETURN
          );
  private static final Bytes POP_UNDERFLOW_CREATE =
      Bytes.fromHexString(
          "0x"
              + "50" // POP (but empty stack)
              + "6000" // PUSH1 0x00
              + "6000" // PUSH1 0x00
              + "F3" // RETURN
          );
  public static final Bytes INVALID_EOF =
      Bytes.fromHexString(
          "0x"
              + "73EF00990100040200010001030000000000000000" // PUSH20 contract
              + "6000" // PUSH1 0x00
              + "52" // MSTORE
              + "6014" // PUSH1 20
              + "600c" // PUSH1 12
              + "F3" // RETURN
          );
  public static final String SENDER = "0xdeadc0de00000000000000000000000000000000";

  /** The Create operation. */
  public static class FakeCreateOperation extends AbstractCreateOperation {

    private MessageFrame successFrame;
    private Address successCreatedAddress;
    private MessageFrame failureFrame;
    private Optional<ExceptionalHaltReason> failureHaltReason;
    private MessageFrame invalidFrame;
    private CodeInvalid invalidInvalidCode;

    /**
     * Instantiates a new Create operation.
     *
     * @param gasCalculator the gas calculator
     */
    public FakeCreateOperation(final GasCalculator gasCalculator) {
      super(0xEF, "FAKECREATE", 3, 1, gasCalculator, 0);
    }

    @Override
    public long cost(final MessageFrame frame, final Supplier<Code> unused) {
      final int inputOffset = clampedToInt(frame.getStackItem(1));
      final int inputSize = clampedToInt(frame.getStackItem(2));
      return clampedAdd(
          clampedAdd(
              gasCalculator().txCreateCost(),
              gasCalculator().memoryExpansionGasCost(frame, inputOffset, inputSize)),
          gasCalculator().initcodeCost(inputSize));
    }

    @Override
    protected Address generateTargetContractAddress(final MessageFrame frame, final Code initcode) {
      final Account sender = frame.getWorldUpdater().get(frame.getRecipientAddress());
      // Decrement nonce by 1 to normalize the effect of transaction execution
      final Address address =
          Address.contractAddress(frame.getRecipientAddress(), sender.getNonce() - 1L);
      frame.warmUpAddress(address);
      return address;
    }

    @Override
    protected Code getInitCode(final MessageFrame frame, final EVM evm) {
      final long inputOffset = clampedToLong(frame.getStackItem(1));
      final long inputSize = clampedToLong(frame.getStackItem(2));
      final Bytes inputData = frame.readMemory(inputOffset, inputSize);
      return evm.getCodeUncached(inputData);
    }

    @Override
    protected void onSuccess(final MessageFrame frame, final Address createdAddress) {
      successFrame = frame;
      successCreatedAddress = createdAddress;
    }

    @Override
    protected void onFailure(
        final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
      failureFrame = frame;
      failureHaltReason = haltReason;
    }

    @Override
    protected void onInvalid(final MessageFrame frame, final CodeInvalid invalidCode) {
      invalidFrame = frame;
      invalidInvalidCode = invalidCode;
    }
  }

  private void executeOperation(final Bytes contract, final EVM evm) {
    final UInt256 memoryOffset = UInt256.fromHexString("0xFF");
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
    final Deque<MessageFrame> messageFrameStack = messageFrame.getMessageFrameStack();
    messageFrame.pushStackItem(Bytes.ofUnsignedLong(contract.size()));
    messageFrame.pushStackItem(memoryOffset);
    messageFrame.pushStackItem(Bytes.EMPTY);
    messageFrame.expandMemory(0, 500);
    messageFrame.writeMemory(memoryOffset.trimLeadingZeros().toInt(), contract.size(), contract);

    when(account.getNonce()).thenReturn(55L);
    when(account.getBalance()).thenReturn(Wei.ZERO);
    when(worldUpdater.getAccount(any())).thenReturn(account);
    when(worldUpdater.get(any())).thenReturn(account);
    when(worldUpdater.getSenderAccount(any())).thenReturn(account);
    when(worldUpdater.getOrCreate(any())).thenReturn(newAccount);
    when(newAccount.getCode()).thenReturn(Bytes.EMPTY);
    when(newAccount.isStorageEmpty()).thenReturn(true);
    when(worldUpdater.updater()).thenReturn(worldUpdater);

    operation.execute(messageFrame, evm);
    final MessageFrame createFrame = messageFrameStack.peek();
    final ContractCreationProcessor ccp =
        new ContractCreationProcessor(evm, false, List.of(), 0, List.of());
    ccp.process(createFrame, OperationTracer.NO_TRACING);
  }

  @Test
  void onSuccess() {
    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);

    executeOperation(SIMPLE_CREATE, evm);

    assertThat(operation.successFrame).isNotNull();
    assertThat(operation.successCreatedAddress)
        .isEqualTo(Address.fromHexString("0xecccb0113190dfd26a044a7f26f45152a4270a64"));
    assertThat(operation.failureFrame).isNull();
    assertThat(operation.failureHaltReason).isNull();
    assertThat(operation.invalidFrame).isNull();
    assertThat(operation.invalidInvalidCode).isNull();
  }

  @Test
  void onFailure() {
    final EVM evm = MainnetEVMs.london(EvmConfiguration.DEFAULT);

    executeOperation(POP_UNDERFLOW_CREATE, evm);

    assertThat(operation.successFrame).isNull();
    assertThat(operation.successCreatedAddress).isNull();
    assertThat(operation.failureFrame).isNotNull();
    assertThat(operation.failureHaltReason)
        .contains(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    assertThat(operation.invalidFrame).isNull();
    assertThat(operation.invalidInvalidCode).isNull();
  }

  @Test
  void onInvalid() {
    final EVM evm = MainnetEVMs.futureEips(EvmConfiguration.DEFAULT);

    executeOperation(INVALID_EOF, evm);

    assertThat(operation.successFrame).isNull();
    assertThat(operation.successCreatedAddress).isNull();
    assertThat(operation.failureFrame).isNull();
    assertThat(operation.failureHaltReason).isNull();
    assertThat(operation.invalidFrame).isNotNull();
    assertThat(operation.invalidInvalidCode).isNotNull();
  }
}

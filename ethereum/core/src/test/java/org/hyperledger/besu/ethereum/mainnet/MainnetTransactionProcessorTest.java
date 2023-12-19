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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.feemarket.CoinbaseFeePriceCalculator;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MainnetTransactionProcessorTest {

  private static final int MAX_STACK_SIZE = 1024;

  private final GasCalculator gasCalculator = new LondonGasCalculator();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TransactionValidatorFactory transactionValidatorFactory;

  @Mock private AbstractMessageProcessor contractCreationProcessor;
  @Mock private AbstractMessageProcessor messageCallProcessor;

  @Mock private Blockchain blockchain;
  @Mock private WorldUpdater worldState;
  @Mock private ProcessableBlockHeader blockHeader;
  @Mock private Transaction transaction;
  @Mock private BlockHashLookup blockHashLookup;

  @Mock private MutableAccount senderAccount;
  @Mock private MutableAccount receiverAccount;

  MainnetTransactionProcessor createTransactionProcessor(final boolean warmCoinbase) {
    return new MainnetTransactionProcessor(
        gasCalculator,
        transactionValidatorFactory,
        contractCreationProcessor,
        messageCallProcessor,
        false,
        warmCoinbase,
        MAX_STACK_SIZE,
        FeeMarket.legacy(),
        CoinbaseFeePriceCalculator.frontier());
  }

  @Test
  void shouldWarmCoinbaseIfRequested() {
    Optional<Address> toAddresss =
        Optional.of(Address.fromHexString("0x2222222222222222222222222222222222222222"));
    when(transaction.getTo()).thenReturn(toAddresss);
    Address senderAddress = Address.fromHexString("0x5555555555555555555555555555555555555555");
    Address coinbaseAddress = Address.fromHexString("0x4242424242424242424242424242424242424242");

    when(transaction.getHash()).thenReturn(Hash.EMPTY);
    when(transaction.getPayload()).thenReturn(Bytes.EMPTY);
    when(transaction.getSender()).thenReturn(senderAddress);
    when(transaction.getValue()).thenReturn(Wei.ZERO);
    when(transactionValidatorFactory.get().validate(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    when(transactionValidatorFactory.get().validateForSender(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    when(worldState.getOrCreate(any())).thenReturn(senderAccount);
    when(worldState.getOrCreateSenderAccount(any())).thenReturn(senderAccount);
    when(worldState.updater()).thenReturn(worldState);

    AtomicBoolean coinbaseWarmed = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              MessageFrame messageFrame = invocation.getArgument(0);
              coinbaseWarmed.set(messageFrame.warmUpAddress(coinbaseAddress));
              messageFrame.getMessageFrameStack().pop();
              return null;
            })
        .when(messageCallProcessor)
        .process(any(), any());

    var transactionProcessor = createTransactionProcessor(true);
    transactionProcessor.processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        coinbaseAddress,
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build(),
        Wei.ZERO);

    assertThat(coinbaseWarmed).isTrue();

    transactionProcessor = createTransactionProcessor(false);
    transactionProcessor.processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        coinbaseAddress,
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build(),
        Wei.ZERO);

    assertThat(coinbaseWarmed).isFalse();
  }

  @Test
  void shouldTraceEndTxOnFailingTransaction() {
    Optional<Address> toAddresss =
        Optional.of(Address.fromHexString("0x2222222222222222222222222222222222222222"));
    Address senderAddress = Address.fromHexString("0x5555555555555555555555555555555555555555");
    Address coinbaseAddress = Address.fromHexString("0x4242424242424242424242424242424242424242");

    when(receiverAccount.getCode()).thenReturn(Bytes.fromHexString("0x600101"));

    when(transaction.getTo()).thenReturn(toAddresss);
    when(transaction.getHash()).thenReturn(Hash.EMPTY);
    when(transaction.getPayload()).thenReturn(Bytes.EMPTY);
    when(transaction.getSender()).thenReturn(senderAddress);
    when(transaction.getValue()).thenReturn(Wei.ZERO);
    when(transactionValidatorFactory.get().validate(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    when(transactionValidatorFactory.get().validateForSender(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    when(worldState.getOrCreate(senderAddress)).thenReturn(senderAccount);
    when(worldState.getOrCreateSenderAccount(senderAddress)).thenReturn(senderAccount);
    when(worldState.get(toAddresss.get())).thenReturn(receiverAccount);
    when(worldState.getAccount(toAddresss.get())).thenReturn(receiverAccount);
    when(worldState.updater()).thenReturn(worldState);
    doAnswer(
            invocation -> {
              MessageFrame messageFrame = invocation.getArgument(0);
              messageFrame.getMessageFrameStack().pop();
              return null;
            })
        .when(messageCallProcessor)
        .process(any(), any());

    final TraceEndTxTracer tracer = new TraceEndTxTracer();
    var transactionProcessor = createTransactionProcessor(true);
    transactionProcessor.processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        coinbaseAddress,
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build(),
        tracer,
        Wei.ZERO);

    assertThat(tracer.traceEndTxCalled).isTrue();
  }

  static class TraceEndTxTracer implements OperationTracer {
    boolean traceEndTxCalled = false;

    @Override
    public void traceEndTransaction(
        final WorldView worldView,
        final org.hyperledger.besu.datatypes.Transaction tx,
        final boolean status,
        final Bytes output,
        final List<Log> logs,
        final long gasUsed,
        final long timeNs) {
      this.traceEndTxCalled = true;
    }
  }

  @Test
  void shouldCallTransactionValidatorWithExpectedTransactionValidationParams() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        transactionValidationParamCaptor();

    final TransactionValidationParams expectedValidationParams =
        ImmutableTransactionValidationParams.builder().build();

    var transactionProcessor = createTransactionProcessor(false);

    transactionProcessor.processTransaction(
        blockchain,
        worldState,
        blockHeader,
        transaction,
        Address.fromHexString("1"),
        blockHashLookup,
        false,
        ImmutableTransactionValidationParams.builder().build(),
        Wei.ZERO);

    assertThat(txValidationParamCaptor.getValue())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidationParams);
  }

  private ArgumentCaptor<TransactionValidationParams> transactionValidationParamCaptor() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        ArgumentCaptor.forClass(TransactionValidationParams.class);
    when(transactionValidatorFactory.get().validate(any(), any(), any()))
        .thenReturn(ValidationResult.valid());
    // returning invalid transaction to halt method execution
    when(transactionValidatorFactory
            .get()
            .validateForSender(any(), any(), txValidationParamCaptor.capture()))
        .thenReturn(ValidationResult.invalid(TransactionInvalidReason.NONCE_TOO_HIGH));
    return txValidationParamCaptor;
  }
}

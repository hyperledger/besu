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
package org.hyperledger.besu.ethereum.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.EvmAccount;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult.Status;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class TransactionSimulatorTest {

  private static final SECP256K1.Signature FAKE_SIGNATURE =
      SECP256K1.Signature.create(SECP256K1.HALF_CURVE_ORDER, SECP256K1.HALF_CURVE_ORDER, (byte) 0);

  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private static final Hash DEFAULT_BLOCK_HEADER_HASH =
      Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");

  private TransactionSimulator transactionSimulator;

  @Mock private Blockchain blockchain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private MutableWorldState worldState;
  @Mock private WorldUpdater worldUpdater;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private MainnetTransactionProcessor transactionProcessor;

  @Before
  public void setUp() {
    this.transactionSimulator =
        new TransactionSimulator(blockchain, worldStateArchive, protocolSchedule);
  }

  @Test
  public void shouldReturnEmptyWhenBlockDoesNotExist() {
    when(blockchain.getBlockHeader(eq(1L))).thenReturn(Optional.empty());

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(legacyTransactionCallParameter(), 1L);

    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void shouldReturnSuccessfulResultWhenProcessingIsSuccessful() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(callParameter.getGasPrice())
            .gasLimit(callParameter.getGasLimit())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, 1L);

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldIncreaseBalanceAccountWhenExceedingBalanceAllowed() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(callParameter.getGasPrice())
            .gasLimit(callParameter.getGasLimit())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();

    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    final MutableAccount mutableAccount =
        mockWorldUpdaterForAccount(Hash.ZERO, callParameter.getFrom());

    transactionSimulator.process(
        callParameter,
        ImmutableTransactionValidationParams.builder().isAllowExceedingBalance(true).build(),
        OperationTracer.NO_TRACING,
        1L);

    verify(mutableAccount).incrementBalance(Wei.of(Long.MAX_VALUE));
  }

  @Test
  public void shouldNotIncreaseBalanceAccountWhenExceedingBalanceIsNotAllowed() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(callParameter.getGasPrice())
            .gasLimit(callParameter.getGasLimit())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();

    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    final MutableAccount mutableAccount =
        mockWorldUpdaterForAccount(Hash.ZERO, callParameter.getFrom());

    transactionSimulator.process(
        callParameter,
        ImmutableTransactionValidationParams.builder().isAllowExceedingBalance(false).build(),
        OperationTracer.NO_TRACING,
        1L);

    verifyNoInteractions(mutableAccount);
  }

  @Test
  public void shouldUseDefaultValuesWhenMissingOptionalFields() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, Address.fromHexString("0x0"), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(Wei.ZERO)
            .gasLimit(0L)
            .to(DEFAULT_FROM)
            .sender(Address.fromHexString("0x0"))
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, 1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseZeroNonceWhenAccountDoesNotExist() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAbsentAccount(Hash.ZERO);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(0L)
            .gasPrice(Wei.ZERO)
            .gasLimit(0L)
            .to(DEFAULT_FROM)
            .sender(Address.fromHexString("0x0"))
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, 1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnFailureResultWhenProcessingFails() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, Address.fromHexString("0x0"), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(callParameter.getGasPrice())
            .gasLimit(callParameter.getGasLimit())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.FAILED);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, 1L);

    assertThat(result.get().isSuccessful()).isFalse();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnEmptyWhenBlockDoesNotExistByHash() {
    when(blockchain.getBlockHeader(eq(Hash.ZERO))).thenReturn(Optional.empty());

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(legacyTransactionCallParameter(), Hash.ZERO);

    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void shouldReturnSuccessfulResultWhenProcessingIsSuccessfulByHash() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L, DEFAULT_BLOCK_HEADER_HASH);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(callParameter.getGasPrice())
            .gasLimit(callParameter.getGasLimit())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, DEFAULT_BLOCK_HEADER_HASH);

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseDefaultValuesWhenMissingOptionalFieldsByHash() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L, DEFAULT_BLOCK_HEADER_HASH);
    mockWorldStateForAccount(Hash.ZERO, Address.fromHexString("0x0"), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(Wei.ZERO)
            .gasLimit(0L)
            .to(DEFAULT_FROM)
            .sender(Address.fromHexString("0x0"))
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, DEFAULT_BLOCK_HEADER_HASH);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseZeroNonceWhenAccountDoesNotExistByHash() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L, DEFAULT_BLOCK_HEADER_HASH);
    mockWorldStateForAbsentAccount(Hash.ZERO);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(0L)
            .gasPrice(Wei.ZERO)
            .gasLimit(0L)
            .to(DEFAULT_FROM)
            .sender(Address.fromHexString("0x0"))
            .value(Wei.ZERO)
            .payload(Bytes.EMPTY)
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, DEFAULT_BLOCK_HEADER_HASH);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnFailureResultWhenProcessingFailsByHash() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L, DEFAULT_BLOCK_HEADER_HASH);
    mockWorldStateForAccount(Hash.ZERO, Address.fromHexString("0x0"), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(callParameter.getGasPrice())
            .gasLimit(callParameter.getGasLimit())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.FAILED);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, DEFAULT_BLOCK_HEADER_HASH);

    assertThat(result.get().isSuccessful()).isFalse();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnSuccessfulResultWhenEip1559TransactionProcessingIsSuccessful() {
    final CallParameter callParameter = eip1559TransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .nonce(1L)
            .gasPrice(callParameter.getGasPrice())
            .gasLimit(callParameter.getGasLimit())
            .feeCap(callParameter.getFeeCap().orElseThrow())
            .gasPremium(callParameter.getGasPremium().orElseThrow())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, 1L);

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  private void mockWorldStateForAccount(
      final Hash stateRoot, final Address address, final long nonce) {
    final Account account = mock(Account.class);
    when(account.getNonce()).thenReturn(nonce);
    when(worldStateArchive.getMutable(eq(stateRoot))).thenReturn(Optional.of(worldState));
    when(worldState.get(eq(address))).thenReturn(account);
  }

  private void mockWorldStateForAbsentAccount(final Hash stateRoot) {
    when(worldStateArchive.getMutable(eq(stateRoot))).thenReturn(Optional.of(worldState));
    when(worldState.get(any())).thenReturn(null);
  }

  private MutableAccount mockWorldUpdaterForAccount(final Hash stateRoot, final Address address) {
    final EvmAccount account = mock(EvmAccount.class);
    final MutableAccount mutableAccount = mock(MutableAccount.class);
    when(worldStateArchive.getMutable(eq(stateRoot))).thenReturn(Optional.of(worldState));
    when(worldState.updater()).thenReturn(worldUpdater);
    when(worldUpdater.getOrCreate(eq(address))).thenReturn(account);
    when(account.getMutable()).thenReturn(mutableAccount);
    return mutableAccount;
  }

  private void mockBlockchainForBlockHeader(final Hash stateRoot, final long blockNumber) {
    mockBlockchainForBlockHeader(stateRoot, blockNumber, Hash.ZERO);
  }

  private void mockBlockchainForBlockHeader(
      final Hash stateRoot, final long blockNumber, final Hash headerHash) {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getStateRoot()).thenReturn(stateRoot);
    when(blockHeader.getNumber()).thenReturn(blockNumber);
    when(blockchain.getBlockHeader(blockNumber)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(headerHash)).thenReturn(Optional.of(blockHeader));
  }

  private void mockProcessorStatusForTransaction(
      final long blockNumber, final Transaction transaction, final Status status) {
    when(protocolSchedule.getByBlockNumber(eq(blockNumber))).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionProcessor()).thenReturn(transactionProcessor);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(BlockHeader::getCoinbase);

    final TransactionProcessingResult result = mock(TransactionProcessingResult.class);
    switch (status) {
      case SUCCESSFUL:
        when(result.isSuccessful()).thenReturn(true);
        break;
      case INVALID:
      case FAILED:
        when(result.isSuccessful()).thenReturn(false);
        break;
    }

    when(transactionProcessor.processTransaction(
            any(), any(), any(), eq(transaction), any(), any(), anyBoolean(), any(), any()))
        .thenReturn(result);
  }

  private void verifyTransactionWasProcessed(final Transaction expectedTransaction) {
    verify(transactionProcessor)
        .processTransaction(
            any(), any(), any(), eq(expectedTransaction), any(), any(), anyBoolean(), any(), any());
  }

  private CallParameter legacyTransactionCallParameter() {
    return new CallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        0,
        Wei.of(0),
        Wei.of(0),
        Bytes.EMPTY);
  }

  private CallParameter eip1559TransactionCallParameter() {
    return new CallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        0,
        Wei.of(0),
        Optional.of(Wei.of(0)),
        Optional.of(Wei.of(0)),
        Wei.of(0),
        Bytes.EMPTY);
  }
}

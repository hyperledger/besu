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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult.Status;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.math.BigInteger;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class TransactionSimulatorTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final BigInteger HALF_CURVE_ORDER = SIGNATURE_ALGORITHM.get().getHalfCurveOrder();
  private static final SECPSignature FAKE_SIGNATURE =
      SIGNATURE_ALGORITHM.get().createSignature(HALF_CURVE_ORDER, HALF_CURVE_ORDER, (byte) 0);

  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private static final Hash DEFAULT_BLOCK_HEADER_HASH =
      Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001");

  private TransactionSimulator transactionSimulator;

  @Mock private Blockchain blockchain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private MutableWorldState worldState;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private MainnetTransactionProcessor transactionProcessor;
  @Mock private MainnetTransactionValidator transactionValidator;

  @Before
  public void setUp() {
    this.transactionSimulator =
        new TransactionSimulator(blockchain, worldStateArchive, protocolSchedule);

    when(transactionProcessor.getTransactionValidator()).thenReturn(transactionValidator);
    when(transactionValidator.getGoQuorumCompatibilityMode()).thenReturn(false);
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
            .type(TransactionType.FRONTIER)
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
  public void shouldSetGasPriceToZeroWhenExceedingBalanceAllowed() {
    final CallParameter callParameter = legacyTransactionCallParameter(Wei.ONE);

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(1L)
            .gasPrice(Wei.ZERO)
            .gasLimit(callParameter.getGasLimit())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();

    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(
        callParameter,
        ImmutableTransactionValidationParams.builder().isAllowExceedingBalance(true).build(),
        OperationTracer.NO_TRACING,
        1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldSetFeePerGasToZeroWhenExceedingBalanceAllowed() {
    final CallParameter callParameter = eip1559TransactionCallParameter(Wei.ONE, Wei.ONE);

    mockBlockchainForBlockHeader(Hash.ZERO, 1L, Wei.ONE);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.ONE)
            .nonce(1L)
            .gasLimit(callParameter.getGasLimit())
            .maxFeePerGas(Wei.ZERO)
            .maxPriorityFeePerGas(Wei.ZERO)
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();

    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(
        callParameter,
        ImmutableTransactionValidationParams.builder().isAllowExceedingBalance(true).build(),
        OperationTracer.NO_TRACING,
        1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldNotSetGasPriceToZeroWhenExceedingBalanceIsNotAllowed() {
    final CallParameter callParameter = legacyTransactionCallParameter(Wei.ONE);

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
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

    transactionSimulator.process(
        callParameter,
        ImmutableTransactionValidationParams.builder().isAllowExceedingBalance(false).build(),
        OperationTracer.NO_TRACING,
        1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldNotSetFeePerGasToZeroWhenExceedingBalanceIsNotAllowed() {
    final CallParameter callParameter = eip1559TransactionCallParameter(Wei.ONE, Wei.ONE);

    mockBlockchainForBlockHeader(Hash.ZERO, 1L, Wei.ONE);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.ONE)
            .nonce(1L)
            .gasLimit(callParameter.getGasLimit())
            .maxFeePerGas(callParameter.getMaxFeePerGas().orElseThrow())
            .maxPriorityFeePerGas(callParameter.getMaxPriorityFeePerGas().orElseThrow())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();

    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(
        callParameter,
        ImmutableTransactionValidationParams.builder().isAllowExceedingBalance(false).build(),
        OperationTracer.NO_TRACING,
        1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseDefaultValuesWhenMissingOptionalFields() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    mockBlockchainForBlockHeader(Hash.ZERO, 1L);
    mockWorldStateForAccount(Hash.ZERO, Address.fromHexString("0x0"), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
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
            .type(TransactionType.FRONTIER)
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
            .type(TransactionType.FRONTIER)
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
            .type(TransactionType.FRONTIER)
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
            .type(TransactionType.FRONTIER)
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
            .type(TransactionType.FRONTIER)
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
            .type(TransactionType.FRONTIER)
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

    mockBlockchainForBlockHeader(Hash.ZERO, 1L, Wei.ONE);
    mockWorldStateForAccount(Hash.ZERO, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.ONE)
            .nonce(1L)
            .gasLimit(callParameter.getGasLimit())
            .maxFeePerGas(callParameter.getMaxFeePerGas().orElseThrow())
            .maxPriorityFeePerGas(callParameter.getMaxPriorityFeePerGas().orElseThrow())
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
    when(worldStateArchive.getMutable(eq(stateRoot), any(), anyBoolean()))
        .thenReturn(Optional.of(worldState));
    final WorldUpdater updater = mock(WorldUpdater.class);
    when(updater.get(address)).thenReturn(account);
    when(worldState.updater()).thenReturn(updater);
  }

  private void mockWorldStateForAbsentAccount(final Hash stateRoot) {
    when(worldStateArchive.getMutable(eq(stateRoot), any(), anyBoolean()))
        .thenReturn(Optional.of(worldState));
    final WorldUpdater updater = mock(WorldUpdater.class);
    when(updater.get(any())).thenReturn(null);
    when(worldState.updater()).thenReturn(updater);
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

  private void mockBlockchainForBlockHeader(
      final Hash stateRoot, final long blockNumber, final Wei baseFee) {
    final BlockHeader blockHeader = mock(BlockHeader.class, Answers.RETURNS_MOCKS);
    when(blockHeader.getStateRoot()).thenReturn(stateRoot);
    when(blockHeader.getNumber()).thenReturn(blockNumber);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(baseFee));
    when(blockHeader.getDifficulty()).thenReturn(Difficulty.ONE);
    when(blockchain.getBlockHeader(blockNumber)).thenReturn(Optional.of(blockHeader));
  }

  private void mockProcessorStatusForTransaction(
      final long blockNumber, final Transaction transaction, final Status status) {
    final BlockHeaderFunctions blockHeaderFunctions = mock(BlockHeaderFunctions.class);
    when(protocolSchedule.getChainId()).thenReturn(Optional.of(BigInteger.ONE));
    when(protocolSchedule.getByBlockNumber(eq(blockNumber))).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionProcessor()).thenReturn(transactionProcessor);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(BlockHeader::getCoinbase);
    when(protocolSpec.getBlockHeaderFunctions()).thenReturn(blockHeaderFunctions);

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
    return legacyTransactionCallParameter(Wei.ZERO);
  }

  private CallParameter legacyTransactionCallParameter(final Wei gasPrice) {
    return new CallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        0,
        gasPrice,
        Wei.of(0),
        Bytes.EMPTY);
  }

  private CallParameter eip1559TransactionCallParameter() {
    return eip1559TransactionCallParameter(Wei.ZERO, Wei.ZERO);
  }

  private CallParameter eip1559TransactionCallParameter(
      final Wei maxFeePerGas, final Wei maxPriorityFeePerGas) {
    return new CallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        0,
        Wei.of(0),
        Optional.of(maxFeePerGas),
        Optional.of(maxPriorityFeePerGas),
        Wei.of(0),
        Bytes.EMPTY);
  }
}

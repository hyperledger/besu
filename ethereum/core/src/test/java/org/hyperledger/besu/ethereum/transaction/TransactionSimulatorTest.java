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
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ImmutableTransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult.Status;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.Optional;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"rawtypes", "unchecked"})
public class TransactionSimulatorTest {

  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final BigInteger HALF_CURVE_ORDER = SIGNATURE_ALGORITHM.get().getHalfCurveOrder();
  private static final SECPSignature FAKE_SIGNATURE =
      SIGNATURE_ALGORITHM.get().createSignature(HALF_CURVE_ORDER, HALF_CURVE_ORDER, (byte) 0);

  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");
  private static final long GASCAP = 500L;
  private TransactionSimulator transactionSimulator;
  private TransactionSimulator cappedTransactionSimulator;

  @Mock private Blockchain blockchain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private MutableWorldState worldState;
  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private MainnetTransactionProcessor transactionProcessor;
  private final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();

  @BeforeEach
  public void setUp() {
    this.transactionSimulator =
        new TransactionSimulator(blockchain, worldStateArchive, protocolSchedule, Optional.empty());
    this.cappedTransactionSimulator =
        new TransactionSimulator(
            blockchain, worldStateArchive, protocolSchedule, Optional.of(GASCAP));
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

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, 1L);

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldSetGasPriceToZeroWhenExceedingBalanceAllowed() {
    final CallParameter callParameter = legacyTransactionCallParameter(Wei.ONE);

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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

    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

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

    final BlockHeader blockHeader = mockBlockHeader(Hash.ZERO, 1L, Wei.ONE);

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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

    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

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

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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

    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

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

    final BlockHeader blockHeader = mockBlockHeader(Hash.ZERO, 1L, Wei.ONE);

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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

    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

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

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, Address.fromHexString("0x0"), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, 1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseZeroNonceWhenAccountDoesNotExist() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAbsentAccount(blockHeader);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, 1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnFailureResultWhenProcessingFails() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, Address.fromHexString("0x0"), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.FAILED);

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

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, blockHeader.getBlockHash());

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseDefaultValuesWhenMissingOptionalFieldsByHash() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, Address.fromHexString("0x0"), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, blockHeader.getBlockHash());

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseZeroNonceWhenAccountDoesNotExistByHash() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAbsentAccount(blockHeader);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

    transactionSimulator.process(callParameter, blockHeader.getBlockHash());

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnFailureResultWhenProcessingFailsByHash() {
    final CallParameter callParameter = legacyTransactionCallParameter();

    final BlockHeader blockHeader =
        blockHeaderTestFixture.number(1L).stateRoot(Hash.ZERO).buildHeader();

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, Address.fromHexString("0x0"), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.FAILED);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, blockHeader.getBlockHash());

    assertThat(result.get().isSuccessful()).isFalse();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnSuccessfulResultWhenEip1559TransactionProcessingIsSuccessful() {
    final CallParameter callParameter = eip1559TransactionCallParameter();

    final BlockHeader blockHeader = mockBlockHeader(Hash.ZERO, 1L, Wei.ONE);

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.FAILED);

    final Optional<TransactionSimulatorResult> result =
        transactionSimulator.process(callParameter, 1L);

    assertThat(result.get().isSuccessful()).isFalse();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldCapGasLimitWhenOriginalTransactionExceedsGasCap() {
    final CallParameter callParameter =
        eip1559TransactionCallParameter(Wei.ZERO, Wei.ZERO, GASCAP + 1);

    final BlockHeader blockHeader = mockBlockHeader(Hash.ZERO, 1L, Wei.ONE);

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

    final Transaction expectedTransaction =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .chainId(BigInteger.ONE)
            .nonce(1L)
            .gasLimit(GASCAP)
            .maxFeePerGas(callParameter.getMaxFeePerGas().orElseThrow())
            .maxPriorityFeePerGas(callParameter.getMaxPriorityFeePerGas().orElseThrow())
            .to(callParameter.getTo())
            .sender(callParameter.getFrom())
            .value(callParameter.getValue())
            .payload(callParameter.getPayload())
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(expectedTransaction, Status.FAILED);

    final Optional<TransactionSimulatorResult> result =
        cappedTransactionSimulator.process(
            callParameter,
            TransactionValidationParams.transactionSimulator(),
            OperationTracer.NO_TRACING,
            1L);

    assertThat(result.get().isSuccessful()).isFalse();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldApplyGasCapWhenOriginalGasLimitIsLowerThanGasCap() {
    final CallParameter callParameter =
        eip1559TransactionCallParameter(Wei.ZERO, Wei.ZERO, GASCAP - 1);

    final BlockHeader blockHeader = mockBlockHeader(Hash.ZERO, 1L, Wei.ONE);

    mockBlockchainForBlockHeader(blockHeader);
    mockWorldStateForAccount(blockHeader, callParameter.getFrom(), 1L);

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
    mockProcessorStatusForTransaction(expectedTransaction, Status.SUCCESSFUL);

    final Optional<TransactionSimulatorResult> result =
        cappedTransactionSimulator.process(
            callParameter,
            TransactionValidationParams.transactionSimulator(),
            OperationTracer.NO_TRACING,
            1L);

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  private void mockWorldStateForAccount(
      final BlockHeader blockHeader, final Address address, final long nonce) {
    final Account account = mock(Account.class);
    when(account.getNonce()).thenReturn(nonce);
    when(worldStateArchive.getMutable(eq(blockHeader), anyBoolean()))
        .thenReturn(Optional.of(worldState));
    final WorldUpdater updater = mock(WorldUpdater.class);
    when(updater.get(address)).thenReturn(account);
    when(worldState.updater()).thenReturn(updater);
  }

  private void mockWorldStateForAbsentAccount(final BlockHeader blockHeader) {
    when(worldStateArchive.getMutable(eq(blockHeader), anyBoolean()))
        .thenReturn(Optional.of(worldState));
    final WorldUpdater updater = mock(WorldUpdater.class);
    when(updater.get(any())).thenReturn(null);
    when(worldState.updater()).thenReturn(updater);
  }

  private BlockHeader mockBlockHeader(
      final Hash stateRoot, final long blockNumber, final Wei baseFee) {
    return blockHeaderTestFixture
        .stateRoot(stateRoot)
        .number(blockNumber)
        .baseFeePerGas(baseFee)
        .difficulty(Difficulty.ONE)
        .buildHeader();
  }

  private void mockBlockchainForBlockHeader(final BlockHeader blockHeader) {
    when(blockchain.getBlockHeader(blockHeader.getNumber())).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(blockHeader.getBlockHash()))
        .thenReturn(Optional.of(blockHeader));
  }

  private void mockProcessorStatusForTransaction(
      final Transaction transaction, final Status status) {
    final BlockHeaderFunctions blockHeaderFunctions = mock(BlockHeaderFunctions.class);
    when(protocolSchedule.getChainId()).thenReturn(Optional.of(BigInteger.ONE));
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionProcessor()).thenReturn(transactionProcessor);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(BlockHeader::getCoinbase);
    when(protocolSpec.getBlockHeaderFunctions()).thenReturn(blockHeaderFunctions);
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.london(0));

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
            any(),
            any(),
            any(),
            eq(transaction),
            any(),
            any(),
            anyBoolean(),
            any(),
            any(),
            any(Wei.class)))
        .thenReturn(result);
  }

  private void verifyTransactionWasProcessed(final Transaction expectedTransaction) {
    verify(transactionProcessor)
        .processTransaction(
            any(),
            any(),
            any(),
            eq(expectedTransaction),
            any(),
            any(),
            anyBoolean(),
            any(),
            any(),
            any(Wei.class));
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
    return eip1559TransactionCallParameter(maxFeePerGas, maxPriorityFeePerGas, 0L);
  }

  private CallParameter eip1559TransactionCallParameter(
      final Wei maxFeePerGas, final Wei maxPriorityFeePerGas, final long gasLimit) {
    return new CallParameter(
        Address.fromHexString("0x0"),
        Address.fromHexString("0x0"),
        gasLimit,
        Wei.of(0),
        Optional.of(maxFeePerGas),
        Optional.of(maxPriorityFeePerGas),
        Wei.of(0),
        Bytes.EMPTY,
        Optional.empty());
  }
}

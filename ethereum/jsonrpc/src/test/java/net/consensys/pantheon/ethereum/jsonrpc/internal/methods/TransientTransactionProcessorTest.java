package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.CallParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessingResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransientTransactionProcessor;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor.Result;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor.Result.Status;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class TransientTransactionProcessorTest {

  private static final SECP256K1.Signature FAKE_SIGNATURE =
      SECP256K1.Signature.create(SECP256K1.HALF_CURVE_ORDER, SECP256K1.HALF_CURVE_ORDER, (byte) 0);

  private static final Address DEFAULT_FROM =
      Address.fromHexString("0x0000000000000000000000000000000000000000");

  private TransientTransactionProcessor transientTransactionProcessor;

  @Mock private Blockchain blockchain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private MutableWorldState worldState;
  @Mock private ProtocolSchedule<?> protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private TransactionProcessor transactionProcessor;

  @Before
  public void setUp() {
    this.transientTransactionProcessor =
        new TransientTransactionProcessor(blockchain, worldStateArchive, protocolSchedule);
  }

  @Test
  public void shouldReturnEmptyWhenBlockDoesNotExist() {
    when(blockchain.getBlockHeader(eq(1L))).thenReturn(Optional.empty());

    final Optional<TransientTransactionProcessingResult> result =
        transientTransactionProcessor.process(callParameter(), 1L);

    assertThat(result.isPresent()).isFalse();
  }

  @Test
  public void shouldReturnSuccessfulResultWhenProcessingIsSuccessful() {
    final CallParameter callParameter = callParameter();

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
    mockProcessorStatusForTransaction(
        1L, expectedTransaction, Status.SUCCESSFUL, callParameter.getPayload());

    final Optional<TransientTransactionProcessingResult> result =
        transientTransactionProcessor.process(callParameter, 1L);

    assertThat(result.get().isSuccessful()).isTrue();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseDefaultValuesWhenMissingOptionalFields() {
    final CallParameter callParameter = callParameter();

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
            .payload(BytesValue.EMPTY)
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL, BytesValue.of());

    transientTransactionProcessor.process(callParameter, 1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldUseZeroNonceWhenAccountDoesNotExist() {
    final CallParameter callParameter = callParameter();

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
            .payload(BytesValue.EMPTY)
            .signature(FAKE_SIGNATURE)
            .build();
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.SUCCESSFUL, BytesValue.of());

    transientTransactionProcessor.process(callParameter, 1L);

    verifyTransactionWasProcessed(expectedTransaction);
  }

  @Test
  public void shouldReturnFailureResultWhenProcessingFails() {
    final CallParameter callParameter = callParameter();

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
    mockProcessorStatusForTransaction(1L, expectedTransaction, Status.FAILED, null);

    final Optional<TransientTransactionProcessingResult> result =
        transientTransactionProcessor.process(callParameter, 1L);

    assertThat(result.get().isSuccessful()).isFalse();
    verifyTransactionWasProcessed(expectedTransaction);
  }

  private void mockWorldStateForAccount(
      final Hash stateRoot, final Address address, final long nonce) {
    final Account account = mock(Account.class);
    when(account.getNonce()).thenReturn(nonce);
    when(worldStateArchive.getMutable(eq(stateRoot))).thenReturn(worldState);
    when(worldState.get(eq(address))).thenReturn(account);
  }

  private void mockWorldStateForAbsentAccount(final Hash stateRoot) {
    when(worldStateArchive.getMutable(eq(stateRoot))).thenReturn(worldState);
    when(worldState.get(any())).thenReturn(null);
  }

  private void mockBlockchainForBlockHeader(final Hash stateRoot, final long blockNumber) {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getStateRoot()).thenReturn(stateRoot);
    when(blockHeader.getNumber()).thenReturn(blockNumber);
    when(blockchain.getBlockHeader(blockNumber)).thenReturn(Optional.of(blockHeader));
  }

  private void mockProcessorStatusForTransaction(
      final long blockNumber,
      final Transaction transaction,
      final Result.Status status,
      final BytesValue output) {
    when(protocolSchedule.getByBlockNumber(eq(blockNumber))).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionProcessor()).thenReturn(transactionProcessor);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(BlockHeader::getCoinbase);

    final Result result = mock(Result.class);
    switch (status) {
      case SUCCESSFUL:
        when(result.isSuccessful()).thenReturn(true);
        break;
      case INVALID:
      case FAILED:
        when(result.isSuccessful()).thenReturn(false);
        break;
    }

    when(transactionProcessor.processTransaction(any(), any(), any(), eq(transaction), any()))
        .thenReturn(result);
  }

  private void verifyTransactionWasProcessed(final Transaction expectedTransaction) {
    verify(transactionProcessor)
        .processTransaction(any(), any(), any(), eq(expectedTransaction), any());
  }

  private CallParameter callParameter() {
    return new CallParameter("0x0", "0x0", "0x0", "0x0", "0x0", "");
  }
}

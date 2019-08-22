/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.transactions;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.GAS_PRICE_TOO_LOW;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.NONCE_TOO_LOW;
import static tech.pegasys.pantheon.ethereum.mainnet.ValidationResult.valid;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.ExecutionContextTestFixture;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.TransactionTestFixture;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeers;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidationParams;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TransactionPoolTest {

  private static final int MAX_TRANSACTIONS = 5;
  private static final KeyPair KEY_PAIR1 = KeyPair.generate();

  private final PendingTransactionListener listener = mock(PendingTransactionListener.class);
  private final TransactionBatchAddedListener batchAddedListener =
      mock(TransactionBatchAddedListener.class);
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @SuppressWarnings("unchecked")
  private final ProtocolSchedule<Void> protocolSchedule = mock(ProtocolSchedule.class);

  @SuppressWarnings("unchecked")
  private final ProtocolSpec<Void> protocolSpec = mock(ProtocolSpec.class);

  private final TransactionValidator transactionValidator = mock(TransactionValidator.class);
  private MutableBlockchain blockchain;

  private final PendingTransactions transactions =
      new PendingTransactions(
          TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
          MAX_TRANSACTIONS,
          TestClock.fixed(),
          metricsSystem);
  private final Transaction transaction1 = createTransaction(1);
  private final Transaction transaction2 = createTransaction(2);
  private final ExecutionContextTestFixture executionContext = ExecutionContextTestFixture.create();
  private final ProtocolContext<Void> protocolContext = executionContext.getProtocolContext();
  private TransactionPool transactionPool;
  private long genesisBlockGasLimit;
  private SyncState syncState;
  private EthContext ethContext;
  private PeerTransactionTracker peerTransactionTracker;

  @Before
  public void setUp() {
    blockchain = executionContext.getBlockchain();
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionValidator()).thenReturn(transactionValidator);
    genesisBlockGasLimit = executionContext.getGenesis().getHeader().getGasLimit();
    syncState = mock(SyncState.class);
    when(syncState.isInSync(anyLong())).thenReturn(true);
    ethContext = mock(EthContext.class);
    EthPeers ethPeers = mock(EthPeers.class);
    when(ethContext.getEthPeers()).thenReturn(ethPeers);
    peerTransactionTracker = mock(PeerTransactionTracker.class);
    transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            batchAddedListener,
            syncState,
            ethContext,
            peerTransactionTracker,
            Wei.of(2),
            metricsSystem);
    blockchain.observeBlockAdded(transactionPool);
  }

  @Test
  public void shouldReturnExclusivelyLocalTransactionsWhenAppropriate() {
    final Transaction localTransaction0 = createTransaction(0);
    transactions.addLocalTransaction(localTransaction0);
    assertThat(transactions.size()).isEqualTo(1);

    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(2);

    transactions.addRemoteTransaction(transaction2);
    assertThat(transactions.size()).isEqualTo(3);

    List<Transaction> localTransactions = transactions.getLocalTransactions();
    assertThat(localTransactions.size()).isEqualTo(1);
  }

  @Test
  public void shouldRemoveTransactionsFromPendingListWhenIncludedInBlockOnChain() {
    transactions.addRemoteTransaction(transaction1);
    assertTransactionPending(transaction1);
    appendBlock(transaction1);

    assertTransactionNotPending(transaction1);
  }

  @Test
  public void shouldRemoveMultipleTransactionsAddedInOneBlock() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);
    appendBlock(transaction1, transaction2);

    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);
    assertThat(transactions.size()).isZero();
  }

  @Test
  public void shouldIgnoreUnknownTransactionsThatAreAddedInABlock() {
    transactions.addRemoteTransaction(transaction1);
    appendBlock(transaction1, transaction2);

    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);
    assertThat(transactions.size()).isZero();
  }

  @Test
  public void shouldNotRemovePendingTransactionsWhenABlockAddedToAFork() {
    transactions.addRemoteTransaction(transaction1);
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block canonicalHead = appendBlock(UInt256.of(1000), commonParent);
    appendBlock(UInt256.ONE, commonParent, transaction1);

    verifyChainHeadIs(canonicalHead);

    assertTransactionPending(transaction1);
  }

  @Test
  public void shouldRemovePendingTransactionsFromAllBlocksOnAForkWhenItBecomesTheCanonicalChain() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block originalChainHead = appendBlock(UInt256.of(1000), commonParent);

    final Block forkBlock1 = appendBlock(UInt256.ONE, commonParent, transaction1);
    verifyChainHeadIs(originalChainHead);

    final Block forkBlock2 = appendBlock(UInt256.of(2000), forkBlock1.getHeader(), transaction2);
    verifyChainHeadIs(forkBlock2);

    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);
  }

  @Test
  public void shouldReadTransactionsFromThePreviousCanonicalHeadWhenAReorgOccurs() {
    givenTransactionIsValid(transaction1);
    givenTransactionIsValid(transaction2);
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block originalFork1 = appendBlock(UInt256.of(1000), commonParent, transaction1);
    final Block originalFork2 = appendBlock(UInt256.ONE, originalFork1.getHeader(), transaction2);
    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);

    final Block reorgFork1 = appendBlock(UInt256.ONE, commonParent);
    verifyChainHeadIs(originalFork2);

    transactions.subscribePendingTransactions(listener);
    final Block reorgFork2 = appendBlock(UInt256.of(2000), reorgFork1.getHeader());
    verifyChainHeadIs(reorgFork2);

    assertTransactionPending(transaction1);
    assertTransactionPending(transaction2);
    verify(listener).onTransactionAdded(transaction1);
    verify(listener).onTransactionAdded(transaction2);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void shouldNotReadTransactionsThatAreInBothForksWhenReorgHappens() {
    givenTransactionIsValid(transaction1);
    givenTransactionIsValid(transaction2);
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block originalFork1 = appendBlock(UInt256.of(1000), commonParent, transaction1);
    final Block originalFork2 = appendBlock(UInt256.ONE, originalFork1.getHeader(), transaction2);
    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);

    final Block reorgFork1 = appendBlock(UInt256.ONE, commonParent, transaction1);
    verifyChainHeadIs(originalFork2);

    final Block reorgFork2 = appendBlock(UInt256.of(2000), reorgFork1.getHeader());
    verifyChainHeadIs(reorgFork2);

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction2);
  }

  @Test
  public void shouldNotAddRemoteTransactionsWhenGasPriceBelowMinimum() {
    final Transaction transaction =
        new TransactionTestFixture()
            .nonce(1)
            .gasLimit(0)
            .gasPrice(Wei.of(1))
            .createTransaction(KEY_PAIR1);
    transactionPool.addRemoteTransactions(singletonList(transaction));

    assertTransactionNotPending(transaction);
    verifyZeroInteractions(transactionValidator); // Reject before validation
  }

  @Test
  public void shouldRejectLocalTransactionsWhenGasPriceBelowMinimum() {

    final Transaction transaction =
        new TransactionTestFixture()
            .nonce(1)
            .gasLimit(0)
            .gasPrice(Wei.of(1))
            .createTransaction(KEY_PAIR1);
    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);

    assertThat(result).isEqualTo(ValidationResult.invalid(GAS_PRICE_TOO_LOW));
    assertTransactionNotPending(transaction);
    verifyZeroInteractions(transactionValidator); // Reject before validation
  }

  @Test
  public void shouldNotAddRemoteTransactionsThatAreInvalidAccordingToInvariantChecks() {
    givenTransactionIsValid(transaction2);
    when(transactionValidator.validate(transaction1))
        .thenReturn(ValidationResult.invalid(NONCE_TOO_LOW));

    transactionPool.addRemoteTransactions(asList(transaction1, transaction2));

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction2);
    verify(batchAddedListener).onTransactionsAdded(singleton(transaction2));
  }

  @Test
  public void shouldNotAddRemoteTransactionsThatAreInvalidAccordingToStateDependentChecks() {
    givenTransactionIsValid(transaction2);
    when(transactionValidator.validate(transaction1)).thenReturn(valid());
    when(transactionValidator.validateForSender(transaction1, null, true))
        .thenReturn(ValidationResult.invalid(NONCE_TOO_LOW));

    transactionPool.addRemoteTransactions(asList(transaction1, transaction2));

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction2);
    verify(batchAddedListener).onTransactionsAdded(singleton(transaction2));
  }

  @Test
  public void shouldAllowSequenceOfTransactionsWithIncreasingNonceFromSameSender() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transaction2 = builder.nonce(2).createTransaction(KEY_PAIR1);
    final Transaction transaction3 = builder.nonce(3).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class))).thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction3), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    assertThat(transactionPool.addLocalTransaction(transaction1)).isEqualTo(valid());
    assertThat(transactionPool.addLocalTransaction(transaction2)).isEqualTo(valid());
    assertThat(transactionPool.addLocalTransaction(transaction3)).isEqualTo(valid());

    assertTransactionPending(transaction1);
    assertTransactionPending(transaction2);
    assertTransactionPending(transaction3);
  }

  @Test
  public void
      shouldAllowSequenceOfTransactionsWithIncreasingNonceFromSameSenderWhenSentInBatchOutOfOrder() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transaction2 = builder.nonce(2).createTransaction(KEY_PAIR1);
    final Transaction transaction3 = builder.nonce(3).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class))).thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction3), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addRemoteTransactions(asList(transaction3, transaction1, transaction2));

    assertTransactionPending(transaction1);
    assertTransactionPending(transaction2);
    assertTransactionPending(transaction3);
  }

  @Test
  public void shouldDiscardRemoteTransactionThatAlreadyExistsBeforeValidation() {
    final PendingTransactions pendingTransactions = mock(PendingTransactions.class);
    final TransactionPool transactionPool =
        new TransactionPool(
            pendingTransactions,
            protocolSchedule,
            protocolContext,
            batchAddedListener,
            syncState,
            ethContext,
            peerTransactionTracker,
            Wei.ZERO,
            metricsSystem);

    when(pendingTransactions.containsTransaction(transaction1.hash())).thenReturn(true);

    transactionPool.addRemoteTransactions(singletonList(transaction1));

    verify(pendingTransactions).containsTransaction(transaction1.hash());
    verifyZeroInteractions(transactionValidator);
    verifyNoMoreInteractions(pendingTransactions);
  }

  @Test
  public void shouldNotNotifyBatchListenerWhenRemoteTransactionDoesNotReplaceExisting() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 =
        builder.nonce(1).gasPrice(Wei.of(10)).createTransaction(KEY_PAIR1);
    final Transaction transaction2 =
        builder.nonce(1).gasPrice(Wei.of(5)).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class))).thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addRemoteTransactions(singletonList(transaction1));
    transactionPool.addRemoteTransactions(singletonList(transaction2));

    assertTransactionPending(transaction1);
    verify(batchAddedListener).onTransactionsAdded(singleton(transaction1));
    verify(batchAddedListener, never()).onTransactionsAdded(singleton(transaction2));
  }

  @Test
  public void shouldNotNotifyBatchListenerWhenLocalTransactionDoesNotReplaceExisting() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 =
        builder.nonce(1).gasPrice(Wei.of(10)).createTransaction(KEY_PAIR1);
    final Transaction transaction2 =
        builder.nonce(1).gasPrice(Wei.of(5)).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class))).thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addLocalTransaction(transaction1);
    transactionPool.addLocalTransaction(transaction2);

    assertTransactionPending(transaction1);
    verify(batchAddedListener).onTransactionsAdded(singletonList(transaction1));
    verify(batchAddedListener, never()).onTransactionsAdded(singletonList(transaction2));
  }

  @Test
  public void shouldRejectLocalTransactionsWhereGasLimitExceedBlockGasLimit() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 =
        builder.gasLimit(genesisBlockGasLimit + 1).createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction1);

    assertThat(transactionPool.addLocalTransaction(transaction1))
        .isEqualTo(ValidationResult.invalid(EXCEEDS_BLOCK_GAS_LIMIT));

    assertTransactionNotPending(transaction1);
    verifyZeroInteractions(batchAddedListener);
  }

  @Test
  public void shouldRejectRemoteTransactionsWhereGasLimitExceedBlockGasLimit() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 =
        builder.gasLimit(genesisBlockGasLimit + 1).createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction1);

    transactionPool.addRemoteTransactions(singleton(transaction1));

    assertTransactionNotPending(transaction1);
    verifyZeroInteractions(batchAddedListener);
  }

  @Test
  public void shouldNotNotifyBatchListenerIfNoTransactionsAreAdded() {
    transactionPool.addRemoteTransactions(emptyList());
    verifyZeroInteractions(batchAddedListener);
  }

  @Test
  public void shouldAllowTransactionWhenAccountWhitelistControllerIsNotPresent() {
    givenTransactionIsValid(transaction1);

    assertThat(transactionPool.addLocalTransaction(transaction1)).isEqualTo(valid());

    assertTransactionPending(transaction1);
  }

  @Test
  public void shouldRejectRemoteTransactionsWhenNotInSync() {
    SyncState syncState = mock(SyncState.class);
    when(syncState.isInSync(anyLong())).thenReturn(false);
    TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            batchAddedListener,
            syncState,
            ethContext,
            peerTransactionTracker,
            Wei.ZERO,
            metricsSystem);

    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transaction2 = builder.nonce(2).createTransaction(KEY_PAIR1);
    final Transaction transaction3 = builder.nonce(3).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class))).thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction3), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addRemoteTransactions(asList(transaction3, transaction1, transaction2));

    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);
    assertTransactionNotPending(transaction3);
    verifyZeroInteractions(batchAddedListener);
  }

  @Test
  public void shouldAllowRemoteTransactionsWhenInSync() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transaction2 = builder.nonce(2).createTransaction(KEY_PAIR1);
    final Transaction transaction3 = builder.nonce(3).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class))).thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction3), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addRemoteTransactions(asList(transaction3, transaction1, transaction2));

    assertTransactionPending(transaction1);
    assertTransactionPending(transaction2);
    assertTransactionPending(transaction3);
  }

  @Test
  public void shouldSendOnlyLocalTransactionToNewlyConnectedPeer() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    EthContext ethContext = ethProtocolManager.ethContext();
    PeerTransactionTracker peerTransactionTracker = new PeerTransactionTracker();
    TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            batchAddedListener,
            syncState,
            ethContext,
            peerTransactionTracker,
            Wei.ZERO,
            metricsSystem);

    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transactionLocal = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transactionRemote = builder.nonce(2).createTransaction(KEY_PAIR1);
    when(transactionValidator.validate(any(Transaction.class))).thenReturn(valid());
    when(transactionValidator.validateForSender(
            any(Transaction.class),
            nullable(Account.class),
            any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addLocalTransaction(transactionLocal);
    transactionPool.addRemoteTransactions(Collections.singletonList(transactionRemote));

    RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    Set<Transaction> transactionsToSendToPeer =
        peerTransactionTracker.claimTransactionsToSendToPeer(peer.getEthPeer());

    assertThat(transactionsToSendToPeer).containsExactly(transactionLocal);
  }

  @Test
  public void shouldCallValidatorWithExpectedValidationParameters() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        ArgumentCaptor.forClass(TransactionValidationParams.class);
    when(transactionValidator.validate(transaction1)).thenReturn(valid());
    when(transactionValidator.validateForSender(any(), any(), txValidationParamCaptor.capture()))
        .thenReturn(valid());

    final TransactionValidationParams expectedValidationParams =
        TransactionValidationParams.transactionPool();

    transactionPool.addLocalTransaction(transaction1);

    assertThat(txValidationParamCaptor.getValue())
        .isEqualToComparingFieldByField(expectedValidationParams);
  }

  private void assertTransactionPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.hash())).contains(t);
  }

  private void assertTransactionNotPending(final Transaction transaction) {
    assertThat(transactions.getTransactionByHash(transaction.hash())).isEmpty();
  }

  private void verifyChainHeadIs(final Block forkBlock2) {
    assertThat(blockchain.getChainHeadHash()).isEqualTo(forkBlock2.getHash());
  }

  private void appendBlock(final Transaction... transactionsToAdd) {
    appendBlock(UInt256.ONE, getHeaderForCurrentChainHead(), transactionsToAdd);
  }

  private BlockHeader getHeaderForCurrentChainHead() {
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }

  private Block appendBlock(
      final UInt256 difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd) {
    final List<Transaction> transactionList = asList(transactionsToAdd);
    final Block block =
        new Block(
            new BlockHeaderTestFixture()
                .difficulty(difficulty)
                .parentHash(parentBlock.getHash())
                .number(parentBlock.getNumber() + 1)
                .buildHeader(),
            new BlockBody(transactionList, emptyList()));
    final List<TransactionReceipt> transactionReceipts =
        transactionList.stream()
            .map(transaction -> new TransactionReceipt(1, 1, emptyList(), Optional.empty()))
            .collect(toList());
    blockchain.appendBlock(block, transactionReceipts);
    return block;
  }

  private Transaction createTransaction(final int transactionNumber) {
    return new TransactionTestFixture()
        .nonce(transactionNumber)
        .gasLimit(0)
        .createTransaction(KEY_PAIR1);
  }

  private void givenTransactionIsValid(final Transaction transaction) {
    when(transactionValidator.validate(transaction)).thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
  }
}

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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.ValidationResult.valid;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.NONCE_TOO_LOW;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class TransactionPoolTest {

  private static final int MAX_TRANSACTIONS = 5;
  private static final KeyPair KEY_PAIR1 =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();

  @Mock private MainnetTransactionValidator transactionValidator;
  @Mock private PendingTransactionListener listener;
  @Mock private MiningParameters miningParameters;
  @Mock private TransactionsMessageSender transactionsMessageSender;
  @Mock private NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;

  @SuppressWarnings("unchecked")
  @Mock
  private ProtocolSchedule protocolSchedule;

  @SuppressWarnings("unchecked")
  @Mock
  private ProtocolSpec protocolSpec;

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private MutableBlockchain blockchain;
  private TransactionBroadcaster transactionBroadcaster;

  private GasPricePendingTransactionsSorter transactions;
  private final Transaction transaction1 = createTransaction(1);
  private final Transaction transaction2 = createTransaction(2);
  private final ExecutionContextTestFixture executionContext = ExecutionContextTestFixture.create();
  private final ProtocolContext protocolContext = executionContext.getProtocolContext();
  private TransactionPool transactionPool;
  private long genesisBlockGasLimit;
  private EthContext ethContext;
  private EthPeers ethPeers;
  private PeerTransactionTracker peerTransactionTracker;
  private ArgumentCaptor<Runnable> syncTaskCapture;

  @Before
  public void setUp() {
    blockchain = executionContext.getBlockchain();
    transactions =
        new GasPricePendingTransactionsSorter(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            MAX_TRANSACTIONS,
            TestClock.fixed(),
            metricsSystem,
            blockchain::getChainHeadHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionValidator()).thenReturn(transactionValidator);
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.legacy());
    genesisBlockGasLimit = executionContext.getGenesis().getHeader().getGasLimit();
    ethContext = mock(EthContext.class);

    final EthScheduler ethScheduler = mock(EthScheduler.class);
    syncTaskCapture = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(ethScheduler).scheduleSyncWorkerTask(syncTaskCapture.capture());
    when(ethContext.getScheduler()).thenReturn(ethScheduler);

    ethPeers = mock(EthPeers.class);
    when(ethContext.getEthPeers()).thenReturn(ethPeers);

    peerTransactionTracker = new PeerTransactionTracker();
    transactionBroadcaster =
        spy(
            new TransactionBroadcaster(
                ethContext,
                transactions,
                peerTransactionTracker,
                transactionsMessageSender,
                newPooledTransactionHashesMessageSender));

    transactionPool = createTransactionPool();
    blockchain.observeBlockAdded(transactionPool);
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.of(2));
  }

  private TransactionPool createTransactionPool() {
    return createTransactionPool(b -> {});
  }

  private TransactionPool createTransactionPool(
      final Consumer<ImmutableTransactionPoolConfiguration.Builder> configConsumer) {
    final ImmutableTransactionPoolConfiguration.Builder configBuilder =
        ImmutableTransactionPoolConfiguration.builder();
    configConsumer.accept(configBuilder);
    final TransactionPoolConfiguration config = configBuilder.build();

    return new TransactionPool(
        transactions,
        protocolSchedule,
        protocolContext,
        transactionBroadcaster,
        ethContext,
        miningParameters,
        metricsSystem,
        config);
  }

  @Test
  public void mainNetValueTransferSucceeds() {
    final Transaction transaction =
        new TransactionTestFixture()
            .value(Wei.ONE)
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);

    assertThat(result).isEqualTo(ValidationResult.valid());
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
  public void shouldRemoveTransactionsFromPendingListWhenIncludedInBlockOnchain() {
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
    final Block canonicalHead = appendBlock(Difficulty.of(1000), commonParent);
    appendBlock(Difficulty.ONE, commonParent, transaction1);

    verifyChainHeadIs(canonicalHead);

    assertTransactionPending(transaction1);
  }

  @Test
  public void shouldRemovePendingTransactionsFromAllBlocksOnAForkWhenItBecomesTheCanonicalChain() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block originalChainHead = appendBlock(Difficulty.of(1000), commonParent);

    final Block forkBlock1 = appendBlock(Difficulty.ONE, commonParent, transaction1);
    verifyChainHeadIs(originalChainHead);

    final Block forkBlock2 = appendBlock(Difficulty.of(2000), forkBlock1.getHeader(), transaction2);
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
    final Block originalFork1 = appendBlock(Difficulty.of(1000), commonParent, transaction1);
    final Block originalFork2 =
        appendBlock(Difficulty.ONE, originalFork1.getHeader(), transaction2);
    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);

    final Block reorgFork1 = appendBlock(Difficulty.ONE, commonParent);
    verifyChainHeadIs(originalFork2);

    transactions.subscribePendingTransactions(listener);
    final Block reorgFork2 = appendBlock(Difficulty.of(2000), reorgFork1.getHeader());
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
    final Block originalFork1 = appendBlock(Difficulty.of(1000), commonParent, transaction1);
    final Block originalFork2 =
        appendBlock(Difficulty.ONE, originalFork1.getHeader(), transaction2);
    assertTransactionNotPending(transaction1);
    assertTransactionNotPending(transaction2);

    final Block reorgFork1 = appendBlock(Difficulty.ONE, commonParent, transaction1);
    verifyChainHeadIs(originalFork2);

    final Block reorgFork2 = appendBlock(Difficulty.of(2000), reorgFork1.getHeader());
    verifyChainHeadIs(reorgFork2);

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction2);
  }

  @Test
  public void addLocalTransaction_strictReplayProtectionOn_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(1);
    givenTransactionIsValid(tx);

    assertLocalTransactionInvalid(tx, TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURE_REQUIRED);
  }

  @Test
  public void
      addLocalTransaction_strictReplayProtectionOn_txWithoutChainId_chainIdIsConfigured_protectionNotSupportedAtCurrentBlock() {
    protocolSupportsTxReplayProtection(1337, false);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(1);
    givenTransactionIsValid(tx);

    assertLocalTransactionValid(tx);
  }

  @Test
  public void addLocalTransaction_strictReplayProtectionOff_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(false));
    final Transaction tx = createTransactionWithoutChainId(1);
    givenTransactionIsValid(tx);

    assertLocalTransactionValid(tx);
  }

  @Test
  public void
      addLocalTransaction_strictReplayProtectionOn_txWithoutChainId_chainIdIsNotConfigured() {
    protocolDoesNotSupportTxReplayProtection();
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(1);
    givenTransactionIsValid(tx);

    assertLocalTransactionValid(tx);
  }

  @Test
  public void addLocalTransaction_strictReplayProtectionOn_txWithChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransaction(1);
    givenTransactionIsValid(tx);

    assertLocalTransactionValid(tx);
  }

  @Test
  public void
      addRemoteTransactions_strictReplayProtectionOn_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(1);
    givenTransactionIsValid(tx);

    assertRemoteTransactionValid(tx);
  }

  @Test
  public void
      addRemoteTransactions_strictReplayProtectionOff_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(false));
    final Transaction tx = createTransactionWithoutChainId(1);
    givenTransactionIsValid(tx);

    assertRemoteTransactionValid(tx);
  }

  @Test
  public void
      addRemoteTransactions_strictReplayProtectionOn_txWithoutChainId_chainIdIsNotConfigured() {
    protocolDoesNotSupportTxReplayProtection();
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(1);
    givenTransactionIsValid(tx);

    assertRemoteTransactionValid(tx);
  }

  @Test
  public void addRemoteTransactions_strictReplayProtectionOn_txWithChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransaction(1);
    givenTransactionIsValid(tx);

    assertRemoteTransactionValid(tx);
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
    verifyNoInteractions(transactionValidator); // Reject before validation
  }

  @Test
  public void shouldNotAddRemoteTransactionsThatAreInvalidAccordingToInvariantChecks() {
    givenTransactionIsValid(transaction2);
    when(transactionValidator.validate(eq(transaction1), any(Optional.class), any()))
        .thenReturn(ValidationResult.invalid(NONCE_TOO_LOW));

    transactionPool.addRemoteTransactions(asList(transaction1, transaction2));

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction2);
    verify(transactionBroadcaster).onTransactionsAdded(singleton(transaction2));
  }

  @Test
  public void shouldNotAddRemoteTransactionsThatAreInvalidAccordingToStateDependentChecks() {
    givenTransactionIsValid(transaction2);
    when(transactionValidator.validate(eq(transaction1), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), eq(null), any(TransactionValidationParams.class)))
        .thenReturn(ValidationResult.invalid(NONCE_TOO_LOW));
    transactionPool.addRemoteTransactions(asList(transaction1, transaction2));

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction2);
    verify(transactionBroadcaster).onTransactionsAdded(singleton(transaction2));
    verify(transactionValidator).validate(eq(transaction1), any(Optional.class), any());
    verify(transactionValidator)
        .validateForSender(eq(transaction1), eq(null), any(TransactionValidationParams.class));
    verify(transactionValidator).validate(eq(transaction2), any(Optional.class), any());
    verify(transactionValidator).validateForSender(eq(transaction2), any(), any());
    verify(transactionValidator, atLeastOnce()).getGoQuorumCompatibilityMode();
    verifyNoMoreInteractions(transactionValidator);
  }

  @Test
  public void shouldAllowSequenceOfTransactionsWithIncreasingNonceFromSameSender() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transaction2 = builder.nonce(2).createTransaction(KEY_PAIR1);
    final Transaction transaction3 = builder.nonce(3).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
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

    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
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
    final GasPricePendingTransactionsSorter pendingTransactions =
        mock(GasPricePendingTransactionsSorter.class);
    final TransactionPool transactionPool =
        new TransactionPool(
            pendingTransactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            TransactionPoolConfiguration.DEFAULT);

    when(pendingTransactions.containsTransaction(transaction1.getHash())).thenReturn(true);

    transactionPool.addRemoteTransactions(singletonList(transaction1));

    verify(pendingTransactions).containsTransaction(transaction1.getHash());
    verifyNoInteractions(transactionValidator);
    verifyNoMoreInteractions(pendingTransactions);
  }

  @Test
  public void shouldNotNotifyBatchListenerWhenRemoteTransactionDoesNotReplaceExisting() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 =
        builder.nonce(1).gasPrice(Wei.of(10)).createTransaction(KEY_PAIR1);
    final Transaction transaction2 =
        builder.nonce(1).gasPrice(Wei.of(5)).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addRemoteTransactions(singletonList(transaction1));
    transactionPool.addRemoteTransactions(singletonList(transaction2));

    assertTransactionPending(transaction1);
    verify(transactionBroadcaster).onTransactionsAdded(singleton(transaction1));
    verify(transactionBroadcaster, never()).onTransactionsAdded(singleton(transaction2));
  }

  @Test
  public void shouldNotNotifyBatchListenerWhenLocalTransactionDoesNotReplaceExisting() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 =
        builder.nonce(1).gasPrice(Wei.of(10)).createTransaction(KEY_PAIR1);
    final Transaction transaction2 =
        builder.nonce(1).gasPrice(Wei.of(5)).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction1), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction2), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());

    transactionPool.addLocalTransaction(transaction1);
    transactionPool.addLocalTransaction(transaction2);

    assertTransactionPending(transaction1);
    verify(transactionBroadcaster).onTransactionsAdded(singletonList(transaction1));
    verify(transactionBroadcaster, never()).onTransactionsAdded(singletonList(transaction2));
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
    verifyNoInteractions(transactionBroadcaster);
  }

  @Test
  public void shouldRejectRemoteTransactionsWhereGasLimitExceedBlockGasLimit() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 =
        builder.gasLimit(genesisBlockGasLimit + 1).createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction1);

    transactionPool.addRemoteTransactions(singleton(transaction1));

    assertTransactionNotPending(transaction1);
    verifyNoInteractions(transactionBroadcaster);
  }

  @Test
  public void shouldNotNotifyBatchListenerIfNoTransactionsAreAdded() {
    transactionPool.addRemoteTransactions(emptyList());
    verifyNoInteractions(transactionBroadcaster);
  }

  @Test
  public void shouldSendPooledTransactionHashesIfPeerSupportsEth65() {
    EthPeer peer = mock(EthPeer.class);
    when(peer.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES)).thenReturn(true);

    givenTransactionIsValid(transaction1);
    transactionPool.addLocalTransaction(transaction1);
    transactionPool.handleConnect(peer);
    syncTaskCapture.getValue().run();
    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(peer);
  }

  @Test
  public void shouldSendFullTransactionsIfPeerDoesNotSupportEth65() {
    EthPeer peer = mock(EthPeer.class);
    when(peer.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES)).thenReturn(false);

    givenTransactionIsValid(transaction1);
    transactionPool.addLocalTransaction(transaction1);
    transactionPool.handleConnect(peer);
    syncTaskCapture.getValue().run();
    verify(transactionsMessageSender).sendTransactionsToPeer(peer);
  }

  @Test
  public void shouldAllowTransactionWhenAccountAllowlistControllerIsNotPresent() {
    givenTransactionIsValid(transaction1);

    assertThat(transactionPool.addLocalTransaction(transaction1)).isEqualTo(valid());

    assertTransactionPending(transaction1);
  }

  @Test
  public void shouldAcceptRemoteTransactionsWhenNotInSync() {

    TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            TransactionPoolConfiguration.DEFAULT);

    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transaction2 = builder.nonce(2).createTransaction(KEY_PAIR1);
    final Transaction transaction3 = builder.nonce(3).createTransaction(KEY_PAIR1);
    when(transactionValidator.validate(eq(transaction1), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validate(eq(transaction2), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validate(eq(transaction3), any(Optional.class), any()))
        .thenReturn(valid());
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
    // verifyNoInteractions(transactionBroadcaster);
  }

  @Test
  public void shouldAllowRemoteTransactionsWhenInSync() {
    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transaction1 = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transaction2 = builder.nonce(2).createTransaction(KEY_PAIR1);
    final Transaction transaction3 = builder.nonce(3).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
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
  public void shouldSendFullTransactionPoolToNewlyConnectedPeer() {
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    EthContext ethContext = ethProtocolManager.ethContext();
    TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            TransactionPoolConfiguration.DEFAULT);

    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transactionLocal = builder.nonce(1).createTransaction(KEY_PAIR1);
    final Transaction transactionRemote = builder.nonce(2).createTransaction(KEY_PAIR1);
    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
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

    assertThat(transactionsToSendToPeer).contains(transactionLocal, transactionRemote);
  }

  @Test
  public void shouldCallValidatorWithExpectedValidationParameters() {
    final ArgumentCaptor<TransactionValidationParams> txValidationParamCaptor =
        ArgumentCaptor.forClass(TransactionValidationParams.class);
    when(transactionValidator.validate(eq(transaction1), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(any(), any(), txValidationParamCaptor.capture()))
        .thenReturn(valid());

    final TransactionValidationParams expectedValidationParams =
        TransactionValidationParams.transactionPool();

    transactionPool.addLocalTransaction(transaction1);

    assertThat(txValidationParamCaptor.getValue())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidationParams);
  }

  @Test
  public void shouldIgnoreFeeCapIfSetZero() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final EthContext ethContext = ethProtocolManager.ethContext();
    final Wei twoEthers = Wei.fromEth(2);
    final TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            ImmutableTransactionPoolConfiguration.builder().txFeeCap(Wei.ZERO).build());
    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            any(Transaction.class),
            nullable(Account.class),
            any(TransactionValidationParams.class)))
        .thenReturn(valid());
    assertThat(
            transactionPool
                .addLocalTransaction(
                    new TransactionTestFixture()
                        .nonce(1)
                        .gasPrice(twoEthers.add(Wei.of(1)))
                        .createTransaction(KEY_PAIR1))
                .isValid())
        .isTrue();
  }

  @Test
  public void shouldIgnoreEIP1559TransactionWhenNotAllowed() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final EthContext ethContext = ethProtocolManager.ethContext();
    final TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            ImmutableTransactionPoolConfiguration.builder().txFeeCap(Wei.ONE).build());
    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
    final Transaction transaction =
        new TransactionTestFixture()
            .nonce(1)
            .type(TransactionType.EIP1559)
            .maxFeePerGas(Optional.of(Wei.ONE))
            .maxPriorityFeePerGas(Optional.of(Wei.ONE))
            .gasLimit(10)
            .gasPrice(null)
            .createTransaction(KEY_PAIR1);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);
    assertThat(result.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.INVALID_TRANSACTION_FORMAT);
  }

  @Test
  public void shouldIgnoreEIP1559TransactionBeforeTheFork() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final EthContext ethContext = ethProtocolManager.ethContext();
    final TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            ImmutableTransactionPoolConfiguration.builder().txFeeCap(Wei.ONE).build());
    // pre-London feemarket
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.legacy());
    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
    final Transaction transaction =
        new TransactionTestFixture()
            .nonce(1)
            .type(TransactionType.EIP1559)
            .maxFeePerGas(Optional.of(Wei.ONE))
            .maxPriorityFeePerGas(Optional.of(Wei.ONE))
            .gasLimit(10)
            .gasPrice(null)
            .createTransaction(KEY_PAIR1);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);
    assertThat(result.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.INVALID_TRANSACTION_FORMAT);
  }

  @Test
  public void shouldRejectLocalTransactionIfFeeCapExceeded() {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final EthContext ethContext = ethProtocolManager.ethContext();
    final Wei twoEthers = Wei.fromEth(2);
    TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            ImmutableTransactionPoolConfiguration.builder().txFeeCap(twoEthers).build());

    final TransactionTestFixture builder = new TransactionTestFixture();
    final Transaction transactionLocal =
        builder.nonce(1).gasPrice(twoEthers.add(Wei.of(1))).createTransaction(KEY_PAIR1);

    when(transactionValidator.validate(any(Transaction.class), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            any(Transaction.class),
            nullable(Account.class),
            any(TransactionValidationParams.class)))
        .thenReturn(valid());

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transactionLocal);
    assertThat(result.getInvalidReason()).isEqualTo(TransactionInvalidReason.TX_FEECAP_EXCEEDED);
  }

  @Test
  public void shouldRejectGoQuorumTransactionWithNonZeroValue() {
    when(transactionValidator.getGoQuorumCompatibilityMode()).thenReturn(true);

    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();
    final EthContext ethContext = ethProtocolManager.ethContext();
    final Wei twoEthers = Wei.fromEth(2);

    final TransactionPool transactionPool =
        new TransactionPool(
            transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            new MiningParameters.Builder().minTransactionGasPrice(Wei.ZERO).build(),
            metricsSystem,
            ImmutableTransactionPoolConfiguration.builder().txFeeCap(twoEthers).build());

    final Transaction transaction37 =
        Transaction.builder().v(BigInteger.valueOf(37)).value(Wei.ONE).build();
    final Transaction transaction38 =
        Transaction.builder().v(BigInteger.valueOf(38)).value(Wei.ONE).build();

    final ValidationResult<TransactionInvalidReason> result37 =
        transactionPool.addLocalTransaction(transaction37);
    final ValidationResult<TransactionInvalidReason> result38 =
        transactionPool.addLocalTransaction(transaction38);

    assertThat(result37.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.ETHER_VALUE_NOT_SUPPORTED);
    assertThat(result38.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.ETHER_VALUE_NOT_SUPPORTED);
  }

  @Test
  public void shouldAcceptZeroGasPriceFrontierTransactionsWhenMining() {
    when(miningParameters.isMiningEnabled()).thenReturn(true);

    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.FRONTIER)
            .gasPrice(Wei.ZERO)
            .value(Wei.ONE)
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);

    assertThat(result).isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldRejectZeroGasPriceFrontierTransactionsWhenNotMining() {
    when(miningParameters.isMiningEnabled()).thenReturn(false);

    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.FRONTIER)
            .gasPrice(Wei.ZERO)
            .createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);

    assertThat(result.getInvalidReason()).isEqualTo(TransactionInvalidReason.GAS_PRICE_TOO_LOW);
  }

  @Test
  public void shouldAcceptZeroGasPriceFrontierTxsWhenMinGasPriceIsZero() {
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.ZERO);

    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.FRONTIER)
            .gasPrice(Wei.ZERO)
            .value(Wei.ONE)
            .chainId(Optional.of(BigInteger.ONE))
            .createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);

    assertThat(result).isEqualTo(ValidationResult.valid());
  }

  @Test
  public void shouldAcceptZeroGasPriceFrontierTxsWhenMinGasPriceIsZeroAndLondonWithZeroBaseFee() {
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.ZERO);
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.london(0, Optional.of(Wei.ZERO)));
    whenBlockBaseFeeIsZero();

    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.FRONTIER)
            .gasPrice(Wei.ZERO)
            .createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);

    assertThat(result).isEqualTo(ValidationResult.valid());
  }

  private void whenBlockBaseFeeIsZero() {
    final BlockHeader header =
        BlockHeaderBuilder.fromHeader(blockchain.getChainHeadHeader())
            .baseFee(Wei.ZERO)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .parentHash(blockchain.getChainHeadHash())
            .buildBlockHeader();
    blockchain.appendBlock(new Block(header, BlockBody.empty()), emptyList());
  }

  @Test
  public void shouldAcceptZeroGasPrice1559TxsWhenMinGasPriceIsZeroAndLondonWithZeroBaseFee() {
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.ZERO);
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.london(0, Optional.of(Wei.ZERO)));
    whenBlockBaseFeeIsZero();

    final Transaction transaction =
        new TransactionTestFixture()
            .type(TransactionType.EIP1559)
            .gasPrice(null)
            .maxFeePerGas(Optional.of(Wei.ZERO))
            .maxPriorityFeePerGas(Optional.of(Wei.ZERO))
            .createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction);

    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(transaction);

    assertThat(result).isEqualTo(ValidationResult.valid());
  }

  private void assertTransactionPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.getHash())).contains(t);
  }

  private void assertTransactionNotPending(final Transaction transaction) {
    assertThat(transactions.getTransactionByHash(transaction.getHash())).isEmpty();
  }

  private void verifyChainHeadIs(final Block forkBlock2) {
    assertThat(blockchain.getChainHeadHash()).isEqualTo(forkBlock2.getHash());
  }

  private void appendBlock(final Transaction... transactionsToAdd) {
    appendBlock(Difficulty.ONE, getHeaderForCurrentChainHead(), transactionsToAdd);
  }

  private BlockHeader getHeaderForCurrentChainHead() {
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }

  private Block appendBlock(
      final Difficulty difficulty,
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

  private Transaction createTransactionWithoutChainId(final int transactionNumber) {
    return new TransactionTestFixture()
        .chainId(Optional.empty())
        .nonce(transactionNumber)
        .gasLimit(0)
        .createTransaction(KEY_PAIR1);
  }

  private void protocolDoesNotSupportTxReplayProtection() {
    when(protocolSchedule.getChainId()).thenReturn(Optional.empty());
  }

  private void protocolSupportsTxReplayProtection(
      final long chainId, final boolean isSupportedAtCurrentBlock) {
    when(protocolSpec.isReplayProtectionSupported()).thenReturn(isSupportedAtCurrentBlock);
    when(protocolSchedule.getChainId()).thenReturn(Optional.of(BigInteger.valueOf(chainId)));
  }

  private void givenTransactionIsValid(final Transaction transaction) {
    when(transactionValidator.validate(eq(transaction), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidator.validateForSender(
            eq(transaction), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
  }

  private void assertLocalTransactionInvalid(
      final Transaction tx, final TransactionInvalidReason invalidReason) {
    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(tx);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getInvalidReason()).isEqualTo(invalidReason);
    assertTransactionNotPending(tx);
  }

  private void assertLocalTransactionValid(final Transaction tx) {
    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(tx);

    assertThat(result.isValid()).isTrue();
    assertTransactionPending(tx);
  }

  private void assertRemoteTransactionValid(final Transaction tx) {
    transactionPool.addRemoteTransactions(List.of(tx));

    verify(transactionBroadcaster).onTransactionsAdded(singleton(tx));
    assertTransactionPending(tx);
  }
}

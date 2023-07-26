/*
 * Copyright Hyperledger Besu Contributors.
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
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.mainnet.ValidationResult.valid;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.GAS_PRICE_TOO_LOW;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.NONCE_TOO_LOW;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.TRANSACTION_REPLACEMENT_UNDERPRICED;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.TX_FEECAP_EXCEEDED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
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
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidatorFactory;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractTransactionsLayeredPendingTransactionsTest {

  protected static final KeyPair KEY_PAIR1 =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();

  private static final KeyPair KEY_PAIR2 =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  protected TransactionValidatorFactory transactionValidatorFactory;

  @Mock protected PendingTransactionAddedListener listener;
  @Mock protected MiningParameters miningParameters;
  @Mock protected TransactionsMessageSender transactionsMessageSender;
  @Mock protected NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender;
  @Mock protected ProtocolSpec protocolSpec;

  protected ProtocolSchedule protocolSchedule;

  protected final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  protected MutableBlockchain blockchain;
  private TransactionBroadcaster transactionBroadcaster;

  protected PendingTransactions transactions;
  private final Transaction transaction0 = createTransaction(0);
  private final Transaction transaction1 = createTransaction(1);

  private final Transaction transactionOtherSender = createTransaction(0, KEY_PAIR2);
  private ExecutionContextTestFixture executionContext;
  protected ProtocolContext protocolContext;
  protected TransactionPool transactionPool;
  protected TransactionPoolConfiguration poolConfig;
  protected long blockGasLimit;
  protected EthProtocolManager ethProtocolManager;
  private EthContext ethContext;
  private PeerTransactionTracker peerTransactionTracker;
  private ArgumentCaptor<Runnable> syncTaskCapture;

  protected abstract PendingTransactions createPendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester);

  protected abstract ExecutionContextTestFixture createExecutionContextTestFixture();

  protected abstract FeeMarket getFeeMarket();

  @BeforeEach
  public void setUp() {
    executionContext = createExecutionContextTestFixture();
    protocolContext = executionContext.getProtocolContext();
    blockchain = executionContext.getBlockchain();

    when(protocolSpec.getTransactionValidatorFactory()).thenReturn(transactionValidatorFactory);
    when(protocolSpec.getFeeMarket()).thenReturn(getFeeMarket());
    protocolSchedule = spy(executionContext.getProtocolSchedule());
    doReturn(protocolSpec).when(protocolSchedule).getByBlockHeader(any());
    blockGasLimit = blockchain.getChainHeadBlock().getHeader().getGasLimit();
    ethProtocolManager = EthProtocolManagerTestUtil.create();
    ethContext = spy(ethProtocolManager.ethContext());

    final EthScheduler ethScheduler = mock(EthScheduler.class);
    syncTaskCapture = ArgumentCaptor.forClass(Runnable.class);
    doNothing().when(ethScheduler).scheduleSyncWorkerTask(syncTaskCapture.capture());
    doReturn(ethScheduler).when(ethContext).getScheduler();

    peerTransactionTracker = new PeerTransactionTracker();

    transactionPool = createTransactionPool();

    blockchain.observeBlockAdded(transactionPool);
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.of(2));
  }

  protected TransactionPool createTransactionPool() {
    return createTransactionPool(b -> {});
  }

  protected TransactionPool createTransactionPool(
      final Consumer<ImmutableTransactionPoolConfiguration.Builder> configConsumer) {
    final ImmutableTransactionPoolConfiguration.Builder configBuilder =
        ImmutableTransactionPoolConfiguration.builder();
    configConsumer.accept(configBuilder);
    poolConfig = configBuilder.build();

    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());

    final BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester =
        (t1, t2) ->
            transactionReplacementHandler.shouldReplace(
                t1, t2, protocolContext.getBlockchain().getChainHeadHeader());

    transactions = spy(createPendingTransactionsSorter(poolConfig, transactionReplacementTester));

    transactionBroadcaster =
        spy(
            new TransactionBroadcaster(
                ethContext,
                peerTransactionTracker,
                transactionsMessageSender,
                newPooledTransactionHashesMessageSender));
    final TransactionPool txPool =
        new TransactionPool(
            () -> transactions,
            protocolSchedule,
            protocolContext,
            transactionBroadcaster,
            ethContext,
            miningParameters,
            new TransactionPoolMetrics(metricsSystem),
            poolConfig);
    txPool.setEnabled();
    return txPool;
  }

  @Test
  public void localTransactionHappyPath() {
    final Transaction transaction = createTransaction(0);

    givenTransactionIsValid(transaction);

    addAndAssertLocalTransactionValid(transaction);
  }

  @Test
  public void shouldReturnExclusivelyLocalTransactionsWhenAppropriate() {
    final Transaction localTransaction0 = createTransaction(0, KEY_PAIR2);

    givenTransactionIsValid(localTransaction0);
    givenTransactionIsValid(transaction0);
    givenTransactionIsValid(transaction1);

    addAndAssertLocalTransactionValid(localTransaction0);
    addAndAssertRemoteTransactionValid(transaction0);
    addAndAssertRemoteTransactionValid(transaction1);

    assertThat(transactions.size()).isEqualTo(3);
    List<Transaction> localTransactions = transactions.getLocalTransactions();
    assertThat(localTransactions.size()).isEqualTo(1);
  }

  @Test
  public void shouldRemoveTransactionsFromPendingListWhenIncludedInBlockOnchain() {
    transactions.addRemoteTransaction(transaction0, Optional.empty());
    assertTransactionPending(transaction0);
    appendBlock(transaction0);

    assertTransactionNotPending(transaction0);
  }

  @Test
  public void shouldRemoveMultipleTransactionsAddedInOneBlock() {
    transactions.addRemoteTransaction(transaction0, Optional.empty());
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    appendBlock(transaction0, transaction1);

    assertTransactionNotPending(transaction0);
    assertTransactionNotPending(transaction1);
    assertThat(transactions.size()).isZero();
  }

  @Test
  public void shouldIgnoreUnknownTransactionsThatAreAddedInABlock() {
    transactions.addRemoteTransaction(transaction0, Optional.empty());
    appendBlock(transaction0, transaction1);

    assertTransactionNotPending(transaction0);
    assertTransactionNotPending(transaction1);
    assertThat(transactions.size()).isZero();
  }

  @Test
  public void shouldNotRemovePendingTransactionsWhenABlockAddedToAFork() {
    transactions.addRemoteTransaction(transaction0, Optional.empty());
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block canonicalHead = appendBlock(Difficulty.of(1000), commonParent);
    appendBlock(Difficulty.ONE, commonParent, transaction0);

    verifyChainHeadIs(canonicalHead);

    assertTransactionPending(transaction0);
  }

  @Test
  public void shouldRemovePendingTransactionsFromAllBlocksOnAForkWhenItBecomesTheCanonicalChain() {
    transactions.addRemoteTransaction(transaction0, Optional.empty());
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block originalChainHead = appendBlock(Difficulty.of(1000), commonParent);

    final Block forkBlock1 = appendBlock(Difficulty.ONE, commonParent, transaction0);
    verifyChainHeadIs(originalChainHead);

    final Block forkBlock2 = appendBlock(Difficulty.of(2000), forkBlock1.getHeader(), transaction1);
    verifyChainHeadIs(forkBlock2);

    assertTransactionNotPending(transaction0);
    assertTransactionNotPending(transaction1);
  }

  @Test
  public void shouldReAddTransactionsFromThePreviousCanonicalHeadWhenAReorgOccurs() {
    givenTransactionIsValid(transaction0);
    givenTransactionIsValid(transactionOtherSender);
    transactions.addLocalTransaction(transaction0, Optional.empty());
    transactions.addRemoteTransaction(transactionOtherSender, Optional.empty());
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block originalFork1 = appendBlock(Difficulty.of(1000), commonParent, transaction0);
    final Block originalFork2 =
        appendBlock(Difficulty.ONE, originalFork1.getHeader(), transactionOtherSender);
    assertTransactionNotPending(transaction0);
    assertTransactionNotPending(transactionOtherSender);
    assertThat(transactions.getLocalTransactions()).isEmpty();

    final Block reorgFork1 = appendBlock(Difficulty.ONE, commonParent);
    verifyChainHeadIs(originalFork2);

    transactions.subscribePendingTransactions(listener);
    final Block reorgFork2 = appendBlock(Difficulty.of(2000), reorgFork1.getHeader());
    verifyChainHeadIs(reorgFork2);

    assertTransactionPending(transaction0);
    assertTransactionPending(transactionOtherSender);
    assertThat(transactions.getLocalTransactions()).contains(transaction0);
    assertThat(transactions.getLocalTransactions()).doesNotContain(transactionOtherSender);
    verify(listener).onTransactionAdded(transaction0);
    verify(listener).onTransactionAdded(transactionOtherSender);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void shouldNotReAddTransactionsThatAreInBothForksWhenReorgHappens() {
    givenTransactionIsValid(transaction0);
    givenTransactionIsValid(transaction1);
    transactions.addRemoteTransaction(transaction0, Optional.empty());
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    final BlockHeader commonParent = getHeaderForCurrentChainHead();
    final Block originalFork1 = appendBlock(Difficulty.of(1000), commonParent, transaction0);
    final Block originalFork2 =
        appendBlock(Difficulty.ONE, originalFork1.getHeader(), transaction1);
    assertTransactionNotPending(transaction0);
    assertTransactionNotPending(transaction1);

    final Block reorgFork1 = appendBlock(Difficulty.ONE, commonParent, transaction1);
    verifyChainHeadIs(originalFork2);

    final Block reorgFork2 = appendBlock(Difficulty.of(2000), reorgFork1.getHeader());
    verifyChainHeadIs(reorgFork2);

    assertTransactionPending(transaction0);
    assertTransactionNotPending(transaction1);
  }

  @Test
  public void addLocalTransaction_strictReplayProtectionOn_txWithChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransaction(0);
    givenTransactionIsValid(tx);

    addAndAssertLocalTransactionValid(tx);
  }

  @Test
  public void addRemoteTransactions_strictReplayProtectionOn_txWithChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransaction(0);
    givenTransactionIsValid(tx);

    addAndAssertRemoteTransactionValid(tx);
  }

  @Test
  public void shouldNotAddRemoteTransactionsWhenGasPriceBelowMinimum() {
    final Transaction transaction = createTransaction(1, Wei.ONE);
    transactionPool.addRemoteTransactions(singletonList(transaction));

    assertTransactionNotPending(transaction);
    verifyNoMoreInteractions(transactionValidatorFactory);
  }

  @Test
  public void shouldNotAddRemoteTransactionsThatAreInvalidAccordingToStateDependentChecks() {
    givenTransactionIsValid(transaction0);
    givenTransactionIsValid(transaction1);
    when(transactionValidatorFactory
            .get()
            .validateForSender(eq(transaction1), eq(null), any(TransactionValidationParams.class)))
        .thenReturn(ValidationResult.invalid(NONCE_TOO_LOW));
    transactionPool.addRemoteTransactions(asList(transaction0, transaction1));

    assertTransactionPending(transaction0);
    assertTransactionNotPending(transaction1);
    verify(transactionBroadcaster).onTransactionsAdded(singletonList(transaction0));
    verify(transactionValidatorFactory.get())
        .validate(eq(transaction0), any(Optional.class), any());
    verify(transactionValidatorFactory.get())
        .validateForSender(eq(transaction0), eq(null), any(TransactionValidationParams.class));
    verify(transactionValidatorFactory.get())
        .validate(eq(transaction1), any(Optional.class), any());
    verify(transactionValidatorFactory.get()).validateForSender(eq(transaction1), any(), any());
    verifyNoMoreInteractions(transactionValidatorFactory.get());
  }

  @Test
  public void shouldAllowSequenceOfTransactionsWithIncreasingNonceFromSameSender() {
    final Transaction transaction1 = createTransaction(0);
    final Transaction transaction2 = createTransaction(1);
    final Transaction transaction3 = createTransaction(2);

    givenTransactionIsValid(transaction1);
    givenTransactionIsValid(transaction2);
    givenTransactionIsValid(transaction3);

    addAndAssertLocalTransactionValid(transaction1);
    addAndAssertLocalTransactionValid(transaction2);
    addAndAssertLocalTransactionValid(transaction3);
  }

  @Test
  public void
      shouldAllowSequenceOfTransactionsWithIncreasingNonceFromSameSenderWhenSentInBatchOutOfOrder() {
    final Transaction transaction1 = createTransaction(0);
    final Transaction transaction2 = createTransaction(1);
    final Transaction transaction3 = createTransaction(2);

    givenTransactionIsValid(transaction1);
    givenTransactionIsValid(transaction2);
    givenTransactionIsValid(transaction3);

    transactionPool.addRemoteTransactions(List.of(transaction3, transaction1, transaction2));
    assertRemoteTransactionValid(transaction3);
    assertRemoteTransactionValid(transaction1);
    assertRemoteTransactionValid(transaction2);
  }

  @Test
  public void shouldDiscardRemoteTransactionThatAlreadyExistsBeforeValidation() {
    doReturn(true).when(transactions).containsTransaction(transaction0);
    transactionPool.addRemoteTransactions(singletonList(transaction0));

    verify(transactions).containsTransaction(transaction0);
    verifyNoInteractions(transactionValidatorFactory);
  }

  @Test
  public void shouldNotNotifyBatchListenerWhenRemoteTransactionDoesNotReplaceExisting() {
    final Transaction transaction1 = createTransaction(0, Wei.of(100));
    final Transaction transaction2 = createTransaction(0, Wei.of(50));

    givenTransactionIsValid(transaction1);
    givenTransactionIsValid(transaction2);

    addAndAssertRemoteTransactionValid(transaction1);
    addAndAssertRemoteTransactionInvalid(transaction2);
  }

  @Test
  public void shouldNotNotifyBatchListenerWhenLocalTransactionDoesNotReplaceExisting() {
    final Transaction transaction1 = createTransaction(0, Wei.of(10));
    final Transaction transaction2 = createTransaction(0, Wei.of(9));

    givenTransactionIsValid(transaction1);
    givenTransactionIsValid(transaction2);

    addAndAssertLocalTransactionValid(transaction1);
    addAndAssertLocalTransactionInvalid(transaction2, TRANSACTION_REPLACEMENT_UNDERPRICED);
  }

  @Test
  public void shouldRejectLocalTransactionsWhereGasLimitExceedBlockGasLimit() {
    final Transaction transaction1 =
        createBaseTransaction(0).gasLimit(blockGasLimit + 1).createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction1);

    addAndAssertLocalTransactionInvalid(transaction1, EXCEEDS_BLOCK_GAS_LIMIT);
  }

  @Test
  public void shouldRejectRemoteTransactionsWhereGasLimitExceedBlockGasLimit() {
    final Transaction transaction1 =
        createBaseTransaction(0).gasLimit(blockGasLimit + 1).createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction1);

    addAndAssertRemoteTransactionInvalid(transaction1);
  }

  @Test
  public void
      shouldAcceptAsPostponedLocalTransactionsEvenIfAnInvalidTransactionWithLowerNonceExists() {
    final Transaction invalidTx =
        createBaseTransaction(0).gasLimit(blockGasLimit + 1).createTransaction(KEY_PAIR1);

    final Transaction nextTx = createBaseTransaction(1).gasLimit(1).createTransaction(KEY_PAIR1);

    givenTransactionIsValid(invalidTx);
    givenTransactionIsValid(nextTx);

    addAndAssertLocalTransactionInvalid(invalidTx, EXCEEDS_BLOCK_GAS_LIMIT);
    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(nextTx);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  public void shouldRejectLocalTransactionsWhenNonceTooFarInFuture() {
    final Transaction transaction1 = createTransaction(Integer.MAX_VALUE);

    givenTransactionIsValid(transaction1);

    addAndAssertLocalTransactionInvalid(transaction1, NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER);
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

    givenTransactionIsValid(transaction0);
    transactionPool.addLocalTransaction(transaction0);
    transactionPool.handleConnect(peer);
    syncTaskCapture.getValue().run();
    verify(newPooledTransactionHashesMessageSender).sendTransactionHashesToPeer(peer);
  }

  @Test
  public void shouldSendFullTransactionsIfPeerDoesNotSupportEth65() {
    EthPeer peer = mock(EthPeer.class);
    when(peer.hasSupportForMessage(EthPV65.NEW_POOLED_TRANSACTION_HASHES)).thenReturn(false);

    givenTransactionIsValid(transaction0);
    transactionPool.addLocalTransaction(transaction0);
    transactionPool.handleConnect(peer);
    syncTaskCapture.getValue().run();
    verify(transactionsMessageSender).sendTransactionsToPeer(peer);
  }

  @Test
  public void shouldSendFullTransactionPoolToNewlyConnectedPeer() {
    final Transaction transactionLocal = createTransaction(0);
    final Transaction transactionRemote = createTransaction(1);

    givenTransactionIsValid(transactionLocal);
    givenTransactionIsValid(transactionRemote);

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

    when(transactionValidatorFactory.get().validate(eq(transaction0), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidatorFactory
            .get()
            .validateForSender(any(), any(), txValidationParamCaptor.capture()))
        .thenReturn(valid());

    final TransactionValidationParams expectedValidationParams =
        TransactionValidationParams.transactionPool();

    transactionPool.addLocalTransaction(transaction0);

    assertThat(txValidationParamCaptor.getValue())
        .usingRecursiveComparison()
        .isEqualTo(expectedValidationParams);
  }

  @Test
  public void shouldIgnoreFeeCapIfSetZero() {
    final Wei twoEthers = Wei.fromEth(2);
    transactionPool = createTransactionPool(b -> b.txFeeCap(Wei.ZERO));
    final Transaction transaction = createTransaction(0, twoEthers.add(Wei.of(1)));

    givenTransactionIsValid(transaction);

    addAndAssertLocalTransactionValid(transaction);
  }

  @Test
  public void shouldRejectLocalTransactionIfFeeCapExceeded() {
    final Wei twoEthers = Wei.fromEth(2);
    transactionPool = createTransactionPool(b -> b.txFeeCap(twoEthers));

    final Transaction transactionLocal = createTransaction(0, twoEthers.add(1));

    givenTransactionIsValid(transactionLocal);

    addAndAssertLocalTransactionInvalid(transactionLocal, TX_FEECAP_EXCEEDED);
  }

  @Test
  public void shouldRejectZeroGasPriceTransactionWhenNotMining() {
    when(miningParameters.isMiningEnabled()).thenReturn(false);

    final Transaction transaction = createTransaction(0, Wei.ZERO);

    givenTransactionIsValid(transaction);

    addAndAssertLocalTransactionInvalid(transaction, GAS_PRICE_TOO_LOW);
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

  protected abstract Block appendBlock(
      final Difficulty difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd);

  protected abstract Transaction createTransaction(
      final int nonce, final Optional<BigInteger> maybeChainId);

  protected abstract Transaction createTransaction(final int nonce, final Wei maxPrice);

  protected abstract TransactionTestFixture createBaseTransaction(final int nonce);

  private Transaction createTransaction(final int nonce) {
    return createTransaction(nonce, Optional.of(BigInteger.ONE));
  }

  private Transaction createTransaction(final int nonce, final KeyPair keyPair) {
    return createBaseTransaction(nonce).createTransaction(keyPair);
  }

  protected void protocolSupportsTxReplayProtection(
      final long chainId, final boolean isSupportedAtCurrentBlock) {
    when(protocolSpec.isReplayProtectionSupported()).thenReturn(isSupportedAtCurrentBlock);
    when(protocolSchedule.getChainId()).thenReturn(Optional.of(BigInteger.valueOf(chainId)));
  }

  protected void givenTransactionIsValid(final Transaction transaction) {
    when(transactionValidatorFactory.get().validate(eq(transaction), any(Optional.class), any()))
        .thenReturn(valid());
    when(transactionValidatorFactory
            .get()
            .validateForSender(
                eq(transaction), nullable(Account.class), any(TransactionValidationParams.class)))
        .thenReturn(valid());
  }

  protected void addAndAssertLocalTransactionInvalid(
      final Transaction tx, final TransactionInvalidReason invalidReason) {
    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(tx);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getInvalidReason()).isEqualTo(invalidReason);
    assertTransactionNotPending(tx);
    verify(transactionBroadcaster, never()).onTransactionsAdded(singletonList(tx));
  }

  protected void addAndAssertLocalTransactionValid(final Transaction tx) {
    final ValidationResult<TransactionInvalidReason> result =
        transactionPool.addLocalTransaction(tx);

    assertThat(result.isValid()).isTrue();
    assertTransactionPending(tx);
    verify(transactionBroadcaster).onTransactionsAdded(singletonList(tx));
    assertThat(transactions.getLocalTransactions()).contains(tx);
  }

  protected void addAndAssertRemoteTransactionValid(final Transaction tx) {
    transactionPool.addRemoteTransactions(List.of(tx));

    assertRemoteTransactionValid(tx);
  }

  protected void assertRemoteTransactionValid(final Transaction tx) {
    verify(transactionBroadcaster)
        .onTransactionsAdded(argThat((List<Transaction> list) -> list.contains(tx)));
    assertTransactionPending(tx);
    assertThat(transactions.getLocalTransactions()).doesNotContain(tx);
  }

  protected void addAndAssertRemoteTransactionInvalid(final Transaction tx) {
    transactionPool.addRemoteTransactions(List.of(tx));

    verify(transactionBroadcaster, never()).onTransactionsAdded(singletonList(tx));
    assertTransactionNotPending(tx);
  }
}

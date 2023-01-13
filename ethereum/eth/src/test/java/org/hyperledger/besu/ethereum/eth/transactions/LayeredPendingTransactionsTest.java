/*
 * Copyright Besu contributors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionSelectionResult.COMPLETE_OPERATION;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionSelectionResult.CONTINUE;
import static org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED_SPARSE;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPrioritizedTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePrioritizedTransactions;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.StubMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LayeredPendingTransactionsTest extends BaseTransactionPoolTest {
  protected static final int MAX_TRANSACTIONS = 5;
  protected static final int CACHE_CAPACITY_BYTES = 1024;
  private static final float LIMITED_TRANSACTIONS_BY_SENDER_PERCENTAGE = 0.8f;
  protected static final String ADDED_COUNTER = "transactions_added_total";
  protected static final String REMOVED_COUNTER = "transactions_removed_total";
  protected static final String REPLACED_COUNTER = "transactions_replaced_total";
  protected static final String REMOTE = "remote";
  protected static final String LOCAL = "local";
  protected static final String DROPPED = "dropped";
  protected static final String PRIORITY_LIST = "priority";

  protected final PendingTransactionListener listener = mock(PendingTransactionListener.class);
  protected final PendingTransactionDroppedListener droppedListener =
      mock(PendingTransactionDroppedListener.class);

  protected final TestClock clock = new TestClock();
  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final TransactionPoolConfiguration poolConf =
      ImmutableTransactionPoolConfiguration.builder()
          .txPoolMaxSize(MAX_TRANSACTIONS)
          .txPoolLimitByAccountPercentage(1.0f)
          .pendingTransactionsCacheSizeBytes(CACHE_CAPACITY_BYTES)
          .build();

  private final TransactionPoolConfiguration senderLimitedConfig =
      ImmutableTransactionPoolConfiguration.builder()
          .txPoolMaxSize(MAX_TRANSACTIONS)
          .txPoolLimitByAccountPercentage(LIMITED_TRANSACTIONS_BY_SENDER_PERCENTAGE)
          .pendingTransactionsCacheSizeBytes(CACHE_CAPACITY_BYTES)
          .build();
  protected LayeredPendingTransactions senderLimitedTransactions;
  private LayeredPendingTransactions pendingTransactions;

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.of(100)));
    return blockHeader;
  }

  @BeforeEach
  public void setup() {
    final BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester =
        (t1, t2) ->
            new TransactionPoolReplacementHandler(poolConf.getPriceBump())
                .shouldReplace(t1, t2, mockBlockHeader());

    final AbstractPrioritizedTransactions pendingTransactionsSorter =
        new BaseFeePrioritizedTransactions(
            poolConf,
            clock,
            metricsSystem,
            LayeredPendingTransactionsTest::mockBlockHeader,
            transactionReplacementTester,
            FeeMarket.london(0));
    pendingTransactions =
        new LayeredPendingTransactions(
            poolConf, pendingTransactionsSorter, transactionReplacementTester);

    senderLimitedTransactions =
        new LayeredPendingTransactions(
            senderLimitedConfig,
            new BaseFeePrioritizedTransactions(
                senderLimitedConfig,
                clock,
                metricsSystem,
                LayeredPendingTransactionsTest::mockBlockHeader,
                transactionReplacementTester,
                FeeMarket.london(0)),
            transactionReplacementTester);
  }

  @Test
  public void returnExclusivelyLocalTransactionsWhenAppropriate() {
    final Transaction localTransaction0 = createTransaction(0, KEYS2);
    pendingTransactions.addLocalTransaction(localTransaction0, Optional.empty());
    assertThat(pendingTransactions.size()).isEqualTo(1);

    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());
    assertThat(pendingTransactions.size()).isEqualTo(2);

    pendingTransactions.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(pendingTransactions.size()).isEqualTo(3);

    final List<Transaction> localTransactions = pendingTransactions.getLocalTransactions();
    assertThat(localTransactions.size()).isEqualTo(1);
  }

  @Test
  public void addRemoteTransactions() {
    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());
    assertThat(pendingTransactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    pendingTransactions.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(pendingTransactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(2);
  }

  @Test
  public void getNotPresentTransaction() {
    assertThat(pendingTransactions.getTransactionByHash(Hash.EMPTY_TRIE_HASH)).isEmpty();
  }

  @Test
  public void getTransactionByHash() {
    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());
    assertTransactionPending(pendingTransactions, transaction0);
  }

  @Test
  public void evictTransactionsWhenSizeLimitExceeded() {
    final List<Transaction> firstTxs = new ArrayList<>();

    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
      final Account sender = mock(Account.class);
      when(sender.getNonce()).thenReturn((long) i);
      final var tx = createTransaction(i, Wei.of(10L), SIGNATURE_ALGORITHM.get().generateKeyPair());
      pendingTransactions.addRemoteTransaction(tx, Optional.of(sender));
      firstTxs.add(tx);
      assertTransactionPending(pendingTransactions, tx);
    }

    assertThat(pendingTransactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isZero();

    final int freeSpace =
        (int)
            (poolConf.getPendingTransactionsCacheSizeBytes() - pendingTransactions.getUsedSpace());

    final Transaction lastBigTx =
        createTransaction(
            0, Wei.of(10L), freeSpace + 1, SIGNATURE_ALGORITHM.get().generateKeyPair());
    final Account lastSender = mock(Account.class);
    when(lastSender.getNonce()).thenReturn(0L);
    pendingTransactions.addRemoteTransaction(lastBigTx, Optional.of(lastSender));
    assertTransactionPending(pendingTransactions, lastBigTx);

    assertTransactionNotPending(pendingTransactions, firstTxs.get(0));
    //    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void addTransactionForMultipleSenders() {
    final var transactionSenderA = createTransaction(0, KEYS1);
    final var transactionSenderB = createTransaction(0, KEYS2);
    assertThat(pendingTransactions.addRemoteTransaction(transactionSenderA, Optional.empty()))
        .isEqualTo(ADDED);
    assertTransactionPending(pendingTransactions, transactionSenderA);
    assertThat(pendingTransactions.addRemoteTransaction(transactionSenderB, Optional.empty()))
        .isEqualTo(ADDED);
    assertTransactionPending(pendingTransactions, transactionSenderB);
  }

  @Test
  public void addAsSparseFirstTransactionWithNonceGap() {
    final var firstTransaction = createTransaction(1);
    assertThat(pendingTransactions.addRemoteTransaction(firstTransaction, Optional.empty()))
        .isEqualTo(ADDED_SPARSE);
    assertTransactionPendingAndNotReady(pendingTransactions, firstTransaction);
  }

  @Test
  public void addAsSparseNextTransactionWithNonceGap() {
    final var transaction0 = createTransaction(0);
    final var transaction2 = createTransaction(2);
    assertThat(pendingTransactions.addRemoteTransaction(transaction0, Optional.empty()))
        .isEqualTo(ADDED);
    assertTransactionPendingAndReady(pendingTransactions, transaction0);
    assertThat(pendingTransactions.addRemoteTransaction(transaction2, Optional.empty()))
        .isEqualTo(ADDED_SPARSE);
    assertTransactionPendingAndNotReady(pendingTransactions, transaction2);
  }

  @Test
  public void dropIfTransactionTooFarInFutureForTheSender() {
    final var futureTransaction =
        createTransaction(poolConf.getTxPoolMaxFutureTransactionByAccount() + 1);
    assertThat(pendingTransactions.addRemoteTransaction(futureTransaction, Optional.empty()))
        .isEqualTo(NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER);
    assertTransactionNotPending(pendingTransactions, futureTransaction);
  }

  @Test
  public void dropAlreadyConfirmedTransaction() {
    final Account sender = mock(Account.class);
    when(sender.getNonce()).thenReturn(5L);

    final Transaction oldTransaction = createTransaction(2);
    assertThat(pendingTransactions.addRemoteTransaction(oldTransaction, Optional.of(sender)))
        .isEqualTo(ALREADY_KNOWN);
    assertThat(pendingTransactions.size()).isEqualTo(0);
    assertTransactionNotPending(pendingTransactions, oldTransaction);
  }

  @Test
  public void notifyListenerWhenRemoteTransactionAdded() {
    pendingTransactions.subscribePendingTransactions(listener);

    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());

    verify(listener).onTransactionAdded(transaction0);
  }

  @Test
  public void notifyListenerWhenLocalTransactionAdded() {
    pendingTransactions.subscribePendingTransactions(listener);

    pendingTransactions.addLocalTransaction(transaction0, Optional.empty());

    verify(listener).onTransactionAdded(transaction0);
  }

  @Test
  public void notNotifyListenerAfterUnsubscribe() {
    final long id = pendingTransactions.subscribePendingTransactions(listener);

    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());

    verify(listener).onTransactionAdded(transaction0);

    pendingTransactions.unsubscribePendingTransactions(id);

    pendingTransactions.addRemoteTransaction(transaction1, Optional.empty());

    verifyNoMoreInteractions(listener);
  }

  //
  //      @Test
  //      public void notifyDroppedListenerWhenRemoteTransactionDropped() {
  //        pendingTransactions.addRemoteTransaction(transaction2, Optional.empty());
  //
  //        pendingTransactions.subscribeDroppedTransactions(droppedListener);
  //
  //        pendingTransactions.remove(transaction2);
  //
  //        verify(droppedListener).onTransactionDropped(transaction2);
  //      }

  @Test
  public void selectTransactionsUntilSelectorRequestsNoMore() {
    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());
    pendingTransactions.addRemoteTransaction(transaction1, Optional.empty());

    final List<Transaction> parsedTransactions = new ArrayList<>();
    pendingTransactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return COMPLETE_OPERATION;
        });

    assertThat(parsedTransactions.size()).isEqualTo(1);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction0);
  }

  @Test
  public void selectTransactionsUntilPendingIsEmpty() {
    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());
    pendingTransactions.addRemoteTransaction(transaction1, Optional.empty());

    final List<Transaction> parsedTransactions = new ArrayList<>();
    pendingTransactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return CONTINUE;
        });

    assertThat(parsedTransactions.size()).isEqualTo(2);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction0);
    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);
  }

  @Test
  public void notSelectReplacedTransaction() {
    final Transaction transaction1 = createTransaction(0, KEYS1);
    final Transaction transaction1b = createTransactionReplacement(transaction1, KEYS1);

    pendingTransactions.addRemoteTransaction(transaction1, Optional.empty());
    pendingTransactions.addRemoteTransaction(transaction1b, Optional.empty());

    final List<Transaction> parsedTransactions = new ArrayList<>();
    pendingTransactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return CONTINUE;
        });

    assertThat(parsedTransactions).containsExactly(transaction1b);
  }

  @Test
  public void selectTransactionsFromSameSenderInNonceOrder() {
    final Transaction transaction0 = createTransaction(0, KEYS1);
    final Transaction transaction1 = createTransaction(1, KEYS1);
    final Transaction transaction2 = createTransaction(2, KEYS1);

    // add out of order
    pendingTransactions.addLocalTransaction(transaction2, Optional.empty());
    pendingTransactions.addLocalTransaction(transaction1, Optional.empty());
    pendingTransactions.addLocalTransaction(transaction0, Optional.empty());

    final List<Transaction> iterationOrder = new ArrayList<>(3);
    pendingTransactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return CONTINUE;
        });

    assertThat(iterationOrder).containsExactly(transaction0, transaction1, transaction2);
  }

  @Test
  public void notForceNonceOrderWhenSendersDiffer() {
    final Account sender2 = mock(Account.class);
    when(sender2.getNonce()).thenReturn(1L);

    final Transaction transactionSender1 = createTransaction(0, Wei.of(10), KEYS1);
    final Transaction transactionSender2 = createTransaction(1, Wei.of(200), KEYS2);

    pendingTransactions.addLocalTransaction(transactionSender1, Optional.empty());
    pendingTransactions.addLocalTransaction(transactionSender2, Optional.of(sender2));

    final List<Transaction> iterationOrder = new ArrayList<>(2);
    pendingTransactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return CONTINUE;
        });

    assertThat(iterationOrder).containsExactly(transactionSender2, transactionSender1);
  }

  @Test
  public void invalidTransactionIsDeletedFromPendingTransactions() {
    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());
    pendingTransactions.addRemoteTransaction(transaction1, Optional.empty());

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    pendingTransactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return DELETE_TRANSACTION_AND_CONTINUE;
        });

    assertThat(parsedTransactions.size()).isEqualTo(2);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction0);
    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);

    assertThat(pendingTransactions.size()).isZero();
  }

  @Test
  public void returnEmptyOptionalAsMaximumNonceWhenNoTransactionsPresent() {
    assertThat(pendingTransactions.getNextNonceForSender(SENDER1)).isEmpty();
  }

  @Test
  public void replaceTransactionWithSameSenderAndNonce() {
    final Transaction transaction1 = createTransaction(0, Wei.of(20), KEYS1);
    final Transaction transaction1b = createTransactionReplacement(transaction1, KEYS1);
    final Transaction transaction2 = createTransaction(1, Wei.of(10), KEYS1);
    assertThat(pendingTransactions.addRemoteTransaction(transaction1, Optional.empty()))
        .isEqualTo(ADDED);
    assertThat(pendingTransactions.addRemoteTransaction(transaction2, Optional.empty()))
        .isEqualTo(ADDED);
    assertThat(
            pendingTransactions
                .addRemoteTransaction(transaction1b, Optional.empty())
                .isReplacement())
        .isTrue();

    assertTransactionNotPending(pendingTransactions, transaction1);
    assertTransactionPending(pendingTransactions, transaction1b);
    assertTransactionPending(pendingTransactions, transaction2);
    assertThat(pendingTransactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(3);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(REPLACED_COUNTER, REMOTE, PRIORITY_LIST)).isEqualTo(1);
  }

  @Test
  public void replaceTransactionWithSameSenderAndNonce_multipleReplacements() {
    final int replacedTxCount = 5;
    final List<Transaction> replacedTransactions = new ArrayList<>(replacedTxCount);
    Transaction duplicateTx = createTransaction(0, Wei.of(50), KEYS1);
    for (int i = 0; i < replacedTxCount; i++) {
      replacedTransactions.add(duplicateTx);
      pendingTransactions.addRemoteTransaction(duplicateTx, Optional.empty());
      duplicateTx = createTransactionReplacement(duplicateTx, KEYS1);
    }

    final Transaction independentTx = createTransaction(1, Wei.ONE, KEYS1);
    assertThat(pendingTransactions.addRemoteTransaction(independentTx, Optional.empty()))
        .isEqualTo(ADDED);
    assertThat(
            pendingTransactions.addRemoteTransaction(duplicateTx, Optional.empty()).isReplacement())
        .isTrue();

    // All txs except the last duplicate should be removed
    replacedTransactions.forEach(tx -> assertTransactionNotPending(pendingTransactions, tx));
    assertTransactionPending(pendingTransactions, duplicateTx);
    // Tx with distinct nonce should be maintained
    assertTransactionPending(pendingTransactions, independentTx);

    assertThat(pendingTransactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(replacedTxCount + 2);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED))
        .isEqualTo(replacedTxCount);
    assertThat(metricsSystem.getCounterValue(REPLACED_COUNTER, REMOTE, PRIORITY_LIST))
        .isEqualTo(replacedTxCount);
  }

  @Test
  public void
      replaceTransactionWithSameSenderAndNonce_multipleReplacementsAddedLocallyAndRemotely() {
    final int replacedTxCount = 5;
    final List<Transaction> replacedTransactions = new ArrayList<>(replacedTxCount);
    int remoteDuplicateCount = 0;
    Transaction replacingTx = createTransaction(0, KEYS1);
    for (int i = 0; i < replacedTxCount; i++) {
      replacedTransactions.add(replacingTx);
      if (i % 2 == 0) {
        pendingTransactions.addRemoteTransaction(replacingTx, Optional.empty());
        remoteDuplicateCount++;
      } else {
        pendingTransactions.addLocalTransaction(replacingTx, Optional.empty());
      }
      replacingTx = createTransactionReplacement(replacingTx, KEYS1);
    }

    final Transaction independentTx = createTransaction(1);
    assertThat(
            pendingTransactions.addLocalTransaction(replacingTx, Optional.empty()).isReplacement())
        .isTrue();
    assertThat(pendingTransactions.addRemoteTransaction(independentTx, Optional.empty()))
        .isEqualTo(ADDED);

    // All txs except the last duplicate should be removed
    replacedTransactions.forEach(tx -> assertTransactionNotPending(pendingTransactions, tx));
    assertTransactionPending(pendingTransactions, replacingTx);

    // Tx with distinct nonce should be maintained
    assertTransactionPending(pendingTransactions, independentTx);

    final int localDuplicateCount = replacedTxCount - remoteDuplicateCount;
    assertThat(pendingTransactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE))
        .isEqualTo(remoteDuplicateCount + 1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL))
        .isEqualTo(localDuplicateCount + 1);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED))
        .isEqualTo(remoteDuplicateCount);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, LOCAL, DROPPED))
        .isEqualTo(localDuplicateCount);
  }

  @Test
  public void notReplaceTransactionWithSameSenderAndNonceWhenGasPriceIsLower() {
    final Transaction transaction1 = createTransaction(0, Wei.of(2));
    final Transaction transaction1b = createTransaction(0, Wei.ONE);
    assertThat(pendingTransactions.addRemoteTransaction(transaction1, Optional.empty()))
        .isEqualTo(ADDED);

    pendingTransactions.subscribePendingTransactions(listener);
    assertThat(pendingTransactions.addRemoteTransaction(transaction1b, Optional.empty()))
        .isEqualTo(REJECTED_UNDERPRICED_REPLACEMENT);

    assertTransactionNotPending(pendingTransactions, transaction1b);
    assertTransactionPending(pendingTransactions, transaction1);
    assertThat(pendingTransactions.size()).isEqualTo(1);
    verifyNoInteractions(listener);
  }

  @Test
  public void trackNextNonceForEachSender() {
    // first sender consecutive txs: 0->1->2
    final Account firstSender = mock(Account.class);
    when(firstSender.getNonce()).thenReturn(0L);
    when(firstSender.getAddress()).thenReturn(SENDER1);
    assertNoNextNonceForSender(pendingTransactions, SENDER1);
    pendingTransactions.addRemoteTransaction(createTransaction(0, KEYS1), Optional.of(firstSender));
    assertNextNonceForSender(pendingTransactions, SENDER1, 1);

    pendingTransactions.addRemoteTransaction(createTransaction(1, KEYS1), Optional.of(firstSender));
    assertNextNonceForSender(pendingTransactions, SENDER1, 2);

    pendingTransactions.addRemoteTransaction(createTransaction(2, KEYS1), Optional.of(firstSender));
    assertNextNonceForSender(pendingTransactions, SENDER1, 3);

    // second sender not in orders: 3->0->2->1
    final Account secondSender = mock(Account.class);
    when(secondSender.getNonce()).thenReturn(0L);
    when(secondSender.getAddress()).thenReturn(SENDER2);
    assertNoNextNonceForSender(pendingTransactions, SENDER2);
    pendingTransactions.addRemoteTransaction(
        createTransaction(3, KEYS2), Optional.of(secondSender));
    assertNoNextNonceForSender(pendingTransactions, SENDER2);

    pendingTransactions.addRemoteTransaction(
        createTransaction(0, KEYS2), Optional.of(secondSender));
    assertNextNonceForSender(pendingTransactions, SENDER2, 1);

    pendingTransactions.addRemoteTransaction(
        createTransaction(2, KEYS2), Optional.of(secondSender));
    assertNextNonceForSender(pendingTransactions, SENDER2, 1);

    // tx 1 will fill the nonce gap and all txs will be ready
    pendingTransactions.addRemoteTransaction(
        createTransaction(1, KEYS2), Optional.of(secondSender));
    assertNextNonceForSender(pendingTransactions, SENDER2, 4);
  }

  @Test
  public void correctNonceIsReturned() {
    final Account sender = mock(Account.class);
    when(sender.getNonce()).thenReturn(1L);
    assertThat(pendingTransactions.getNextNonceForSender(transaction2.getSender())).isEmpty();
    // since tx 3 is missing, 4 is sparse,
    // note that 0 is already known since sender nonce is 1
    addLocalTransactions(pendingTransactions, sender, 0, 1, 2, 4);
    assertThat(pendingTransactions.size()).isEqualTo(3);
    assertThat(pendingTransactions.getNextNonceForSender(transaction2.getSender()))
        .isPresent()
        .hasValue(3);

    // tx 3 arrives and is added, while 4 is moved to ready
    addLocalTransactions(pendingTransactions, sender, 3);
    assertThat(pendingTransactions.size()).isEqualTo(4);
    assertThat(pendingTransactions.getNextNonceForSender(transaction2.getSender()))
        .isPresent()
        .hasValue(5);

    // when 5 is added, the pool is full, and so 6 and 7 are dropped since too far in future sender
    addLocalTransactions(pendingTransactions, sender, 5, 6, 7);
    assertThat(pendingTransactions.size()).isEqualTo(5);

    // assert that transactions are pruned by account from the latest future nonce first
    assertThat(pendingTransactions.getNextNonceForSender(transaction2.getSender()))
        .isPresent()
        .hasValue(6);
  }

  @Test
  public void correctNonceIsReturnedForSenderLimitedPool() {
    final Account sender = mock(Account.class);
    when(sender.getNonce()).thenReturn(1L);

    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender())).isEmpty();
    // since tx 3 is missing, 4 is sparse,
    // note that 0 is already known since sender nonce is 1
    addLocalTransactions(senderLimitedTransactions, sender, 0, 1, 2, 4);
    assertThat(senderLimitedTransactions.size()).isEqualTo(3);
    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender()))
        .isPresent()
        .hasValue(3);

    // tx 3 arrives and is added, while 4 is moved to ready
    addLocalTransactions(senderLimitedTransactions, sender, 3);
    assertThat(senderLimitedTransactions.size()).isEqualTo(4);
    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender()))
        .isPresent()
        .hasValue(5);

    // for sender max 4 txs are allowed, so 5, 6 and 7 are dropped since too far in future sender
    addLocalTransactions(senderLimitedTransactions, sender, 5, 6, 7);
    assertThat(senderLimitedTransactions.size()).isEqualTo(4);

    // assert that we drop txs with future nonce first
    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender()))
        .isPresent()
        .hasValue(5);
  }

  @Test
  public void correctNonceIsReturnedWithRepeatedTransactions() {
    assertThat(pendingTransactions.getNextNonceForSender(transaction2.getSender())).isEmpty();
    final Account sender = mock(Account.class);
    addLocalTransactions(pendingTransactions, sender, 0, 1, 2, 1, 0, 4);
    assertThat(pendingTransactions.getNextNonceForSender(transaction2.getSender()))
        .isPresent()
        .hasValue(3);
    addLocalTransactions(pendingTransactions, sender, 3);
  }

  @Test
  public void shouldNotIncrementAddedCounterWhenRemoteTransactionAlreadyPresent() {
    pendingTransactions.addLocalTransaction(transaction0, Optional.empty());
    assertThat(pendingTransactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);

    assertThat(pendingTransactions.addRemoteTransaction(transaction0, Optional.empty()))
        .isEqualTo(ALREADY_KNOWN);
    assertThat(pendingTransactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);
  }

  @Test
  public void shouldNotIncrementAddedCounterWhenLocalTransactionAlreadyPresent() {
    pendingTransactions.addRemoteTransaction(transaction0, Optional.empty());
    assertThat(pendingTransactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    assertThat(pendingTransactions.addLocalTransaction(transaction0, Optional.empty()))
        .isEqualTo(ALREADY_KNOWN);
    assertThat(pendingTransactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);
  }

  @Test
  public void doNothingIfTransactionAlreadyPending() {
    final var addedTxs = populateCache(1, 0);
    assertThat(
            pendingTransactions.addRemoteTransaction(
                addedTxs[0].transaction, Optional.of(addedTxs[0].account)))
        .isEqualTo(ALREADY_KNOWN);
    assertTransactionPendingAndReady(pendingTransactions, addedTxs[0].transaction);
  }
  //
  //  @Test
  //  public void doNothingWhenRemovingNotPresentTransaction() {
  //    final var transaction = createTransaction(0);
  //    assertTransactionNotReady(transaction);
  //    readyTransactionsCache.remove(transaction);
  //    assertTransactionNotReady(transaction);
  //  }
  @Test
  public void returnsCorrectNextNonceWhenAddedTransactionsHaveGaps() {
    final var addedTxs = populateCache(3, 0, 1);
    assertThat(pendingTransactions.getNextNonceForSender(addedTxs[0].transaction.getSender()))
        .isPresent()
        .hasValue(1);
  }

  //    @Test
  //    public void emptyStreamWhenNoReadyTransactionsForSender() {
  //      final var transaction = createTransaction(0);
  //      assertTransactionNotPending(pendingTransactions, transaction);
  //
  //   assertThat(pendingTransactions.streamReadyTransactions(transaction.getSender())).isEmpty();
  //    }
  //
  //  @Test
  //  public void emptyStreamWhenUsingNonceAndNoReadyTransactionsForSender() {
  //    final var transaction = createTransaction(0);
  //    assertTransactionNotReady(transaction);
  //    assertThat(readyTransactionsCache.streamReadyTransactions(transaction.getSender(), 1))
  //        .isEmpty();
  //  }
  //
  //  @Test
  //  public void streamWithOnlyOneReadyTransactionForSender() {
  //    final var readyTxs = populateCache(1, 0);
  //    assertThat(readyTxs).hasSize(1);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(0L);
  //    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender()))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(readyTxs);
  //  }
  //
  //  @Test
  //  public void emptyStreamWhenUsingNonceGreaterThanMaxPresentForSender() {
  //    final var readyTxs = populateCache(2, 1);
  //    assertThat(readyTxs).hasSize(2);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L,
  // 2L);
  //    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender(), 2L))
  //        .isEmpty();
  //  }
  //
  //  @Test
  //  public void streamWithMoreReadyTransactionForSender() {
  //    final var readyTxs = populateCache(3, 1);
  //    assertThat(readyTxs).hasSize(3);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce))
  //        .containsExactly(1L, 2L, 3L);
  //    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender()))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(readyTxs);
  //  }
  //
  //  @Test
  //  public void streamWhenUsingNonceWithMoreReadyTransactionForSenderReturnOnlyGreaterThanNonce()
  // {
  //    final var readyTxs = populateCache(3, 1);
  //    assertThat(readyTxs).hasSize(3);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce))
  //        .containsExactly(1L, 2L, 3L);
  //    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender(), 2))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(readyTxs[2]);
  //  }
  //
  //  @Test
  //  public void streamWithMoreReadyTransactionsWithGapForSender() {
  //    final var readyTxs = populateCache(4, 1, 3);
  //    assertThat(readyTxs).hasSize(2);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L,
  // 2L);
  //    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender()))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(readyTxs);
  //  }
  //
  //  @Test
  //  public void emptyStreamWhenUsingNonceWithMoreReadyTransactionsWithGapForSender() {
  //    final var readyTxs = populateCache(4, 1, 3);
  //    assertThat(readyTxs).hasSize(2);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L,
  // 2L);
  //    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender(), 3))
  //        .isEmpty();
  //  }
  //
  //  @Test
  //  public void noPromotableTransactionsWhenCacheIsEmpty() {
  //    final var confirmedTransaction = createTransaction(0);
  //    assertTransactionNotReady(confirmedTransaction);
  //    readyTransactionsCache.removeConfirmedTransactions(
  //        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
  //    assertThat(readyTransactionsCache.getPromotableTransactions(1,
  // this::alwaysPromote)).isEmpty();
  //  }
  //
  //  @Test
  //  public void noPromotableTransactionsWhenNoConfirmedTransactionsAndCacheIsEmpty() {
  //    readyTransactionsCache.removeConfirmedTransactions(Map.of());
  //    assertThat(readyTransactionsCache.getPromotableTransactions(1,
  // this::alwaysPromote)).isEmpty();
  //  }
  //
  //  @Test
  //  public void noPromotedTransactionsWhenMaxPromotableIsZero() {
  //    final var readyTxs = populateCache(2, 1);
  //    assertThat(readyTxs).hasSize(2);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L,
  // 2L);
  //
  //    final var confirmedTransaction = readyTxs[0];
  //
  //    readyTransactionsCache.removeConfirmedTransactions(
  //        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
  //    assertThat(readyTransactionsCache.getPromotableTransactions(0,
  // this::alwaysPromote)).isEmpty();
  //  }
  //
  //  @Test
  //  public void promoteSameSenderTransactionsOnConfirmedTransactions() {
  //    final var readyTxs = populateCache(2, 1);
  //    assertThat(readyTxs).hasSize(2);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L,
  // 2L);
  //
  //    final var confirmedTransaction = readyTxs[0];
  //    readyTransactionsCache.removeConfirmedTransactions(
  //        Map.of(confirmedTransaction.getSender(), Optional.of(1L)));
  //    assertThat(readyTransactionsCache.getPromotableTransactions(1, this::alwaysPromote))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(readyTxs[1]);
  //  }
  //
  //  @Test
  //  public void promoteOtherSenderTransactionsOnConfirmedTransactions() {
  //    final var readyTxs = populateCache(1, KEYS1);
  //    assertThat(readyTxs).hasSize(1);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(0L);
  //
  //    final var confirmedTransaction = createTransaction(0, KEYS2);
  //    assertNoReadyTransactionsForSender(confirmedTransaction.getSender());
  //    readyTransactionsCache.removeConfirmedTransactions(
  //        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
  //    assertThat(readyTransactionsCache.getPromotableTransactions(1, this::alwaysPromote))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(readyTxs[0]);
  //  }
  //
  //  @Test
  //  public void limitPromotedTransactionsOnConfirmedTransactions() {
  //    final var transaction0SenderA = createTransaction(0, Wei.of(100), KEYS1);
  //    final var transaction1SenderA = createTransaction(1, Wei.of(95), KEYS1);
  //    populateCache(transaction0SenderA, transaction1SenderA);
  //    assertSenderHasExactlyReadyTransactions(transaction0SenderA, transaction1SenderA);
  //
  //    final var transaction0SenderB = createTransaction(0, Wei.of(90), KEYS2);
  //    final var transaction1SenderB = createTransaction(1, Wei.of(100), KEYS2);
  //    populateCache(transaction0SenderB, transaction1SenderB);
  //    assertSenderHasExactlyReadyTransactions(transaction0SenderB, transaction1SenderB);
  //
  //    final var transaction0SenderC =
  //        createTransaction(1, Wei.of(80), SIGNATURE_ALGORITHM.get().generateKeyPair());
  //    populateCache(transaction0SenderC);
  //    assertSenderHasExactlyReadyTransactions(transaction0SenderC);
  //
  //    final var confirmedTransaction = transaction0SenderA;
  //    readyTransactionsCache.removeConfirmedTransactions(
  //        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
  //    assertThat(readyTransactionsCache.getPromotableTransactions(2, this::alwaysPromote))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(transaction1SenderA, transaction0SenderB);
  //  }
  //
  //  @Test
  //  public void allTransactionBelowConfirmedNonceAreRemovedForSender() {
  //    final var readyTxs = populateCache(3, 0);
  //    assertThat(readyTxs).hasSize(3);
  //    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce))
  //        .containsExactly(0L, 1L, 2L);
  //
  //    final var confirmedTransaction = readyTxs[1];
  //    readyTransactionsCache.removeConfirmedTransactions(
  //        Map.of(confirmedTransaction.getSender(), Optional.of(1L)));
  //    assertThat(readyTransactionsCache.getPromotableTransactions(2, this::alwaysPromote))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(readyTxs[2]);
  //
  //    assertSenderHasExactlyReadyTransactions(readyTxs[2]);
  //  }
  //
  //  @Test
  //  public void postponeTransactionIfNotFitsInCache() {
  //    final var largeTransaction = createTransaction(0, CACHE_CAPACITY_BYTES);
  //    assertThat(readyTransactionsCache.add(createPendingTransaction(largeTransaction), 0))
  //        .isEqualTo(TX_POOL_FULL);
  //    assertTransactionNotReady(largeTransaction);
  //  }
  //
  //  @Test
  //  public void postponeTransactionWithHigherNonceFirstForSenderWhenCacheIsFull() {
  //    final var lowFeeTransaction = createTransaction(0, Wei.of(10), 100, KEYS1);
  //    final var largeTransaction = createTransaction(1, Wei.of(20), CACHE_CAPACITY_BYTES - 50,
  // KEYS1);
  //    populateCache(lowFeeTransaction);
  //    // largeTransaction is postponed even if of higher fee than the first one, to avoid gaps
  //    assertThat(readyTransactionsCache.add(createPendingTransaction(largeTransaction), 0))
  //        .isEqualTo(TX_POOL_FULL);
  //    assertSenderHasExactlyReadyTransactions(lowFeeTransaction);
  //  }
  //
  //  @Test
  //  public void postponeLessFeeTransactionForOtherSendersWhenCacheIsFull() {
  //    final var lowFeeTransactionSenderA =
  //        createTransaction(0, Wei.of(10), 100, SIGNATURE_ALGORITHM.get().generateKeyPair());
  //    final var lowFeeTransactionSenderB =
  //        createTransaction(0, Wei.of(10), 100, SIGNATURE_ALGORITHM.get().generateKeyPair());
  //    final var largeHighFeeTransactionSenderC =
  //        createTransaction(
  //            0, Wei.of(20), CACHE_CAPACITY_BYTES - lowFeeTransactionSenderB.getSize() + 1,
  // KEYS2);
  //
  //    populateCache(lowFeeTransactionSenderA, lowFeeTransactionSenderB);
  //
  //    // to make space for the large transaction both previous transactions are postponed
  //    assertThat(
  //            readyTransactionsCache.add(createPendingTransaction(largeHighFeeTransactionSenderC),
  // 0))
  //        .isEqualTo(ADDED);
  //
  //    assertNoReadyTransactionsForSender(lowFeeTransactionSenderA.getSender());
  //    assertNoReadyTransactionsForSender(lowFeeTransactionSenderB.getSender());
  //    assertSenderHasExactlyReadyTransactions(largeHighFeeTransactionSenderC);
  //  }
  //
  //  //  @Test
  //  //  void postponedToReadyWhenFillingNonceGap() {
  //  //    final var postponedTransaction = createTransaction(1);
  //  //    when(postponedTransactionsCache.promoteForSender(
  //  //            eq(postponedTransaction.getSender()), eq(0L), anyLong()))
  //  //        .thenReturn(
  //  //            CompletableFuture.completedFuture(
  //  //                List.of(createPendingTransaction(postponedTransaction))));
  //  //
  //  //    assertTransactionNotPresent(postponedTransaction);
  //  //
  //  //    final var previousTransaction = createTransaction(0);
  //  //    populateCache(previousTransaction);
  //  //    assertSenderHasExactlyTransactions(previousTransaction, postponedTransaction);
  //  //  }
  //
  //  //  @Test
  //  //  void noPostponedToReadyWhenPostponedDoesNotFitInCache() {
  //  //    final var arrivesLateTransaction = createTransaction(0);
  //  //    final var postponedTransaction =
  //  //        createTransaction(
  //  //            1, Wei.of(10), CACHE_CAPACITY_BYTES - arrivesLateTransaction.getSize() + 1,
  // KEYS1);
  //  //    when(postponedTransactionsCache.promoteForSender(
  //  //            eq(postponedTransaction.getSender()), eq(0L), anyLong()))
  //  //        .thenReturn(
  //  //            CompletableFuture.completedFuture(
  //  //                List.of(createPendingTransaction(postponedTransaction))));
  //  //
  //  //    assertTransactionNotPresent(postponedTransaction);
  //  //
  //  //    populateCache(arrivesLateTransaction);
  //  //    assertSenderHasExactlyTransactions(arrivesLateTransaction);
  //  //  }

  //  private TransactionAndAccount[] populateCache(final int numTxs, final KeyPair keys) {
  //    return populateCache(numTxs, keys, 0, OptionalLong.empty());
  //  }

  private TransactionAndAccount[] populateCache(final int numTxs, final long startingNonce) {
    return populateCache(numTxs, KEYS1, startingNonce, OptionalLong.empty());
  }

  private TransactionAndAccount[] populateCache(
      final int numTxs, final long startingNonce, final long missingNonce) {
    return populateCache(numTxs, KEYS1, startingNonce, OptionalLong.of(missingNonce));
  }

  private TransactionAndAccount[] populateCache(
      final int numTxs,
      final KeyPair keys,
      final long startingNonce,
      final OptionalLong maybeGapNonce) {
    final List<TransactionAndAccount> addedTransactions = new ArrayList<>(numTxs);
    boolean afterGap = false;
    for (int i = 0; i < numTxs; i++) {
      final long nonce = startingNonce + i;
      if (maybeGapNonce.isPresent() && maybeGapNonce.getAsLong() == nonce) {
        afterGap = true;
      } else {
        final var transaction = createTransaction(nonce, keys);
        final Account sender = mock(Account.class);
        when(sender.getNonce()).thenReturn(startingNonce);
        final var res = pendingTransactions.addRemoteTransaction(transaction, Optional.of(sender));
        if (afterGap) {
          assertThat(res).isEqualTo(ADDED_SPARSE);
          assertTransactionPendingAndNotReady(pendingTransactions, transaction);
        } else {
          assertThat(res).isEqualTo(ADDED);
          assertTransactionPendingAndReady(pendingTransactions, transaction);
          addedTransactions.add(new TransactionAndAccount(transaction, sender));
        }
      }
    }
    return addedTransactions.toArray(TransactionAndAccount[]::new);
  }

  //  private void populateCache(final Transaction... transactions) {
  //    assertThat(
  //            Arrays.stream(transactions)
  //                .map(
  //                    pendingTransaction -> {
  //                      final Account sender = mock(Account.class);
  //                      when(sender.getNonce()).thenReturn(transactions[0].getNonce());
  //                      return pendingTransactions.addRemoteTransaction(
  //                          pendingTransaction, Optional.of(sender));
  //                    })
  //                .filter(ADDED::equals)
  //                .count())
  //        .isEqualTo(transactions.length);
  //  }
  //
  //  private Transaction createTransaction(final long nonce) {
  //    return createTransaction(nonce, Wei.of(5000L), KEYS1);
  //  }
  //
  //  private Transaction createTransaction(final long nonce, final KeyPair keys) {
  //    return createTransaction(nonce, Wei.of(5000L), keys);
  //  }
  //
  //    private Transaction createTransaction(final long nonce, final Wei maxGasPrice) {
  //      return createTransaction(nonce, maxGasPrice, KEYS1);
  //    }
  //  //
  //  //  private Transaction createTransaction(final long nonce, final int payloadSize) {
  //  //    return createTransaction(nonce, Wei.of(5000L), payloadSize, KEYS1);
  //  //  }
  //  //
  //  private Transaction createTransaction(
  //      final long nonce, final Wei maxGasPrice, final KeyPair keys) {
  //    return createTransaction(nonce, maxGasPrice, 0, keys);
  //  }
  //
  //  private Transaction createTransaction(
  //      final long nonce, final Wei maxGasPrice, final int payloadSize, final KeyPair keys) {
  //
  //    return createTransaction(
  //        randomizeTxType.nextBoolean() ? TransactionType.EIP1559 : TransactionType.FRONTIER,
  //        nonce,
  //        maxGasPrice,
  //        payloadSize,
  //        keys);
  //  }
  //
  //  private Transaction createTransaction(
  //      final TransactionType type,
  //      final long nonce,
  //      final Wei maxGasPrice,
  //      final int payloadSize,
  //      final KeyPair keys) {
  //
  //    var payloadBytes = Bytes.repeat((byte) 1, payloadSize);
  //    var tx =
  //        new TransactionTestFixture()
  //
  // .to(Optional.of(Address.fromHexString("0x634316eA0EE79c701c6F67C53A4C54cBAfd2316d")))
  //            .value(Wei.of(nonce))
  //            .nonce(nonce)
  //            .type(type)
  //            .payload(payloadBytes);
  //    if (type.supports1559FeeMarket()) {
  //      tx.maxFeePerGas(Optional.of(maxGasPrice))
  //          .maxPriorityFeePerGas(Optional.of(maxGasPrice.divide(10)));
  //    } else {
  //      tx.gasPrice(maxGasPrice);
  //    }
  //    return tx.createTransaction(keys);
  //  }
  //
  //    protected Transaction createTransactionReplacement(
  //        final Transaction originalTransaction, final KeyPair keys) {
  //      return createTransaction(
  //          originalTransaction.getType(),
  //          originalTransaction.getNonce(),
  //          originalTransaction.getMaxGasFee().multiply(2),
  //          0,
  //          keys);
  //    }
  //    private void assertTransactionPendingAndReady(final ReadyTransactionsCache
  // pendingTransactions, final Transaction transaction) {
  //      assertTransactionPending(pendingTransactions, transaction);
  //      assertThat(pendingTransactions.getReady(transaction.getSender(), transaction.getNonce()))
  //          .isPresent()
  //          .map(PendingTransaction::getHash)
  //          .hasValue(transaction.getHash());
  //    }
  //
  //    private void assertTransactionPendingAndNotReady(final ReadyTransactionsCache
  // pendingTransactions, final Transaction transaction) {
  //      assertTransactionPending(pendingTransactions, transaction);
  //      final var maybeTransaction =
  //          pendingTransactions.getReady(transaction.getSender(), transaction.getNonce());
  //      if (!maybeTransaction.isEmpty()) {
  //        assertThat(maybeTransaction)
  //            .isPresent()
  //            .map(PendingTransaction::getHash)
  //            .isNotEqualTo(transaction.getHash());
  //      }
  //    }
  //  //
  //  private void assertNoReadyTransactionsForSender(final Address sender) {
  //    assertThat(readyTransactionsCache.streamReadyTransactions(sender)).isEmpty();
  //  }
  //
  //  private void assertSenderHasExactlyReadyTransactions(final Transaction... transactions) {
  //    assertThat(readyTransactionsCache.streamReadyTransactions(transactions[0].getSender()))
  //        .map(PendingTransaction::getTransaction)
  //        .containsExactly(transactions);
  //  }

  //  private boolean alwaysPromote(final PendingTransaction pendingTransaction) {
  //    return true;
  //  }
  //
  //  protected void assertTransactionPending(
  //      final PendingTransactionsSorter transactions, final Transaction t) {
  //    assertThat(transactions.getTransactionByHash(t.getHash())).contains(t);
  //  }
  //
  //  protected void assertTransactionNotPending(
  //      final PendingTransactionsSorter transactions, final Transaction t) {
  //    assertThat(transactions.getTransactionByHash(t.getHash())).isEmpty();
  //  }
  //
  //    private void assertNoNextNonceForSender(final Address sender) {
  //      assertThat(pendingTransactions.getNextNonceForSender(sender)).isEmpty();
  //    }
  //
  //    protected void assertNextNonceForSender(final Address sender1, final int i) {
  //      assertThat(pendingTransactions.getNextNonceForSender(sender1)).isPresent().hasValue(i);
  //    }
  //
  //    protected void addLocalTransactions(final Account sender, final long... nonces) {
  //      addLocalTransactions(pendingTransactions, sender, nonces);
  //    }
  //
  //    protected void addLocalTransactions(
  //        final PendingTransactionsSorter sorter, final Account sender, final long... nonces) {
  //      for (final long nonce : nonces) {
  //        sorter.addLocalTransaction(createTransaction(nonce), Optional.of(sender));
  //      }
  //    }

  private static class TransactionAndAccount {
    final Transaction transaction;
    final Account account;

    public TransactionAndAccount(final Transaction transaction, final Account account) {
      this.transaction = transaction;
      this.account = account;
    }
  }
}

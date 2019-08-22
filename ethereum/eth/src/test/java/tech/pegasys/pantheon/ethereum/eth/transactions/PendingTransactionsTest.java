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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionTestFixture;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions.TransactionSelectionResult;
import tech.pegasys.pantheon.metrics.StubMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import com.google.common.collect.Lists;
import org.junit.Test;

public class PendingTransactionsTest {

  private static final int MAX_TRANSACTIONS = 5;
  private static final KeyPair KEYS1 = KeyPair.generate();
  private static final KeyPair KEYS2 = KeyPair.generate();
  private static final String ADDED_COUNTER = "transactions_added_total";
  private static final String REMOVED_COUNTER = "transactions_removed_total";
  private static final String REMOTE = "remote";
  private static final String LOCAL = "local";
  private static final String DROPPED = "dropped";

  private final TestClock clock = new TestClock();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final PendingTransactions transactions =
      new PendingTransactions(
          TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
          MAX_TRANSACTIONS,
          TestClock.fixed(),
          metricsSystem);
  private final Transaction transaction1 = createTransaction(2);
  private final Transaction transaction2 = createTransaction(1);

  private final PendingTransactionListener listener = mock(PendingTransactionListener.class);
  private final PendingTransactionDroppedListener droppedListener =
      mock(PendingTransactionDroppedListener.class);
  private static final Address SENDER1 = Util.publicKeyToAddress(KEYS1.getPublicKey());
  private static final Address SENDER2 = Util.publicKeyToAddress(KEYS2.getPublicKey());

  @Test
  public void shouldReturnExclusivelyLocalTransactionsWhenAppropriate() {
    final Transaction localTransaction0 = createTransaction(0);
    transactions.addLocalTransaction(localTransaction0);
    assertThat(transactions.size()).isEqualTo(1);

    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(2);

    transactions.addRemoteTransaction(transaction2);
    assertThat(transactions.size()).isEqualTo(3);

    final List<Transaction> localTransactions = transactions.getLocalTransactions();
    assertThat(localTransactions.size()).isEqualTo(1);
  }

  @Test
  public void shouldAddATransaction() {
    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    transactions.addRemoteTransaction(transaction2);
    assertThat(transactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(2);
  }

  @Test
  public void shouldReturnEmptyOptionalWhenNoTransactionWithGivenHashExists() {
    assertThat(transactions.getTransactionByHash(Hash.EMPTY_TRIE_HASH)).isEmpty();
  }

  @Test
  public void shouldGetTransactionByHash() {
    transactions.addRemoteTransaction(transaction1);
    assertTransactionPending(transaction1);
  }

  @Test
  public void shouldDropOldestTransactionWhenLimitExceeded() {
    final Transaction oldestTransaction = createTransaction(0);
    transactions.addRemoteTransaction(oldestTransaction);
    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isZero();

    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 1));
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionNotPending(oldestTransaction);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldHandleMaximumTransactionLimitCorrectlyWhenSameTransactionAddedMultipleTimes() {
    transactions.addRemoteTransaction(createTransaction(0));
    transactions.addRemoteTransaction(createTransaction(0));

    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 1));
    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 2));
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
  }

  @Test
  public void shouldPrioritizeLocalTransaction() {
    final Transaction localTransaction = createTransaction(0);
    transactions.addLocalTransaction(localTransaction);

    for (int i = 1; i <= MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionPending(localTransaction);
  }

  @Test
  public void shouldStartDroppingLocalTransactionsWhenPoolIsFullOfLocalTransactions() {
    final Transaction firstLocalTransaction = createTransaction(0);
    transactions.addLocalTransaction(firstLocalTransaction);

    for (int i = 1; i <= MAX_TRANSACTIONS; i++) {
      transactions.addLocalTransaction(createTransaction(i));
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionNotPending(firstLocalTransaction);
  }

  @Test
  public void shouldNotifyListenerWhenRemoteTransactionAdded() {
    transactions.subscribePendingTransactions(listener);

    transactions.addRemoteTransaction(transaction1);

    verify(listener).onTransactionAdded(transaction1);
  }

  @Test
  public void shouldNotNotifyListenerAfterUnsubscribe() {
    final long id = transactions.subscribePendingTransactions(listener);

    transactions.addRemoteTransaction(transaction1);

    verify(listener).onTransactionAdded(transaction1);

    transactions.unsubscribePendingTransactions(id);

    transactions.addRemoteTransaction(transaction2);

    verifyZeroInteractions(listener);
  }

  @Test
  public void shouldNotifyListenerWhenLocalTransactionAdded() {
    transactions.subscribePendingTransactions(listener);

    transactions.addLocalTransaction(transaction1);

    verify(listener).onTransactionAdded(transaction1);
  }

  @Test
  public void shouldNotifyDroppedListenerWhenRemoteTransactionDropped() {
    transactions.addRemoteTransaction(transaction1);

    transactions.subscribeDroppedTransactions(droppedListener);

    transactions.removeTransaction(transaction1);

    verify(droppedListener).onTransactionDropped(transaction1);
  }

  @Test
  public void shouldNotNotifyDroppedListenerAfterUnsubscribe() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final long id = transactions.subscribeDroppedTransactions(droppedListener);

    transactions.removeTransaction(transaction1);

    verify(droppedListener).onTransactionDropped(transaction1);

    transactions.unsubscribeDroppedTransactions(id);

    transactions.removeTransaction(transaction2);

    verifyNoMoreInteractions(droppedListener);
  }

  @Test
  public void shouldNotifyDroppedListenerWhenLocalTransactionDropped() {
    transactions.addLocalTransaction(transaction1);

    transactions.subscribeDroppedTransactions(droppedListener);

    transactions.removeTransaction(transaction1);

    verify(droppedListener).onTransactionDropped(transaction1);
  }

  @Test
  public void shouldNotNotifyDroppedListenerWhenTransactionAddedToBlock() {
    transactions.addRemoteTransaction(transaction1);

    transactions.subscribeDroppedTransactions(droppedListener);

    transactions.transactionAddedToBlock(transaction1);

    verifyZeroInteractions(droppedListener);
  }

  @Test
  public void selectTransactionsUntilSelectorRequestsNoMore() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.COMPLETE_OPERATION;
        });

    assertThat(parsedTransactions.size()).isEqualTo(1);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
  }

  @Test
  public void selectTransactionsUntilPendingIsEmpty() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(parsedTransactions.size()).isEqualTo(2);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);
  }

  @Test
  public void invalidTransactionIsDeletedFromPendingTransactions() {
    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
        });

    assertThat(parsedTransactions.size()).isEqualTo(2);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);

    assertThat(transactions.size()).isZero();
  }

  @Test
  public void shouldReturnEmptyOptionalAsMaximumNonceWhenNoTransactionsPresent() {
    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
  }

  @Test
  public void shouldReturnEmptyOptionalAsMaximumNonceWhenLastTransactionForSenderRemoved() {
    final Transaction transaction = transactionWithNonceAndSender(1, KEYS1);
    transactions.addRemoteTransaction(transaction);
    transactions.removeTransaction(transaction);
    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
  }

  @Test
  public void shouldReplaceTransactionWithSameSenderAndNonce() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    final Transaction transaction1b = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);
    final Transaction transaction2 = transactionWithNonceSenderAndGasPrice(2, KEYS1, 1);
    assertThat(transactions.addRemoteTransaction(transaction1)).isTrue();
    assertThat(transactions.addRemoteTransaction(transaction2)).isTrue();
    assertThat(transactions.addRemoteTransaction(transaction1b)).isTrue();

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction1b);
    assertTransactionPending(transaction2);
    assertThat(transactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(3);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldReplaceOnlyTransactionFromSenderWhenItHasTheSameNonce() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    final Transaction transaction1b = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);
    assertThat(transactions.addRemoteTransaction(transaction1)).isTrue();
    assertThat(transactions.addRemoteTransaction(transaction1b)).isTrue();

    assertTransactionNotPending(transaction1);
    assertTransactionPending(transaction1b);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldNotReplaceTransactionWithSameSenderAndNonceWhenGasPriceIsLower() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);
    final Transaction transaction1b = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    assertThat(transactions.addRemoteTransaction(transaction1)).isTrue();

    transactions.subscribePendingTransactions(listener);
    assertThat(transactions.addRemoteTransaction(transaction1b)).isFalse();

    assertTransactionNotPending(transaction1b);
    assertTransactionPending(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    verifyZeroInteractions(listener);
  }

  @Test
  public void shouldTrackMaximumNonceForEachSender() {
    transactions.addRemoteTransaction(transactionWithNonceAndSender(0, KEYS1));
    assertMaximumNonceForSender(SENDER1, 1);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(1, KEYS1));
    assertMaximumNonceForSender(SENDER1, 2);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(2, KEYS1));
    assertMaximumNonceForSender(SENDER1, 3);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(20, KEYS2));
    assertMaximumNonceForSender(SENDER2, 21);
    assertMaximumNonceForSender(SENDER1, 3);
  }

  @Test
  public void shouldIterateTransactionsFromSameSenderInNonceOrder() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS1);
    final Transaction transaction3 = transactionWithNonceAndSender(2, KEYS1);

    transactions.addLocalTransaction(transaction1);
    transactions.addLocalTransaction(transaction2);
    transactions.addLocalTransaction(transaction3);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder).containsExactly(transaction1, transaction2, transaction3);
  }

  @Test
  public void shouldNotForceNonceOrderWhenSendersDiffer() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS2);

    transactions.addLocalTransaction(transaction1);
    transactions.addLocalTransaction(transaction2);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder).containsExactly(transaction2, transaction1);
  }

  @Test
  public void shouldNotIncreasePriorityOfTransactionsBecauseOfNonceOrder() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS1);
    final Transaction transaction3 = transactionWithNonceAndSender(2, KEYS1);
    final Transaction transaction4 = transactionWithNonceAndSender(5, KEYS2);

    transactions.addLocalTransaction(transaction1);
    transactions.addLocalTransaction(transaction4);
    transactions.addLocalTransaction(transaction2);
    transactions.addLocalTransaction(transaction3);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    // Ignoring nonces, the order would be 3, 2, 4, 1 but we have to delay 3 and 2 until after 1.
    assertThat(iterationOrder)
        .containsExactly(transaction4, transaction1, transaction2, transaction3);
  }

  private void assertMaximumNonceForSender(final Address sender1, final int i) {
    assertThat(transactions.getNextNonceForSender(sender1)).isEqualTo(OptionalLong.of(i));
  }

  private Transaction transactionWithNonceAndSender(final int nonce, final KeyPair keyPair) {
    return new TransactionTestFixture().nonce(nonce).createTransaction(keyPair);
  }

  private Transaction transactionWithNonceSenderAndGasPrice(
      final int nonce, final KeyPair keyPair, final long gasPrice) {
    return new TransactionTestFixture()
        .nonce(nonce)
        .gasPrice(Wei.of(gasPrice))
        .createTransaction(keyPair);
  }

  private void assertTransactionPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.hash())).contains(t);
  }

  private void assertTransactionNotPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.hash())).isEmpty();
  }

  private Transaction createTransaction(final int transactionNumber) {
    return new TransactionTestFixture()
        .value(Wei.of(transactionNumber))
        .nonce(transactionNumber)
        .createTransaction(KEYS1);
  }

  @Test
  public void shouldEvictMultipleOldTransactions() {
    final int maxTransactionRetentionHours = 1;
    final PendingTransactions transactions =
        new PendingTransactions(
            maxTransactionRetentionHours, MAX_TRANSACTIONS, clock, metricsSystem);

    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    transactions.addRemoteTransaction(transaction2);
    assertThat(transactions.size()).isEqualTo(2);

    clock.step(2L, ChronoUnit.HOURS);
    transactions.evictOldTransactions();
    assertThat(transactions.size()).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(2);
  }

  @Test
  public void shouldEvictSingleOldTransaction() {
    final int maxTransactionRetentionHours = 1;
    final PendingTransactions transactions =
        new PendingTransactions(
            maxTransactionRetentionHours, MAX_TRANSACTIONS, clock, metricsSystem);
    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    clock.step(2L, ChronoUnit.HOURS);
    transactions.evictOldTransactions();
    assertThat(transactions.size()).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldEvictExclusivelyOldTransactions() {
    final int maxTransactionRetentionHours = 2;
    final PendingTransactions transactions =
        new PendingTransactions(
            maxTransactionRetentionHours, MAX_TRANSACTIONS, clock, metricsSystem);
    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    clock.step(3L, ChronoUnit.HOURS);
    transactions.addRemoteTransaction(transaction2);
    assertThat(transactions.size()).isEqualTo(2);
    transactions.evictOldTransactions();
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldNotIncrementAddedCounterWhenRemoteTransactionAlreadyPresent() {
    transactions.addLocalTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);

    assertThat(transactions.addRemoteTransaction(transaction1)).isFalse();
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);
  }

  @Test
  public void shouldNotIncrementAddedCounterWhenLocalTransactionAlreadyPresent() {
    transactions.addRemoteTransaction(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    assertThat(transactions.addLocalTransaction(transaction1)).isFalse();
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);
  }
}

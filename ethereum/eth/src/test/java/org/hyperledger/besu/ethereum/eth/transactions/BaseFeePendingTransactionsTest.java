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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionSelectionResult;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.metrics.StubMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.junit.Test;

public class BaseFeePendingTransactionsTest {

  private static final int MAX_TRANSACTIONS = 5;
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final KeyPair KEYS1 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  private static final KeyPair KEYS2 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  private static final String ADDED_COUNTER = "transactions_added_total";
  private static final String REMOVED_COUNTER = "transactions_removed_total";
  private static final String REMOTE = "remote";
  private static final String LOCAL = "local";
  private static final String DROPPED = "dropped";

  private final TestClock clock = new TestClock();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final BaseFeePendingTransactionsSorter transactions =
      new BaseFeePendingTransactionsSorter(
          TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
          MAX_TRANSACTIONS,
          TestClock.fixed(),
          metricsSystem,
          BaseFeePendingTransactionsTest::mockBlockHeader,
          TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);
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
  public void shouldPrioritizeGasPriceThenTimeAddedToPool() {
    final List<Transaction> lowGasPriceTransactions =
        IntStream.range(0, MAX_TRANSACTIONS)
            .mapToObj(i -> transactionWithNonceSenderAndGasPrice(i + 1, KEYS1, 10))
            .collect(Collectors.toUnmodifiableList());

    // Fill the pool
    lowGasPriceTransactions.forEach(transactions::addRemoteTransaction);

    // This should kick the oldest tx with the low gas price out, namely the first one we added
    final Transaction highGasPriceTransaction =
        transactionWithNonceSenderAndGasPrice(MAX_TRANSACTIONS + 1, KEYS1, 100);
    transactions.addRemoteTransaction(highGasPriceTransaction);
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    assertTransactionPending(highGasPriceTransaction);
    assertTransactionNotPending(lowGasPriceTransactions.get(0));
    lowGasPriceTransactions.stream().skip(1).forEach(this::assertTransactionPending);
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

    verifyNoMoreInteractions(listener);
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

    verifyNoInteractions(droppedListener);
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
  public void shouldNotSelectReplacedTransaction() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    final Transaction transaction2 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);

    transactions.addRemoteTransaction(transaction1);
    transactions.addRemoteTransaction(transaction2);

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(parsedTransactions).containsExactly(transaction2);
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
  public void shouldReplaceTransactionWithSameSenderAndNonce_multipleReplacements() {
    final int replacedTxCount = 5;
    final List<Transaction> replacedTransactions = new ArrayList<>();
    for (int i = 0; i < replacedTxCount; i++) {
      final Transaction duplicateTx = transactionWithNonceSenderAndGasPrice(1, KEYS1, i + 1);
      replacedTransactions.add(duplicateTx);
      transactions.addRemoteTransaction(duplicateTx);
    }
    final Transaction finalReplacingTx = transactionWithNonceSenderAndGasPrice(1, KEYS1, 100);
    final Transaction independentTx = transactionWithNonceSenderAndGasPrice(2, KEYS1, 1);
    assertThat(transactions.addRemoteTransaction(independentTx)).isTrue();
    assertThat(transactions.addRemoteTransaction(finalReplacingTx)).isTrue();

    // All tx's except the last duplicate should be removed
    replacedTransactions.forEach(this::assertTransactionNotPending);
    assertTransactionPending(finalReplacingTx);
    // Tx with distinct nonce should be maintained
    assertTransactionPending(independentTx);

    assertThat(transactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(replacedTxCount + 2);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED))
        .isEqualTo(replacedTxCount);
  }

  @Test
  public void
      shouldReplaceTransactionWithSameSenderAndNonce_multipleReplacementsAddedLocallyAndRemotely() {
    final int replacedTxCount = 11;
    final List<Transaction> replacedTransactions = new ArrayList<>();
    int remoteDuplicateCount = 0;
    for (int i = 0; i < replacedTxCount; i++) {
      final Transaction duplicateTx =
          transactionWithNonceSenderAndGasPrice(1, KEYS1, (i * 110 / 100) + 1);
      replacedTransactions.add(duplicateTx);
      if (i % 2 == 0) {
        transactions.addRemoteTransaction(duplicateTx);
        remoteDuplicateCount++;
      } else {
        transactions.addLocalTransaction(duplicateTx);
      }
    }
    final Transaction finalReplacingTx = transactionWithNonceSenderAndGasPrice(1, KEYS1, 100);
    final Transaction independentTx = transactionWithNonceSenderAndGasPrice(2, KEYS1, 1);
    assertThat(transactions.addLocalTransaction(finalReplacingTx))
        .isEqualTo(TransactionAddedStatus.ADDED);
    assertThat(transactions.addRemoteTransaction(independentTx)).isTrue();

    // All tx's except the last duplicate should be removed
    replacedTransactions.forEach(this::assertTransactionNotPending);
    assertTransactionPending(finalReplacingTx);
    // Tx with distinct nonce should be maintained
    assertTransactionPending(independentTx);

    final int localDuplicateCount = replacedTxCount - remoteDuplicateCount;
    assertThat(transactions.size()).isEqualTo(2);
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
    verifyNoInteractions(listener);
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
    assertThat(transactions.getTransactionByHash(t.getHash())).contains(t);
  }

  private void assertTransactionNotPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.getHash())).isEmpty();
  }

  private Transaction createTransaction(final long transactionNumber) {
    return new TransactionTestFixture()
        .value(Wei.of(transactionNumber))
        .nonce(transactionNumber)
        .createTransaction(KEYS1);
  }

  @Test
  public void shouldEvictMultipleOldTransactions() {
    final int maxTransactionRetentionHours = 1;
    final BaseFeePendingTransactionsSorter transactions =
        new BaseFeePendingTransactionsSorter(
            maxTransactionRetentionHours,
            MAX_TRANSACTIONS,
            clock,
            metricsSystem,
            BaseFeePendingTransactionsTest::mockBlockHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);

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
    final BaseFeePendingTransactionsSorter transactions =
        new BaseFeePendingTransactionsSorter(
            maxTransactionRetentionHours,
            MAX_TRANSACTIONS,
            clock,
            metricsSystem,
            BaseFeePendingTransactionsTest::mockBlockHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);
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
    final BaseFeePendingTransactionsSorter transactions =
        new BaseFeePendingTransactionsSorter(
            maxTransactionRetentionHours,
            MAX_TRANSACTIONS,
            clock,
            metricsSystem,
            BaseFeePendingTransactionsTest::mockBlockHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);
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

    assertThat(transactions.addLocalTransaction(transaction1))
        .isEqualTo(TransactionAddedStatus.ALREADY_KNOWN);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);
  }

  @Test
  public void assertThatCorrectNonceIsReturned() {
    assertThat(transactions.getNextNonceForSender(transaction1.getSender())).isEmpty();
    addLocalTransactions(1, 2, 4, 5);
    assertThat(transactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(3);
    addLocalTransactions(3);
    assertThat(transactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(6);
    addLocalTransactions(6, 10);
    assertThat(transactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(7);
  }

  @Test
  public void assertThatCorrectNonceIsReturnedLargeGap() {
    assertThat(transactions.getNextNonceForSender(transaction1.getSender())).isEmpty();
    addLocalTransactions(1, 2, Long.MAX_VALUE);
    assertThat(transactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(3);
    addLocalTransactions(3);
  }

  @Test
  public void assertThatCorrectNonceIsReturnedWithRepeatedTXes() {
    assertThat(transactions.getNextNonceForSender(transaction1.getSender())).isEmpty();
    addLocalTransactions(1, 2, 4, 4, 4, 4, 4, 4, 4, 4);
    assertThat(transactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(3);
    addLocalTransactions(3);
  }

  private void addLocalTransactions(final long... nonces) {
    for (final long nonce : nonces) {
      transactions.addLocalTransaction(createTransaction(nonce));
    }
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
    return blockHeader;
  }
}

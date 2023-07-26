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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTED;
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
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.StubMetricsSystem;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.testutil.TestClock;

import java.time.Clock;
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
import org.junit.jupiter.api.Test;

public abstract class AbstractPendingTransactionsTestBase {

  protected static final int MAX_TRANSACTIONS = 5;
  private static final float LIMITED_TRANSACTIONS_BY_SENDER_PERCENTAGE = 0.8f;
  protected static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  protected static final KeyPair KEYS1 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  protected static final KeyPair KEYS2 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  protected static final String ADDED_COUNTER = "transactions_added_total";
  protected static final String REMOVED_COUNTER = "transactions_removed_total";
  protected static final String REMOTE = "remote";
  protected static final String LOCAL = "local";
  protected static final String DROPPED = "dropped";

  protected final TestClock clock = new TestClock();
  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  protected AbstractPendingTransactionsSorter transactions =
      getPendingTransactions(
          ImmutableTransactionPoolConfiguration.builder()
              .txPoolMaxSize(MAX_TRANSACTIONS)
              .txPoolLimitByAccountPercentage(1.0f)
              .build(),
          Optional.empty());
  private final TransactionPoolConfiguration senderLimitedConfig =
      ImmutableTransactionPoolConfiguration.builder()
          .txPoolMaxSize(MAX_TRANSACTIONS)
          .txPoolLimitByAccountPercentage(LIMITED_TRANSACTIONS_BY_SENDER_PERCENTAGE)
          .build();
  protected PendingTransactions senderLimitedTransactions =
      getPendingTransactions(senderLimitedConfig, Optional.empty());

  protected final Transaction transaction1 = createTransaction(2);
  protected final Transaction transaction2 = createTransaction(1);

  protected final PendingTransactionAddedListener listener =
      mock(PendingTransactionAddedListener.class);
  protected final PendingTransactionDroppedListener droppedListener =
      mock(PendingTransactionDroppedListener.class);
  protected static final Address SENDER1 = Util.publicKeyToAddress(KEYS1.getPublicKey());
  protected static final Address SENDER2 = Util.publicKeyToAddress(KEYS2.getPublicKey());

  abstract AbstractPendingTransactionsSorter getPendingTransactions(
      final TransactionPoolConfiguration poolConfig, Optional<Clock> clock);

  @Test
  public void shouldReturnExclusivelyLocalTransactionsWhenAppropriate() {
    final Transaction localTransaction0 = createTransaction(0);
    transactions.addLocalTransaction(localTransaction0, Optional.empty());
    assertThat(transactions.size()).isEqualTo(1);

    transactions.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(transactions.size()).isEqualTo(2);

    transactions.addRemoteTransaction(transaction2, Optional.empty());
    assertThat(transactions.size()).isEqualTo(3);

    final List<Transaction> localTransactions = transactions.getLocalTransactions();
    assertThat(localTransactions.size()).isEqualTo(1);
  }

  @Test
  public void shouldAddATransaction() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    transactions.addRemoteTransaction(transaction2, Optional.empty());
    assertThat(transactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(2);
  }

  @Test
  public void shouldReturnEmptyOptionalWhenNoTransactionWithGivenHashExists() {
    assertThat(transactions.getTransactionByHash(Hash.EMPTY_TRIE_HASH)).isEmpty();
  }

  @Test
  public void shouldGetTransactionByHash() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    assertTransactionPending(transaction1);
  }

  @Test
  public void shouldDropOldestTransactionWhenLimitExceeded() {
    final Transaction oldestTransaction =
        transactionWithNonceSenderAndGasPrice(0, SIGNATURE_ALGORITHM.get().generateKeyPair(), 10L);
    final Account oldestSender = mock(Account.class);
    when(oldestSender.getNonce()).thenReturn(0L);
    senderLimitedTransactions.addRemoteTransaction(oldestTransaction, Optional.of(oldestSender));
    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
      final Account sender = mock(Account.class);
      when(sender.getNonce()).thenReturn((long) i);
      senderLimitedTransactions.addRemoteTransaction(
          transactionWithNonceSenderAndGasPrice(
              i, SIGNATURE_ALGORITHM.get().generateKeyPair(), 10L),
          Optional.of(sender));
    }
    assertThat(senderLimitedTransactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isZero();

    final Account lastSender = mock(Account.class);
    when(lastSender.getNonce()).thenReturn(6L);
    senderLimitedTransactions.addRemoteTransaction(
        createTransaction(MAX_TRANSACTIONS + 1), Optional.of(lastSender));
    assertThat(senderLimitedTransactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionNotPending(oldestTransaction);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldDropTransactionWithATooFarNonce() {
    Transaction furthestFutureTransaction = null;
    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
      furthestFutureTransaction = transactionWithNonceSenderAndGasPrice(i, KEYS1, 10L);
      senderLimitedTransactions.addRemoteTransaction(furthestFutureTransaction, Optional.empty());
    }
    assertThat(senderLimitedTransactions.size())
        .isEqualTo(senderLimitedConfig.getTxPoolMaxFutureTransactionByAccount());
    assertThat(senderLimitedConfig.getTxPoolMaxFutureTransactionByAccount()).isEqualTo(4);
    assertThat(senderLimitedTransactions.getTransactionByHash(furthestFutureTransaction.getHash()))
        .isEmpty();
  }

  @Test
  public void shouldHandleMaximumTransactionLimitCorrectlyWhenSameTransactionAddedMultipleTimes() {
    transactions.addRemoteTransaction(createTransaction(0), Optional.empty());
    transactions.addRemoteTransaction(createTransaction(0), Optional.empty());

    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i), Optional.empty());
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 1), Optional.empty());
    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 2), Optional.empty());
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
  }

  @Test
  public void shouldPrioritizeLocalTransaction() {
    final Transaction localTransaction = createTransaction(0);
    transactions.addLocalTransaction(localTransaction, Optional.empty());

    for (int i = 1; i <= MAX_TRANSACTIONS; i++) {
      transactions.addRemoteTransaction(createTransaction(i), Optional.empty());
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionPending(localTransaction);
  }

  @Test
  public void shouldStartDroppingLocalTransactionsWhenPoolIsFullOfLocalTransactions() {
    Transaction lastLocalTransactionForSender = null;

    for (int i = 0; i <= MAX_TRANSACTIONS; i++) {
      lastLocalTransactionForSender = createTransaction(i);
      transactions.addLocalTransaction(lastLocalTransactionForSender, Optional.empty());
    }
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionNotPending(lastLocalTransactionForSender);
  }

  @Test
  public void shouldNotifyListenerWhenRemoteTransactionAdded() {
    transactions.subscribePendingTransactions(listener);

    transactions.addRemoteTransaction(transaction1, Optional.empty());

    verify(listener).onTransactionAdded(transaction1);
  }

  @Test
  public void shouldNotNotifyListenerAfterUnsubscribe() {
    final long id = transactions.subscribePendingTransactions(listener);

    transactions.addRemoteTransaction(transaction1, Optional.empty());

    verify(listener).onTransactionAdded(transaction1);

    transactions.unsubscribePendingTransactions(id);

    transactions.addRemoteTransaction(transaction2, Optional.empty());

    verifyNoMoreInteractions(listener);
  }

  @Test
  public void shouldNotifyListenerWhenLocalTransactionAdded() {
    transactions.subscribePendingTransactions(listener);

    transactions.addLocalTransaction(transaction1, Optional.empty());

    verify(listener).onTransactionAdded(transaction1);
  }

  @Test
  public void shouldNotifyDroppedListenerWhenRemoteTransactionDropped() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());

    transactions.subscribeDroppedTransactions(droppedListener);

    transactions.removeTransaction(transaction1);

    verify(droppedListener).onTransactionDropped(transaction1);
  }

  @Test
  public void shouldNotNotifyDroppedListenerAfterUnsubscribe() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    transactions.addRemoteTransaction(transaction2, Optional.empty());

    final long id = transactions.subscribeDroppedTransactions(droppedListener);

    transactions.removeTransaction(transaction1);

    verify(droppedListener).onTransactionDropped(transaction1);

    transactions.unsubscribeDroppedTransactions(id);

    transactions.removeTransaction(transaction2);

    verifyNoMoreInteractions(droppedListener);
  }

  @Test
  public void shouldNotifyDroppedListenerWhenLocalTransactionDropped() {
    transactions.addLocalTransaction(transaction1, Optional.empty());

    transactions.subscribeDroppedTransactions(droppedListener);

    transactions.removeTransaction(transaction1);

    verify(droppedListener).onTransactionDropped(transaction1);
  }

  @Test
  public void shouldNotNotifyDroppedListenerWhenTransactionAddedToBlock() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());

    transactions.subscribeDroppedTransactions(droppedListener);

    transactions.transactionAddedToBlock(transaction1);

    verifyNoInteractions(droppedListener);
  }

  @Test
  public void selectTransactionsUntilSelectorRequestsNoMore() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    transactions.addRemoteTransaction(transaction2, Optional.empty());

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD;
        });

    assertThat(parsedTransactions.size()).isEqualTo(1);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
  }

  @Test
  public void selectTransactionsUntilPendingIsEmpty() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    transactions.addRemoteTransaction(transaction2, Optional.empty());

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return SELECTED;
        });

    assertThat(parsedTransactions.size()).isEqualTo(2);
    assertThat(parsedTransactions.get(0)).isEqualTo(transaction2);
    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);
  }

  @Test
  public void shouldNotSelectReplacedTransaction() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    final Transaction transaction2 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);

    transactions.addRemoteTransaction(transaction1, Optional.empty());
    transactions.addRemoteTransaction(transaction2, Optional.empty());

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return SELECTED;
        });

    assertThat(parsedTransactions).containsExactly(transaction2);
  }

  @Test
  public void invalidTransactionIsDeletedFromPendingTransactions() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    transactions.addRemoteTransaction(transaction2, Optional.empty());

    final List<Transaction> parsedTransactions = Lists.newArrayList();
    transactions.selectTransactions(
        transaction -> {
          parsedTransactions.add(transaction);
          return TransactionSelectionResult.invalid(
              TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE.name());
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
    transactions.addRemoteTransaction(transaction, Optional.empty());
    transactions.removeTransaction(transaction);
    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
  }

  @Test
  public void shouldReplaceTransactionWithSameSenderAndNonce() {
    final Transaction transaction1 = transactionWithNonceSenderAndGasPrice(1, KEYS1, 1);
    final Transaction transaction1b = transactionWithNonceSenderAndGasPrice(1, KEYS1, 2);
    final Transaction transaction2 = transactionWithNonceSenderAndGasPrice(2, KEYS1, 1);
    assertThat(transactions.addRemoteTransaction(transaction1, Optional.empty())).isEqualTo(ADDED);
    assertThat(transactions.addRemoteTransaction(transaction2, Optional.empty())).isEqualTo(ADDED);
    assertThat(transactions.addRemoteTransaction(transaction1b, Optional.empty())).isEqualTo(ADDED);

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
      transactions.addRemoteTransaction(duplicateTx, Optional.empty());
    }
    final Transaction finalReplacingTx = transactionWithNonceSenderAndGasPrice(1, KEYS1, 100);
    final Transaction independentTx = transactionWithNonceSenderAndGasPrice(2, KEYS1, 1);
    assertThat(transactions.addRemoteTransaction(independentTx, Optional.empty())).isEqualTo(ADDED);
    assertThat(transactions.addRemoteTransaction(finalReplacingTx, Optional.empty()))
        .isEqualTo(ADDED);

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
        transactions.addRemoteTransaction(duplicateTx, Optional.empty());
        remoteDuplicateCount++;
      } else {
        transactions.addLocalTransaction(duplicateTx, Optional.empty());
      }
    }
    final Transaction finalReplacingTx = transactionWithNonceSenderAndGasPrice(1, KEYS1, 100);
    final Transaction independentTx = transactionWithNonceSenderAndGasPrice(2, KEYS1, 1);
    assertThat(transactions.addLocalTransaction(finalReplacingTx, Optional.empty()))
        .isEqualTo(ADDED);
    assertThat(transactions.addRemoteTransaction(independentTx, Optional.empty())).isEqualTo(ADDED);

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
    assertThat(transactions.addRemoteTransaction(transaction1, Optional.empty())).isEqualTo(ADDED);
    assertThat(transactions.addRemoteTransaction(transaction1b, Optional.empty())).isEqualTo(ADDED);

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
    assertThat(transactions.addRemoteTransaction(transaction1, Optional.empty())).isEqualTo(ADDED);

    transactions.subscribePendingTransactions(listener);
    assertThat(transactions.addRemoteTransaction(transaction1b, Optional.empty()))
        .isEqualTo(REJECTED_UNDERPRICED_REPLACEMENT);

    assertTransactionNotPending(transaction1b);
    assertTransactionPending(transaction1);
    assertThat(transactions.size()).isEqualTo(1);
    verifyNoInteractions(listener);
  }

  @Test
  public void shouldTrackMaximumNonceForEachSender() {
    transactions.addRemoteTransaction(transactionWithNonceAndSender(0, KEYS1), Optional.empty());
    assertMaximumNonceForSender(SENDER1, 1);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(1, KEYS1), Optional.empty());
    assertMaximumNonceForSender(SENDER1, 2);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(2, KEYS1), Optional.empty());
    assertMaximumNonceForSender(SENDER1, 3);

    transactions.addRemoteTransaction(transactionWithNonceAndSender(4, KEYS2), Optional.empty());
    assertMaximumNonceForSender(SENDER2, 5);
    assertMaximumNonceForSender(SENDER1, 3);
  }

  @Test
  public void shouldIterateTransactionsFromSameSenderInNonceOrder() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS1);
    final Transaction transaction3 = transactionWithNonceAndSender(2, KEYS1);

    transactions.addLocalTransaction(transaction1, Optional.empty());
    transactions.addLocalTransaction(transaction2, Optional.empty());
    transactions.addLocalTransaction(transaction3, Optional.empty());

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return SELECTED;
        });

    assertThat(iterationOrder).containsExactly(transaction1, transaction2, transaction3);
  }

  @Test
  public void shouldNotForceNonceOrderWhenSendersDiffer() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS2);

    transactions.addLocalTransaction(transaction1, Optional.empty());
    transactions.addLocalTransaction(transaction2, Optional.empty());

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return SELECTED;
        });

    assertThat(iterationOrder).containsExactly(transaction2, transaction1);
  }

  @Test
  public void shouldNotIncreasePriorityOfTransactionsBecauseOfNonceOrder() {
    final Transaction transaction1 = transactionWithNonceAndSender(0, KEYS1);
    final Transaction transaction2 = transactionWithNonceAndSender(1, KEYS1);
    final Transaction transaction3 = transactionWithNonceAndSender(2, KEYS1);
    final Transaction transaction4 = transactionWithNonceAndSender(4, KEYS2);

    transactions.addLocalTransaction(transaction1, Optional.empty());
    transactions.addLocalTransaction(transaction4, Optional.empty());
    transactions.addLocalTransaction(transaction2, Optional.empty());
    transactions.addLocalTransaction(transaction3, Optional.empty());

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return SELECTED;
        });

    // Ignoring nonces, the order would be 3, 2, 4, 1 but we have to delay 3 and 2 until after 1.
    assertThat(iterationOrder)
        .containsExactly(transaction4, transaction1, transaction2, transaction3);
  }

  protected void assertMaximumNonceForSender(final Address sender1, final int i) {
    assertThat(transactions.getNextNonceForSender(sender1)).isEqualTo(OptionalLong.of(i));
  }

  protected Transaction transactionWithNonceAndSender(final int nonce, final KeyPair keyPair) {
    return new TransactionTestFixture().nonce(nonce).createTransaction(keyPair);
  }

  protected Transaction transactionWithNonceSenderAndGasPrice(
      final int nonce, final KeyPair keyPair, final long gasPrice) {
    return new TransactionTestFixture()
        .nonce(nonce)
        .gasPrice(Wei.of(gasPrice))
        .createTransaction(keyPair);
  }

  protected void assertTransactionPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.getHash())).contains(t);
  }

  protected void assertTransactionNotPending(final Transaction t) {
    assertThat(transactions.getTransactionByHash(t.getHash())).isEmpty();
  }

  protected Transaction createTransaction(final long transactionNumber) {
    return new TransactionTestFixture()
        .value(Wei.of(transactionNumber))
        .nonce(transactionNumber)
        .createTransaction(KEYS1);
  }

  @Test
  public void shouldEvictMultipleOldTransactions() {
    final int maxTransactionRetentionHours = 1;
    final PendingTransactions transactions =
        getPendingTransactions(
            ImmutableTransactionPoolConfiguration.builder()
                .pendingTxRetentionPeriod(maxTransactionRetentionHours)
                .txPoolMaxSize(MAX_TRANSACTIONS)
                .txPoolLimitByAccountPercentage(1)
                .build(),
            Optional.of(clock));

    transactions.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(transactions.size()).isEqualTo(1);
    transactions.addRemoteTransaction(transaction2, Optional.empty());
    assertThat(transactions.size()).isEqualTo(2);

    clock.step(2L, ChronoUnit.HOURS);
    transactions.evictOldTransactions();
    assertThat(transactions.size()).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(2);
  }

  @Test
  public void shouldEvictSingleOldTransaction() {
    final PendingTransactions evictSingleTransactions =
        getPendingTransactions(
            ImmutableTransactionPoolConfiguration.builder()
                .pendingTxRetentionPeriod(1)
                .txPoolMaxSize(MAX_TRANSACTIONS)
                .txPoolLimitByAccountPercentage(1.0f)
                .build(),
            Optional.of(clock));
    evictSingleTransactions.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(evictSingleTransactions.size()).isEqualTo(1);
    clock.step(2L, ChronoUnit.HOURS);
    evictSingleTransactions.evictOldTransactions();
    assertThat(evictSingleTransactions.size()).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldEvictExclusivelyOldTransactions() {
    final PendingTransactions twoHourEvictionTransactionPool =
        getPendingTransactions(
            ImmutableTransactionPoolConfiguration.builder()
                .pendingTxRetentionPeriod(2)
                .txPoolMaxSize(MAX_TRANSACTIONS)
                .txPoolLimitByAccountPercentage(1.0f)
                .build(),
            Optional.of(clock));

    twoHourEvictionTransactionPool.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(twoHourEvictionTransactionPool.size()).isEqualTo(1);
    clock.step(3L, ChronoUnit.HOURS);
    twoHourEvictionTransactionPool.addRemoteTransaction(transaction2, Optional.empty());
    assertThat(twoHourEvictionTransactionPool.size()).isEqualTo(2);
    twoHourEvictionTransactionPool.evictOldTransactions();
    assertThat(twoHourEvictionTransactionPool.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
  }

  @Test
  public void shouldNotIncrementAddedCounterWhenRemoteTransactionAlreadyPresent() {
    transactions.addLocalTransaction(transaction1, Optional.empty());
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);

    assertThat(transactions.addRemoteTransaction(transaction1, Optional.empty()))
        .isEqualTo(ALREADY_KNOWN);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);
  }

  @Test
  public void shouldNotIncrementAddedCounterWhenLocalTransactionAlreadyPresent() {
    transactions.addRemoteTransaction(transaction1, Optional.empty());
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    assertThat(transactions.addLocalTransaction(transaction1, Optional.empty()))
        .isEqualTo(ALREADY_KNOWN);
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

    // assert that transactions are pruned by account from latest future nonces first
    assertThat(transactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(6);
  }

  @Test
  public void assertThatCorrectNonceIsReturnedForSenderLimitedPool() {
    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction1.getSender())).isEmpty();
    addLocalTransactions(senderLimitedTransactions, 1, 2, 4, 5);
    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(3);
    addLocalTransactions(senderLimitedTransactions, 3);

    // assert we have dropped previously added tx 5, and next nonce is now 5
    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(5);
    addLocalTransactions(senderLimitedTransactions, 6, 10);

    // assert that we drop future nonces first:
    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction1.getSender()))
        .isPresent()
        .hasValue(5);
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

  protected void addLocalTransactions(final long... nonces) {
    addLocalTransactions(transactions, nonces);
  }

  protected void addLocalTransactions(final PendingTransactions sorter, final long... nonces) {
    for (final long nonce : nonces) {
      final Account sender = mock(Account.class);
      when(sender.getNonce()).thenReturn(1L);
      sorter.addLocalTransaction(createTransaction(nonce), Optional.of(sender));
    }
  }

  protected static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
    return blockHeader;
  }

  @Test
  public void shouldPrioritizeGasPriceThenTimeAddedToPool() {
    transactions.subscribeDroppedTransactions(
        transaction -> assertThat(transaction.getGasPrice().get().toLong()).isLessThan(100));

    // Fill the pool with transactions from random senders
    final List<Transaction> lowGasPriceTransactions =
        IntStream.range(0, MAX_TRANSACTIONS)
            .mapToObj(
                i -> {
                  final Account randomSender = mock(Account.class);
                  final Transaction lowPriceTx =
                      transactionWithNonceSenderAndGasPrice(
                          0, SIGNATURE_ALGORITHM.get().generateKeyPair(), 10);
                  transactions.addRemoteTransaction(lowPriceTx, Optional.of(randomSender));
                  return lowPriceTx;
                })
            .collect(Collectors.toUnmodifiableList());

    // This should kick the oldest tx with the low gas price out, namely the first one we added
    final Account highPriceSender = mock(Account.class);
    final Transaction highGasPriceTransaction =
        transactionWithNonceSenderAndGasPrice(0, KEYS1, 100);
    transactions.addRemoteTransaction(highGasPriceTransaction, Optional.of(highPriceSender));
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    assertTransactionPending(highGasPriceTransaction);
    assertTransactionNotPending(lowGasPriceTransactions.get(0));
    lowGasPriceTransactions.stream().skip(1).forEach(this::assertTransactionPending);
  }
}

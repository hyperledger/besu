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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED_SPARSE;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.PendingTransactionsSorter.TransactionSelectionResult.COMPLETE_OPERATION;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.PendingTransactionsSorter.TransactionSelectionResult.CONTINUE;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.PendingTransactionsSorter.TransactionSelectionResult.DELETE_TRANSACTION_AND_CONTINUE;
import static org.mockito.Mockito.doReturn;
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
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.eth.transactions.cache.ReadyTransactionsCache;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.StubMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import org.junit.Test;

public abstract class AbstractPrioritizedTransactionsTestBase {
//
//  protected static final int MAX_TRANSACTIONS = 5;
//  private static final float LIMITED_TRANSACTIONS_BY_SENDER_PERCENTAGE = 0.8f;
//  protected static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
//      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
//  protected static final KeyPair KEYS1 = SIGNATURE_ALGORITHM.get().generateKeyPair();
//  protected static final KeyPair KEYS2 = SIGNATURE_ALGORITHM.get().generateKeyPair();
//  protected static final String ADDED_COUNTER = "transactions_added_total";
//  protected static final String REMOVED_COUNTER = "transactions_removed_total";
//  protected static final String REPLACED_COUNTER = "transactions_replaced_total";
//  protected static final String REMOTE = "remote";
//  protected static final String LOCAL = "local";
//  protected static final String DROPPED = "dropped";
//  protected static final String PRIORITY_LIST = "priority";
//
//  protected final TestClock clock = new TestClock();
//  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
//  protected ReadyTransactionsCache readyTransactionsCache;
//
//  protected AbstractPrioritizedTransactions transactions =
//      getSorter(
//          ImmutableTransactionPoolConfiguration.builder()
//              .txPoolMaxSize(MAX_TRANSACTIONS)
//              .txPoolLimitByAccountPercentage(1.0f)
//              .build(),
//          Optional.empty());
//  private final TransactionPoolConfiguration senderLimitedConfig =
//      ImmutableTransactionPoolConfiguration.builder()
//          .txPoolMaxSize(MAX_TRANSACTIONS)
//          .txPoolLimitByAccountPercentage(LIMITED_TRANSACTIONS_BY_SENDER_PERCENTAGE)
//          .build();
//  protected AbstractPrioritizedTransactions senderLimitedTransactions =
//      getSorter(senderLimitedConfig, Optional.empty());
//
//  private final TransactionPoolConfiguration largePoolConfig =
//      ImmutableTransactionPoolConfiguration.builder()
//          .txPoolMaxSize(MAX_TRANSACTIONS * 10)
//          .txPoolLimitByAccountPercentage(1.0f)
//          .build();
//  protected AbstractPrioritizedTransactions largePoolTransactions =
//      getSorter(largePoolConfig, Optional.empty());
//
//  protected final Transaction transaction0 = createTransaction(0);
//  protected final Transaction transaction1 = createTransaction(1);
//  protected final Transaction transaction2 = createTransaction(2);
//
//  protected final PendingTransactionListener listener = mock(PendingTransactionListener.class);
//  protected final PendingTransactionDroppedListener droppedListener =
//      mock(PendingTransactionDroppedListener.class);
//  protected static final Address SENDER1 = Util.publicKeyToAddress(KEYS1.getPublicKey());
//  protected static final Address SENDER2 = Util.publicKeyToAddress(KEYS2.getPublicKey());
//
//  private AbstractPrioritizedTransactions getSorter(
//      final TransactionPoolConfiguration poolConfig, final Optional<Clock> clock) {
//    return getSorter(
//        poolConfig, clock, (pt1, pt2) -> transactionReplacementTester(poolConfig, pt1, pt2));
//  }
//
//  abstract AbstractPrioritizedTransactions getSorter(
//      final TransactionPoolConfiguration poolConfig,
//      final Optional<Clock> clock,
//      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
//          transactionReplacementTester);
//
//  abstract BlockHeader mockBlockHeader();
//
//  private boolean transactionReplacementTester(
//      final TransactionPoolConfiguration poolConfig,
//      final PendingTransaction pt1,
//      final PendingTransaction pt2) {
//    final TransactionPoolReplacementHandler transactionReplacementHandler =
//        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());
//    return transactionReplacementHandler.shouldReplace(pt1, pt2, mockBlockHeader());
//  }
//
//  @Test
//  public void shouldNotCreateGapsInPrioritizedList() {
//    transactions.addRemoteTransaction(transaction0, Optional.empty());
//    transactions.addRemoteTransaction(transaction1, Optional.empty());
//
//    assertThat(transactions.size()).isEqualTo(2);
//
//    // pretend tx 2 was only added as ready and not prioritized,
//    // so we can now simulate the adding of tx 3
//    final Transaction transaction3 = createTransaction(3);
//    final PendingTransaction pendingTransaction3 =
//        new PendingTransaction.Remote(transaction3, Instant.now());
//    doReturn(ADDED).when(readyTransactionsCache).add(pendingTransaction3, 0);
//
//    // tx 3 must not be added, otherwise a gap is created
//    assertThat(transactions.size()).isEqualTo(2);
//    assertTransactionPending(transaction0);
//    assertTransactionPending(transaction1);
//    assertTransactionNotPending(transaction3);
//  }
//
//  @Test
//  public void shouldDropTransactionWithATooFarNonce() {
//    Transaction furthestFutureTransaction = null;
//    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
//      furthestFutureTransaction = createTransaction(i);
//      senderLimitedTransactions.addRemoteTransaction(furthestFutureTransaction, Optional.empty());
//    }
//    assertThat(senderLimitedTransactions.size())
//        .isEqualTo(senderLimitedConfig.getTxPoolMaxFutureTransactionByAccount());
//    assertThat(senderLimitedConfig.getTxPoolMaxFutureTransactionByAccount()).isEqualTo(4);
//    assertThat(senderLimitedTransactions.getTransactionByHash(furthestFutureTransaction.getHash()))
//        .isEmpty();
//  }
//
//  @Test
//  public void shouldDropTransactionAlreadyConfirmed() {
//    final Account sender = mock(Account.class);
//    when(sender.getNonce()).thenReturn(5L);
//
//    final Transaction oldTransaction = createTransaction(2);
//    assertThat(transactions.addRemoteTransaction(oldTransaction, Optional.of(sender)))
//        .isEqualTo(ALREADY_KNOWN);
//    assertThat(transactions.size()).isEqualTo(0);
//    assertThat(transactions.getTransactionByHash(oldTransaction.getHash())).isEmpty();
//  }
//
//  @Test
//  public void shouldHandleMaximumTransactionLimitCorrectlyWhenSameTransactionAddedMultipleTimes() {
//    transactions.addRemoteTransaction(createTransaction(0), Optional.empty());
//    transactions.addRemoteTransaction(createTransaction(0), Optional.empty());
//
//    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
//      transactions.addRemoteTransaction(createTransaction(i), Optional.empty());
//    }
//    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
//
//    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 1), Optional.empty());
//    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 2), Optional.empty());
//    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
//  }
//
//  @Test
//  public void shouldPrioritizeLocalTransaction() {
//    final Transaction localTransaction = createTransaction(0);
//    transactions.addLocalTransaction(localTransaction, Optional.empty());
//
//    for (int i = 1; i <= MAX_TRANSACTIONS; i++) {
//      transactions.addRemoteTransaction(createTransaction(i), Optional.empty());
//    }
//    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
//    assertTransactionPending(localTransaction);
//  }
//
//  @Test
//  public void shouldStartDroppingLocalTransactionsWhenPoolIsFullOfLocalTransactions() {
//    Transaction lastLocalTransactionForSender = null;
//
//    for (int i = 0; i <= MAX_TRANSACTIONS; i++) {
//      lastLocalTransactionForSender = createTransaction(i, Wei.of(i));
//      transactions.addLocalTransaction(lastLocalTransactionForSender, Optional.empty());
//    }
//    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
//    assertTransactionNotPending(lastLocalTransactionForSender);
//  }
//
//  @Test
//  public void shouldNotifyListenerWhenRemoteTransactionAdded() {
//    transactions.subscribePendingTransactions(listener);
//
//    transactions.addRemoteTransaction(transaction0, Optional.empty());
//
//    verify(listener).onTransactionAdded(transaction0);
//  }
//
//  @Test
//  public void shouldNotNotifyListenerAfterUnsubscribe() {
//    final long id = transactions.subscribePendingTransactions(listener);
//
//    transactions.addRemoteTransaction(transaction0, Optional.empty());
//
//    verify(listener).onTransactionAdded(transaction0);
//
//    transactions.unsubscribePendingTransactions(id);
//
//    transactions.addRemoteTransaction(transaction1, Optional.empty());
//
//    verifyNoMoreInteractions(listener);
//  }
//
//  @Test
//  public void shouldNotifyListenerWhenLocalTransactionAdded() {
//    transactions.subscribePendingTransactions(listener);
//
//    transactions.addLocalTransaction(transaction0, Optional.empty());
//
//    verify(listener).onTransactionAdded(transaction0);
//  }
//
//  //  @Test
//  //  public void shouldNotifyDroppedListenerWhenRemoteTransactionDropped() {
//  //    transactions.addRemoteTransaction(transaction2, Optional.empty());
//  //
//  //    transactions.subscribeDroppedTransactions(droppedListener);
//  //
//  //    transactions.removeTransaction(transaction2);
//  //
//  //    verify(droppedListener).onTransactionDropped(transaction2);
//  //  }
//
//  //  @Test
//  //  public void shouldNotNotifyDroppedListenerAfterUnsubscribe() {
//  //    transactions.addRemoteTransaction(transaction2, Optional.empty());
//  //    transactions.addRemoteTransaction(transaction1, Optional.empty());
//  //
//  //    final long id = transactions.subscribeDroppedTransactions(droppedListener);
//  //
//  //    transactions.removeTransaction(transaction2);
//  //
//  //    verify(droppedListener).onTransactionDropped(transaction2);
//  //
//  //    transactions.unsubscribeDroppedTransactions(id);
//  //
//  //    transactions.removeTransaction(transaction1);
//  //
//  //    verifyNoMoreInteractions(droppedListener);
//  //  }
//
//  //  @Test
//  //  public void shouldNotifyDroppedListenerWhenLocalTransactionDropped() {
//  //    transactions.addLocalTransaction(transaction2, Optional.empty());
//  //
//  //    transactions.subscribeDroppedTransactions(droppedListener);
//  //
//  //    transactions.removeTransaction(transaction2);
//  //
//  //    verify(droppedListener).onTransactionDropped(transaction2);
//  //  }
//
//  @Test
//  public void shouldNotNotifyDroppedListenerWhenTransactionAddedToBlock() {
//    transactions.addRemoteTransaction(transaction2, Optional.empty());
//
//    transactions.subscribeDroppedTransactions(droppedListener);
//
//    final Block block = createBlock(transaction2);
//
//    transactions.manageBlockAdded(block.getHeader(), List.of(transaction2), FeeMarket.london(0));
//
//    verifyNoInteractions(droppedListener);
//  }
//
//  @Test
//  public void selectTransactionsUntilSelectorRequestsNoMore() {
//    transactions.addRemoteTransaction(transaction0, Optional.empty());
//    transactions.addRemoteTransaction(transaction1, Optional.empty());
//
//    final List<Transaction> parsedTransactions = Lists.newArrayList();
//    transactions.selectTransactions(
//        transaction -> {
//          parsedTransactions.add(transaction);
//          return COMPLETE_OPERATION;
//        });
//
//    assertThat(parsedTransactions.size()).isEqualTo(1);
//    assertThat(parsedTransactions.get(0)).isEqualTo(transaction0);
//  }
//
//  @Test
//  public void selectTransactionsUntilPendingIsEmpty() {
//    transactions.addRemoteTransaction(transaction0, Optional.empty());
//    transactions.addRemoteTransaction(transaction1, Optional.empty());
//
//    final List<Transaction> parsedTransactions = Lists.newArrayList();
//    transactions.selectTransactions(
//        transaction -> {
//          parsedTransactions.add(transaction);
//          return CONTINUE;
//        });
//
//    assertThat(parsedTransactions.size()).isEqualTo(2);
//    assertThat(parsedTransactions.get(0)).isEqualTo(transaction0);
//    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);
//  }
//
//  @Test
//  public void shouldNotSelectReplacedTransaction() {
//    final Transaction transaction1 = createTransaction(0, KEYS1);
//    final Transaction transaction1b = createTransactionReplacement(transaction1, KEYS1);
//
//    transactions.addRemoteTransaction(transaction1, Optional.empty());
//    transactions.addRemoteTransaction(transaction1b, Optional.empty());
//
//    final List<Transaction> parsedTransactions = Lists.newArrayList();
//    transactions.selectTransactions(
//        transaction -> {
//          parsedTransactions.add(transaction);
//          return CONTINUE;
//        });
//
//    assertThat(parsedTransactions).containsExactly(transaction1b);
//  }
//
//  @Test
//  public void invalidTransactionIsDeletedFromPendingTransactions() {
//    transactions.addRemoteTransaction(transaction0, Optional.empty());
//    transactions.addRemoteTransaction(transaction1, Optional.empty());
//
//    final List<Transaction> parsedTransactions = Lists.newArrayList();
//    transactions.selectTransactions(
//        transaction -> {
//          parsedTransactions.add(transaction);
//          return DELETE_TRANSACTION_AND_CONTINUE;
//        });
//
//    assertThat(parsedTransactions.size()).isEqualTo(2);
//    assertThat(parsedTransactions.get(0)).isEqualTo(transaction0);
//    assertThat(parsedTransactions.get(1)).isEqualTo(transaction1);
//
//    assertThat(transactions.size()).isZero();
//  }
//
//  @Test
//  public void shouldReturnEmptyOptionalAsMaximumNonceWhenNoTransactionsPresent() {
//    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
//  }
//
//  //  @Test
//  //  public void shouldReturnEmptyOptionalAsMaximumNonceWhenLastTransactionForSenderRemoved() {
//  //    final Transaction transaction = createTransaction(1, KEYS1);
//  //    transactions.addRemoteTransaction(transaction, Optional.empty());
//  //    transactions.removeTransaction(transaction);
//  //    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
//  //  }
//
//  @Test
//  public void shouldReplaceTransactionWithSameSenderAndNonce() {
//    final Transaction transaction1 = createTransaction(0, Wei.of(20), KEYS1);
//    final Transaction transaction1b = createTransactionReplacement(transaction1, KEYS1);
//    final Transaction transaction2 = createTransaction(1, Wei.of(10), KEYS1);
//    assertThat(transactions.addRemoteTransaction(transaction1, Optional.empty())).isEqualTo(ADDED);
//    assertThat(transactions.addRemoteTransaction(transaction2, Optional.empty())).isEqualTo(ADDED);
//    assertThat(transactions.addRemoteTransaction(transaction1b, Optional.empty()).isReplacement())
//        .isTrue();
//
//    assertTransactionNotPending(transaction1);
//    assertTransactionPending(transaction1b);
//    assertTransactionPending(transaction2);
//    assertThat(transactions.size()).isEqualTo(2);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(3);
//    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(REPLACED_COUNTER, REMOTE, PRIORITY_LIST)).isEqualTo(1);
//  }
//
//  @Test
//  public void shouldReplaceTransactionWithSameSenderAndNonce_multipleReplacements() {
//    final int replacedTxCount = 5;
//    final List<Transaction> replacedTransactions = new ArrayList<>(replacedTxCount);
//    Transaction duplicateTx = createTransaction(0, Wei.of(50), KEYS1);
//    for (int i = 0; i < replacedTxCount; i++) {
//      replacedTransactions.add(duplicateTx);
//      transactions.addRemoteTransaction(duplicateTx, Optional.empty());
//      duplicateTx = createTransactionReplacement(duplicateTx, KEYS1);
//    }
//
//    final Transaction independentTx = createTransaction(1, Wei.ONE, KEYS1);
//    assertThat(transactions.addRemoteTransaction(independentTx, Optional.empty())).isEqualTo(ADDED);
//    assertThat(transactions.addRemoteTransaction(duplicateTx, Optional.empty()).isReplacement())
//        .isTrue();
//
//    // All txs except the last duplicate should be removed
//    replacedTransactions.forEach(this::assertTransactionNotPending);
//    assertTransactionPending(duplicateTx);
//    // Tx with distinct nonce should be maintained
//    assertTransactionPending(independentTx);
//
//    assertThat(transactions.size()).isEqualTo(2);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(replacedTxCount + 2);
//    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED))
//        .isEqualTo(replacedTxCount);
//    assertThat(metricsSystem.getCounterValue(REPLACED_COUNTER, REMOTE, PRIORITY_LIST))
//        .isEqualTo(replacedTxCount);
//  }
//
//  @Test
//  public void
//      shouldReplaceTransactionWithSameSenderAndNonce_multipleReplacementsAddedLocallyAndRemotely() {
//    final int replacedTxCount = 11;
//    final List<Transaction> replacedTransactions = new ArrayList<>(replacedTxCount);
//    int remoteDuplicateCount = 0;
//    Transaction replacingTx = createTransaction(0, KEYS1);
//    for (int i = 0; i < replacedTxCount; i++) {
//      // final Transaction replacingTx =
//      //     createTransaction(0, Wei.of((i * 110 / 100) + 1));
//      replacedTransactions.add(replacingTx);
//      if (i % 2 == 0) {
//        largePoolTransactions.addRemoteTransaction(replacingTx, Optional.empty());
//        remoteDuplicateCount++;
//      } else {
//        largePoolTransactions.addLocalTransaction(replacingTx, Optional.empty());
//      }
//      replacingTx = createTransactionReplacement(replacingTx, KEYS1);
//    }
//    // final Transaction finalReplacingTx = createTransaction(0, Wei.of(100));
//    final Transaction independentTx = createTransaction(1);
//    assertThat(
//            largePoolTransactions
//                .addLocalTransaction(replacingTx, Optional.empty())
//                .isReplacement())
//        .isTrue();
//    assertThat(largePoolTransactions.addRemoteTransaction(independentTx, Optional.empty()))
//        .isEqualTo(ADDED);
//
//    // All txs except the last duplicate should be removed
//    replacedTransactions.forEach(tx -> assertTransactionNotPending(largePoolTransactions, tx));
//    assertTransactionPending(largePoolTransactions, replacingTx);
//    // Tx with distinct nonce should be maintained
//    assertTransactionPending(largePoolTransactions, independentTx);
//
//    final int localDuplicateCount = replacedTxCount - remoteDuplicateCount;
//    assertThat(largePoolTransactions.size()).isEqualTo(2);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE))
//        .isEqualTo(remoteDuplicateCount + 1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL))
//        .isEqualTo(localDuplicateCount + 1);
//    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED))
//        .isEqualTo(remoteDuplicateCount);
//    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, LOCAL, DROPPED))
//        .isEqualTo(localDuplicateCount);
//  }
//
//  @Test
//  public void shouldReplaceOnlyTransactionFromSenderWhenItHasTheSameNonce() {
//    final Transaction transaction0 = createTransaction(0, Wei.of(100), KEYS1);
//    final Transaction transaction0b = createTransactionReplacement(transaction0, KEYS1);
//    assertThat(transactions.addRemoteTransaction(transaction0, Optional.empty())).isEqualTo(ADDED);
//    assertThat(transactions.addRemoteTransaction(transaction0b, Optional.empty()).isReplacement())
//        .isTrue();
//
//    assertTransactionNotPending(transaction0);
//    assertTransactionPending(transaction0b);
//    assertThat(transactions.size()).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(2);
//    assertThat(metricsSystem.getCounterValue(REMOVED_COUNTER, REMOTE, DROPPED)).isEqualTo(1);
//  }
//
//  @Test
//  public void shouldNotReplaceTransactionWithSameSenderAndNonceWhenGasPriceIsLower() {
//    final Transaction transaction1 = createTransaction(0, Wei.of(2));
//    final Transaction transaction1b = createTransaction(0, Wei.ONE);
//    assertThat(transactions.addRemoteTransaction(transaction1, Optional.empty())).isEqualTo(ADDED);
//
//    transactions.subscribePendingTransactions(listener);
//    assertThat(transactions.addRemoteTransaction(transaction1b, Optional.empty()))
//        .isEqualTo(REJECTED_UNDERPRICED_REPLACEMENT);
//
//    assertTransactionNotPending(transaction1b);
//    assertTransactionPending(transaction1);
//    assertThat(transactions.size()).isEqualTo(1);
//    verifyNoInteractions(listener);
//  }
//
//  @Test
//  public void shouldTrackNextNonceForEachSender() {
//    // first sender consecutive txs: 0->1->2
//    final Account firstSender = mock(Account.class);
//    when(firstSender.getNonce()).thenReturn(0L);
//    when(firstSender.getAddress()).thenReturn(SENDER1);
//    assertNoNextNonceForSender(SENDER1);
//    transactions.addRemoteTransaction(createTransaction(0, KEYS1), Optional.of(firstSender));
//    assertNextNonceForSender(SENDER1, 1);
//
//    transactions.addRemoteTransaction(createTransaction(1, KEYS1), Optional.of(firstSender));
//    assertNextNonceForSender(SENDER1, 2);
//
//    transactions.addRemoteTransaction(createTransaction(2, KEYS1), Optional.of(firstSender));
//    assertNextNonceForSender(SENDER1, 3);
//
//    // second sender not in orders: 3->0->2->1
//    final Account secondSender = mock(Account.class);
//    when(secondSender.getNonce()).thenReturn(0L);
//    when(secondSender.getAddress()).thenReturn(SENDER2);
//    assertNoNextNonceForSender(SENDER2);
//    transactions.addRemoteTransaction(createTransaction(3, KEYS2), Optional.of(secondSender));
//    assertNoNextNonceForSender(SENDER2);
//
//    transactions.addRemoteTransaction(createTransaction(0, KEYS2), Optional.of(secondSender));
//    assertNextNonceForSender(SENDER2, 1);
//
//    transactions.addRemoteTransaction(createTransaction(2, KEYS2), Optional.of(secondSender));
//    assertNextNonceForSender(SENDER2, 1);
//
//    transactions.addRemoteTransaction(createTransaction(1, KEYS2), Optional.of(secondSender));
//    assertNextNonceForSender(SENDER2, 2);
//  }
//
//  @Test
//  public void shouldIterateTransactionsFromSameSenderInNonceOrder() {
//    final Transaction transaction1 = createTransaction(0, KEYS1);
//    final Transaction transaction2 = createTransaction(1, KEYS1);
//    final Transaction transaction3 = createTransaction(2, KEYS1);
//
//    transactions.addLocalTransaction(transaction1, Optional.empty());
//    transactions.addLocalTransaction(transaction2, Optional.empty());
//    transactions.addLocalTransaction(transaction3, Optional.empty());
//
//    final List<Transaction> iterationOrder = new ArrayList<>(3);
//    transactions.selectTransactions(
//        transaction -> {
//          iterationOrder.add(transaction);
//          return CONTINUE;
//        });
//
//    assertThat(iterationOrder).containsExactly(transaction1, transaction2, transaction3);
//  }
//
//  @Test
//  public void shouldNotForceNonceOrderWhenSendersDiffer() {
//    final Account sender2 = mock(Account.class);
//    when(sender2.getNonce()).thenReturn(1L);
//
//    final Transaction transactionSender1 = createTransaction(0, Wei.of(10), KEYS1);
//    final Transaction transactionSender2 = createTransaction(1, Wei.of(200), KEYS2);
//
//    transactions.addLocalTransaction(transactionSender1, Optional.empty());
//    transactions.addLocalTransaction(transactionSender2, Optional.of(sender2));
//
//    final List<Transaction> iterationOrder = new ArrayList<>(2);
//    transactions.selectTransactions(
//        transaction -> {
//          iterationOrder.add(transaction);
//          return CONTINUE;
//        });
//
//    assertThat(iterationOrder).containsExactly(transactionSender2, transactionSender1);
//  }
//
//  @Test
//  public void shouldNotIncreasePriorityOfTransactionsBecauseOfNonceOrder() {
//    final Account sender = mock(Account.class);
//    when(sender.getNonce()).thenReturn(1L);
//
//    final Transaction transaction1 = createTransaction(1);
//    final Transaction transaction2 = createTransaction(2);
//    final Transaction transaction3 = createTransaction(3);
//    final Transaction transaction4 = createTransaction(4);
//
//    assertThat(transactions.addLocalTransaction(transaction1, Optional.of(sender)))
//        .isEqualTo(ADDED);
//    // tx 4 is added as sparse
//    assertThat(transactions.addLocalTransaction(transaction4, Optional.of(sender)))
//        .isEqualTo(ADDED_SPARSE);
//    assertThat(transactions.addLocalTransaction(transaction2, Optional.of(sender)))
//        .isEqualTo(ADDED);
//    assertThat(transactions.addLocalTransaction(transaction3, Optional.of(sender)))
//        .isEqualTo(ADDED);
//
//    final List<Transaction> iterationOrder = new ArrayList<>(3);
//    transactions.selectTransactions(
//        transaction -> {
//          iterationOrder.add(transaction);
//          return CONTINUE;
//        });
//
//    assertThat(iterationOrder).containsExactly(transaction1, transaction2, transaction3);
//  }
//
//  @Test
//  public void shouldNotIncrementAddedCounterWhenRemoteTransactionAlreadyPresent() {
//    transactions.addLocalTransaction(transaction0, Optional.empty());
//    assertThat(transactions.size()).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);
//
//    assertThat(transactions.addRemoteTransaction(transaction0, Optional.empty()))
//        .isEqualTo(ALREADY_KNOWN);
//    assertThat(transactions.size()).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);
//  }
//
//  @Test
//  public void shouldNotIncrementAddedCounterWhenLocalTransactionAlreadyPresent() {
//    transactions.addRemoteTransaction(transaction0, Optional.empty());
//    assertThat(transactions.size()).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);
//
//    assertThat(transactions.addLocalTransaction(transaction0, Optional.empty()))
//        .isEqualTo(ALREADY_KNOWN);
//    assertThat(transactions.size()).isEqualTo(1);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(0);
//    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);
//  }
//
//  @Test
//  public void assertThatCorrectNonceIsReturned() {
//    final Account sender = mock(Account.class);
//    when(sender.getNonce()).thenReturn(1L);
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender())).isEmpty();
//    // since tx 3 is missing, 4 is sparse,
//    // note that 0 is already known since sender nonce is 1
//    addLocalTransactions(sender, 0, 1, 2, 4);
//    assertThat(transactions.size()).isEqualTo(2);
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(3);
//
//    // tx 3 arrives and is added, while 4 is move to ready
//    addLocalTransactions(sender, 3);
//    assertThat(transactions.size()).isEqualTo(4);
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(5);
//
//    // when 4 is added, the pool is full, and so 6 and 7 are postponed since last for the sender
//    addLocalTransactions(sender, 5, 6, 7);
//    assertThat(transactions.size()).isEqualTo(5);
//
//    // assert that transactions are pruned by account from the latest future nonce first
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(6);
//  }
//
//  @Test
//  public void assertThatCorrectNonceIsReturnedForSenderLimitedPool() {
//    final Account sender = mock(Account.class);
//    when(sender.getNonce()).thenReturn(1L);
//    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender())).isEmpty();
//    // since tx 3 is missing, 4 is postponed,
//    // note that 0 is already known since sender nonce is 1
//    addLocalTransactions(senderLimitedTransactions, sender, 0, 1, 2, 4);
//    assertThat(senderLimitedTransactions.size()).isEqualTo(2);
//    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(3);
//
//    // tx 3 arrives and is added, while 4 is still postponed
//    // Todo: change when we have the code to move from postponed to ready
//    addLocalTransactions(senderLimitedTransactions, sender, 3);
//    assertThat(senderLimitedTransactions.size()).isEqualTo(3);
//    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(4);
//
//    addLocalTransactions(senderLimitedTransactions, sender, 4, 5, 6, 7);
//    assertThat(senderLimitedTransactions.size()).isEqualTo(4);
//
//    // assert that we drop txs with future nonce first
//    assertThat(senderLimitedTransactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(5);
//  }
//
//  @Test
//  public void assertThatCorrectNonceIsReturnedLargeGap() {
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender())).isEmpty();
//    final Account sender = mock(Account.class);
//    addLocalTransactions(sender, 0, 1, 2, Long.MAX_VALUE);
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(3);
//    addLocalTransactions(sender, 3);
//  }
//
//  @Test
//  public void assertThatCorrectNonceIsReturnedWithRepeatedTXes() {
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender())).isEmpty();
//    final Account sender = mock(Account.class);
//    addLocalTransactions(sender, 0, 1, 2, 4, 4, 4, 4, 4, 4, 4, 4);
//    assertThat(transactions.getNextNonceForSender(transaction2.getSender()))
//        .isPresent()
//        .hasValue(3);
//    addLocalTransactions(sender, 3);
//  }
//
//  protected void shouldPrioritizeValueThenTimeAddedToPool(
//      final Iterator<Transaction> lowValueTxSupplier,
//      final Transaction highValueTx,
//      final Transaction expectedDroppedTx) {
//    transactions.subscribeDroppedTransactions(
//        transaction -> assertThat(transaction.getGasPrice().get().toLong()).isLessThan(100));
//
//    // Fill the pool with transactions from random senders
//    final List<Transaction> lowGasPriceTransactions =
//        IntStream.range(0, MAX_TRANSACTIONS)
//            .mapToObj(
//                i -> {
//                  final Account randomSender = mock(Account.class);
//                  final Transaction lowPriceTx = lowValueTxSupplier.next();
//                  transactions.addRemoteTransaction(lowPriceTx, Optional.of(randomSender));
//                  return lowPriceTx;
//                })
//            .collect(Collectors.toUnmodifiableList());
//
//    // This should kick the oldest tx with the low gas price out, namely the first one we added
//    final Account highPriceSender = mock(Account.class);
//    transactions.addRemoteTransaction(highValueTx, Optional.of(highPriceSender));
//    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
//
//    assertTransactionPending(highValueTx);
//    assertTransactionNotPending(expectedDroppedTx);
//    lowGasPriceTransactions.stream()
//        .filter(tx -> !tx.equals(expectedDroppedTx))
//        .forEach(this::assertTransactionPending);
//  }
//
//  private void assertNoNextNonceForSender(final Address sender) {
//    assertThat(transactions.getNextNonceForSender(sender)).isEqualTo(OptionalLong.empty());
//  }
//
//  protected void assertNextNonceForSender(final Address sender1, final int i) {
//    assertThat(transactions.getNextNonceForSender(sender1)).isEqualTo(OptionalLong.of(i));
//  }
//
//  protected void assertTransactionPending(
//      final PendingTransactionsSorter transactions, final Transaction t) {
//    assertThat(transactions.getTransactionByHash(t.getHash())).contains(t);
//  }
//
//  protected void assertTransactionPending(final Transaction t) {
//    assertTransactionPending(transactions, t);
//  }
//
//  protected void assertTransactionNotPending(
//      final PendingTransactionsSorter transactions, final Transaction t) {
//    assertThat(transactions.getTransactionByHash(t.getHash())).isEmpty();
//  }
//
//  protected void assertTransactionNotPending(final Transaction t) {
//    assertTransactionNotPending(transactions, t);
//  }
//
//  protected Transaction createTransaction(final long transactionNumber) {
//    return createTransaction(transactionNumber, Wei.of(5000L), KEYS1);
//  }
//
//  protected Transaction createTransaction(final long transactionNumber, final KeyPair keys) {
//    return createTransaction(transactionNumber, Wei.of(5000L), keys);
//  }
//
//  protected Transaction createTransaction(final long transactionNumber, final Wei maxGasPrice) {
//    return createTransaction(transactionNumber, maxGasPrice, KEYS1);
//  }
//
//  protected abstract Transaction createTransaction(
//      final long transactionNumber, final Wei maxGasPrice, final KeyPair keys);
//
//  protected abstract Transaction createTransactionReplacement(
//      final Transaction originalTransaction, final KeyPair keys);
//
//  protected void addLocalTransactions(final Account sender, final long... nonces) {
//    addLocalTransactions(transactions, sender, nonces);
//  }
//
//  protected void addLocalTransactions(
//      final PendingTransactionsSorter sorter, final Account sender, final long... nonces) {
//    for (final long nonce : nonces) {
//      sorter.addLocalTransaction(createTransaction(nonce), Optional.of(sender));
//    }
//  }
//
//  protected Block createBlock(final Transaction... transactionsToAdd) {
//    final List<Transaction> transactionList = asList(transactionsToAdd);
//    final Block block =
//        new Block(
//            new BlockHeaderTestFixture()
//                .baseFeePerGas(Wei.of(10L))
//                .gasLimit(300000)
//                .parentHash(Hash.ZERO)
//                .number(1)
//                .buildHeader(),
//            new BlockBody(transactionList, emptyList()));
//    return block;
//  }
}

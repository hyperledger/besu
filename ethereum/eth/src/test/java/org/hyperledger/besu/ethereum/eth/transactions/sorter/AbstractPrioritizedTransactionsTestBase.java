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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.BaseTransactionPoolTest;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPrioritizedTransactions.PrioritizeResult;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

public abstract class AbstractPrioritizedTransactionsTestBase extends BaseTransactionPoolTest {
  protected static final int MAX_TRANSACTIONS = 5;
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
  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  //
  protected AbstractPrioritizedTransactions transactions =
      getSorter(
          ImmutableTransactionPoolConfiguration.builder()
              .txPoolMaxSize(MAX_TRANSACTIONS)
              .txPoolLimitByAccountPercentage(1.0f)
              .build(),
          Optional.empty());
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
  //
  //  protected final PendingTransactionListener listener = mock(PendingTransactionListener.class);
  //  protected final PendingTransactionDroppedListener droppedListener =
  //      mock(PendingTransactionDroppedListener.class);
  //  protected static final Address SENDER1 = Util.publicKeyToAddress(KEYS1.getPublicKey());
  //  protected static final Address SENDER2 = Util.publicKeyToAddress(KEYS2.getPublicKey());
  //
  private AbstractPrioritizedTransactions getSorter(
      final TransactionPoolConfiguration poolConfig, final Optional<Clock> clock) {
    return getSorter(
        poolConfig, clock, (pt1, pt2) -> transactionReplacementTester(poolConfig, pt1, pt2));
  }

  abstract AbstractPrioritizedTransactions getSorter(
      final TransactionPoolConfiguration poolConfig,
      final Optional<Clock> clock,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester);

  abstract BlockHeader mockBlockHeader();

  private boolean transactionReplacementTester(
      final TransactionPoolConfiguration poolConfig,
      final PendingTransaction pt1,
      final PendingTransaction pt2) {
    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());
    return transactionReplacementHandler.shouldReplace(pt1, pt2, mockBlockHeader());
  }

  @Test
  public void shouldNotCreateGapsInPrioritizedList() {
    assertThat(prioritizeTransaction(transaction0).isPrioritized()).isTrue();
    assertThat(prioritizeTransaction(transaction1).isPrioritized()).isTrue();
    assertThat(transactions.size()).isEqualTo(2);

    // pretend tx 2 was only added as ready and not prioritized,
    // so we can now simulate the adding of tx 3
    final Transaction transaction3 = createTransaction(3);
    assertThat(prioritizeTransaction(transaction3).isPrioritized()).isFalse();

    // tx 3 must not be added, otherwise a gap is created
    assertThat(transactions.size()).isEqualTo(2);

    assertTransactionPrioritized(transaction0);
    assertTransactionPrioritized(transaction1);
    assertTransactionNotPrioritized(transaction3);
  }
  //
  //  @Test
  //  public void
  // shouldHandleMaximumTransactionLimitCorrectlyWhenSameTransactionAddedMultipleTimes() {
  //    transactions.addRemoteTransaction(createTransaction(0), Optional.empty());
  //    transactions.addRemoteTransaction(createTransaction(0), Optional.empty());
  //
  //    for (int i = 1; i < MAX_TRANSACTIONS; i++) {
  //      transactions.addRemoteTransaction(createTransaction(i), Optional.empty());
  //    }
  //    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
  //
  //    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 1),
  // Optional.empty());
  //    transactions.addRemoteTransaction(createTransaction(MAX_TRANSACTIONS + 2),
  // Optional.empty());
  //    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
  //  }
  //
  @Test
  public void prioritizeLocalTransactionThenValue() {
    final PendingTransaction localTransaction =
        createLocalPendingTransaction(createTransaction(0, KEYS1));
    assertThat(prioritizeTransaction(localTransaction).isPrioritized()).isTrue();

    final List<PendingTransaction> remoteTxs = new ArrayList<>();
    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
      final PendingTransaction highValueRemoteTx =
          createRemotePendingTransaction(
              createTransaction(
                  0,
                  Wei.of(BigInteger.valueOf(100).pow(i)),
                  SIGNATURE_ALGORITHM.get().generateKeyPair()));
      remoteTxs.add(highValueRemoteTx);
      assertThat(prioritizeTransaction(highValueRemoteTx).isPrioritized()).isTrue();
    }

    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionPrioritized(localTransaction);
    assertTransactionNotPrioritized(remoteTxs.get(0));
    remoteTxs.stream().skip(1).forEach(remoteTx -> assertTransactionPrioritized(remoteTx));
  }

  @Test
  public void shouldStartDroppingLocalTransactionsWhenPoolIsFullOfLocalTransactions() {
    final List<PendingTransaction> localTransactions = new ArrayList<>();

    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
      final var localTransaction = createLocalPendingTransaction(createTransaction(i));
      assertThat(prioritizeTransaction(localTransaction).isPrioritized()).isTrue();
      localTransactions.add(localTransaction);
    }

    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    // this will be rejected since the prioritized set is full of txs from the same sender with
    // lower nonce
    final var lastLocalTransaction =
        createLocalPendingTransaction(createTransaction(MAX_TRANSACTIONS));
    assertThat(prioritizeTransaction(lastLocalTransaction).isPrioritized()).isFalse();

    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    localTransactions.forEach(this::assertTransactionPrioritized);
    assertTransactionNotPrioritized(lastLocalTransaction);
  }

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
  //    transactions.manageBlockAdded(block.getHeader(), List.of(transaction2),
  // FeeMarket.london(0));
  //
  //    verifyNoInteractions(droppedListener);
  //  }

  //  //  @Test
  //  //  public void shouldReturnEmptyOptionalAsMaximumNonceWhenLastTransactionForSenderRemoved() {
  //  //    final Transaction transaction = createTransaction(1, KEYS1);
  //  //    transactions.addRemoteTransaction(transaction, Optional.empty());
  //  //    transactions.removeTransaction(transaction);
  //  //    assertThat(transactions.getNextNonceForSender(SENDER1)).isEmpty();
  //  //  }
  //
  //
  protected void shouldPrioritizeValueThenTimeAddedToPool(
      final Iterator<PendingTransaction> lowValueTxSupplier,
      final PendingTransaction highValueTx,
      final PendingTransaction expectedDroppedTx) {

    // Fill the pool with transactions from random senders
    final List<PendingTransaction> lowGasPriceTransactions =
        IntStream.range(0, MAX_TRANSACTIONS)
            .mapToObj(
                i -> {
                  final var lowPriceTx = lowValueTxSupplier.next();
                  final var senderTxs = new TreeMap<Long, PendingTransaction>();
                  senderTxs.put(lowPriceTx.getNonce(), lowPriceTx);
                  final var prioritizeResult =
                      transactions.maybePrioritizeAddedTransaction(
                          senderTxs, lowPriceTx, 0, TransactionAddedResult.ADDED);

                  assertThat(prioritizeResult.isPrioritized()).isTrue();
                  assertThat(prioritizeResult.maybeDemotedTransaction()).isEmpty();
                  return lowPriceTx;
                })
            .collect(Collectors.toUnmodifiableList());

    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    // This should kick the oldest tx with the low gas price out, namely the first one we added
    final var highValueSenderTxs = new TreeMap<Long, PendingTransaction>();
    highValueSenderTxs.put(highValueTx.getNonce(), highValueTx);

    final var highValuePrioRes =
        transactions.maybePrioritizeAddedTransaction(
            highValueSenderTxs, highValueTx, 0, TransactionAddedResult.ADDED);
    assertThat(highValuePrioRes.isPrioritized()).isTrue();
    assertThat(highValuePrioRes.maybeDemotedTransaction())
        .isPresent()
        .map(PendingTransaction::getHash)
        .contains(expectedDroppedTx.getHash());
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);

    assertThat(transactions.getTransactionByHash(highValueTx.getHash())).isPresent();
    assertThat(transactions.getTransactionByHash(expectedDroppedTx.getHash())).isEmpty();
    lowGasPriceTransactions.stream()
        .filter(tx -> !tx.equals(expectedDroppedTx))
        .forEach(tx -> assertThat(transactions.getTransactionByHash(tx.getHash())).isPresent());
  }
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

  protected PrioritizeResult prioritizeTransaction(final Transaction tx) {
    return prioritizeTransaction(createRemotePendingTransaction(tx));
  }

  protected PrioritizeResult prioritizeTransaction(final PendingTransaction tx) {
    final var senderTxs = new TreeMap<Long, PendingTransaction>();
    senderTxs.put(tx.getNonce(), tx);

    return transactions.maybePrioritizeAddedTransaction(
        senderTxs, tx, 0, TransactionAddedResult.ADDED);
  }

  protected void assertTransactionPrioritized(final PendingTransaction tx) {
    assertThat(transactions.getTransactionByHash(tx.getHash())).isPresent();
  }

  protected void assertTransactionNotPrioritized(final PendingTransaction tx) {
    assertThat(transactions.getTransactionByHash(tx.getHash())).isEmpty();
  }

  protected void assertTransactionPrioritized(final Transaction tx) {
    assertThat(transactions.getTransactionByHash(tx.getHash())).isPresent();
  }

  protected void assertTransactionNotPrioritized(final Transaction tx) {
    assertThat(transactions.getTransactionByHash(tx.getHash())).isEmpty();
  }
}

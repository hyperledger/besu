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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

public abstract class AbstractPrioritizedTransactionsTestBase extends BaseTransactionPoolTest {
  protected static final int MAX_TRANSACTIONS = 5;
  protected final TransactionPoolMetrics txPoolMetrics = new TransactionPoolMetrics(metricsSystem);
  protected final EvictCollectorLayer evictCollector = new EvictCollectorLayer(txPoolMetrics);
  protected AbstractPrioritizedTransactions transactions =
      getSorter(
          ImmutableTransactionPoolConfiguration.builder()
              .maxPrioritizedTransactions(MAX_TRANSACTIONS)
              .maxFutureBySender(MAX_TRANSACTIONS)
              .build());

  private AbstractPrioritizedTransactions getSorter(final TransactionPoolConfiguration poolConfig) {
    return getSorter(
        poolConfig,
        evictCollector,
        txPoolMetrics,
        (pt1, pt2) -> transactionReplacementTester(poolConfig, pt1, pt2));
  }

  abstract AbstractPrioritizedTransactions getSorter(
      final TransactionPoolConfiguration poolConfig,
      final TransactionsLayer nextLayer,
      final TransactionPoolMetrics txPoolMetrics,
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
  public void prioritizeLocalTransactionThenValue() {
    final PendingTransaction localTransaction =
        createLocalPendingTransaction(createTransaction(0, KEYS1));
    assertThat(prioritizeTransaction(localTransaction)).isEqualTo(ADDED);

    final List<PendingTransaction> remoteTxs = new ArrayList<>();
    TransactionAddedResult prioritizeResult = null;
    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
      final PendingTransaction highValueRemoteTx =
          createRemotePendingTransaction(
              createTransaction(
                  0,
                  Wei.of(BigInteger.valueOf(100).pow(i)),
                  SIGNATURE_ALGORITHM.get().generateKeyPair()));
      remoteTxs.add(highValueRemoteTx);
      prioritizeResult = prioritizeTransaction(highValueRemoteTx);
      assertThat(prioritizeResult).isEqualTo(ADDED);
    }

    assertEvicted(remoteTxs.get(0));
    assertTransactionPrioritized(localTransaction);
    remoteTxs.stream().skip(1).forEach(remoteTx -> assertTransactionPrioritized(remoteTx));
  }

  @Test
  public void shouldStartDroppingLocalTransactionsWhenPoolIsFullOfLocalTransactions() {
    final List<PendingTransaction> localTransactions = new ArrayList<>();

    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
      final var localTransaction = createLocalPendingTransaction(createTransaction(i));
      assertThat(prioritizeTransaction(localTransaction)).isEqualTo(ADDED);
      localTransactions.add(localTransaction);
    }

    assertThat(transactions.count()).isEqualTo(MAX_TRANSACTIONS);

    // this will be rejected since the prioritized set is full of txs from the same sender with
    // lower nonce
    final var lastLocalTransaction =
        createLocalPendingTransaction(createTransaction(MAX_TRANSACTIONS));
    prioritizeTransaction(lastLocalTransaction);
    assertEvicted(lastLocalTransaction);

    assertThat(transactions.count()).isEqualTo(MAX_TRANSACTIONS);

    localTransactions.forEach(this::assertTransactionPrioritized);
    assertTransactionNotPrioritized(lastLocalTransaction);
  }

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
                  final var prioritizeResult = transactions.add(lowPriceTx, 0);

                  assertThat(prioritizeResult).isEqualTo(ADDED);
                  assertThat(evictCollector.getEvictedTransactions()).isEmpty();
                  return lowPriceTx;
                })
            .toList();

    assertThat(transactions.count()).isEqualTo(MAX_TRANSACTIONS);

    // This should kick the oldest tx with the low gas price out, namely the first one we added
    final var highValuePrioRes = transactions.add(highValueTx, 0);
    assertThat(highValuePrioRes).isEqualTo(ADDED);
    assertEvicted(expectedDroppedTx);

    assertTransactionPrioritized(highValueTx);
    lowGasPriceTransactions.stream()
        .filter(tx -> !tx.equals(expectedDroppedTx))
        .forEach(tx -> assertThat(transactions.getByHash(tx.getHash())).isPresent());
  }

  protected TransactionAddedResult prioritizeTransaction(final Transaction tx) {
    return prioritizeTransaction(createRemotePendingTransaction(tx));
  }

  protected TransactionAddedResult prioritizeTransaction(final PendingTransaction tx) {
    return transactions.add(tx, 0);
  }

  protected void assertTransactionPrioritized(final PendingTransaction tx) {
    assertThat(transactions.getByHash(tx.getHash())).isPresent();
  }

  protected void assertTransactionNotPrioritized(final PendingTransaction tx) {
    assertThat(transactions.getByHash(tx.getHash())).isEmpty();
  }

  protected void assertTransactionPrioritized(final Transaction tx) {
    assertThat(transactions.getByHash(tx.getHash())).isPresent();
  }

  protected void assertTransactionNotPrioritized(final Transaction tx) {
    assertThat(transactions.getByHash(tx.getHash())).isEmpty();
  }

  protected void assertEvicted(final PendingTransaction tx) {
    assertThat(evictCollector.getEvictedTransactions()).contains(tx);
  }
}

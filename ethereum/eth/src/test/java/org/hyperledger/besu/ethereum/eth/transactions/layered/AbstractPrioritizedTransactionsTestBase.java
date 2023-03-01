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

public abstract class AbstractPrioritizedTransactionsTestBase extends BaseTransactionPoolTest {
  //  protected static final int MAX_TRANSACTIONS = 5;
  //  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  //
  //  protected AbstractPrioritizedTransactions transactions =
  //      getSorter(
  //          ImmutableTransactionPoolConfiguration.builder()
  //              .maxPrioritizedTransactions(MAX_TRANSACTIONS)
  //              .maxFutureBySender(MAX_TRANSACTIONS)
  //              .build(),
  //          Optional.empty());
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
  //    assertThat(prioritizeTransaction(transaction0).isPrioritized()).isTrue();
  //    assertThat(prioritizeTransaction(transaction1).isPrioritized()).isTrue();
  //    assertThat(transactions.count()).isEqualTo(2);
  //
  //    // pretend tx 2 was only added as ready and not prioritized,
  //    // so we can now simulate the adding of tx 3
  //    final Transaction transaction3 = createTransaction(3);
  //    assertThat(prioritizeTransaction(transaction3).isPrioritized()).isFalse();
  //
  //    // tx 3 must not be added, otherwise a gap is created
  //    assertThat(transactions.count()).isEqualTo(2);
  //
  //    assertTransactionPrioritized(transaction0);
  //    assertTransactionPrioritized(transaction1);
  //    assertTransactionNotPrioritized(transaction3);
  //  }
  //
  //  @Test
  //  public void prioritizeLocalTransactionThenValue() {
  //    final PendingTransaction localTransaction =
  //        createLocalPendingTransaction(createTransaction(0, KEYS1));
  //    assertThat(prioritizeTransaction(localTransaction).isPrioritized()).isTrue();
  //
  //    final List<PendingTransaction> remoteTxs = new ArrayList<>();
  //    PrioritizeResult prioritizeResult = null;
  //    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
  //      final PendingTransaction highValueRemoteTx =
  //          createRemotePendingTransaction(
  //              createTransaction(
  //                  0,
  //                  Wei.of(BigInteger.valueOf(100).pow(i)),
  //                  SIGNATURE_ALGORITHM.get().generateKeyPair()));
  //      remoteTxs.add(highValueRemoteTx);
  //      prioritizeResult = prioritizeTransaction(highValueRemoteTx);
  //      assertThat(prioritizeResult.isPrioritized()).isTrue();
  //    }
  //
  //
  // assertThat(prioritizeResult.maybeDemotedTransaction()).isPresent().contains(remoteTxs.get(0));
  //    assertTransactionPrioritized(localTransaction);
  //    remoteTxs.stream().skip(1).forEach(remoteTx -> assertTransactionPrioritized(remoteTx));
  //  }
  //
  //  @Test
  //  public void shouldStartDroppingLocalTransactionsWhenPoolIsFullOfLocalTransactions() {
  //    final List<PendingTransaction> localTransactions = new ArrayList<>();
  //
  //    for (int i = 0; i < MAX_TRANSACTIONS; i++) {
  //      final var localTransaction = createLocalPendingTransaction(createTransaction(i));
  //      assertThat(prioritizeTransaction(localTransaction).isPrioritized()).isTrue();
  //      localTransactions.add(localTransaction);
  //    }
  //
  //    assertThat(transactions.count()).isEqualTo(MAX_TRANSACTIONS);
  //
  //    // this will be rejected since the prioritized set is full of txs from the same sender with
  //    // lower nonce
  //    final var lastLocalTransaction =
  //        createLocalPendingTransaction(createTransaction(MAX_TRANSACTIONS));
  //    assertThat(prioritizeTransaction(lastLocalTransaction).isPrioritized()).isFalse();
  //
  //    assertThat(transactions.count()).isEqualTo(MAX_TRANSACTIONS);
  //
  //    localTransactions.forEach(this::assertTransactionPrioritized);
  //    assertTransactionNotPrioritized(lastLocalTransaction);
  //  }
  //
  //  protected void shouldPrioritizeValueThenTimeAddedToPool(
  //      final Iterator<PendingTransaction> lowValueTxSupplier,
  //      final PendingTransaction highValueTx,
  //      final PendingTransaction expectedDroppedTx) {
  //
  //    // Fill the pool with transactions from random senders
  //    final List<PendingTransaction> lowGasPriceTransactions =
  //        IntStream.range(0, MAX_TRANSACTIONS)
  //            .mapToObj(
  //                i -> {
  //                  final var lowPriceTx = lowValueTxSupplier.next();
  //                  final var prioritizeResult =
  //                      transactions.prioritizeTransaction(
  //                          lowPriceTx, 0, TransactionAddedResult.ADDED);
  //
  //                  assertThat(prioritizeResult.isPrioritized()).isTrue();
  //                  assertThat(prioritizeResult.maybeDemotedTransaction()).isEmpty();
  //                  return lowPriceTx;
  //                })
  //            .collect(Collectors.toUnmodifiableList());
  //
  //    assertThat(transactions.count()).isEqualTo(MAX_TRANSACTIONS);
  //
  //    // This should kick the oldest tx with the low gas price out, namely the first one we added
  //    final var highValuePrioRes =
  //        transactions.prioritizeTransaction(highValueTx, 0, TransactionAddedResult.ADDED);
  //    assertThat(highValuePrioRes.isPrioritized()).isTrue();
  //    assertThat(highValuePrioRes.maybeDemotedTransaction())
  //        .isPresent()
  //        .map(PendingTransaction::getHash)
  //        .contains(expectedDroppedTx.getHash());
  //
  //    assertThat(transactions.getByHash(highValueTx.getHash())).isPresent();
  //    lowGasPriceTransactions.stream()
  //        .filter(tx -> !tx.equals(expectedDroppedTx))
  //        .forEach(tx -> assertThat(transactions.getByHash(tx.getHash())).isPresent());
  //  }
  //
  //  protected PrioritizeResult prioritizeTransaction(final Transaction tx) {
  //    return prioritizeTransaction(createRemotePendingTransaction(tx));
  //  }
  //
  //  protected PrioritizeResult prioritizeTransaction(final PendingTransaction tx) {
  //    return transactions.prioritizeTransaction(tx, 0, TransactionAddedResult.ADDED);
  //  }
  //
  //  protected void assertTransactionPrioritized(final PendingTransaction tx) {
  //    assertThat(transactions.getByHash(tx.getHash())).isPresent();
  //  }
  //
  //  protected void assertTransactionNotPrioritized(final PendingTransaction tx) {
  //    assertThat(transactions.getByHash(tx.getHash())).isEmpty();
  //  }
  //
  //  protected void assertTransactionPrioritized(final Transaction tx) {
  //    assertThat(transactions.getByHash(tx.getHash())).isPresent();
  //  }
  //
  //  protected void assertTransactionNotPrioritized(final Transaction tx) {
  //    assertThat(transactions.getByHash(tx.getHash())).isEmpty();
  //  }
}

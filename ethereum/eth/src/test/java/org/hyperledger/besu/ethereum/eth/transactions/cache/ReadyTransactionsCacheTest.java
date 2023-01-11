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
package org.hyperledger.besu.ethereum.eth.transactions.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ADDED_SPARSE;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.TX_POOL_FULL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.plugin.data.TransactionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.function.BiFunction;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReadyTransactionsCacheTest {
  protected static final int MAX_TRANSACTIONS = 5;
  protected static final int CACHE_CAPACITY_BYTES = 1024;
  protected static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  protected static final KeyPair KEYS1 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  protected static final KeyPair KEYS2 = SIGNATURE_ALGORITHM.get().generateKeyPair();

  private static final Random randomizeTxType = new Random();

  private final TransactionPoolConfiguration poolConf =
      ImmutableTransactionPoolConfiguration.builder()
          .txPoolMaxSize(MAX_TRANSACTIONS)
          .txPoolLimitByAccountPercentage(1.0f)
          .pendingTransactionsCacheSizeBytes(CACHE_CAPACITY_BYTES)
          .build();

  private ReadyTransactionsCache readyTransactionsCache;

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

    readyTransactionsCache = new ReadyTransactionsCache(poolConf, transactionReplacementTester);
  }

  @Test
  public void addFirstTransaction() {
    final var firstTransaction = createTransaction(0);
    assertThat(readyTransactionsCache.add(createPendingTransaction(firstTransaction), 0))
        .isEqualTo(ADDED);
    assertTransactionPresent(firstTransaction);
  }

  @Test
  public void addTransactionForMultipleSenders() {
    final var transactionSenderA = createTransaction(0, KEYS1);
    final var transactionSenderB = createTransaction(0, KEYS2);
    assertThat(readyTransactionsCache.add(createPendingTransaction(transactionSenderA), 0))
        .isEqualTo(ADDED);
    assertTransactionPresent(transactionSenderA);
    assertThat(readyTransactionsCache.add(createPendingTransaction(transactionSenderB), 0))
        .isEqualTo(ADDED);
    assertTransactionPresent(transactionSenderB);
  }

  @Test
  public void shouldPostponeFirstTransactionWithNonceGap() {
    final var firstTransaction = createTransaction(1);
    assertThat(readyTransactionsCache.add(createPendingTransaction(firstTransaction), 0))
        .isEqualTo(TX_POOL_FULL);
    assertTransactionNotPresent(firstTransaction);
  }

  @Test
  public void shouldPostponeNextTransactionWithNonceGap() {
    final var transaction0 = createTransaction(0);
    final var transaction2 = createTransaction(2);
    assertThat(readyTransactionsCache.add(createPendingTransaction(transaction0), 0))
        .isEqualTo(ADDED);
    assertTransactionPresent(transaction0);
    assertThat(readyTransactionsCache.add(createPendingTransaction(transaction2), 0))
        .isEqualTo(TX_POOL_FULL);
    assertTransactionNotPresent(transaction2);
  }

  @Test
  public void shouldPostponeIfTooMuchPendingTransactionForTheSender() {
    final var futureTransaction =
        createTransaction(poolConf.getTxPoolMaxFutureTransactionByAccount() + 1);
    assertThat(readyTransactionsCache.add(createPendingTransaction(futureTransaction), 0))
        .isEqualTo(TX_POOL_FULL);
    assertTransactionNotPresent(futureTransaction);
  }

  @Test
  public void shouldReplaceTransaction() {
    final var lowValueTransaction = createTransaction(0, KEYS1);
    final var highValueTransaction = createTransactionReplacement(lowValueTransaction, KEYS1);
    assertThat(readyTransactionsCache.add(createPendingTransaction(lowValueTransaction), 0))
        .isEqualTo(ADDED);
    assertTransactionPresent(lowValueTransaction);
    final var txAddResult =
        readyTransactionsCache.add(createPendingTransaction(highValueTransaction), 0);
    assertThat(txAddResult.isReplacement()).isTrue();
    assertThat(txAddResult.maybeReplacedTransaction())
        .isPresent()
        .map(PendingTransaction::getHash)
        .hasValue(lowValueTransaction.getHash());
    assertTransactionPresent(highValueTransaction);
    assertTransactionNotPresent(lowValueTransaction);
  }

  @Test
  public void shouldNotReplaceTransaction() {
    final var highValueTransaction = createTransaction(0, Wei.of(101));
    final var lowValueTransaction = createTransaction(0, Wei.of(100));

    assertThat(readyTransactionsCache.add(createPendingTransaction(highValueTransaction), 0))
        .isEqualTo(ADDED);
    assertTransactionPresent(highValueTransaction);
    assertThat(readyTransactionsCache.add(createPendingTransaction(lowValueTransaction), 0))
        .isEqualTo(REJECTED_UNDERPRICED_REPLACEMENT);
    assertTransactionNotPresent(lowValueTransaction);
    assertTransactionPresent(highValueTransaction);
  }

  @Test
  public void doNothingIfTransactionAlreadyPresent() {
    final var addedTxs = populateCache(1, 0);
    assertThat(readyTransactionsCache.add(createPendingTransaction(addedTxs[0]), 0))
        .isEqualTo(ALREADY_KNOWN);
    assertTransactionPresent(addedTxs[0]);
  }

  @Test
  public void returnsEmptyWhenGettingNotPresentTransaction() {
    final var transaction = createTransaction(0);
    assertTransactionNotPresent(transaction);
    assertThat(readyTransactionsCache.get(transaction.getSender(), 0)).isEmpty();
  }

  @Test
  public void removePreviouslyAddedTransaction() {
    final var addedTxs = populateCache(1, 0);
    readyTransactionsCache.remove(addedTxs[0]);
    assertTransactionNotPresent(addedTxs[0]);
  }

  @Test
  public void doNothingWhenRemovingNotPresentTransaction() {
    final var transaction = createTransaction(0);
    assertTransactionNotPresent(transaction);
    readyTransactionsCache.remove(transaction);
    assertTransactionNotPresent(transaction);
  }

  @Test
  public void returnsNextReadyNonceForSenderWithOneReadyTransaction() {
    final var addedTxs = populateCache(1, 0);
    assertThat(readyTransactionsCache.getNextReadyNonce(addedTxs[0].getSender()))
        .isPresent()
        .hasValue(1);
  }

  @Test
  public void returnsNextReadyNonceForSenderWithMultipleReadyTransactions() {
    final var addedTxs = populateCache(2, 0);
    assertThat(readyTransactionsCache.getNextReadyNonce(addedTxs[0].getSender()))
        .isPresent()
        .hasValue(2);
  }

  @Test
  public void returnsCorrectNextReadyWhenAddedTransactionsHaveGaps() {
    final var addedTxs = populateCache(3, 0, 1);
    assertThat(readyTransactionsCache.getNextReadyNonce(addedTxs[0].getSender()))
        .isPresent()
        .hasValue(1);
  }

  @Test
  public void returnsEmptyHasNextReadyNonceForSenderWithoutReadyTransactions() {
    final var transaction = createTransaction(0);
    assertTransactionNotPresent(transaction);
    assertThat(readyTransactionsCache.getNextReadyNonce(transaction.getSender())).isEmpty();
  }

  @Test
  public void emptyStreamWhenNoReadyTransactionsForSender() {
    final var transaction = createTransaction(0);
    assertTransactionNotPresent(transaction);
    assertThat(readyTransactionsCache.streamReadyTransactions(transaction.getSender())).isEmpty();
  }

  @Test
  public void emptyStreamWhenUsingNonceAndNoReadyTransactionsForSender() {
    final var transaction = createTransaction(0);
    assertTransactionNotPresent(transaction);
    assertThat(readyTransactionsCache.streamReadyTransactions(transaction.getSender(), 1))
        .isEmpty();
  }

  @Test
  public void streamWithOnlyOneReadyTransactionForSender() {
    final var readyTxs = populateCache(1, 0);
    assertThat(readyTxs).hasSize(1);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(0L);
    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender()))
        .map(PendingTransaction::getTransaction)
        .containsExactly(readyTxs);
  }

  @Test
  public void emptyStreamWhenUsingNonceGreaterThanMaxPresentForSender() {
    final var readyTxs = populateCache(2, 1);
    assertThat(readyTxs).hasSize(2);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L, 2L);
    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender(), 2L))
        .isEmpty();
  }

  @Test
  public void streamWithMoreReadyTransactionForSender() {
    final var readyTxs = populateCache(3, 1);
    assertThat(readyTxs).hasSize(3);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce))
        .containsExactly(1L, 2L, 3L);
    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender()))
        .map(PendingTransaction::getTransaction)
        .containsExactly(readyTxs);
  }

  @Test
  public void streamWhenUsingNonceWithMoreReadyTransactionForSenderReturnOnlyGreaterThanNonce() {
    final var readyTxs = populateCache(3, 1);
    assertThat(readyTxs).hasSize(3);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce))
        .containsExactly(1L, 2L, 3L);
    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender(), 2))
        .map(PendingTransaction::getTransaction)
        .containsExactly(readyTxs[2]);
  }

  @Test
  public void streamWithMoreReadyTransactionsWithGapForSender() {
    final var readyTxs = populateCache(4, 1, 3);
    assertThat(readyTxs).hasSize(2);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L, 2L);
    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender()))
        .map(PendingTransaction::getTransaction)
        .containsExactly(readyTxs);
  }

  @Test
  public void emptyStreamWhenUsingNonceWithMoreReadyTransactionsWithGapForSender() {
    final var readyTxs = populateCache(4, 1, 3);
    assertThat(readyTxs).hasSize(2);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L, 2L);
    assertThat(readyTransactionsCache.streamReadyTransactions(readyTxs[0].getSender(), 3))
        .isEmpty();
  }

  @Test
  public void noPromotableTransactionsWhenCacheIsEmpty() {
    final var confirmedTransaction = createTransaction(0);
    assertTransactionNotPresent(confirmedTransaction);
    readyTransactionsCache.removeConfirmedTransactions(
        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
    assertThat(readyTransactionsCache.getPromotableTransactions(1, this::alwaysPromote)).isEmpty();
  }

  @Test
  public void noPromotableTransactionsWhenNoConfirmedTransactionsAndCacheIsEmpty() {
    readyTransactionsCache.removeConfirmedTransactions(Map.of());
    assertThat(readyTransactionsCache.getPromotableTransactions(1, this::alwaysPromote)).isEmpty();
  }

  @Test
  public void noPromotedTransactionsWhenMaxPromotableIsZero() {
    final var readyTxs = populateCache(2, 1);
    assertThat(readyTxs).hasSize(2);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L, 2L);

    final var confirmedTransaction = readyTxs[0];

    readyTransactionsCache.removeConfirmedTransactions(
        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
    assertThat(readyTransactionsCache.getPromotableTransactions(0, this::alwaysPromote)).isEmpty();
  }

  @Test
  public void promoteSameSenderTransactionsOnConfirmedTransactions() {
    final var readyTxs = populateCache(2, 1);
    assertThat(readyTxs).hasSize(2);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(1L, 2L);

    final var confirmedTransaction = readyTxs[0];
    readyTransactionsCache.removeConfirmedTransactions(
        Map.of(confirmedTransaction.getSender(), Optional.of(1L)));
    assertThat(readyTransactionsCache.getPromotableTransactions(1, this::alwaysPromote))
        .map(PendingTransaction::getTransaction)
        .containsExactly(readyTxs[1]);
  }

  @Test
  public void promoteOtherSenderTransactionsOnConfirmedTransactions() {
    final var readyTxs = populateCache(1, KEYS1);
    assertThat(readyTxs).hasSize(1);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce)).containsExactly(0L);

    final var confirmedTransaction = createTransaction(0, KEYS2);
    assertNoTransactionsForSender(confirmedTransaction.getSender());
    readyTransactionsCache.removeConfirmedTransactions(
        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
    assertThat(readyTransactionsCache.getPromotableTransactions(1, this::alwaysPromote))
        .map(PendingTransaction::getTransaction)
        .containsExactly(readyTxs[0]);
  }

  @Test
  public void limitPromotedTransactionsOnConfirmedTransactions() {
    final var transaction0SenderA = createTransaction(0, Wei.of(100), KEYS1);
    final var transaction1SenderA = createTransaction(1, Wei.of(95), KEYS1);
    populateCache(transaction0SenderA, transaction1SenderA);
    assertSenderHasExactlyTransactions(transaction0SenderA, transaction1SenderA);

    final var transaction0SenderB = createTransaction(0, Wei.of(90), KEYS2);
    final var transaction1SenderB = createTransaction(1, Wei.of(100), KEYS2);
    populateCache(transaction0SenderB, transaction1SenderB);
    assertSenderHasExactlyTransactions(transaction0SenderB, transaction1SenderB);

    final var transaction0SenderC =
        createTransaction(1, Wei.of(80), SIGNATURE_ALGORITHM.get().generateKeyPair());
    populateCache(transaction0SenderC);
    assertSenderHasExactlyTransactions(transaction0SenderC);

    final var confirmedTransaction = transaction0SenderA;
    readyTransactionsCache.removeConfirmedTransactions(
        Map.of(confirmedTransaction.getSender(), Optional.of(0L)));
    assertThat(readyTransactionsCache.getPromotableTransactions(2, this::alwaysPromote))
        .map(PendingTransaction::getTransaction)
        .containsExactly(transaction1SenderA, transaction0SenderB);
  }

  @Test
  public void allTransactionBelowConfirmedNonceAreRemovedForSender() {
    final var readyTxs = populateCache(3, 0);
    assertThat(readyTxs).hasSize(3);
    assertThat(Arrays.stream(readyTxs).mapToLong(Transaction::getNonce))
        .containsExactly(0L, 1L, 2L);

    final var confirmedTransaction = readyTxs[1];
    readyTransactionsCache.removeConfirmedTransactions(
        Map.of(confirmedTransaction.getSender(), Optional.of(1L)));
    assertThat(readyTransactionsCache.getPromotableTransactions(2, this::alwaysPromote))
        .map(PendingTransaction::getTransaction)
        .containsExactly(readyTxs[2]);

    assertSenderHasExactlyTransactions(readyTxs[2]);
  }

  @Test
  public void postponeTransactionIfNotFitsInCache() {
    final var largeTransaction = createTransaction(0, CACHE_CAPACITY_BYTES);
    assertThat(readyTransactionsCache.add(createPendingTransaction(largeTransaction), 0))
        .isEqualTo(TX_POOL_FULL);
    assertTransactionNotPresent(largeTransaction);
  }

  @Test
  public void postponeTransactionWithHigherNonceFirstForSenderWhenCacheIsFull() {
    final var lowFeeTransaction = createTransaction(0, Wei.of(10), 100, KEYS1);
    final var largeTransaction = createTransaction(1, Wei.of(20), CACHE_CAPACITY_BYTES - 50, KEYS1);
    populateCache(lowFeeTransaction);
    // largeTransaction is postponed even if of higher fee than the first one, to avoid gaps
    assertThat(readyTransactionsCache.add(createPendingTransaction(largeTransaction), 0))
        .isEqualTo(TX_POOL_FULL);
    assertSenderHasExactlyTransactions(lowFeeTransaction);
  }

  @Test
  public void postponeLessFeeTransactionForOtherSendersWhenCacheIsFull() {
    final var lowFeeTransactionSenderA =
        createTransaction(0, Wei.of(10), 100, SIGNATURE_ALGORITHM.get().generateKeyPair());
    final var lowFeeTransactionSenderB =
        createTransaction(0, Wei.of(10), 100, SIGNATURE_ALGORITHM.get().generateKeyPair());
    final var largeHighFeeTransactionSenderC =
        createTransaction(
            0, Wei.of(20), CACHE_CAPACITY_BYTES - lowFeeTransactionSenderB.getSize() + 1, KEYS2);

    populateCache(lowFeeTransactionSenderA, lowFeeTransactionSenderB);

    // to make space for the large transaction both previous transactions are postponed
    assertThat(
            readyTransactionsCache.add(createPendingTransaction(largeHighFeeTransactionSenderC), 0))
        .isEqualTo(ADDED);

    assertNoTransactionsForSender(lowFeeTransactionSenderA.getSender());
    assertNoTransactionsForSender(lowFeeTransactionSenderB.getSender());
    assertSenderHasExactlyTransactions(largeHighFeeTransactionSenderC);
  }

  //  @Test
  //  void postponedToReadyWhenFillingNonceGap() {
  //    final var postponedTransaction = createTransaction(1);
  //    when(postponedTransactionsCache.promoteForSender(
  //            eq(postponedTransaction.getSender()), eq(0L), anyLong()))
  //        .thenReturn(
  //            CompletableFuture.completedFuture(
  //                List.of(createPendingTransaction(postponedTransaction))));
  //
  //    assertTransactionNotPresent(postponedTransaction);
  //
  //    final var previousTransaction = createTransaction(0);
  //    populateCache(previousTransaction);
  //    assertSenderHasExactlyTransactions(previousTransaction, postponedTransaction);
  //  }

  //  @Test
  //  void noPostponedToReadyWhenPostponedDoesNotFitInCache() {
  //    final var arrivesLateTransaction = createTransaction(0);
  //    final var postponedTransaction =
  //        createTransaction(
  //            1, Wei.of(10), CACHE_CAPACITY_BYTES - arrivesLateTransaction.getSize() + 1, KEYS1);
  //    when(postponedTransactionsCache.promoteForSender(
  //            eq(postponedTransaction.getSender()), eq(0L), anyLong()))
  //        .thenReturn(
  //            CompletableFuture.completedFuture(
  //                List.of(createPendingTransaction(postponedTransaction))));
  //
  //    assertTransactionNotPresent(postponedTransaction);
  //
  //    populateCache(arrivesLateTransaction);
  //    assertSenderHasExactlyTransactions(arrivesLateTransaction);
  //  }

  private Transaction[] populateCache(final int numTxs, final KeyPair keys) {
    return populateCache(numTxs, keys, 0, OptionalLong.empty());
  }

  private Transaction[] populateCache(final int numTxs, final long startingNonce) {
    return populateCache(numTxs, KEYS1, startingNonce, OptionalLong.empty());
  }

  private Transaction[] populateCache(
      final int numTxs, final long startingNonce, final long missingNonce) {
    return populateCache(numTxs, KEYS1, startingNonce, OptionalLong.of(missingNonce));
  }

  private Transaction[] populateCache(
      final int numTxs,
      final KeyPair keys,
      final long startingNonce,
      final OptionalLong maybeGapNonce) {
    final List<Transaction> addedTransactions = new ArrayList<>(numTxs);
    boolean afterGap = false;
    for (int i = 0; i < numTxs; i++) {
      final long nonce = startingNonce + i;
      if (maybeGapNonce.isPresent() && maybeGapNonce.getAsLong() == nonce) {
        afterGap = true;
      } else {
        final var transaction = createTransaction(nonce, keys);
        final var res =
            readyTransactionsCache.add(createPendingTransaction(transaction), startingNonce);
        if (afterGap) {
          assertThat(res).isEqualTo(ADDED_SPARSE);
          assertTransactionNotPresent(transaction);
        } else {
          assertThat(res).isEqualTo(ADDED);
          assertTransactionPresent(transaction);
          addedTransactions.add(transaction);
        }
      }
    }
    return addedTransactions.toArray(Transaction[]::new);
  }

  private void populateCache(final Transaction... transactions) {
    assertThat(
            Arrays.stream(transactions)
                .map(this::createPendingTransaction)
                .map(
                    pendingTransaction ->
                        readyTransactionsCache.add(pendingTransaction, transactions[0].getNonce()))
                .filter(ADDED::equals)
                .count())
        .isEqualTo(transactions.length);
  }

  private PendingTransaction createPendingTransaction(final Transaction transaction) {
    return new PendingTransaction.Remote(transaction, Instant.now());
  }

  private Transaction createTransaction(final long nonce) {
    return createTransaction(nonce, Wei.of(5000L), KEYS1);
  }

  private Transaction createTransaction(final long nonce, final KeyPair keys) {
    return createTransaction(nonce, Wei.of(5000L), keys);
  }

  private Transaction createTransaction(final long nonce, final Wei maxGasPrice) {
    return createTransaction(nonce, maxGasPrice, KEYS1);
  }

  private Transaction createTransaction(final long nonce, final int payloadSize) {
    return createTransaction(nonce, Wei.of(5000L), payloadSize, KEYS1);
  }

  private Transaction createTransaction(
      final long nonce, final Wei maxGasPrice, final KeyPair keys) {
    return createTransaction(nonce, maxGasPrice, 0, keys);
  }

  private Transaction createTransaction(
      final long nonce, final Wei maxGasPrice, final int payloadSize, final KeyPair keys) {

    return createTransaction(
        randomizeTxType.nextBoolean() ? TransactionType.EIP1559 : TransactionType.FRONTIER,
        nonce,
        maxGasPrice,
        payloadSize,
        keys);
  }

  private Transaction createTransaction(
      final TransactionType type,
      final long nonce,
      final Wei maxGasPrice,
      final int payloadSize,
      final KeyPair keys) {

    var payloadBytes = Bytes.repeat((byte) 1, payloadSize);
    var tx =
        new TransactionTestFixture()
            .to(Optional.of(Address.fromHexString("0x634316eA0EE79c701c6F67C53A4C54cBAfd2316d")))
            .value(Wei.of(nonce))
            .nonce(nonce)
            .type(type)
            .payload(payloadBytes);
    if (type.supports1559FeeMarket()) {
      tx.maxFeePerGas(Optional.of(maxGasPrice))
          .maxPriorityFeePerGas(Optional.of(maxGasPrice.divide(10)));
    } else {
      tx.gasPrice(maxGasPrice);
    }
    return tx.createTransaction(keys);
  }

  private Transaction createTransactionReplacement(
      final Transaction originalTransaction, final KeyPair keys) {
    return createTransaction(
        originalTransaction.getType(),
        originalTransaction.getNonce(),
        originalTransaction.getMaxGasFee().multiply(2),
        originalTransaction.getPayload().size(),
        keys);
  }

  private void assertTransactionPresent(final Transaction transaction) {
    assertThat(readyTransactionsCache.get(transaction.getSender(), transaction.getNonce()))
        .isPresent()
        .map(PendingTransaction::getHash)
        .hasValue(transaction.getHash());
  }

  private void assertTransactionNotPresent(final Transaction transaction) {
    final var maybeTransaction =
        readyTransactionsCache.get(transaction.getSender(), transaction.getNonce());
    if (!maybeTransaction.isEmpty()) {
      assertThat(maybeTransaction)
          .isPresent()
          .map(PendingTransaction::getHash)
          .isNotEqualTo(transaction.getHash());
    }
  }

  private void assertNoTransactionsForSender(final Address sender) {
    assertThat(readyTransactionsCache.streamReadyTransactions(sender)).isEmpty();
  }

  private void assertSenderHasExactlyTransactions(final Transaction... transactions) {
    assertThat(readyTransactionsCache.streamReadyTransactions(transactions[0].getSender()))
        .map(PendingTransaction::getTransaction)
        .containsExactly(transactions);
  }

  private boolean alwaysPromote(final PendingTransaction pendingTransaction) {
    return true;
  }
}

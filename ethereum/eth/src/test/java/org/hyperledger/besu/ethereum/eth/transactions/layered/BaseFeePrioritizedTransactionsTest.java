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

public class BaseFeePrioritizedTransactionsTest extends AbstractPrioritizedTransactionsTestBase {
  //
  //  private static final Random randomizeTxType = new Random();
  //
  //  @Override
  //  AbstractPrioritizedTransactions getSorter(
  //      final TransactionPoolConfiguration poolConfig,
  //      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
  //          transactionReplacementTester) {
  //
  //    return new BaseFeePrioritizedTransactions(
  //        poolConfig,
  //        clock.orElse(TestClock.system(ZoneId.systemDefault())),
  //        this::mockBlockHeader,
  //        transactionReplacementTester,
  //        FeeMarket.london(0L));
  //  }
  //
  //  @Override
  //  protected BlockHeader mockBlockHeader() {
  //    final BlockHeader blockHeader = mock(BlockHeader.class);
  //    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ONE));
  //    return blockHeader;
  //  }
  //
  //  @Override
  //  protected Transaction createTransaction(
  //      final long nonce, final Wei maxGasPrice, final KeyPair keys) {
  //
  //    return createTransaction(
  //        randomizeTxType.nextBoolean() ? TransactionType.EIP1559 : TransactionType.FRONTIER,
  //        nonce,
  //        maxGasPrice,
  //        keys);
  //  }
  //
  //  protected Transaction createTransaction(
  //      final TransactionType type, final long nonce, final Wei maxGasPrice, final KeyPair keys) {
  //
  //    var tx = new TransactionTestFixture().value(Wei.of(nonce)).nonce(nonce).type(type);
  //    if (type.supports1559FeeMarket()) {
  //      tx.maxFeePerGas(Optional.of(maxGasPrice))
  //          .maxPriorityFeePerGas(Optional.of(maxGasPrice.divide(10)));
  //    } else {
  //      tx.gasPrice(maxGasPrice);
  //    }
  //    return tx.createTransaction(keys);
  //  }
  //
  //  @Override
  //  protected Transaction createTransactionReplacement(
  //      final Transaction originalTransaction, final KeyPair keys) {
  //    return createTransaction(
  //        originalTransaction.getType(),
  //        originalTransaction.getNonce(),
  //        originalTransaction.getMaxGasPrice().multiply(2),
  //        keys);
  //  }
  //
  //  @Test
  //  public void shouldPrioritizePriorityFeeThenTimeAddedToPoolOnlyEIP1559Txs() {
  //    shouldPrioritizePriorityFeeThenTimeAddedToPoolSameTypeTxs(TransactionType.EIP1559);
  //  }
  //
  //  @Test
  //  public void shouldPrioritizeGasPriceThenTimeAddedToPoolOnlyFrontierTxs() {
  //    shouldPrioritizePriorityFeeThenTimeAddedToPoolSameTypeTxs(TransactionType.FRONTIER);
  //  }
  //
  //  @Test
  //  public void shouldPrioritizeEffectivePriorityFeeThenTimeAddedToPoolOnMixedTypes() {
  //    final var nextBlockBaseFee = Optional.of(Wei.ONE);
  //
  //    final PendingTransaction highGasPriceTransaction =
  //        createRemotePendingTransaction(createTransaction(0, Wei.of(100), KEYS1));
  //
  //    final List<PendingTransaction> lowValueTxs =
  //        IntStream.range(0, MAX_TRANSACTIONS)
  //            .mapToObj(
  //                i ->
  //                    new PendingTransaction.Remote(
  //                        createTransaction(
  //                            0, Wei.of(10), SIGNATURE_ALGORITHM.get().generateKeyPair()),
  //                        System.currentTimeMillis()))
  //            .collect(Collectors.toUnmodifiableList());
  //
  //    final var lowestPriorityFee =
  //        lowValueTxs.stream()
  //            .sorted(
  //                Comparator.comparing(
  //                    pt -> pt.getTransaction().getEffectivePriorityFeePerGas(nextBlockBaseFee)))
  //            .findFirst()
  //            .get()
  //            .getTransaction()
  //            .getEffectivePriorityFeePerGas(nextBlockBaseFee);
  //
  //    final var firstLowValueTx =
  //        lowValueTxs.stream()
  //            .filter(
  //                pt ->
  //                    pt.getTransaction()
  //                        .getEffectivePriorityFeePerGas(nextBlockBaseFee)
  //                        .equals(lowestPriorityFee))
  //            .findFirst()
  //            .get();
  //
  //    shouldPrioritizeValueThenTimeAddedToPool(
  //        lowValueTxs.iterator(), highGasPriceTransaction, firstLowValueTx);
  //  }
  //
  //  private void shouldPrioritizePriorityFeeThenTimeAddedToPoolSameTypeTxs(
  //      final TransactionType transactionType) {
  //    final PendingTransaction highGasPriceTransaction =
  //        createRemotePendingTransaction(createTransaction(0, Wei.of(100), KEYS1));
  //
  //    final var lowValueTxs =
  //        IntStream.range(0, MAX_TRANSACTIONS)
  //            .mapToObj(
  //                i ->
  //                    createRemotePendingTransaction(
  //                        createTransaction(
  //                            transactionType,
  //                            0,
  //                            Wei.of(10),
  //                            0,
  //                            SIGNATURE_ALGORITHM.get().generateKeyPair())))
  //            .collect(Collectors.toUnmodifiableList());
  //
  //    shouldPrioritizeValueThenTimeAddedToPool(
  //        lowValueTxs.iterator(), highGasPriceTransaction, lowValueTxs.get(0));
  //  }
}

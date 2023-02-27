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

public class GasPricePrioritizedTransactionsTest extends AbstractPrioritizedTransactionsTestBase {
  //
  //  @Override
  //  AbstractPrioritizedTransactions getSorter(
  //      final TransactionPoolConfiguration poolConfig,
  //      final Optional<Clock> clock,
  //      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
  //          transactionReplacementTester) {
  //
  //    return new GasPricePrioritizedTransactions(
  //        poolConfig,
  //        clock.orElse(TestClock.system(ZoneId.systemDefault())),
  //        transactionReplacementTester);
  //  }
  //
  //  @Override
  //  protected BlockHeader mockBlockHeader() {
  //    final BlockHeader blockHeader = mock(BlockHeader.class);
  //    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
  //    return blockHeader;
  //  }
  //
  //  @Override
  //  protected Transaction createTransaction(
  //      final long transactionNumber, final Wei maxGasPrice, final KeyPair keys) {
  //    return new TransactionTestFixture()
  //        .value(Wei.of(transactionNumber))
  //        .nonce(transactionNumber)
  //        .gasPrice(maxGasPrice)
  //        .createTransaction(keys);
  //  }
  //
  //  @Override
  //  protected Transaction createTransactionReplacement(
  //      final Transaction originalTransaction, final KeyPair keys) {
  //    return createTransaction(
  //        originalTransaction.getNonce(), originalTransaction.getMaxGasPrice().multiply(2), keys);
  //  }
  //
  //  @Test
  //  public void shouldPrioritizeGasPriceThenTimeAddedToPool() {
  //    final List<PendingTransaction> lowValueTxs =
  //        IntStream.range(0, MAX_TRANSACTIONS)
  //            .mapToObj(
  //                i ->
  //                    createRemotePendingTransaction(
  //                        createTransaction(
  //                            0, Wei.of(10), SIGNATURE_ALGORITHM.get().generateKeyPair())))
  //            .collect(Collectors.toUnmodifiableList());
  //
  //    final PendingTransaction highGasPriceTransaction =
  //        createRemotePendingTransaction(createTransaction(0, Wei.of(100), KEYS1));
  //
  //    shouldPrioritizeValueThenTimeAddedToPool(
  //        lowValueTxs.iterator(), highGasPriceTransaction, lowValueTxs.get(0));
  //  }
}

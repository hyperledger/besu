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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.testutil.TestClock;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

public class GasPricePendingTransactionsTest extends AbstractPendingTransactionsTestBase {

  @Override
  AbstractPendingTransactionsSorter getSorter(
      final TransactionPoolConfiguration poolConfig, final Optional<Clock> clock) {
    return new BaseFeePendingTransactionsSorter(
        poolConfig,
        clock.orElse(TestClock.system(ZoneId.systemDefault())),
        metricsSystem,
        AbstractPendingTransactionsTestBase::mockBlockHeader);
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

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
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.testutil.TestClock;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

public class BaseFeePendingTransactionsTest extends AbstractPendingTransactionsTestBase {

  @Override
  AbstractPendingTransactionsSorter getSorter(
      final TransactionPoolConfiguration poolConfig, final Optional<Clock> clock) {
    return new BaseFeePendingTransactionsSorter(
        poolConfig,
        clock.orElse(TestClock.system(ZoneId.systemDefault())),
        metricsSystem,
        AbstractPendingTransactionsTestBase::mockBlockHeader);
  }

  private static final Random randomizeTxType = new Random();

  @Override
  protected Transaction createTransaction(final long transactionNumber) {
    var tx = new TransactionTestFixture().value(Wei.of(transactionNumber)).nonce(transactionNumber);
    if (randomizeTxType.nextBoolean()) {
      tx.type(TransactionType.EIP1559)
          .maxFeePerGas(Optional.of(Wei.of(5000L)))
          .maxPriorityFeePerGas(Optional.of(Wei.of(50L)));
    }
    return tx.createTransaction(KEYS1);
  }

  @Test
  public void shouldEvictHighestNonceForSenderOfTheOldestTransactionFirst() {
    final Account firstSender = mock(Account.class);
    when(firstSender.getNonce()).thenReturn(0L);

    final KeyPair firstSenderKeys = SIGNATURE_ALGORITHM.get().generateKeyPair();
    // first sender sends 2 txs
    final Transaction oldestTx = transactionWithNonceSenderAndGasPrice(1, firstSenderKeys, 9);
    final Transaction penultimateTx = transactionWithNonceSenderAndGasPrice(2, firstSenderKeys, 11);
    transactions.addRemoteTransaction(oldestTx, Optional.of(firstSender));
    transactions.addRemoteTransaction(penultimateTx, Optional.of(firstSender));

    final List<Transaction> lowGasPriceTransactions =
        IntStream.range(0, MAX_TRANSACTIONS - 2)
            .mapToObj(
                i ->
                    transactionWithNonceSenderAndGasPrice(
                        i + 1, SIGNATURE_ALGORITHM.get().generateKeyPair(), 10))
            .collect(Collectors.toUnmodifiableList());

    // Fill the pool with transactions from random senders
    lowGasPriceTransactions.forEach(tx -> transactions.addRemoteTransaction(tx, Optional.empty()));

    // This should kick the tx with the highest nonce for the sender of the oldest tx, that is
    // the penultimate tx
    final Transaction highGasPriceTransaction =
        transactionWithNonceSenderAndGasPrice(1, KEYS1, 100);
    transactions.addRemoteTransaction(highGasPriceTransaction, Optional.empty());
    assertThat(transactions.size()).isEqualTo(MAX_TRANSACTIONS);
    assertTransactionNotPending(penultimateTx);
    assertTransactionPending(oldestTx);
    IntStream.range(0, MAX_TRANSACTIONS - 2)
        .forEach(i -> assertTransactionPending(lowGasPriceTransactions.get(i)));
  }
}

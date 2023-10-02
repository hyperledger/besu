/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.stream.Stream;

public class AccountTransactionOrder {

  private static final Comparator<PendingTransaction> SORT_BY_NONCE =
      Comparator.comparing(PendingTransaction::getNonce);
  private final NavigableSet<PendingTransaction> transactionsForSender =
      new TreeSet<>(SORT_BY_NONCE);
  private final NavigableSet<PendingTransaction> deferredTransactions =
      new TreeSet<>(SORT_BY_NONCE);

  public AccountTransactionOrder(final Stream<PendingTransaction> senderTransactions) {
    senderTransactions.forEach(this.transactionsForSender::add);
  }

  /**
   * Determine the transactions from this sender that are able to be processed given that <code>
   * nextTransactionInPriorityOrder</code> has been reached in the normal priority order.
   *
   * <p>Transactions may be deferred from their place in normal priority order if the sender has
   * other transactions in the pool with lower nonces. Deferred transactions are delayed until the
   * transactions preceding them are reached.
   *
   * @param nextTransactionInPriorityOrder the next transaction to be processed in normal priority
   *     order. Must be from the sender this instance is ordering.
   * @return the transactions from this sender that are now due to be processed, in order.
   */
  public Iterable<PendingTransaction> transactionsToProcess(
      final PendingTransaction nextTransactionInPriorityOrder) {
    deferredTransactions.add(nextTransactionInPriorityOrder);
    final List<PendingTransaction> transactionsToApply = new ArrayList<>();
    while (!deferredTransactions.isEmpty()
        && !transactionsForSender.isEmpty()
        && deferredTransactions.first().equals(transactionsForSender.first())) {
      final PendingTransaction transaction = deferredTransactions.first();
      transactionsToApply.add(transaction);
      deferredTransactions.remove(transaction);
      transactionsForSender.remove(transaction);
    }
    return transactionsToApply;
  }
}

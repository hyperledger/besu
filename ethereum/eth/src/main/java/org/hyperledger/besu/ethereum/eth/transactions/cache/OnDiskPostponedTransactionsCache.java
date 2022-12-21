/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public class OnDiskPostponedTransactionsCache implements PostponedTransactionsCache {

  private final TransactionPoolConfiguration poolConfig;

  private final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;

  private final int maxPromotablePerSender;

  public OnDiskPostponedTransactionsCache(
      final TransactionPoolConfiguration poolConfig,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    this.poolConfig = poolConfig;
    this.maxPromotablePerSender = poolConfig.getTxPoolMaxFutureTransactionByAccount();
    this.transactionReplacementTester = transactionReplacementTester;
  }

  @Override
  public CompletableFuture<List<PendingTransaction>> promoteForSender(
      final Address sender, final long lastReadyNonce, final long maxSize) {
    // todo: concrete implementation
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public CompletableFuture<List<PendingTransaction>> promote(
      final int maxPromotable, final long maxSize) {
    // todo: concrete implementation
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public void add(final PendingTransaction pendingTransaction) {
    // todo: concrete implementation
  }

  @Override
  public void addAll(final List<PendingTransaction> evictedTransactions) {
    // todo: concrete implementation
  }

  @Override
  public void remove(final Transaction transaction) {
    // todo: concrete implementation
  }

  @Override
  public void removeForSenderBelowNonce(final Address sender, final long maxConfirmedNonce) {
    // todo: concrete implementation
  }
}

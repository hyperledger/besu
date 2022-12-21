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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class InMemoryPostponedTransactionsCache implements PostponedTransactionsCache {
  private final Map<Address, NavigableMap<Long, PendingTransaction>> postponedBySender =
      new ConcurrentHashMap<>();

  @Override
  public CompletableFuture<List<PendingTransaction>> promoteForSender(
      final Address sender, final long lastReadyNonce, final long maxSize) {
    final var senderTxs = postponedBySender.get(sender);
    if (senderTxs == null) {
      return CompletableFuture.completedFuture(List.of());
    }

    senderTxs.headMap(lastReadyNonce, true).clear();
    final List<PendingTransaction> promotedTxs = new ArrayList<>(senderTxs.size());

    long expectedNonce = lastReadyNonce + 1;
    long size = 0;

    for (final var ptEntry : senderTxs.entrySet()) {
      size += ptEntry.getValue().getTransaction().getSize();
      if (size > maxSize || ptEntry.getKey() != expectedNonce) {
        break;
      }
      promotedTxs.add(senderTxs.pollFirstEntry().getValue());
      expectedNonce++;
    }

    return CompletableFuture.completedFuture(promotedTxs);
  }

  private long internalPromote(
      final NavigableMap<Long, PendingTransaction> candidateTxs,
      final long maxSize,
      final List<PendingTransaction> promotedTxs) {
    long size = 0;
    for (final var pt : candidateTxs.entrySet()) {
      final var txSize = pt.getValue().getTransaction().getSize();
      if (size + txSize > maxSize) {
        break;
      }
      promotedTxs.add(candidateTxs.pollFirstEntry().getValue());
      size += txSize;
    }
    return size;
  }

  @Override
  public CompletableFuture<List<PendingTransaction>> promote(
      final int maxPromotable, final long maxSize) {
    return CompletableFuture.completedFuture(List.of());
  }

  @Override
  public void add(final PendingTransaction pendingTransaction) {
    postponedBySender.compute(
        pendingTransaction.getSender(),
        (sender, txs) -> {
          var updatedTxs = txs == null ? new TreeMap<Long, PendingTransaction>() : txs;
          updatedTxs.put(pendingTransaction.getNonce(), pendingTransaction);
          return updatedTxs;
        });
  }

  @Override
  public void addAll(final List<PendingTransaction> evictedTransactions) {
    evictedTransactions.forEach(this::add);
  }

  @Override
  public void remove(final Transaction transaction) {
    postponedBySender.computeIfPresent(
        transaction.getSender(),
        (sender, txs) -> {
          txs.remove(transaction.getNonce());
          return txs;
        });
  }

  @Override
  public void removeForSenderBelowNonce(final Address sender, final long maxConfirmedNonce) {
    postponedBySender.computeIfPresent(
        sender,
        (_unused, txs) -> {
          txs.tailMap(maxConfirmedNonce, true).clear();
          return txs;
        });
  }
}

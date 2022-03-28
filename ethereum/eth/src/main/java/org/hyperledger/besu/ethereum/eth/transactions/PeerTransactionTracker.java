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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Collections.emptySet;
import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PeerTransactionTracker implements EthPeer.DisconnectCallback {
  private static final int MAX_TRACKED_SEEN_TRANSACTIONS = 100_000;
  private final Map<EthPeer, Set<Hash>> seenTransactions = new ConcurrentHashMap<>();
  private final Map<EthPeer, Set<Transaction>> transactionsToSend = new ConcurrentHashMap<>();

  public synchronized void markTransactionsAsSeen(
      final EthPeer peer, final Collection<Transaction> transactions) {
    markTransactionHashesAsSeen(peer, toHashList(transactions));
  }

  public synchronized void markTransactionHashesAsSeen(
      final EthPeer peer, final Collection<Hash> txHashes) {
    final Set<Hash> seenTransactionsForPeer = getOrCreateSeenTransactionsForPeer(peer);
    seenTransactionsForPeer.addAll(txHashes);
  }

  public synchronized void addToPeerSendQueue(final EthPeer peer, final Transaction transaction) {
    if (!hasPeerSeenTransaction(peer, transaction)) {
      transactionsToSend.computeIfAbsent(peer, key -> createTransactionsSet()).add(transaction);
    }
  }

  public Iterable<EthPeer> getEthPeersWithUnsentTransactions() {
    return transactionsToSend.keySet();
  }

  public synchronized Set<Transaction> claimTransactionsToSendToPeer(final EthPeer peer) {
    final Set<Transaction> transactionsToSend = this.transactionsToSend.remove(peer);
    if (transactionsToSend != null) {
      markTransactionsAsSeen(peer, transactionsToSend);
      return transactionsToSend;
    } else {
      return emptySet();
    }
  }

  public boolean hasSeenTransaction(final Hash txHash) {
    return seenTransactions.values().stream().anyMatch(seen -> seen.contains(txHash));
  }

  private Set<Hash> getOrCreateSeenTransactionsForPeer(final EthPeer peer) {
    return seenTransactions.computeIfAbsent(peer, key -> createTransactionsSet());
  }

  boolean hasPeerSeenTransaction(final EthPeer peer, final Transaction transaction) {
    return hasPeerSeenTransaction(peer, transaction.getHash());
  }

  boolean hasPeerSeenTransaction(final EthPeer peer, final Hash txHash) {
    final Set<Hash> seenTransactionsForPeer = seenTransactions.get(peer);
    return seenTransactionsForPeer != null && seenTransactionsForPeer.contains(txHash);
  }

  private <T> Set<T> createTransactionsSet() {
    return Collections.newSetFromMap(
        new LinkedHashMap<T, Boolean>(1 << 4, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(final Map.Entry<T, Boolean> eldest) {
            return size() > MAX_TRACKED_SEEN_TRANSACTIONS;
          }
        });
  }

  @Override
  public void onDisconnect(final EthPeer peer) {
    seenTransactions.remove(peer);
    transactionsToSend.remove(peer);
  }
}

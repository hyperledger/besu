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

import java.util.Arrays;
import java.util.List;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PeerPendingTransactionTracker implements EthPeer.DisconnectCallback {
  private static final int MAX_TRACKED_SEEN_TRANSACTIONS = 100_000;
  private static final List<Capability> REQUIRED_PROTOCOLS =
      Arrays.asList(EthProtocol.ETH66, EthProtocol.ETH65);
  private final Map<EthPeer, Set<Hash>> seenTransactions = new ConcurrentHashMap<>();
  private final Map<EthPeer, Set<Hash>> transactionHashsToSend = new ConcurrentHashMap<>();
  private final AbstractPendingTransactionsSorter pendingTransactions;

  public PeerPendingTransactionTracker(
      final AbstractPendingTransactionsSorter pendingTransactions) {
    this.pendingTransactions = pendingTransactions;
  }

  public synchronized void markTransactionsHashesAsSeen(
      final EthPeer peer, final Collection<Hash> txHashes) {
    final Set<Hash> seenTransactionsForPeer = getOrCreateSeenTransactionsForPeer(peer);
    seenTransactionsForPeer.addAll(txHashes);
  }

  public synchronized void addToPeerSendQueue(final EthPeer peer, final Hash hash) {
    if (!hasPeerSeenTransaction(peer, hash)) {
      transactionHashsToSend.computeIfAbsent(peer, key -> createTransactionsSet()).add(hash);
    }
  }

  public Iterable<EthPeer> getEthPeersWithUnsentTransactionHashes() {
    return transactionHashsToSend.keySet();
  }

  public synchronized Set<Hash> claimTransactionHashesToSendToPeer(final EthPeer peer) {
    final Set<Hash> transactionHashesToSend = this.transactionHashsToSend.remove(peer);
    if (transactionHashesToSend != null) {
      markTransactionsHashesAsSeen(
          peer,
          transactionHashesToSend.stream()
              .filter(h -> pendingTransactions.getTransactionByHash(h).isPresent())
              .collect(Collectors.toSet()));
      return transactionHashesToSend;
    } else {
      return emptySet();
    }
  }

  public boolean isPeerSupported(final EthPeer peer) {
    return REQUIRED_PROTOCOLS.stream().anyMatch(peer.getAgreedCapabilities()::contains);
  }

  private Set<Hash> getOrCreateSeenTransactionsForPeer(final EthPeer peer) {
    return seenTransactions.computeIfAbsent(peer, key -> createTransactionsSet());
  }

  private boolean hasPeerSeenTransaction(final EthPeer peer, final Hash hash) {
    final Set<Hash> seenTransactionsForPeer = seenTransactions.get(peer);
    return seenTransactionsForPeer != null && seenTransactionsForPeer.contains(hash);
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
    transactionHashsToSend.remove(peer);
  }
}

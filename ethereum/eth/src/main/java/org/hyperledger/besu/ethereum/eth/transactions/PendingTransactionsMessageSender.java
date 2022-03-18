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

import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;

class PendingTransactionsMessageSender {

  private final PeerTransactionTracker transactionTracker;

  public PendingTransactionsMessageSender(
      final PeerTransactionTracker transactionTracker) {
    this.transactionTracker = transactionTracker;
  }

//  public void sendTransactionHashesToPeers() {
//    StreamSupport.stream(
//          transactionTracker.getEthPeersWithUnsentTransactions().spliterator(), true)
//        .parallel()
//        .forEach(this::sentTransactionHashesToPeer);
//  }

  public void sendTransactionHashesToPeer(final EthPeer peer) {
    for (final List<Transaction> txBatch :
        Iterables.partition(
            transactionTracker.claimTransactionsToSendToPeer(peer),
            TransactionPoolConfiguration.MAX_PENDING_TRANSACTIONS_HASHES)) {
      try {
        peer.send(NewPooledTransactionHashesMessage.create(toHashes(txBatch)));
      } catch (final PeerNotConnected __) {
        break;
      }
    }
  }

  private List<Hash> toHashes(Collection<Transaction> transactions) {
    return transactions.stream().map(Transaction::getHash).collect(Collectors.toList());
  }
}

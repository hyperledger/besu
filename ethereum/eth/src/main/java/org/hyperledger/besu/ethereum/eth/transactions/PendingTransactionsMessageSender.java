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

import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;

import java.util.stream.StreamSupport;

import com.google.common.collect.Iterables;

class PendingTransactionsMessageSender {

  private final PeerPendingTransactionTracker transactionTracker;

  public PendingTransactionsMessageSender(final PeerPendingTransactionTracker transactionTracker) {
    this.transactionTracker = transactionTracker;
  }

  public void sendTransactionsToPeers() {
    StreamSupport.stream(transactionTracker.getEthPeersWithUnsentTransactions().spliterator(), true)
        .parallel()
        .forEach(this::sendTransactionsToPeer);
  }

  private void sendTransactionsToPeer(final EthPeer peer) {
    Iterables.partition(
            transactionTracker.claimTransactionsToSendToPeer(peer),
            4096 // implementation determined limit for how many hashes to send at once
            )
        .forEach(
            hashes -> {
              try {
                peer.send(NewPooledTransactionHashesMessage.create(hashes));
              } catch (final PeerNotConnected __) {
                // if the peer isn't connected anymore, don't do anything
              }
            });
  }
}

/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.transactions;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.messages.LimitedTransactionsMessages;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;

import java.util.Set;
import java.util.stream.StreamSupport;

class TransactionsMessageSender {

  private final PeerTransactionTracker transactionTracker;

  public TransactionsMessageSender(final PeerTransactionTracker transactionTracker) {
    this.transactionTracker = transactionTracker;
  }

  public void sendTransactionsToPeers() {
    StreamSupport.stream(transactionTracker.getEthPeersWithUnsentTransactions().spliterator(), true)
        .parallel()
        .forEach(this::sendTransactionsToPeer);
  }

  private void sendTransactionsToPeer(final EthPeer peer) {
    final Set<Transaction> allTxToSend = transactionTracker.claimTransactionsToSendToPeer(peer);
    while (!allTxToSend.isEmpty()) {
      final LimitedTransactionsMessages limitedTransactionsMessages =
          LimitedTransactionsMessages.createLimited(allTxToSend);
      allTxToSend.removeAll(limitedTransactionsMessages.getIncludedTransactions());
      try {
        peer.send(limitedTransactionsMessages.getTransactionsMessage());
      } catch (final PeerNotConnected e) {
        return;
      }
    }
  }
}

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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.LimitedNewPooledTransactionHashesMessages;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Set;
import java.util.stream.StreamSupport;

class PendingTransactionsMessageSender {

  private final PeerPendingTransactionTracker transactionTracker;
  private final Counter newPooledTransactionsMessageSendCounter;

  public PendingTransactionsMessageSender(
      final PeerPendingTransactionTracker transactionTracker, final MetricsSystem metricsSystem) {
    this.transactionTracker = transactionTracker;
    newPooledTransactionsMessageSendCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "new_pooled_transactions_message_sent",
            "Count of new pooled transactions message sent");
  }

  public void sendTransactionsToPeers() {
    StreamSupport.stream(transactionTracker.getEthPeersWithUnsentTransactions().spliterator(), true)
        .parallel()
        .forEach(this::sendTransactionsToPeer);
  }

  private void sendTransactionsToPeer(final EthPeer ethPeer) {
    final Set<Hash> allTxToSend = transactionTracker.claimTransactionsToSendToPeer(ethPeer);
    while (!allTxToSend.isEmpty()) {
      final LimitedNewPooledTransactionHashesMessages limitedTransactionsMessages =
          LimitedNewPooledTransactionHashesMessages.createLimited(allTxToSend);
      allTxToSend.removeAll(limitedTransactionsMessages.getIncludedTransactions());
      try {
        ethPeer.send(limitedTransactionsMessages.getTransactionsMessage());
        newPooledTransactionsMessageSendCounter.inc();
      } catch (final PeerNotConnected e) {
        return;
      }
    }
  }
}

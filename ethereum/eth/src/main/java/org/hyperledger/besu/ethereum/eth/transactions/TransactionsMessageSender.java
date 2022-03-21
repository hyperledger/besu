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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.LimitedTransactionsMessages;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TransactionsMessageSender {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionsMessageSender.class);

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
      final Set<Transaction> includedTransactions =
          limitedTransactionsMessages.getIncludedTransactions();
      traceLambda(
          LOG,
          "Sending transactions to peer {} all transactions count {}, "
              + "single message transactions {}, single message list {}",
          peer::toString,
          allTxToSend::size,
          includedTransactions::size,
          () -> toHashList(includedTransactions));
      allTxToSend.removeAll(limitedTransactionsMessages.getIncludedTransactions());
      try {
        peer.send(limitedTransactionsMessages.getTransactionsMessage());
      } catch (final PeerNotConnected e) {
        return;
      }
    }
  }

  private List<Hash> toHashList(final Collection<Transaction> txs) {
    return txs.stream().map(Transaction::getHash).collect(Collectors.toList());
  }
}

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

import static org.hyperledger.besu.ethereum.core.Transaction.toHashList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import java.util.List;

import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NewPooledTransactionHashesMessageSender {
  private static final Logger LOG =
      LoggerFactory.getLogger(NewPooledTransactionHashesMessageSender.class);
  private static final int MAX_TRANSACTIONS_HASHES = 4096;

  private final PeerTransactionTracker transactionTracker;

  public NewPooledTransactionHashesMessageSender(final PeerTransactionTracker transactionTracker) {
    this.transactionTracker = transactionTracker;
  }

  public void sendTransactionHashesToPeer(final EthPeer peer) {
    final Capability capability = peer.getConnection().capability(EthProtocol.NAME);
    for (final List<Transaction> txBatch :
        Iterables.partition(
            transactionTracker.claimTransactionHashesToSendToPeer(peer), MAX_TRANSACTIONS_HASHES)) {
      try {
        final List<Hash> txHashes = toHashList(txBatch);
        LOG.atTrace()
            .setMessage(
                "Sending transaction hashes to peer {}, transaction hashes count {}, list {}")
            .addArgument(peer)
            .addArgument(txHashes::size)
            .addArgument(txHashes)
            .log();

        final NewPooledTransactionHashesMessage message =
            NewPooledTransactionHashesMessage.create(txBatch, capability);
        peer.send(message);
      } catch (final PeerNotConnected unused) {
        break;
      }
    }
  }
}

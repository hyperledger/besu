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

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;

class PendingTransactionSender implements TransactionBatchAddedListener {

  private final PeerPendingTransactionTracker transactionTracker;
  private final PendingTransactionsMessageSender transactionsMessageSender;
  private final EthContext ethContext;

  public PendingTransactionSender(
      final PeerPendingTransactionTracker transactionTracker,
      final PendingTransactionsMessageSender transactionsMessageSender,
      final EthContext ethContext) {
    this.transactionTracker = transactionTracker;
    this.transactionsMessageSender = transactionsMessageSender;
    this.ethContext = ethContext;
  }

  @Override
  public void onTransactionsAdded(final Iterable<Transaction> transactions) {
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .filter(
            peer -> transactionTracker.isPeerSupported(peer, EthProtocol.ETH66, EthProtocol.ETH65))
        .forEach(
            peer ->
                transactions.forEach(
                    transaction ->
                        transactionTracker.addToPeerSendQueue(peer, transaction.getHash())));
    ethContext
        .getScheduler()
        .scheduleSyncWorkerTask(transactionsMessageSender::sendTransactionsToPeers);
  }
}

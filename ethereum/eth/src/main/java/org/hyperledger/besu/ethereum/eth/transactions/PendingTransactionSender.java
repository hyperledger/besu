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
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;

class PendingTransactionSender implements TransactionBatchAddedListener {

  private final PeerPendingTransactionTracker transactionHashTracker;
  private final PendingTransactionsMessageSender transactionHashesMessageSender;
  private final EthContext ethContext;

  public PendingTransactionSender(
      final PeerPendingTransactionTracker transactionHashTracker,
      final PendingTransactionsMessageSender transactionHashesMessageSender,
      final EthContext ethContext) {
    this.transactionHashTracker = transactionHashTracker;
    this.transactionHashesMessageSender = transactionHashesMessageSender;
    this.ethContext = ethContext;
  }

  @Override
  public void onTransactionsAdded(final Iterable<Transaction> transactions) {
    ethContext
        .getEthPeers()
        .streamAvailablePeers()
        .filter(peer -> transactionHashTracker.isPeerSupported(peer))
        .forEach(
            peer ->
                transactions.forEach(
                    transaction ->
                        transactionHashTracker.addToPeerSendQueue(peer, transaction.getHash())));
//    ethContext
//        .getScheduler()
//        .scheduleSyncWorkerTask(transactionHashesMessageSender::sendTransactionHashesToPeers);
  }
}

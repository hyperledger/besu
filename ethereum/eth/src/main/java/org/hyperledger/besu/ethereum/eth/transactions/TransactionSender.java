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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool.TransactionBatchAddedListener;

class TransactionSender implements TransactionBatchAddedListener {

  private final PeerTransactionTracker transactionTracker;
  private final TransactionsMessageSender transactionsMessageSender;
  private final EthContext ethContext;

  public TransactionSender(
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
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
        .forEach(
            peer ->
                transactions.forEach(
                    transaction -> transactionTracker.addToPeerSendQueue(peer, transaction)));
    ethContext
        .getScheduler()
        .scheduleSyncWorkerTask(transactionsMessageSender::sendTransactionsToPeers);
  }
}

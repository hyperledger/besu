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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.MetricsSystem;

import java.time.Clock;

public class TransactionPoolFactory {

  public static TransactionPool createTransactionPool(
      final ProtocolSchedule<?> protocolSchedule,
      final ProtocolContext<?> protocolContext,
      final EthContext ethContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final SyncState syncState,
      final Wei minTransactionGasPrice,
      final TransactionPoolConfiguration transactionPoolConfiguration) {

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            transactionPoolConfiguration.getPendingTxRetentionPeriod(),
            transactionPoolConfiguration.getTxPoolMaxSize(),
            clock,
            metricsSystem);

    final PeerTransactionTracker transactionTracker = new PeerTransactionTracker();
    final TransactionsMessageSender transactionsMessageSender =
        new TransactionsMessageSender(transactionTracker);

    final TransactionPool transactionPool =
        new TransactionPool(
            pendingTransactions,
            protocolSchedule,
            protocolContext,
            new TransactionSender(transactionTracker, transactionsMessageSender, ethContext),
            syncState,
            ethContext,
            transactionTracker,
            minTransactionGasPrice,
            metricsSystem);

    final TransactionsMessageHandler transactionsMessageHandler =
        new TransactionsMessageHandler(
            ethContext.getScheduler(),
            new TransactionsMessageProcessor(
                transactionTracker,
                transactionPool,
                metricsSystem.createCounter(
                    PantheonMetricCategory.TRANSACTION_POOL,
                    "transactions_messages_skipped_total",
                    "Total number of transactions messages skipped by the processor.")),
            transactionPoolConfiguration.getTxMessageKeepAliveSeconds());

    ethContext.getEthMessages().subscribe(EthPV62.TRANSACTIONS, transactionsMessageHandler);
    protocolContext.getBlockchain().observeBlockAdded(transactionPool);
    ethContext.getEthPeers().subscribeDisconnect(transactionTracker);
    return transactionPool;
  }
}

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

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Clock;
import java.util.Optional;

public class TransactionPoolFactory {

  public static TransactionPool createTransactionPool(
      final ProtocolSchedule<?> protocolSchedule,
      final ProtocolContext<?> protocolContext,
      final EthContext ethContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final SyncState syncState,
      final Wei minTransactionGasPrice,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final boolean eth65Enabled) {

    final PendingTransactions pendingTransactions =
        new PendingTransactions(
            transactionPoolConfiguration.getPendingTxRetentionPeriod(),
            transactionPoolConfiguration.getTxPoolMaxSize(),
            transactionPoolConfiguration.getPooledTransactionHashesSize(),
            clock,
            metricsSystem);

    final PeerTransactionTracker transactionTracker = new PeerTransactionTracker();
    final TransactionsMessageSender transactionsMessageSender =
        new TransactionsMessageSender(transactionTracker);

    final Optional<PeerPendingTransactionTracker> pendingTransactionTracker =
        eth65Enabled
            ? Optional.of(new PeerPendingTransactionTracker(pendingTransactions))
            : Optional.empty();
    final Optional<PendingTransactionsMessageSender> pendingTransactionsMessageSender =
        eth65Enabled
            ? Optional.of(new PendingTransactionsMessageSender(pendingTransactionTracker.get()))
            : Optional.empty();

    return createTransactionPool(
        protocolSchedule,
        protocolContext,
        ethContext,
        metricsSystem,
        syncState,
        minTransactionGasPrice,
        transactionPoolConfiguration,
        pendingTransactions,
        transactionTracker,
        transactionsMessageSender,
        pendingTransactionTracker,
        pendingTransactionsMessageSender,
        eth65Enabled);
  }

  static TransactionPool createTransactionPool(
      final ProtocolSchedule<?> protocolSchedule,
      final ProtocolContext<?> protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final SyncState syncState,
      final Wei minTransactionGasPrice,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final PendingTransactions pendingTransactions,
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final Optional<PeerPendingTransactionTracker> pendingTransactionTracker,
      final Optional<PendingTransactionsMessageSender> pendingTransactionsMessageSender,
      final boolean eth65Enabled) {
    checkState(
        eth65Enabled == pendingTransactionTracker.isPresent(),
        "Pending transaction tracker is present iff eth/65 is enabled.");
    checkState(
        eth65Enabled == pendingTransactionsMessageSender.isPresent(),
        "Pending transaction message sender is present iff eth/65 is enabled.");
    final TransactionPool transactionPool =
        new TransactionPool(
            pendingTransactions,
            protocolSchedule,
            protocolContext,
            new TransactionSender(transactionTracker, transactionsMessageSender, ethContext),
            eth65Enabled
                ? Optional.of(
                    new PendingTransactionSender(
                        pendingTransactionTracker.get(),
                        pendingTransactionsMessageSender.get(),
                        ethContext))
                : Optional.empty(),
            syncState,
            ethContext,
            transactionTracker,
            pendingTransactionTracker,
            minTransactionGasPrice,
            metricsSystem);
    final TransactionsMessageHandler transactionsMessageHandler =
        new TransactionsMessageHandler(
            ethContext.getScheduler(),
            new TransactionsMessageProcessor(
                transactionTracker,
                transactionPool,
                metricsSystem.createCounter(
                    BesuMetricCategory.TRANSACTION_POOL,
                    "transactions_messages_skipped_total",
                    "Total number of transactions messages skipped by the processor.")),
            transactionPoolConfiguration.getTxMessageKeepAliveSeconds());
    ethContext.getEthMessages().subscribe(EthPV62.TRANSACTIONS, transactionsMessageHandler);
    if (eth65Enabled) {
      final PendingTransactionsMessageHandler pooledTransactionsMessageHandler =
          new PendingTransactionsMessageHandler(
              ethContext.getScheduler(),
              new PendingTransactionsMessageProcessor(
                  pendingTransactionTracker.get(),
                  transactionPool,
                  metricsSystem.createCounter(
                      BesuMetricCategory.TRANSACTION_POOL,
                      "pending_transactions_messages_skipped_total",
                      "Total number of pending transactions messages skipped by the processor."),
                  ethContext,
                  metricsSystem),
              transactionPoolConfiguration.getTxMessageKeepAliveSeconds());
      ethContext
          .getEthMessages()
          .subscribe(EthPV65.NEW_POOLED_TRANSACTION_HASHES, pooledTransactionsMessageHandler);
      ethContext.getEthPeers().subscribeDisconnect(pendingTransactionTracker.get());
    }

    protocolContext.getBlockchain().observeBlockAdded(transactionPool);
    ethContext.getEthPeers().subscribeDisconnect(transactionTracker);
    return transactionPool;
  }
}

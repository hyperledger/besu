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

import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LAYERED;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.layered.AbstractPrioritizedTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.layered.BaseFeePrioritizedTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.layered.EndLayer;
import org.hyperledger.besu.ethereum.eth.transactions.layered.GasPricePrioritizedTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.layered.LayeredPendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.layered.ReadyTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.layered.SparseTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Clock;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionPoolFactory {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPoolFactory.class);

  public static TransactionPool createTransactionPool(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final SyncState syncState,
      final MiningParameters miningParameters,
      final TransactionPoolConfiguration transactionPoolConfiguration) {

    final TransactionPoolMetrics metrics = new TransactionPoolMetrics(metricsSystem);

    final PeerTransactionTracker transactionTracker = new PeerTransactionTracker();
    final TransactionsMessageSender transactionsMessageSender =
        new TransactionsMessageSender(transactionTracker);

    final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender =
        new NewPooledTransactionHashesMessageSender(transactionTracker);

    return createTransactionPool(
        protocolSchedule,
        protocolContext,
        ethContext,
        clock,
        metrics,
        syncState,
        miningParameters,
        transactionPoolConfiguration,
        transactionTracker,
        transactionsMessageSender,
        newPooledTransactionHashesMessageSender);
  }

  static TransactionPool createTransactionPool(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final Clock clock,
      final TransactionPoolMetrics metrics,
      final SyncState syncState,
      final MiningParameters miningParameters,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender) {

    final TransactionPool transactionPool =
        new TransactionPool(
            () ->
                createPendingTransactions(
                    protocolSchedule,
                    protocolContext,
                    clock,
                    metrics,
                    transactionPoolConfiguration),
            protocolSchedule,
            protocolContext,
            new TransactionBroadcaster(
                ethContext,
                transactionTracker,
                transactionsMessageSender,
                newPooledTransactionHashesMessageSender),
            ethContext,
            miningParameters,
            metrics,
            transactionPoolConfiguration);

    final TransactionsMessageHandler transactionsMessageHandler =
        new TransactionsMessageHandler(
            ethContext.getScheduler(),
            new TransactionsMessageProcessor(transactionTracker, transactionPool, metrics),
            transactionPoolConfiguration.getTxMessageKeepAliveSeconds());

    final NewPooledTransactionHashesMessageHandler pooledTransactionsMessageHandler =
        new NewPooledTransactionHashesMessageHandler(
            ethContext.getScheduler(),
            new NewPooledTransactionHashesMessageProcessor(
                transactionTracker,
                transactionPool,
                transactionPoolConfiguration,
                ethContext,
                metrics),
            transactionPoolConfiguration.getTxMessageKeepAliveSeconds());

    subscribeTransactionHandlers(
        protocolContext,
        ethContext,
        transactionTracker,
        transactionPool,
        transactionsMessageHandler,
        pooledTransactionsMessageHandler);

    if (syncState.isInitialSyncPhaseDone()) {
      LOG.info("Enabling transaction pool");
      pooledTransactionsMessageHandler.setEnabled();
      transactionsMessageHandler.setEnabled();
      transactionPool.setEnabled();
    } else {
      LOG.info("Transaction pool disabled while initial sync in progress");
    }

    syncState.subscribeCompletionReached(
        new BesuEvents.InitialSyncCompletionListener() {
          @Override
          public void onInitialSyncCompleted() {
            LOG.info("Enabling transaction handling following initial sync");
            transactionTracker.reset();
            transactionPool.setEnabled();
            transactionsMessageHandler.setEnabled();
            pooledTransactionsMessageHandler.setEnabled();
          }

          @Override
          public void onInitialSyncRestart() {
            LOG.info("Disabling transaction handling during re-sync");
            pooledTransactionsMessageHandler.setDisabled();
            transactionsMessageHandler.setDisabled();
            transactionPool.setDisabled();
          }
        });

    return transactionPool;
  }

  private static void subscribeTransactionHandlers(
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final PeerTransactionTracker transactionTracker,
      final TransactionPool transactionPool,
      final TransactionsMessageHandler transactionsMessageHandler,
      final NewPooledTransactionHashesMessageHandler pooledTransactionsMessageHandler) {
    ethContext.getEthPeers().subscribeDisconnect(transactionTracker);
    protocolContext.getBlockchain().observeBlockAdded(transactionPool);
    ethContext.getEthMessages().subscribe(EthPV62.TRANSACTIONS, transactionsMessageHandler);
    ethContext
        .getEthMessages()
        .subscribe(EthPV65.NEW_POOLED_TRANSACTION_HASHES, pooledTransactionsMessageHandler);
  }

  private static PendingTransactions createPendingTransactions(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final Clock clock,
      final TransactionPoolMetrics metrics,
      final TransactionPoolConfiguration transactionPoolConfiguration) {

    boolean isFeeMarketImplementBaseFee =
        protocolSchedule.anyMatch(
            scheduledSpec -> scheduledSpec.spec().getFeeMarket().implementsBaseFee());

    if (transactionPoolConfiguration.getTxPoolImplementation().equals(LAYERED)) {
      return createLayeredPendingTransactions(
          protocolSchedule,
          protocolContext,
          metrics,
          transactionPoolConfiguration,
          isFeeMarketImplementBaseFee);
    } else {
      return createPendingTransactionSorter(
          protocolContext,
          clock,
          metrics.getMetricsSystem(),
          transactionPoolConfiguration,
          isFeeMarketImplementBaseFee);
    }
  }

  private static AbstractPendingTransactionsSorter createPendingTransactionSorter(
      final ProtocolContext protocolContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final boolean isFeeMarketImplementBaseFee) {
    if (isFeeMarketImplementBaseFee) {
      return new BaseFeePendingTransactionsSorter(
          transactionPoolConfiguration,
          clock,
          metricsSystem,
          protocolContext.getBlockchain()::getChainHeadHeader);
    } else {
      return new GasPricePendingTransactionsSorter(
          transactionPoolConfiguration,
          clock,
          metricsSystem,
          protocolContext.getBlockchain()::getChainHeadHeader);
    }
  }

  private static PendingTransactions createLayeredPendingTransactions(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPoolMetrics metrics,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final boolean isFeeMarketImplementBaseFee) {

    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(transactionPoolConfiguration.getPriceBump());

    final BiFunction<PendingTransaction, PendingTransaction, Boolean> transactionReplacementTester =
        (t1, t2) ->
            transactionReplacementHandler.shouldReplace(
                t1, t2, protocolContext.getBlockchain().getChainHeadHeader());

    final EndLayer endLayer = new EndLayer(metrics);

    final SparseTransactions sparseTransactions =
        new SparseTransactions(
            transactionPoolConfiguration, endLayer, metrics, transactionReplacementTester);

    final ReadyTransactions readyTransactions =
        new ReadyTransactions(
            transactionPoolConfiguration,
            sparseTransactions,
            metrics,
            transactionReplacementTester);

    final AbstractPrioritizedTransactions pendingTransactionsSorter;
    if (isFeeMarketImplementBaseFee) {
      final BaseFeeMarket baseFeeMarket =
          (BaseFeeMarket)
              protocolSchedule
                  .getByBlockHeader(protocolContext.getBlockchain().getChainHeadHeader())
                  .getFeeMarket();

      pendingTransactionsSorter =
          new BaseFeePrioritizedTransactions(
              transactionPoolConfiguration,
              protocolContext.getBlockchain()::getChainHeadHeader,
              readyTransactions,
              metrics,
              transactionReplacementTester,
              baseFeeMarket);
    } else {
      pendingTransactionsSorter =
          new GasPricePrioritizedTransactions(
              transactionPoolConfiguration,
              readyTransactions,
              metrics,
              transactionReplacementTester);
    }

    return new LayeredPendingTransactions(transactionPoolConfiguration, pendingTransactionsSorter);
  }
}

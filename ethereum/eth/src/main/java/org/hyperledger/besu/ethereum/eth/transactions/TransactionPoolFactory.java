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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.messages.EthPV62;
import org.hyperledger.besu.ethereum.eth.messages.EthPV65;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Clock;
import java.util.function.Supplier;

public class TransactionPoolFactory {

  public static TransactionPool createTransactionPool(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<Boolean> shouldProcessTransactions,
      final MiningParameters miningParameters,
      final TransactionPoolConfiguration transactionPoolConfiguration) {

    final AbstractPendingTransactionsSorter pendingTransactions =
        createPendingTransactionsSorter(
            protocolSchedule, protocolContext, clock, metricsSystem, transactionPoolConfiguration);

    final PeerTransactionTracker transactionTracker = new PeerTransactionTracker();
    final TransactionsMessageSender transactionsMessageSender =
        new TransactionsMessageSender(transactionTracker);

    final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender =
        new NewPooledTransactionHashesMessageSender(transactionTracker);

    return createTransactionPool(
        protocolSchedule,
        protocolContext,
        ethContext,
        metricsSystem,
        shouldProcessTransactions,
        miningParameters,
        transactionPoolConfiguration,
        pendingTransactions,
        transactionTracker,
        transactionsMessageSender,
        newPooledTransactionHashesMessageSender);
  }

  static TransactionPool createTransactionPool(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final Supplier<Boolean> shouldProcessTransactions,
      final MiningParameters miningParameters,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final PeerTransactionTracker transactionTracker,
      final TransactionsMessageSender transactionsMessageSender,
      final NewPooledTransactionHashesMessageSender newPooledTransactionHashesMessageSender) {
    final TransactionPool transactionPool =
        new TransactionPool(
            pendingTransactions,
            protocolSchedule,
            protocolContext,
            new TransactionBroadcaster(
                ethContext,
                pendingTransactions,
                transactionTracker,
                transactionsMessageSender,
                newPooledTransactionHashesMessageSender),
            ethContext,
            miningParameters,
            metricsSystem,
            transactionPoolConfiguration);

    final TransactionsMessageHandler transactionsMessageHandler =
        new TransactionsMessageHandler(
            ethContext.getScheduler(),
            new TransactionsMessageProcessor(transactionTracker, transactionPool, metricsSystem),
            transactionPoolConfiguration.getTxMessageKeepAliveSeconds());
    ethContext.getEthMessages().subscribe(EthPV62.TRANSACTIONS, transactionsMessageHandler);
    final NewPooledTransactionHashesMessageHandler pooledTransactionsMessageHandler =
        new NewPooledTransactionHashesMessageHandler(
            ethContext.getScheduler(),
            new NewPooledTransactionHashesMessageProcessor(
                transactionTracker,
                transactionPool,
                transactionPoolConfiguration,
                ethContext,
                metricsSystem,
                shouldProcessTransactions),
            transactionPoolConfiguration.getTxMessageKeepAliveSeconds());
    ethContext
        .getEthMessages()
        .subscribe(EthPV65.NEW_POOLED_TRANSACTION_HASHES, pooledTransactionsMessageHandler);

    protocolContext.getBlockchain().observeBlockAdded(transactionPool);
    ethContext.getEthPeers().subscribeDisconnect(transactionTracker);
    return transactionPool;
  }

  private static AbstractPendingTransactionsSorter createPendingTransactionsSorter(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    boolean isFeeMarketImplementBaseFee =
        protocolSchedule
            .streamMilestoneBlocks()
            .map(protocolSchedule::getByBlockNumber)
            .map(ProtocolSpec::getFeeMarket)
            .anyMatch(FeeMarket::implementsBaseFee);
    if (isFeeMarketImplementBaseFee) {
      return new BaseFeePendingTransactionsSorter(
          transactionPoolConfiguration.getPendingTxRetentionPeriod(),
          transactionPoolConfiguration.getTxPoolMaxSize(),
          clock,
          metricsSystem,
          protocolContext.getBlockchain()::getChainHeadHeader,
          transactionPoolConfiguration.getPriceBump());
    } else {
      return new GasPricePendingTransactionsSorter(
          transactionPoolConfiguration.getPendingTxRetentionPeriod(),
          transactionPoolConfiguration.getTxPoolMaxSize(),
          clock,
          metricsSystem,
          protocolContext.getBlockchain()::getChainHeadHeader,
          transactionPoolConfiguration.getPriceBump());
    }
  }
}

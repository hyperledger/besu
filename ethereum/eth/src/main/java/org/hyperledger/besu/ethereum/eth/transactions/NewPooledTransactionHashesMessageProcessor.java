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

import static java.time.Instant.now;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.task.BufferedGetPooledTransactionsFromPeerFetcher;
import org.hyperledger.besu.ethereum.eth.messages.NewPooledTransactionHashesMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewPooledTransactionHashesMessageProcessor {

  private static final int SKIPPED_MESSAGES_LOGGING_THRESHOLD = 1000;

  private static final Logger LOG =
      LoggerFactory.getLogger(NewPooledTransactionHashesMessageProcessor.class);

  private final ConcurrentHashMap<EthPeer, BufferedGetPooledTransactionsFromPeerFetcher>
      scheduledTasks;

  private final PeerTransactionTracker transactionTracker;
  private final Counter totalSkippedNewPooledTransactionHashesMessageCounter;
  private final TransactionPool transactionPool;
  private final TransactionPoolConfiguration transactionPoolConfiguration;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final Supplier<Boolean> shouldProcessMessages;

  public NewPooledTransactionHashesMessageProcessor(
      final PeerTransactionTracker transactionTracker,
      final TransactionPool transactionPool,
      final TransactionPoolConfiguration transactionPoolConfiguration,
      final EthContext ethContext,
      final MetricsSystem metricsSystem,
      final Supplier<Boolean> shouldProcessMessages) {
    this.transactionTracker = transactionTracker;
    this.transactionPool = transactionPool;
    this.transactionPoolConfiguration = transactionPoolConfiguration;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.shouldProcessMessages = shouldProcessMessages;
    this.totalSkippedNewPooledTransactionHashesMessageCounter =
        new RunnableCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.TRANSACTION_POOL,
                "new_pooled_transaction_hashes_messages_skipped_total",
                "Total number of new pooled transaction hashes messages skipped by the processor."),
            () ->
                LOG.warn(
                    "{} expired new pooled transaction hashes messages have been skipped.",
                    SKIPPED_MESSAGES_LOGGING_THRESHOLD),
            SKIPPED_MESSAGES_LOGGING_THRESHOLD);
    this.scheduledTasks = new ConcurrentHashMap<>();
  }

  void processNewPooledTransactionHashesMessage(
      final EthPeer peer,
      final NewPooledTransactionHashesMessage transactionsMessage,
      final Instant startedAt,
      final Duration keepAlive) {
    // Check if message is not expired.
    if (startedAt.plus(keepAlive).isAfter(now())) {
      this.processNewPooledTransactionHashesMessage(peer, transactionsMessage);
    } else {
      totalSkippedNewPooledTransactionHashesMessageCounter.inc();
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  private void processNewPooledTransactionHashesMessage(
      final EthPeer peer, final NewPooledTransactionHashesMessage transactionsMessage) {
    try {
      final List<Hash> incomingTransactionHashes = transactionsMessage.pendingTransactions();

      traceLambda(
          LOG,
          "Received pooled transaction hashes message from {}, incoming hashes {}, incoming list {}",
          peer::toString,
          incomingTransactionHashes::size,
          incomingTransactionHashes::toString);

      if (shouldProcessMessages.get()) {
        final BufferedGetPooledTransactionsFromPeerFetcher bufferedTask =
            scheduledTasks.computeIfAbsent(
                peer,
                ethPeer -> {
                  ethContext
                      .getScheduler()
                      .scheduleFutureTask(
                          new FetcherCreatorTask(peer),
                          transactionPoolConfiguration.getEth65TrxAnnouncedBufferingPeriod());

                  return new BufferedGetPooledTransactionsFromPeerFetcher(
                      ethContext, peer, transactionPool, transactionTracker, metricsSystem);
                });

        bufferedTask.addHashes(
            incomingTransactionHashes.stream()
                .filter(hash -> transactionPool.getTransactionByHash(hash).isEmpty())
                .collect(Collectors.toList()));
      }
    } catch (final RLPException ex) {
      if (peer != null) {
        LOG.debug(
            "Malformed pooled transaction hashes message received, disconnecting: {}", peer, ex);
        peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      }
    }
  }

  public class FetcherCreatorTask implements Runnable {
    final EthPeer peer;

    public FetcherCreatorTask(final EthPeer peer) {
      this.peer = peer;
    }

    @Override
    public void run() {
      if (peer != null) {
        final BufferedGetPooledTransactionsFromPeerFetcher fetcher = scheduledTasks.remove(peer);
        if (!peer.isDisconnected()) {
          fetcher.requestTransactions();
        }
      }
    }
  }
}

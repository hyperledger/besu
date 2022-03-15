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

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.TransactionsMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TransactionsMessageProcessor {

  private static final int SKIPPED_MESSAGES_LOGGING_THRESHOLD = 1000;
  private static final Logger LOG = LoggerFactory.getLogger(TransactionsMessageProcessor.class);
  private final PeerTransactionTracker transactionTracker;
  private final TransactionPool transactionPool;
  private final Counter totalSkippedTransactionsMessageCounter;

  public TransactionsMessageProcessor(
      final PeerTransactionTracker transactionTracker,
      final TransactionPool transactionPool,
      final Counter metricsCounter) {
    this.transactionTracker = transactionTracker;
    this.transactionPool = transactionPool;
    this.totalSkippedTransactionsMessageCounter =
        new RunnableCounter(
            metricsCounter,
            () ->
                LOG.warn(
                    "{} expired transaction messages have been skipped.",
                    SKIPPED_MESSAGES_LOGGING_THRESHOLD),
            SKIPPED_MESSAGES_LOGGING_THRESHOLD);
  }

  void processTransactionsMessage(
      final EthPeer peer,
      final TransactionsMessage transactionsMessage,
      final Instant startedAt,
      final Duration keepAlive) {
    // Check if message not expired.
    if (startedAt.plus(keepAlive).isAfter(now())) {
      this.processTransactionsMessage(peer, transactionsMessage);
    } else {
      totalSkippedTransactionsMessageCounter.inc();
    }
  }

  private void processTransactionsMessage(
      final EthPeer peer, final TransactionsMessage transactionsMessage) {
    try {
      final List<Transaction> readTransactions = transactionsMessage.transactions();
      traceLambda(
          LOG,
          "Received transactions message from {}, transactions {}, list {}",
          peer::toString,
          readTransactions::size,
          () -> readTransactions.stream().map(Transaction::getHash).collect(Collectors.toList()));

      final Set<Transaction> transactions = Sets.newHashSet(readTransactions);
      transactionTracker.markTransactionsAsSeen(peer, transactions);
      transactionPool.addRemoteTransactions(transactions);
    } catch (final RLPException ex) {
      if (peer != null) {
        LOG.debug("Malformed transaction message received, disconnecting: {}", peer, ex);
        peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      }
    }
  }
}

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

import static java.time.Instant.now;
import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.messages.TransactionsMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.Logger;

class TransactionsMessageProcessor {

  private static final int SKIPPED_MESSAGES_LOGGING_THRESHOLD = 1000;
  private static final Logger LOG = getLogger();
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
      LOG.trace("Received transactions message from {}", peer);

      final Iterator<Transaction> readTransactions =
          transactionsMessage.transactions(Transaction::readFrom);
      final Set<Transaction> transactions = Sets.newHashSet(readTransactions);
      transactionTracker.markTransactionsAsSeen(peer, transactions);
      transactionPool.addRemoteTransactions(transactions);
    } catch (final RLPException ex) {
      if (peer != null) {
        peer.disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
      }
    }
  }
}

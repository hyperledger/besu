/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionPoolMetrics {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPoolMetrics.class);
  public static final String ADDED_COUNTER_NAME = "added_total";
  public static final String REMOVED_COUNTER_NAME = "removed_total";
  public static final String REJECTED_COUNTER_NAME = "rejected_total";
  public static final String EXPIRED_MESSAGES_COUNTER_NAME = "messages_expired_total";
  private static final int SKIPPED_MESSAGES_LOGGING_THRESHOLD = 1000;
  private final MetricsSystem metricsSystem;
  private final LabelledMetric<Counter> addedCounter;
  private final LabelledMetric<Counter> removedCounter;
  private final LabelledMetric<Counter> rejectedCounter;
  private final LabelledGauge spaceUsed;
  private final LabelledGauge transactionCount;
  private final LabelledGauge uniqueSenderCount;
  private final LabelledMetric<Counter> expiredMessagesCounter;
  private final Map<String, RunnableCounter> expiredMessagesRunnableCounters = new HashMap<>();
  private final LabelledMetric<Counter> alreadySeenTransactionsCounter;

  public TransactionPoolMetrics(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;

    addedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            ADDED_COUNTER_NAME,
            "Count of transactions added to the transaction pool",
            "source",
            "priority",
            "layer");

    removedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            REMOVED_COUNTER_NAME,
            "Count of transactions removed from the transaction pool",
            "source",
            "priority",
            "operation",
            "layer");

    rejectedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            REJECTED_COUNTER_NAME,
            "Count of transactions not accepted to the transaction pool",
            "source",
            "priority",
            "reason",
            "layer");

    spaceUsed =
        metricsSystem.createLabelledGauge(
            BesuMetricCategory.TRANSACTION_POOL,
            "space_used",
            "The amount of space used by the transactions in the layer",
            "layer");

    transactionCount =
        metricsSystem.createLabelledGauge(
            BesuMetricCategory.TRANSACTION_POOL,
            "number_of_transactions",
            "The number of transactions currently present in the layer",
            "layer");

    uniqueSenderCount =
        metricsSystem.createLabelledGauge(
            BesuMetricCategory.TRANSACTION_POOL,
            "unique_senders",
            "The number of senders with at least one transaction currently present in the layer",
            "layer");

    expiredMessagesCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            EXPIRED_MESSAGES_COUNTER_NAME,
            "Total number of received transaction pool messages expired and not processed.",
            "message");

    alreadySeenTransactionsCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "remote_transactions_already_seen_total",
            "Total number of received transactions already seen",
            "message");
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public void initSpaceUsed(final DoubleSupplier spaceUsedSupplier, final String layer) {
    spaceUsed.labels(spaceUsedSupplier, layer);
  }

  public void initTransactionCount(
      final DoubleSupplier transactionCountSupplier, final String layer) {
    transactionCount.labels(transactionCountSupplier, layer);
  }

  public void initUniqueSenderCount(
      final DoubleSupplier uniqueSenderCountSupplier, final String layer) {
    uniqueSenderCount.labels(uniqueSenderCountSupplier, layer);
  }

  public void initExpiredMessagesCounter(final String message) {
    expiredMessagesRunnableCounters.put(
        message,
        new RunnableCounter(
            expiredMessagesCounter.labels(message),
            () ->
                LOG.warn(
                    "{} expired {} messages have been skipped.",
                    SKIPPED_MESSAGES_LOGGING_THRESHOLD,
                    message),
            SKIPPED_MESSAGES_LOGGING_THRESHOLD));
  }

  public void incrementAdded(final PendingTransaction pendingTransaction, final String layer) {
    addedCounter
        .labels(
            location(pendingTransaction.isReceivedFromLocalSource()),
            priority(pendingTransaction.hasPriority()),
            layer)
        .inc();
  }

  public void incrementRemoved(
      final PendingTransaction pendingTransaction, final String operation, final String layer) {
    removedCounter
        .labels(
            location(pendingTransaction.isReceivedFromLocalSource()),
            priority(pendingTransaction.hasPriority()),
            operation,
            layer)
        .inc();
  }

  public void incrementRejected(
      final PendingTransaction pendingTransaction,
      final TransactionInvalidReason rejectReason,
      final String layer) {
    incrementRejected(
        pendingTransaction.isReceivedFromLocalSource(),
        pendingTransaction.hasPriority(),
        rejectReason,
        layer);
  }

  public void incrementRejected(
      final boolean receivedFromLocalSource,
      final boolean hasPriority,
      final TransactionInvalidReason rejectReason,
      final String layer) {
    rejectedCounter
        .labels(
            location(receivedFromLocalSource), priority(hasPriority), rejectReason.name(), layer)
        .inc();
  }

  public void incrementExpiredMessages(final String message) {
    expiredMessagesCounter.labels(message).inc();
  }

  public void incrementAlreadySeenTransactions(final String message, final long count) {
    alreadySeenTransactionsCounter.labels(message).inc(count);
  }

  private String location(final boolean receivedFromLocalSource) {
    return receivedFromLocalSource ? "local" : "remote";
  }

  private String priority(final boolean hasPriority) {
    return hasPriority ? "yes" : "no";
  }
}

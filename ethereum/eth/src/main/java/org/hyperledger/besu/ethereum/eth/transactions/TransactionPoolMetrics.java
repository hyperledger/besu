/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.eth.transactions.layered.AddReason;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ReplaceableDoubleSupplier;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionPoolMetrics {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPoolMetrics.class);
  public static final String ADDED_COUNTER_NAME = "added_total";
  public static final String REMOVED_COUNTER_NAME = "removed_total";
  public static final String REJECTED_COUNTER_NAME = "rejected_total";
  public static final String PENALIZED_COUNTER_NAME = "penalized_total";
  public static final String EXPIRED_MESSAGES_COUNTER_NAME = "messages_expired_total";
  private static final int SKIPPED_MESSAGES_LOGGING_THRESHOLD = 1000;
  private final MetricsSystem metricsSystem;
  private final LabelledMetric<Counter> addedCounter;
  private final LabelledMetric<Counter> removedCounter;
  private final LabelledMetric<Counter> rejectedCounter;
  private final LabelledMetric<Counter> penalizedCounter;
  private final LabelledSuppliedMetric spaceUsed;
  private final LabelledSuppliedMetric transactionCount;
  private final LabelledSuppliedMetric transactionCountByType;
  private final LabelledSuppliedMetric uniqueSenderCount;
  private final LabelledMetric<Counter> expiredMessagesCounter;
  private final Map<String, RunnableCounter> expiredMessagesRunnableCounters = new HashMap<>();
  private final LabelledMetric<Counter> alreadySeenTransactionsCounter;
  private final Map<String, ReplaceableDoubleSupplier> spaceUsedSuppliers = new HashMap<>();
  private final Map<String, ReplaceableDoubleSupplier> transactionCountSuppliers = new HashMap<>();
  private final Map<Pair<String, TransactionType>, ReplaceableDoubleSupplier>
      transactionCountByTypeSuppliers = new HashMap<>();
  private final Map<String, ReplaceableDoubleSupplier> uniqueSendersSuppliers = new HashMap<>();

  public TransactionPoolMetrics(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;

    addedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            ADDED_COUNTER_NAME,
            "Count of transactions added to the transaction pool",
            "source",
            "priority",
            "reason",
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

    penalizedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            PENALIZED_COUNTER_NAME,
            "Count of penalized transactions in the transaction pool",
            "source",
            "priority",
            "layer");

    spaceUsed =
        metricsSystem.createLabelledSuppliedGauge(
            BesuMetricCategory.TRANSACTION_POOL,
            "space_used",
            "The amount of space used by the transactions in the layer",
            "layer");

    transactionCount =
        metricsSystem.createLabelledSuppliedGauge(
            BesuMetricCategory.TRANSACTION_POOL,
            "number_of_transactions",
            "The number of transactions currently present in the layer",
            "layer");

    transactionCountByType =
        metricsSystem.createLabelledSuppliedGauge(
            BesuMetricCategory.TRANSACTION_POOL,
            "number_of_transactions_by_type",
            "The number of transactions, of a specified type, currently present in the layer",
            "layer",
            "type");

    uniqueSenderCount =
        metricsSystem.createLabelledSuppliedGauge(
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
    spaceUsedSuppliers.compute(
        layer,
        (unused, existingSupplier) -> {
          if (existingSupplier == null) {
            final var newSupplier = new ReplaceableDoubleSupplier(spaceUsedSupplier);
            spaceUsed.labels(newSupplier, layer);
            return newSupplier;
          }
          return existingSupplier.replaceDoubleSupplier(spaceUsedSupplier);
        });
  }

  public void initTransactionCountByType(
      final DoubleSupplier spaceUsedSupplier, final String layer, final TransactionType type) {
    transactionCountByTypeSuppliers.compute(
        Pair.of(layer, type),
        (unused, existingSupplier) -> {
          if (existingSupplier == null) {
            final var newSupplier = new ReplaceableDoubleSupplier(spaceUsedSupplier);
            transactionCountByType.labels(newSupplier, layer, type.name());
            return newSupplier;
          }
          return existingSupplier.replaceDoubleSupplier(spaceUsedSupplier);
        });
  }

  public void initTransactionCount(
      final DoubleSupplier transactionCountSupplier, final String layer) {
    transactionCountSuppliers.compute(
        layer,
        (unused, existingSupplier) -> {
          if (existingSupplier == null) {
            final var newSupplier = new ReplaceableDoubleSupplier(transactionCountSupplier);
            transactionCount.labels(newSupplier, layer);
            return newSupplier;
          }
          return existingSupplier.replaceDoubleSupplier(transactionCountSupplier);
        });
  }

  public void initUniqueSenderCount(
      final DoubleSupplier uniqueSenderCountSupplier, final String layer) {
    uniqueSendersSuppliers.compute(
        layer,
        (unused, existingSupplier) -> {
          if (existingSupplier == null) {
            final var newSupplier = new ReplaceableDoubleSupplier(uniqueSenderCountSupplier);
            uniqueSenderCount.labels(newSupplier, layer);
            return newSupplier;
          }
          return existingSupplier.replaceDoubleSupplier(uniqueSenderCountSupplier);
        });
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

  public void incrementAdded(
      final PendingTransaction pendingTransaction, final AddReason addReason, final String layer) {
    addedCounter
        .labels(
            location(pendingTransaction.isReceivedFromLocalSource()),
            priority(pendingTransaction.hasPriority()),
            addReason.label(),
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

  public void incrementPenalized(final PendingTransaction pendingTransaction, final String layer) {
    penalizedCounter
        .labels(
            location(pendingTransaction.isReceivedFromLocalSource()),
            priority(pendingTransaction.hasPriority()),
            layer)
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

  public void createBlobCacheSizeMetric(final IntSupplier sizeSupplier) {
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "blob_cache_size",
        "Current size of the blob cache",
        sizeSupplier);
  }

  public void createBlobMapSizeMetric(final IntSupplier sizeSupplier) {
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "blob_map_size",
        "Current size of the blob map",
        sizeSupplier);
  }
}

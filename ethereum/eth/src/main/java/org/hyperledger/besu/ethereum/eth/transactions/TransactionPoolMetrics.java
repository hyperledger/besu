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

import org.hyperledger.besu.ethereum.eth.transactions.layered.AbstractPrioritizedTransactions;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.RunnableCounter;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionPoolMetrics {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPoolMetrics.class);
  private static final int SKIPPED_MESSAGES_LOGGING_THRESHOLD = 1000;
  private final MetricsSystem metricsSystem;
  private final LabelledMetric<Counter> addedCounter;
  private final LabelledMetric<Counter> removedCounter;
  private final LabelledMetric<Counter> replacedCount;
  private final LabelledMetric<Counter> invalidCounter;
  private final LabelledMetric<Counter> rejectedCounter;
  private final LabelledMetric<Counter> evictedCounter;
  private final LabelledMetric<Counter> prioritizedCounter;

  private final Counter expiredTransactionsMessageCounter;
  private final LabelledMetric<Counter> alreadySeenTransactionsCounter;

  private final Counter expiredNewPooledTransactionHashesMessageCounter;

  public TransactionPoolMetrics(final MetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;

    addedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_added_total",
            "Count of transactions added to the transaction pool",
            "source", "list");

    invalidCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_invalid_total",
            "Count of transactions added to the transaction pool",
            "source",
            "reason");

    removedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_removed_total",
            "Count of transactions removed from the transaction pool",
            "source",
            "operation", "list");

    replacedCount =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_replaced_total",
            "Count of transactions replaced in the transaction pool",
            "source",
            "list");

    rejectedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_rejected_total",
            "Count of transactions not accepted to the transaction pool",
            "source",
            "operation", "list");

    evictedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_evicted_total",
            "Count of transactions evicted from the transaction pool when it is full",
            "list");

    prioritizedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_prioritized_total",
            "Count of transactions prioritized in the pool by result",
            "source",
            "result");

    expiredTransactionsMessageCounter =
        new RunnableCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.TRANSACTION_POOL,
                "transactions_messages_expired_total",
                "Total number of transactions messages expired and not processed."),
            () ->
                LOG.warn(
                    "{} expired transaction messages have been skipped.",
                    SKIPPED_MESSAGES_LOGGING_THRESHOLD),
            SKIPPED_MESSAGES_LOGGING_THRESHOLD);

    alreadySeenTransactionsCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "remote_transactions_already_seen_total",
            "Total number of received transactions already seen",
            "message");

    expiredNewPooledTransactionHashesMessageCounter =
        new RunnableCounter(
            metricsSystem.createCounter(
                BesuMetricCategory.TRANSACTION_POOL,
                "new_pooled_transaction_hashes_messages_expired_total",
                "Total number of new pooled transaction hashes messages expired and not processed."),
            () ->
                LOG.warn(
                    "{} expired new pooled transaction hashes messages have been skipped.",
                    SKIPPED_MESSAGES_LOGGING_THRESHOLD),
            SKIPPED_MESSAGES_LOGGING_THRESHOLD);
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public void initPendingTransactionCount(final IntSupplier supplier) {
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "pending_transactions_count",
        "Current count of the pending transactions in the pool",
        supplier);
  }

  public void initPendingTransactionSpace(final LongSupplier supplier) {
    metricsSystem.createLongGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "pending_transactions_size",
        "Current size of the pending transactions in the pool",
        supplier);
  }

  public void initReadyTransactionCount(final IntSupplier supplier) {
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "ready_transactions_count",
        "Current count of the ready pending transactions in the pool",
        supplier);
  }

  public void initSparseTransactionCount(final IntSupplier supplier) {
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "sparse_transactions_count",
        "Current count of the sparse pending transactions in the pool",
        supplier);
  }

  public void initPrioritizedTransactionSize(final IntSupplier supplier) {
    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "prioritized_transactions_count",
        "Current count of prioritized transactions in the pool",
        supplier);
  }

  public void incrementAdded(final boolean receivedFromLocalSource, final String list) {
    addedCounter.labels(location(receivedFromLocalSource), list).inc();
  }

  public void incrementRemoved(final boolean receivedFromLocalSource, final String operation, final String list) {
    removedCounter.labels(location(receivedFromLocalSource), operation, list).inc();
  }

  public void incrementReplaced(final boolean receivedFromLocalSource, final String list) {
    replacedCount.labels(location(receivedFromLocalSource), list).inc();
  }

  public void incrementInvalid(
      final boolean receivedFromLocalSource, final TransactionInvalidReason invalidReason) {
    invalidCounter.labels(location(receivedFromLocalSource), invalidReason.name()).inc();
  }

  public void incrementRejected(
      final boolean receivedFromLocalSource, final TransactionInvalidReason rejectReason, final String list) {
    rejectedCounter.labels(location(receivedFromLocalSource), rejectReason.name(), list).inc();
  }

  public void incrementExpiredTransactionsMessage() {
    expiredTransactionsMessageCounter.inc();
  }

  public void incrementAlreadySeenTransactions(final String message, final long count) {
    alreadySeenTransactionsCounter.labels(message).inc(count);
  }

  public void incrementExpiredNewPooledTransactionHashesMessage() {
    expiredNewPooledTransactionHashesMessageCounter.inc();
  }

  public void incrementEvicted(final String set, final int count) {
    evictedCounter.labels(set).inc(count);
  }

  public void incrementPrioritized(
      final boolean receivedFromLocalSource,
      final AbstractPrioritizedTransactions.PrioritizeResult prioritizeResult) {
    prioritizedCounter
        .labels(location(receivedFromLocalSource), prioritizeResult.toMetricLabel())
        .inc();
  }

  private String location(final boolean receivedFromLocalSource) {
    return receivedFromLocalSource ? "local" : "remote";
  }
}

/*
 * Copyright contributors to Besu.
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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.layered.AddReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import kotlin.ranges.LongRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Utility class for writing structured logs about the transaction pool lifecycle in a
 * machine-friendly format.
 *
 * <p>This class provides structured logging capabilities that are kept separate from the
 * transaction pool business logic to maintain code clarity. The logs are not intended for human
 * consumption but rather for automated processing and analysis.
 *
 * <h2>Primary Use Cases:</h2>
 *
 * <ol>
 *   <li><b>Record and Replay Mechanism:</b> Records real-world transaction pool interactions for
 *       debugging purposes. These recordings can be replayed in local development environments to
 *       reproduce and diagnose production issues. Logs are written to the {@code TX_POOL_REPLAY}
 *       logger.
 *   <li><b>Transaction Lifecycle Tracking:</b> Logs the complete lifecycle of every transaction
 *       from entry into the pool through various state transitions. These logs are designed to be
 *       ingested by log management systems like Loki, enabling queries to track how transactions
 *       move across instances and to investigate transaction history. Logs are written to the
 *       {@code TX_POOL_LIFECYCLE} logger.
 * </ol>
 *
 * <h2>Log Scopes:</h2>
 *
 * <ul>
 *   <li>Transaction pool initialization and configuration
 *   <li>Transaction addition, removal, and movement between layers
 *   <li>Transaction validation and invalidation
 *   <li>Transaction penalization and selection
 *   <li>Block confirmation and reorganization events
 *   <li>Dynamic pricing updates (minimum gas price, priority fees)
 *   <li>Sender account state (balances, nonces)
 * </ul>
 *
 * <p>All logging operations use lazy evaluation through suppliers to minimize performance impact
 * when logging is disabled. The class maintains state for certain values (e.g., minimum gas prices)
 * to avoid redundant logging of unchanged values.
 *
 * <p>This is a utility class with only static methods and should not be instantiated.
 */
public class TransactionPoolStructuredLogUtils {
  private static final Logger LOG_FOR_REPLAY = LoggerFactory.getLogger("TX_POOL_REPLAY");
  private static final Logger LOG_TX_POOL_LIFECYCLE = LoggerFactory.getLogger("TX_POOL_LIFECYCLE");
  private static final String MSG_TX_POOL_LIFECYCLE =
      "event={} hash={} sender={} nonce={} type={} gasPrice={} maxFeePerGas={} maxPriorityFeePerGas={}"
          + " reason={} isLocal={} hasPriority={} addedAt={} score={} layer={}";
  private static volatile Wei lastRecordedMinTransactionGasPrice = Wei.ZERO;
  private static volatile Wei lastRecordedMinPriorityFeePerGas = Wei.ZERO;

  /**
   * Logs the start of a transaction pool session for replay purposes.
   *
   * <p>Records the initial state including:
   *
   * <ul>
   *   <li>Block header information (number, base fee, gas used, gas limit)
   *   <li>Priority senders configuration
   *   <li>Maximum prioritized transactions by type
   *   <li>Minimum score threshold
   * </ul>
   *
   * <p>This method also resets internal state tracking for minimum gas prices. Only logs when TRACE
   * level is enabled for the {@code TX_POOL_REPLAY} logger.
   *
   * @param configuration the transaction pool configuration containing priority settings
   * @param blockHeader the optional initial block header; if absent, default values are logged
   */
  public static void logStart(
      final TransactionPoolConfiguration configuration, final Optional<BlockHeader> blockHeader) {
    if (LOG_FOR_REPLAY.isTraceEnabled()) {

      LOG_FOR_REPLAY.trace("START");

      // log the initial block header data
      LOG_FOR_REPLAY
          .atTrace()
          .setMessage("{},{},{},{}")
          .addArgument(() -> blockHeader.map(BlockHeader::getNumber).orElse(0L))
          .addArgument(
              () ->
                  blockHeader
                      .flatMap(BlockHeader::getBaseFee)
                      .map(Wei::getAsBigInteger)
                      .orElse(BigInteger.ZERO))
          .addArgument(() -> blockHeader.map(BlockHeader::getGasUsed).orElse(0L))
          .addArgument(() -> blockHeader.map(BlockHeader::getGasLimit).orElse(0L))
          .log();

      // log the priority senders
      LOG_FOR_REPLAY
          .atTrace()
          .setMessage("{}")
          .addArgument(
              () ->
                  configuration.getPrioritySenders().stream()
                      .map(Address::toHexString)
                      .collect(Collectors.joining(",")))
          .log();

      // log the max prioritized txs by type
      LOG_FOR_REPLAY
          .atTrace()
          .setMessage("{}")
          .addArgument(
              () ->
                  configuration.getMaxPrioritizedTransactionsByType().entrySet().stream()
                      .map(e -> e.getKey().name() + "=" + e.getValue())
                      .collect(Collectors.joining(",")))
          .log();

      // log configuration: minScore
      LOG_FOR_REPLAY.atTrace().setMessage("{}").addArgument(configuration::getMinScore).log();
    }
  }

  /**
   * Logs the stop of a transaction pool session for replay purposes.
   *
   * <p>Records the end of a replay session and resets internal state tracking, including minimum
   * transaction gas price and minimum priority fee per gas. Only logs when TRACE level is enabled
   * for the {@code TX_POOL_REPLAY} logger.
   */
  public static void logStop() {
    if (LOG_FOR_REPLAY.isTraceEnabled()) {
      LOG_FOR_REPLAY.trace("STOP");
      lastRecordedMinTransactionGasPrice = Wei.ZERO;
      lastRecordedMinPriorityFeePerGas = Wei.ZERO;
    }
  }

  /**
   * Logs statistical information about the current state of pending transactions.
   *
   * <p>Outputs aggregate statistics from the pending transactions collection, prefixed with 'S,'
   * for identification during replay parsing. Only logs when TRACE level is enabled for the {@code
   * TX_POOL_REPLAY} logger.
   *
   * @param pendingTransactions the pending transactions collection to report statistics for
   */
  public static void logStats(final PendingTransactions pendingTransactions) {
    LOG_FOR_REPLAY.atTrace().setMessage("S,{}").addArgument(pendingTransactions::logStats).log();
  }

  /**
   * Logs when a transaction is determined to be invalid.
   *
   * <p>Records the transaction details along with the validation failure reason to both the replay
   * and lifecycle logs. This helps track why transactions were rejected.
   *
   * @param transaction the invalid transaction
   * @param reason the validation result containing the reason for invalidity
   * @param isLocal true if the transaction was received from a local source
   * @param hasPriority true if the transaction sender has priority status
   */
  public static void logInvalid(
      final Transaction transaction,
      final ValidationResult<TransactionInvalidReason> reason,
      final boolean isLocal,
      final boolean hasPriority) {
    populateTransactionArguments(
        "invalid",
        transaction,
        isLocal,
        hasPriority,
        reason.isValid() ? "N/A" : reason.getInvalidReason().name());
  }

  /**
   * Logs when a transaction is added to the transaction pool.
   *
   * <p>Records comprehensive information for replay purposes including:
   *
   * <ul>
   *   <li>Transaction sequence number and addition timestamp
   *   <li>Sender address and nonce range (accounting for gaps)
   *   <li>Transaction type, hash, and RLP-encoded data
   * </ul>
   *
   * <p>Also logs lifecycle information including the layer name and reason for addition. Logs are
   * written to both replay and lifecycle loggers.
   *
   * @param pendingTransaction the transaction that was added
   * @param gap the nonce gap before this transaction (number of missing predecessor transactions)
   * @param reason the reason why the transaction was added
   * @param layerName the name of the pool layer where the transaction was added
   */
  public static void logAdded(
      final PendingTransaction pendingTransaction,
      final int gap,
      final AddReason reason,
      final String layerName) {
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("T,{},{},{},{},{},{},{},{}")
        .addArgument(pendingTransaction::getSequence)
        .addArgument(pendingTransaction::getAddedAt)
        .addArgument(pendingTransaction::getSender)
        .addArgument(() -> pendingTransaction.getNonce() - gap)
        .addArgument(pendingTransaction::getNonce)
        .addArgument(() -> pendingTransaction.getTransaction().getType())
        .addArgument(pendingTransaction::getHash)
        .addArgument(() -> pendingTransaction.getTransaction().encoded())
        .log();
    populatePendingTransactionKeyValues("added", pendingTransaction, reason.name(), layerName);
  }

  /**
   * Logs when a transaction is penalized.
   *
   * <p>Penalization occurs when a transaction fails selection criteria during block building.
   * Records the transaction hash for replay purposes and full lifecycle details including the
   * selection result that caused the penalization.
   *
   * @param pendingTransaction the transaction that was penalized
   * @param selectionResult the result explaining why the transaction was not selected
   * @param layerName the name of the pool layer where the transaction resides
   */
  public static void logPenalized(
      final PendingTransaction pendingTransaction,
      final TransactionSelectionResult selectionResult,
      final String layerName) {
    LOG_FOR_REPLAY.atTrace().setMessage("PZ,{}").addArgument(pendingTransaction::getHash).log();
    populatePendingTransactionKeyValues(
        "penalized", pendingTransaction, selectionResult.toString(), layerName);
  }

  /**
   * Logs when a transaction is removed from the transaction pool.
   *
   * <p>Records comprehensive removal information including:
   *
   * <ul>
   *   <li>Transaction sequence number and addition timestamp
   *   <li>Sender address and nonce
   *   <li>Transaction type, hash, and RLP-encoded data
   *   <li>Reason for removal
   * </ul>
   *
   * <p>Logs are written to both replay and lifecycle loggers.
   *
   * @param pendingTransaction the transaction that was removed
   * @param reason the reason why the transaction was removed
   * @param layerName the name of the pool layer from which the transaction was removed
   */
  public static void logRemoved(
      final PendingTransaction pendingTransaction,
      final RemovalReason reason,
      final String layerName) {
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("D,{},{},{},{},{},{},{}")
        .addArgument(pendingTransaction::getSequence)
        .addArgument(pendingTransaction::getAddedAt)
        .addArgument(pendingTransaction::getSender)
        .addArgument(pendingTransaction::getNonce)
        .addArgument(() -> pendingTransaction.getTransaction().getType())
        .addArgument(pendingTransaction::getHash)
        .addArgument(() -> pendingTransaction.getTransaction().encoded())
        .log();
    populatePendingTransactionKeyValues("removed", pendingTransaction, reason.label(), layerName);
  }

  /**
   * Logs when a transaction is moved between layers in the transaction pool.
   *
   * <p>Movement typically occurs when a transaction's priority or readiness status changes,
   * requiring it to be relocated to a different management layer within the pool. Logs lifecycle
   * information including the reason for the move.
   *
   * @param pendingTransaction the transaction that was moved
   * @param reason the reason why the transaction was moved
   * @param layerName the name of the destination pool layer
   */
  public static void logMoved(
      final PendingTransaction pendingTransaction,
      final RemovalReason reason,
      final String layerName) {
    populatePendingTransactionKeyValues("moved", pendingTransaction, reason.label(), layerName);
  }

  /**
   * Logs when the minimum transaction gas price threshold changes.
   *
   * <p>Only logs if the new value differs from the last recorded value to avoid redundant log
   * entries. This is used during replay to accurately reconstruct the dynamic pricing state at any
   * point in time. Only logs when TRACE level is enabled for the {@code TX_POOL_REPLAY} logger.
   *
   * @param currMinTransactionGasPrice the new minimum transaction gas price
   */
  public static void logMinGasPrice(final Wei currMinTransactionGasPrice) {
    if (LOG_FOR_REPLAY.isTraceEnabled()
        && !lastRecordedMinTransactionGasPrice.equals(currMinTransactionGasPrice)) {
      LOG_FOR_REPLAY.trace("MGP,{}", currMinTransactionGasPrice.toShortHexString());
      lastRecordedMinTransactionGasPrice = currMinTransactionGasPrice;
    }
  }

  /**
   * Logs when the minimum priority fee per gas threshold changes.
   *
   * <p>Only logs if the new value differs from the last recorded value to avoid redundant log
   * entries. This is used during replay to accurately reconstruct the dynamic pricing state for
   * EIP-1559 transactions at any point in time. Only logs when TRACE level is enabled for the
   * {@code TX_POOL_REPLAY} logger.
   *
   * @param currMinPriorityFeePerGas the new minimum priority fee per gas
   */
  public static void logMinPriorityFeePerGas(final Wei currMinPriorityFeePerGas) {
    if (LOG_FOR_REPLAY.isTraceEnabled()
        && !lastRecordedMinPriorityFeePerGas.equals(currMinPriorityFeePerGas)) {
      LOG_FOR_REPLAY.trace("MPF,{}", currMinPriorityFeePerGas.toShortHexString());
      lastRecordedMinPriorityFeePerGas = currMinPriorityFeePerGas;
    }
  }

  /**
   * Logs the current balance of a sender account.
   *
   * <p>This information is crucial for replay as it allows accurate reconstruction of account
   * state, which affects transaction validation and pool management decisions. Only logs when TRACE
   * level is enabled for the {@code TX_POOL_REPLAY} logger.
   *
   * @param sender the address of the account
   * @param balance the current balance of the account
   */
  public static void logSenderBalance(final Address sender, final Wei balance) {
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("SB,{},{}")
        .addArgument(sender)
        .addArgument(balance::toShortHexString)
        .log();
  }

  /**
   * Logs when a block is confirmed, including transaction confirmations and reorganizations.
   *
   * <p>Records comprehensive block confirmation information including:
   *
   * <ul>
   *   <li>Block number and hash
   *   <li>Maximum confirmed nonce for each sender with confirmed transactions
   *   <li>Nonce ranges for reorganized transactions (transactions that were in a previous block but
   *       are now back in the pool due to chain reorganization)
   *   <li>Complete RLP-encoded block header for accurate replay
   * </ul>
   *
   * <p>This information is essential for replaying chain reorganization scenarios and understanding
   * how the transaction pool state evolves with blockchain progression. Only logs when TRACE level
   * is enabled for the {@code TX_POOL_REPLAY} logger.
   *
   * @param blockHeader the confirmed block header
   * @param maxConfirmedNonceBySender map of sender addresses to their maximum confirmed nonce in
   *     this block
   * @param reorgTransactions list of transactions that were reorganized (removed from canonical
   *     chain)
   */
  public static void logConfirmed(
      final BlockHeader blockHeader,
      final Map<Address, Long> maxConfirmedNonceBySender,
      final List<Transaction> reorgTransactions) {
    if (LOG_FOR_REPLAY.isTraceEnabled()) {
      final var reorgNonceRangeBySender = nonceRangeBySender(reorgTransactions);
      final var strMaxConfirmedNonceBySender =
          maxConfirmedNonceBySender.entrySet().stream()
              .map(e -> e.getKey().toHexString() + "," + e.getValue())
              .collect(Collectors.joining(","));
      final var strReorgNonceRangeBySender =
          reorgNonceRangeBySender.entrySet().stream()
              .map(
                  e ->
                      e.getKey().toHexString()
                          + ","
                          + e.getValue().getStart()
                          + ","
                          + e.getValue().getEndInclusive())
              .collect(Collectors.joining(","));
      final var rlp = new BytesValueRLPOutput();
      blockHeader.writeTo(rlp);
      final var blockHeaderRlp = rlp.encoded().toHexString();
      LOG_FOR_REPLAY.trace(
          "B,{},{},{},R,{},{}",
          blockHeader.getNumber(),
          blockHeader.getBlockHash(),
          strMaxConfirmedNonceBySender,
          strReorgNonceRangeBySender,
          blockHeaderRlp);
    }
  }

  /**
   * Computes the nonce range (minimum to maximum) for each sender from a list of transactions.
   *
   * <p>Groups transactions by sender and calculates the inclusive range of nonces for each sender.
   * This is used to efficiently represent which transactions were affected by a chain
   * reorganization.
   *
   * @param confirmedTransactions the list of transactions to analyze
   * @return a map from sender address to their nonce range (inclusive on both ends)
   */
  private static Map<Address, LongRange> nonceRangeBySender(
      final List<Transaction> confirmedTransactions) {
    class MutableLongRange {
      long start = Long.MAX_VALUE;
      long end = 0;

      void update(final long nonce) {
        if (nonce < start) {
          start = nonce;
        }
        if (nonce > end) {
          end = nonce;
        }
      }

      MutableLongRange combine(final MutableLongRange other) {
        update(other.start);
        update(other.end);
        return this;
      }

      LongRange toImmutable() {
        return new LongRange(start, end);
      }
    }
    return confirmedTransactions.stream()
        .collect(
            groupingBy(
                Transaction::getSender,
                mapping(
                    Transaction::getNonce,
                    Collector.of(
                        MutableLongRange::new,
                        MutableLongRange::update,
                        MutableLongRange::combine,
                        MutableLongRange::toImmutable))));
  }

  /**
   * Populates and logs common transaction arguments for lifecycle tracking.
   *
   * <p>This is a helper method for logging basic transaction events where no pending transaction
   * context is available (e.g., for invalid transactions that were never added). Fields specific to
   * pending transactions (addedAt, score, layer) are logged as "N/A".
   *
   * @param event the event type (e.g., "invalid", "rejected")
   * @param transaction the transaction being logged
   * @param isLocal true if the transaction was received from a local source
   * @param hasPriority true if the transaction sender has priority status
   * @param reason the reason associated with this event
   */
  private static void populateTransactionArguments(
      final String event,
      final Transaction transaction,
      final boolean isLocal,
      final boolean hasPriority,
      final String reason) {
    createLebWithCommonData(event, transaction, isLocal, hasPriority, reason)
        .addArgument("N/A") // addedAt
        .addArgument("N/A") // score
        .addArgument("N/A") // layer
        .log();
  }

  /**
   * Populates and logs complete pending transaction information for lifecycle tracking.
   *
   * <p>This is a helper method that includes all available context about a pending transaction,
   * including when it was added, its priority score, and which layer it resides in.
   *
   * @param event the event type (e.g., "added", "removed", "moved", "penalized")
   * @param pendingTransaction the pending transaction being logged
   * @param reason the reason associated with this event
   * @param layer the pool layer where the transaction resides or was removed from
   */
  private static void populatePendingTransactionKeyValues(
      final String event,
      final PendingTransaction pendingTransaction,
      final String reason,
      final String layer) {
    createLebWithCommonData(
            event,
            pendingTransaction.getTransaction(),
            pendingTransaction.isReceivedFromLocalSource(),
            pendingTransaction.hasPriority(),
            reason)
        .addArgument(() -> Instant.ofEpochMilli(pendingTransaction.getAddedAt()).toString())
        .addArgument(pendingTransaction.getScore())
        .addArgument(layer)
        .log();
  }

  /**
   * Creates a logging event builder with common transaction data fields.
   *
   * <p>This helper method constructs a {@link LoggingEventBuilder} pre-populated with standard
   * transaction information that is common to all lifecycle events:
   *
   * <ul>
   *   <li>Event type
   *   <li>Transaction hash, sender, nonce, and type
   *   <li>Gas pricing information (gasPrice, maxFeePerGas, maxPriorityFeePerGas)
   *   <li>Event reason
   *   <li>Local source and priority flags
   * </ul>
   *
   * <p>The returned builder can be further augmented with event-specific fields before logging. All
   * arguments use lazy evaluation to minimize performance impact when logging is disabled. Logs are
   * written to the {@code TX_POOL_LIFECYCLE} logger at TRACE level.
   *
   * @param event the event type being logged
   * @param transaction the transaction being logged
   * @param isLocal true if the transaction was received from a local source
   * @param hasPriority true if the transaction sender has priority status
   * @param reason the reason or context for this event
   * @return a logging event builder pre-populated with common transaction data
   */
  private static LoggingEventBuilder createLebWithCommonData(
      final String event,
      final Transaction transaction,
      final boolean isLocal,
      final boolean hasPriority,
      final String reason) {
    return LOG_TX_POOL_LIFECYCLE
        .atTrace()
        .setMessage(MSG_TX_POOL_LIFECYCLE)
        .addArgument(event)
        .addArgument(transaction::getHash)
        .addArgument(transaction::getSender)
        .addArgument(transaction::getNonce)
        .addArgument(transaction::getType)
        .addArgument(
            () ->
                transaction
                    .getGasPrice()
                    .map(wei -> wei.getAsBigInteger().toString())
                    .orElse("N/A"))
        .addArgument(
            () ->
                transaction
                    .getMaxFeePerGas()
                    .map(wei -> wei.getAsBigInteger().toString())
                    .orElse("N/A"))
        .addArgument(
            () ->
                transaction
                    .getMaxPriorityFeePerGas()
                    .map(wei -> wei.getAsBigInteger().toString())
                    .orElse("N/A"))
        .addArgument(reason)
        .addArgument(isLocal)
        .addArgument(hasPriority);
  }
}

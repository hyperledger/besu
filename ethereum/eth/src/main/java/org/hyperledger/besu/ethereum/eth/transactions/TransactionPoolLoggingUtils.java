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

public class TransactionPoolLoggingUtils {
  private static final Logger LOG_FOR_REPLAY = LoggerFactory.getLogger("TX_POOL_REPLAY");
  private static final Logger LOG_TX_POOL_LIFECYCLE = LoggerFactory.getLogger("TX_POOL_LIFECYCLE");

  private static final String MSG_TX_POOL_LIFECYCLE =
      "event={} hash={} sender={} nonce={} type={} gasPrice={} maxFeePerGas={} maxPriorityFeePerGas={} reason={} isLocal={} hasPriority={} addedAt={} score={} layer={}";

  private static volatile Wei lastRecordedMinTransactionGasPrice = Wei.ZERO;
  private static volatile Wei lastRecordedMinPriorityFeePerGas = Wei.ZERO;

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

  public static void logStop() {
    if (LOG_FOR_REPLAY.isTraceEnabled()) {
      LOG_FOR_REPLAY.trace("STOP");
      lastRecordedMinTransactionGasPrice = Wei.ZERO;
      lastRecordedMinPriorityFeePerGas = Wei.ZERO;
    }
  }

  public static void logStats(final PendingTransactions pendingTransactions) {
    LOG_FOR_REPLAY.atTrace().setMessage("S,{}").addArgument(pendingTransactions::logStats).log();
  }

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

  public static void logPenalized(
      final PendingTransaction pendingTransaction,
      final TransactionSelectionResult selectionResult,
      final String layerName) {
    LOG_FOR_REPLAY.atTrace().setMessage("PZ,{}").addArgument(pendingTransaction::getHash).log();

    populatePendingTransactionKeyValues(
        "penalized", pendingTransaction, selectionResult.toString(), layerName);
  }

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

  public static void logMoved(
      final PendingTransaction pendingTransaction,
      final RemovalReason reason,
      final String layerName) {
    populatePendingTransactionKeyValues("moved", pendingTransaction, reason.label(), layerName);
  }

  public static void logMinGasPrice(final Wei currMinTransactionGasPrice) {
    if (LOG_FOR_REPLAY.isTraceEnabled()
        && !lastRecordedMinTransactionGasPrice.equals(currMinTransactionGasPrice)) {
      LOG_FOR_REPLAY.trace("MGP,{}", currMinTransactionGasPrice.toShortHexString());
      lastRecordedMinTransactionGasPrice = currMinTransactionGasPrice;
    }
  }

  public static void logMinPriorityFeePerGas(final Wei currMinPriorityFeePerGas) {
    if (LOG_FOR_REPLAY.isTraceEnabled()
        && !lastRecordedMinPriorityFeePerGas.equals(currMinPriorityFeePerGas)) {
      LOG_FOR_REPLAY.trace("MPF,{}", currMinPriorityFeePerGas.toShortHexString());
      lastRecordedMinPriorityFeePerGas = currMinPriorityFeePerGas;
    }
  }

  public static void logSenderBalance(final Address sender, final Wei balance) {
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("SB,{},{}")
        .addArgument(sender)
        .addArgument(balance::toShortHexString)
        .log();
  }

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

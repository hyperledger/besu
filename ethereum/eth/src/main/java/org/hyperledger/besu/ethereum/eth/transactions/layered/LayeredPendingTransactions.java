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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.reducing;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.ALREADY_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.INTERNAL_ERROR;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.INVALIDATED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.RECONCILED;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionAddedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import kotlin.ranges.LongRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LayeredPendingTransactions implements PendingTransactions {
  private static final Logger LOG = LoggerFactory.getLogger(LayeredPendingTransactions.class);
  private static final Logger LOG_FOR_REPLAY = LoggerFactory.getLogger("LOG_FOR_REPLAY");
  private final TransactionPoolConfiguration poolConfig;
  private final AbstractPrioritizedTransactions prioritizedTransactions;

  public LayeredPendingTransactions(
      final TransactionPoolConfiguration poolConfig,
      final AbstractPrioritizedTransactions prioritizedTransactions) {
    this.poolConfig = poolConfig;
    this.prioritizedTransactions = prioritizedTransactions;
  }

  @Override
  public synchronized void reset() {
    prioritizedTransactions.reset();
  }

  @Override
  public synchronized TransactionAddedResult addTransaction(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {

    final long stateSenderNonce = maybeSenderAccount.map(AccountState::getNonce).orElse(0L);

    logTransactionForReplayAdd(pendingTransaction, stateSenderNonce);

    if (hasAccountNonceDisparity(pendingTransaction, stateSenderNonce)) {
      reconcileSender(pendingTransaction.getSender(), stateSenderNonce);
    }

    final long nonceDistance = pendingTransaction.getNonce() - stateSenderNonce;

    final TransactionAddedResult nonceChecksResult =
        nonceChecks(pendingTransaction, stateSenderNonce, nonceDistance);
    if (nonceChecksResult != null) {
      return nonceChecksResult;
    }

    try {
      return prioritizedTransactions.add(pendingTransaction, (int) nonceDistance);
    } catch (final Throwable throwable) {
      return reconcileAndRetryAdd(
          pendingTransaction, stateSenderNonce, (int) nonceDistance, throwable);
    }
  }

  private TransactionAddedResult reconcileAndRetryAdd(
      final PendingTransaction pendingTransaction,
      final long stateSenderNonce,
      final int nonceDistance,
      final Throwable throwable) {
    // in case something unexpected happened, log this sender txs, force a reconcile and retry
    // another time
    // ToDo: demote to debug when Layered TxPool is out of preview
    LOG.warn(
        "Unexpected error {} when adding transaction {}, current sender status {}",
        throwable,
        pendingTransaction.toTraceLog(),
        prioritizedTransactions.logSender(pendingTransaction.getSender()));
    LOG.warn("Stack trace", throwable);
    reconcileSender(pendingTransaction.getSender(), stateSenderNonce);
    try {
      return prioritizedTransactions.add(pendingTransaction, nonceDistance);
    } catch (final Throwable throwable2) {
      LOG.warn(
          "Unexpected error {} when adding transaction {}, current sender status {}",
          throwable,
          pendingTransaction.toTraceLog(),
          prioritizedTransactions.logSender(pendingTransaction.getSender()));
      LOG.warn("Stack trace", throwable);
      return INTERNAL_ERROR;
    }
  }

  /**
   * Detect a disparity between account nonce has seen by the world state and the txpool, that could
   * happen during the small amount of time during block import when the world state is updated
   * while the txpool still does not process the confirmed txs, or when there is a reorg and the
   * sender nonce goes back.
   *
   * @param pendingTransaction the incoming transaction to check
   * @param stateSenderNonce account nonce from the world state
   * @return false if the nonce for the sender has seen by the txpool matches the value of the
   *     account nonce in the world state, true if they differ
   */
  private boolean hasAccountNonceDisparity(
      final PendingTransaction pendingTransaction, final long stateSenderNonce) {
    final OptionalLong maybeTxPoolSenderNonce =
        prioritizedTransactions.getCurrentNonceFor(pendingTransaction.getSender());
    if (maybeTxPoolSenderNonce.isPresent()) {
      final long txPoolSenderNonce = maybeTxPoolSenderNonce.getAsLong();
      if (stateSenderNonce != txPoolSenderNonce) {
        LOG.atDebug()
            .setMessage(
                "Nonce disparity detected when adding pending transaction {}. "
                    + "Account nonce from world state is {} while current txpool nonce is {}")
            .addArgument(pendingTransaction::toTraceLog)
            .addArgument(stateSenderNonce)
            .addArgument(txPoolSenderNonce)
            .log();
        return true;
      }
    }
    return false;
  }

  /**
   * Rebuild the txpool for a sender according to the specified nonce. This is used in case the
   * account nonce has seen by the txpool is not the correct one (see {@link
   * LayeredPendingTransactions#hasAccountNonceDisparity(PendingTransaction, long)} for when this
   * could happen). It works by removing all the txs for the sender and re-adding them using the
   * passed nonce.
   *
   * @param sender the sender for which rebuild the txpool
   * @param stateSenderNonce the world state account nonce to use in the txpool for the sender
   */
  private void reconcileSender(final Address sender, final long stateSenderNonce) {
    final var existingSenderTxs = prioritizedTransactions.getAllFor(sender);
    if (existingSenderTxs.isEmpty()) {
      LOG.debug("Sender {} has no transactions to reconcile", sender);
      return;
    }

    LOG.atDebug()
        .setMessage("Sender {} with nonce {} has {} transaction(s) to reconcile {}")
        .addArgument(sender)
        .addArgument(stateSenderNonce)
        .addArgument(existingSenderTxs::size)
        .addArgument(() -> prioritizedTransactions.logSender(sender))
        .log();

    final var reAddTxs = new ArrayDeque<PendingTransaction>(existingSenderTxs.size());

    // it is more performant to invalidate backward
    for (int i = existingSenderTxs.size() - 1; i >= 0; --i) {
      final var ptx = existingSenderTxs.get(i);
      prioritizedTransactions.remove(ptx, RECONCILED);
      if (ptx.getNonce() >= stateSenderNonce) {
        reAddTxs.addFirst(ptx);
      }
    }

    if (!reAddTxs.isEmpty()) {
      // re-add all the previous txs
      final long lowestNonce = reAddTxs.getFirst().getNonce();
      final int newNonceDistance = (int) Math.max(0, lowestNonce - stateSenderNonce);

      reAddTxs.forEach(ptx -> prioritizedTransactions.add(ptx, newNonceDistance));
    }

    LOG.atDebug()
        .setMessage("Sender {} with nonce {} status after reconciliation {}")
        .addArgument(sender)
        .addArgument(stateSenderNonce)
        .addArgument(() -> prioritizedTransactions.logSender(sender))
        .log();
  }

  private void logTransactionForReplayAdd(
      final PendingTransaction pendingTransaction, final long senderNonce) {
    // csv fields: sequence, addedAt, sender, sender_nonce, nonce, type, hash, rlp
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("T,{},{},{},{},{},{},{},{}")
        .addArgument(pendingTransaction.getSequence())
        .addArgument(pendingTransaction.getAddedAt())
        .addArgument(pendingTransaction.getSender())
        .addArgument(senderNonce)
        .addArgument(pendingTransaction.getNonce())
        .addArgument(pendingTransaction.getTransaction().getType())
        .addArgument(pendingTransaction::getHash)
        .addArgument(
            () -> {
              final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
              pendingTransaction.getTransaction().writeTo(rlp);
              return rlp.encoded().toHexString();
            })
        .log();
  }

  private void logTransactionForReplayDelete(final PendingTransaction pendingTransaction) {
    // csv fields: sequence, addedAt, sender, nonce, type, hash, rlp
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("D,{},{},{},{},{},{},{}")
        .addArgument(pendingTransaction.getSequence())
        .addArgument(pendingTransaction.getAddedAt())
        .addArgument(pendingTransaction.getSender())
        .addArgument(pendingTransaction.getNonce())
        .addArgument(pendingTransaction.getTransaction().getType())
        .addArgument(pendingTransaction::getHash)
        .addArgument(
            () -> {
              final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
              pendingTransaction.getTransaction().writeTo(rlp);
              return rlp.encoded().toHexString();
            })
        .log();
  }

  private TransactionAddedResult nonceChecks(
      final PendingTransaction pendingTransaction,
      final long senderNonce,
      final long nonceDistance) {
    if (nonceDistance < 0) {
      LOG.atTrace()
          .setMessage("Drop already confirmed transaction {}, since current sender nonce is {}")
          .addArgument(pendingTransaction::toTraceLog)
          .addArgument(senderNonce)
          .log();
      return ALREADY_KNOWN;
    } else if (nonceDistance >= poolConfig.getMaxFutureBySender()) {
      LOG.atTrace()
          .setMessage(
              "Drop too much in the future transaction {}, since current sender nonce is {}")
          .addArgument(pendingTransaction::toTraceLog)
          .addArgument(senderNonce)
          .log();
      return NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;
    }
    return null;
  }

  @Override
  public void evictOldTransactions() {}

  @Override
  public synchronized List<Transaction> getLocalTransactions() {
    return prioritizedTransactions.getAllLocal();
  }

  @Override
  public synchronized List<Transaction> getPriorityTransactions() {
    return prioritizedTransactions.getAllPriority();
  }

  @Override
  // There's a small edge case here we could encounter.
  // When we pass an upgrade block that has a new transaction type, we start allowing transactions
  // of that new type into our pool.
  // If we then reorg to a block lower than the upgrade block height _and_ we create a block, that
  // block could end up with transactions of the new type.
  // This seems like it would be very rare but worth it to document that we don't handle that case
  // right now.
  public synchronized void selectTransactions(
      final PendingTransactions.TransactionSelector selector) {
    final List<PendingTransaction> invalidTransactions = new ArrayList<>();
    final Set<Hash> alreadyChecked = new HashSet<>();
    final Set<Address> skipSenders = new HashSet<>();
    final AtomicBoolean completed = new AtomicBoolean(false);

    prioritizedTransactions.stream()
        .takeWhile(unused -> !completed.get())
        .filter(highPrioPendingTx -> !skipSenders.contains(highPrioPendingTx.getSender()))
        .peek(this::logSenderTxs)
        .forEach(
            highPrioPendingTx ->
                prioritizedTransactions.stream(highPrioPendingTx.getSender())
                    .takeWhile(
                        candidatePendingTx ->
                            !skipSenders.contains(candidatePendingTx.getSender())
                                && !completed.get())
                    .filter(
                        candidatePendingTx ->
                            !alreadyChecked.contains(candidatePendingTx.getHash())
                                && candidatePendingTx.getNonce() <= highPrioPendingTx.getNonce())
                    .forEach(
                        candidatePendingTx -> {
                          alreadyChecked.add(candidatePendingTx.getHash());
                          final var res = selector.evaluateTransaction(candidatePendingTx);

                          LOG.atTrace()
                              .setMessage("Selection result {} for transaction {}")
                              .addArgument(res)
                              .addArgument(candidatePendingTx::toTraceLog)
                              .log();

                          if (res.discard()) {
                            invalidTransactions.add(candidatePendingTx);
                            logTransactionForReplayDelete(candidatePendingTx);
                          }

                          if (res.stop()) {
                            completed.set(true);
                          }

                          if (!res.selected()) {
                            // avoid processing other txs from this sender if this one is skipped
                            // since the following will not be selected due to the nonce gap
                            skipSenders.add(candidatePendingTx.getSender());
                            LOG.trace("Skipping tx from sender {}", candidatePendingTx.getSender());
                          }
                        }));

    invalidTransactions.forEach(
        invalidTx -> prioritizedTransactions.remove(invalidTx, INVALIDATED));
  }

  private void logSenderTxs(final PendingTransaction highPrioPendingTx) {
    LOG.atTrace()
        .setMessage("highPrioPendingTx {}, senderTxs {}")
        .addArgument(highPrioPendingTx::toTraceLog)
        .addArgument(
            () ->
                prioritizedTransactions.stream(highPrioPendingTx.getSender())
                    .map(PendingTransaction::toTraceLog)
                    .collect(Collectors.joining(", ")))
        .log();
  }

  @Override
  public long maxSize() {
    return -1;
  }

  @Override
  public synchronized int size() {
    return prioritizedTransactions.count();
  }

  @Override
  public synchronized boolean containsTransaction(final Transaction transaction) {
    return prioritizedTransactions.contains(transaction);
  }

  @Override
  public synchronized Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return prioritizedTransactions.getByHash(transactionHash);
  }

  @Override
  public synchronized List<PendingTransaction> getPendingTransactions() {
    return prioritizedTransactions.getAll();
  }

  @Override
  public long subscribePendingTransactions(final PendingTransactionAddedListener listener) {
    return prioritizedTransactions.subscribeToAdded(listener);
  }

  @Override
  public void unsubscribePendingTransactions(final long id) {
    prioritizedTransactions.unsubscribeFromAdded(id);
  }

  @Override
  public long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return prioritizedTransactions.subscribeToDropped(listener);
  }

  @Override
  public void unsubscribeDroppedTransactions(final long id) {
    prioritizedTransactions.unsubscribeFromDropped(id);
  }

  @Override
  public OptionalLong getNextNonceForSender(final Address sender) {
    return prioritizedTransactions.getNextNonceFor(sender);
  }

  @Override
  public synchronized void manageBlockAdded(
      final BlockHeader blockHeader,
      final List<Transaction> confirmedTransactions,
      final List<Transaction> reorgTransactions,
      final FeeMarket feeMarket) {
    LOG.atTrace()
        .setMessage("Managing new added block {}")
        .addArgument(blockHeader::toLogString)
        .log();

    final var maxConfirmedNonceBySender = maxNonceBySender(confirmedTransactions);

    final var reorgNonceRangeBySender = nonceRangeBySender(reorgTransactions);

    try {
      prioritizedTransactions.blockAdded(feeMarket, blockHeader, maxConfirmedNonceBySender);
    } catch (final Throwable throwable) {
      LOG.warn(
          "Unexpected error {} when managing added block {}, maxNonceBySender {}, reorgNonceRangeBySender {}",
          throwable,
          blockHeader.toLogString(),
          maxConfirmedNonceBySender,
          reorgTransactions);
      LOG.warn("Stack trace", throwable);
    }

    logBlockHeaderForReplay(blockHeader, maxConfirmedNonceBySender, reorgNonceRangeBySender);
  }

  private void logBlockHeaderForReplay(
      final BlockHeader blockHeader,
      final Map<Address, Long> maxConfirmedNonceBySender,
      final Map<Address, LongRange> reorgNonceRangeBySender) {
    // block number, block hash, sender, max nonce ..., rlp
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("B,{},{},{},R,{},{}")
        .addArgument(blockHeader.getNumber())
        .addArgument(blockHeader.getBlockHash())
        .addArgument(
            () ->
                maxConfirmedNonceBySender.entrySet().stream()
                    .map(e -> e.getKey().toHexString() + "," + e.getValue())
                    .collect(Collectors.joining(",")))
        .addArgument(
            () ->
                reorgNonceRangeBySender.entrySet().stream()
                    .map(
                        e ->
                            e.getKey().toHexString()
                                + ","
                                + e.getValue().getStart()
                                + ","
                                + e.getValue().getEndInclusive())
                    .collect(Collectors.joining(",")))
        .addArgument(
            () -> {
              final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
              blockHeader.writeTo(rlp);
              return rlp.encoded().toHexString();
            })
        .log();
  }

  private Map<Address, Long> maxNonceBySender(final List<Transaction> confirmedTransactions) {
    return confirmedTransactions.stream()
        .collect(
            groupingBy(
                Transaction::getSender, mapping(Transaction::getNonce, reducing(0L, Math::max))));
  }

  private Map<Address, LongRange> nonceRangeBySender(
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

  @Override
  public synchronized String toTraceLog() {
    return "";
  }

  @Override
  public synchronized String logStats() {
    return prioritizedTransactions.logStats();
  }

  @Override
  public Optional<Transaction> restoreBlob(final Transaction transaction) {
    Transaction.Builder txBuilder = Transaction.builder();
    txBuilder.copiedFrom(transaction);
    final BlobsWithCommitments bwc = prioritizedTransactions.getBlobCache().getIfPresent(transaction.getHash());
    if(bwc != null) {
      txBuilder.blobsWithCommitments(bwc);
      return Optional.of(txBuilder.build());
    } else {
      return Optional.empty();
    }
  }
}

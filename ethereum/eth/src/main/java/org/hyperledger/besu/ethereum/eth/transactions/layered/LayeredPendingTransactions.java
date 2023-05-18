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
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.REORG_SENDER;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.INVALIDATED;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.REORG;

import org.hyperledger.besu.datatypes.Address;
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
  private final Set<Address> localSenders = new HashSet<>();
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
  public synchronized TransactionAddedResult addRemoteTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    return addTransaction(new PendingTransaction.Remote(transaction), maybeSenderAccount);
  }

  @Override
  public synchronized TransactionAddedResult addLocalTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    final TransactionAddedResult addedResult =
        addTransaction(new PendingTransaction.Local(transaction), maybeSenderAccount);
    if (addedResult.isSuccess()) {
      localSenders.add(transaction.getSender());
    }
    return addedResult;
  }

  TransactionAddedResult addTransaction(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {

    final long senderNonce = maybeSenderAccount.map(AccountState::getNonce).orElse(0L);

    logTransactionForReplayAdd(pendingTransaction, senderNonce);

    final long nonceDistance = pendingTransaction.getNonce() - senderNonce;

    final TransactionAddedResult nonceChecksResult =
        nonceChecks(pendingTransaction, senderNonce, nonceDistance);
    if (nonceChecksResult != null) {
      return nonceChecksResult;
    }

    try {
      TransactionAddedResult result =
          prioritizedTransactions.add(pendingTransaction, (int) nonceDistance);

      if (result.equals(REORG_SENDER)) {
        result = reorgSenderOf(pendingTransaction, (int) nonceDistance);
      }

      return result;
    } catch (final Throwable throwable) {
      // in case something unexpected happened, log this sender txs and force a reorg of his txs
      LOG.warn(
          "Unexpected error {} when adding transaction {}, current sender status {}",
          throwable,
          pendingTransaction.toTraceLog(),
          prioritizedTransactions.logSender(pendingTransaction.getSender()));
      LOG.warn("Stack trace", throwable);
      reorgSenderOf(pendingTransaction, (int) nonceDistance);
      return INTERNAL_ERROR;
    }
  }

  private TransactionAddedResult reorgSenderOf(
      final PendingTransaction pendingTransaction, final int nonceDistance) {
    final var existingSenderTxs = prioritizedTransactions.getAllFor(pendingTransaction.getSender());

    // it is more performant to invalidate backward
    for (int i = existingSenderTxs.size() - 1; i >= 0; --i) {
      prioritizedTransactions.remove(existingSenderTxs.get(i), REORG);
    }

    // add the new one and re-add all the previous
    final var result = prioritizedTransactions.add(pendingTransaction, nonceDistance);
    existingSenderTxs.forEach(ptx -> prioritizedTransactions.add(ptx, nonceDistance));
    LOG.atTrace()
        .setMessage(
            "Pending transaction {} with nonce distance {} triggered a reorg for sender {} with {} existing transactions: {}")
        .addArgument(pendingTransaction::toTraceLog)
        .addArgument(nonceDistance)
        .addArgument(pendingTransaction::getSender)
        .addArgument(existingSenderTxs::size)
        .addArgument(
            () ->
                existingSenderTxs.stream()
                    .map(PendingTransaction::toTraceLog)
                    .collect(Collectors.joining("; ")))
        .log();
    return result;
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
  public synchronized boolean isLocalSender(final Address sender) {
    return localSenders.contains(sender);
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
    final AtomicBoolean completed = new AtomicBoolean(false);

    prioritizedTransactions.stream()
        .takeWhile(unused -> !completed.get())
        .peek(
            highPrioPendingTx ->
                LOG.atDebug()
                    .setMessage("highPrioPendingTx {}, senderTxs {}")
                    .addArgument(highPrioPendingTx::toTraceLog)
                    .addArgument(
                        () ->
                            prioritizedTransactions.stream(highPrioPendingTx.getSender())
                                .map(PendingTransaction::toTraceLog)
                                .collect(Collectors.joining(", ")))
                    .log())
        .forEach(
            highPrioPendingTx ->
                prioritizedTransactions.stream(highPrioPendingTx.getSender())
                    .takeWhile(unused -> !completed.get())
                    .filter(
                        candidatePendingTx ->
                            !alreadyChecked.contains(candidatePendingTx.getHash()))
                    .filter(
                        candidatePendingTx ->
                            candidatePendingTx.getNonce() <= highPrioPendingTx.getNonce())
                    .forEach(
                        candidatePendingTx -> {
                          alreadyChecked.add(candidatePendingTx.getHash());
                          switch (selector.evaluateTransaction(
                              candidatePendingTx.getTransaction())) {
                            case CONTINUE:
                              LOG.atTrace()
                                  .setMessage("CONTINUE: Transaction {}")
                                  .addArgument(candidatePendingTx::toTraceLog)
                                  .log();
                              break;
                            case DELETE_TRANSACTION_AND_CONTINUE:
                              invalidTransactions.add(candidatePendingTx);
                              LOG.atTrace()
                                  .setMessage("DELETE_TRANSACTION_AND_CONTINUE: Transaction {}")
                                  .addArgument(candidatePendingTx::toTraceLog)
                                  .log();
                              logTransactionForReplayDelete(candidatePendingTx);
                              break;
                            case COMPLETE_OPERATION:
                              completed.set(true);
                              LOG.atTrace()
                                  .setMessage("COMPLETE_OPERATION: Transaction {}")
                                  .addArgument(candidatePendingTx::toTraceLog)
                                  .log();
                              break;
                          }
                        }));

    invalidTransactions.forEach(
        invalidTx -> prioritizedTransactions.remove(invalidTx, INVALIDATED));
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
    LOG.atDebug()
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
}

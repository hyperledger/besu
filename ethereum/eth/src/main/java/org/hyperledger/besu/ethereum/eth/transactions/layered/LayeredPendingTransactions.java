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
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult.NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER;

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
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LayeredPendingTransactions implements PendingTransactions {
  private static final Logger LOG = LoggerFactory.getLogger(LayeredPendingTransactions.class);
  private static final Logger LOG_TX_CSV = LoggerFactory.getLogger("LOG_TX_CSV");
  private final TransactionPoolConfiguration poolConfig;
  private final Set<Address> localSenders = new HashSet<>();
  private final AbstractPrioritizedTransactions prioritizedTransactions;
  private final TransactionPoolMetrics metrics;

  public LayeredPendingTransactions(
      final TransactionPoolConfiguration poolConfig,
      final AbstractPrioritizedTransactions prioritizedTransactions,
      final TransactionPoolMetrics metrics) {
    this.poolConfig = poolConfig;
    this.prioritizedTransactions = prioritizedTransactions;
    this.metrics = metrics;
  }

  @Override
  public synchronized void reset() {
    prioritizedTransactions.reset();
  }

  @Override
  public synchronized TransactionAddedResult addRemoteTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    return addTransaction(
        new PendingTransaction.Remote(transaction), maybeSenderAccount);
  }

  @Override
  public synchronized TransactionAddedResult addLocalTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    final TransactionAddedResult addedResult =
        addTransaction(
            new PendingTransaction.Local(transaction),
            maybeSenderAccount);
    if (addedResult.isSuccess()) {
      localSenders.add(transaction.getSender());
    }
    return addedResult;
  }

  private TransactionAddedResult addTransaction(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {

    final long senderNonce = maybeSenderAccount.map(AccountState::getNonce).orElse(0L);

    logTxToCSV(pendingTransaction, senderNonce);

    final long nonceDistance = pendingTransaction.getNonce() - senderNonce;

    final TransactionAddedResult nonceChecksResult =
        nonceChecks(pendingTransaction, senderNonce, nonceDistance);
    if (nonceChecksResult != null) {
      metrics.incrementRejected(
          pendingTransaction.isReceivedFromLocalSource(),
          nonceChecksResult.maybeInvalidReason().get(),
          "layertxpool");
      return nonceChecksResult;
    }

    final TransactionAddedResult result =
        prioritizedTransactions.add(pendingTransaction, (int) nonceDistance);

    assert prioritizedTransactions.consistencyCheck(new HashMap<>());

    return result;
  }

  private void logTxToCSV(final PendingTransaction pendingTransaction, final long senderNonce) {
    // csv fields: sequence, addedAt, sender, sender_nonce, nonce, type, hash, rlp
    LOG_TX_CSV
        .atTrace()
        .setMessage("T,{},{},{},{},{},{},{},{}")
        .addArgument(pendingTransaction.getSequence())
        .addArgument(pendingTransaction.getAddedToPoolAt())
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
                              break;
                            case DELETE_TRANSACTION_AND_CONTINUE:
                              invalidTransactions.add(candidatePendingTx);
                              break;
                            case COMPLETE_OPERATION:
                              completed.set(true);
                              break;
                          }
                        }));

    invalidTransactions.forEach(prioritizedTransactions::invalidate);
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
  public synchronized Set<PendingTransaction> getPendingTransactions() {
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
      final FeeMarket feeMarket) {
    LOG.atDebug()
        .setMessage("Managing new added block {}")
        .addArgument(blockHeader::toLogString)
        .log();

    final var maxConfirmedNonceBySender = maxConfirmedNonceBySender(confirmedTransactions);

    prioritizedTransactions.blockAdded(feeMarket, blockHeader, maxConfirmedNonceBySender);

    logBlockHeaderToCSV(blockHeader, maxConfirmedNonceBySender);

    assert prioritizedTransactions.consistencyCheck(new HashMap<>());
  }

  private void logBlockHeaderToCSV(
      final BlockHeader blockHeader, final Map<Address, Long> maxConfirmedNonceBySender) {
    // block number, block hash, sender, max nonce ..., rlp
    LOG_TX_CSV
        .atTrace()
        .setMessage("B,{},{},{},{}")
        .addArgument(blockHeader.getNumber())
        .addArgument(blockHeader.getBlockHash())
        .addArgument(
            () ->
                maxConfirmedNonceBySender.entrySet().stream()
                    .map(e -> e.getKey().toHexString() + "," + e.getValue())
                    .collect(Collectors.joining(",")))
        .addArgument(
            () -> {
              final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
              blockHeader.writeTo(rlp);
              return rlp.encoded().toHexString();
            })
        .log();
  }

  private Map<Address, Long> maxConfirmedNonceBySender(
      final List<Transaction> confirmedTransactions) {
    return confirmedTransactions.stream()
        .collect(
            groupingBy(
                Transaction::getSender, mapping(Transaction::getNonce, reducing(0L, Math::max))));
  }

  @Override
  public synchronized String toTraceLog() {
    return "";
  }

  @Override
  public synchronized String logStats() {
    final String statsString = prioritizedTransactions.logStats();
    LOG_TX_CSV.atTrace().setMessage("S,{}").addArgument(statsString).log();
    return statsString;
  }
}

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

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LayeredPendingTransactions implements PendingTransactions {
  private static final Logger LOG = LoggerFactory.getLogger(LayeredPendingTransactions.class);
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
        new PendingTransaction.Remote(transaction, System.currentTimeMillis()), maybeSenderAccount);
  }

  @Override
  public synchronized TransactionAddedResult addLocalTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    final TransactionAddedResult addedResult =
        addTransaction(
            new PendingTransaction.Local(transaction, System.currentTimeMillis()),
            maybeSenderAccount);
    if (addedResult.isSuccess()) {
      localSenders.add(transaction.getSender());
    }
    return addedResult;
  }

  private TransactionAddedResult addTransaction(
      final PendingTransaction pendingTransaction, final Optional<Account> maybeSenderAccount) {

    final long senderNonce = maybeSenderAccount.map(AccountState::getNonce).orElse(0L);

    final long nonceDistance = pendingTransaction.getNonce() - senderNonce;

    final TransactionAddedResult nonceChecksResult =
        nonceChecks(pendingTransaction, senderNonce, nonceDistance);
    if (nonceChecksResult != null) {
      metrics.incrementInvalid(
          pendingTransaction.isReceivedFromLocalSource(),
          nonceChecksResult.maybeInvalidReason().get());
      return nonceChecksResult;
    }

    final TransactionAddedResult result =
        prioritizedTransactions.add(pendingTransaction, (int) nonceDistance);

    if (LOG.isDebugEnabled()) {
      consistencyCheck();
    }

    return result;
  }

  @Nullable
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
    final List<PendingTransaction> transactionsToRemove = new ArrayList<>();
    final Set<Hash> alreadyChecked = new HashSet<>();
    final AtomicBoolean completed = new AtomicBoolean(false);

    prioritizedTransactions.stream()
        .takeWhile(unsed -> !completed.get())
        .forEach(
            highPrioPendingTx ->
                prioritizedTransactions.stream(highPrioPendingTx.getSender())
                    .takeWhile(unsed -> !completed.get())
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
                              transactionsToRemove.add(candidatePendingTx);
                              break;
                            case COMPLETE_OPERATION:
                              completed.set(true);
                              break;
                          }
                        }));

    transactionsToRemove.forEach(prioritizedTransactions::remove);
  }

  @Override
  public long maxSize() {
    return -1;
  }

  @Override
  public int size() {
    return prioritizedTransactions.count();
  }

  @Override
  public boolean containsTransaction(final Transaction transaction) {
    return prioritizedTransactions.contains(transaction);
  }

  @Override
  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return prioritizedTransactions.getByHash(transactionHash);
  }

  @Override
  public Set<PendingTransaction> getPendingTransactions() {
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

    prioritizedTransactions.removeConfirmed(maxConfirmedNonceBySender(confirmedTransactions));

    prioritizedTransactions.blockAdded(blockHeader, feeMarket);
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
    //
    //    return "Ready by sender ("
    //        + toTraceLog(readyBySender)
    //        + "), Sparse by sender ("
    //        + sparseTransactions.toTraceLog()
    //        + "); "
    //        + prioritizedTransactions.toTraceLog();
    return null;
  }

  //  private String toTraceLog(final Map<Address, NavigableMap<Long, PendingTransaction>>
  // senderTxs) {
  //    return senderTxs.entrySet().stream()
  //        .map(
  //            e ->
  //                e.getKey()
  //                    + "="
  //                    + e.getValue().entrySet().stream()
  //                        .map(etx -> etx.getKey() + ":" + etx.getValue().toTraceLog())
  //                        .collect(Collectors.joining(",", "[", "]")))
  //        .collect(Collectors.joining(";"));
  //  }

  //  private synchronized int getReadyCount() {
  //    return readyBySender.values().stream().mapToInt(Map::size).sum();
  //  }

  /*
    private synchronized int getSparseCount() {
      return sparseBySender.values().stream().mapToInt(Map::size).sum();
    }
  */
  @Override
  public synchronized String logStats() {
    //
    //    return "Pending: "
    //        + pendingTransactions.size()
    //        + ", Ready: "
    //        + getReadyCount()
    //        + ", Prioritized: "
    //        + prioritizedTransactions.size()
    //        + ", Sparse: "
    //        + sparseTransactions.logStats()
    //        + ", Space used: "
    //        + spaceUsed
    //        + ", Prioritized stats: "
    //        + prioritizedTransactions.logStats();
    return null;
  }

  private void consistencyCheck() {
    //    // check numbers
    //    final int pendingTotal = pendingTransactions.size();
    //    final int readyTotal = getReadyCount();
    //    final int sparseTotal = sparseTransactions.getSparseCount();
    //    if (pendingTotal != readyTotal + sparseTotal) {
    //      LOG.error("Pending != Ready + Sparse ({} != {} + {})", pendingTotal, readyTotal,
    // sparseTotal);
    //    }
    //
    //    readyCheck();
    //
    //    sparseTransactions.consistencyCheck();
    //
    //    prioritizedCheck();
  }
  //
  //  private void readyCheck() {
  //    if (orderByMaxFee.size() != readyBySender.size()) {
  //      LOG.error(
  //          "OrderByMaxFee != ReadyBySender ({} != {})", orderByMaxFee.size(),
  // readyBySender.size());
  //    }
  //
  //    orderByMaxFee.stream()
  //        .filter(
  //            tx -> {
  //              if (readyBySender.containsKey(tx.getSender())) {
  //                if (readyBySender.get(tx.getSender()).firstEntry().getValue().equals(tx)) {
  //                  return false;
  //                }
  //              }
  //              return true;
  //            })
  //        .forEach(tx -> LOG.error("OrderByMaxFee tx {} the first ReadyBySender", tx));
  //
  //    orderByMaxFee.stream()
  //        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
  //        .forEach(tx -> LOG.error("OrderByMaxFee tx {} not found in PendingTransactions", tx));
  //
  //    readyBySender.values().stream()
  //        .flatMap(senderTxs -> senderTxs.values().stream())
  //        .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
  //        .forEach(tx -> LOG.error("ReadyBySender tx {} not found in PendingTransactions", tx));
  //  }
  /*
    private void sparseCheck(final int sparseTotal) {
      if (sparseTotal != sparseEvictionOrder.size()) {
        LOG.error("Sparse != Eviction order ({} != {})", sparseTotal, sparseEvictionOrder.size());
      }

      sparseEvictionOrder.stream()
          .filter(
              tx -> {
                if (sparseBySender.containsKey(tx.getSender())) {
                  if (sparseBySender.get(tx.getSender()).containsKey(tx.getNonce())) {
                    if (sparseBySender.get(tx.getSender()).get(tx.getNonce()).equals(tx)) {
                      return false;
                    }
                  }
                }
                return true;
              })
          .forEach(tx -> LOG.error("SparseEvictionOrder tx {} not found in SparseBySender", tx));

      sparseEvictionOrder.stream()
          .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
          .forEach(tx -> LOG.error("SparseEvictionOrder tx {} not found in PendingTransactions", tx));

      sparseBySender.values().stream()
          .flatMap(senderTxs -> senderTxs.values().stream())
          .filter(tx -> !pendingTransactions.containsKey(tx.getHash()))
          .forEach(tx -> LOG.error("SparseBySender tx {} not found in PendingTransactions", tx));
    }
  */
  //
  //  private void prioritizedCheck() {
  //    prioritizedTransactions
  //        .prioritizedTransactions()
  //        .forEachRemaining(
  //            ptx -> {
  //              if (!pendingTransactions.containsKey(ptx.getHash())) {
  //                LOG.error("PrioritizedTransaction {} not found in PendingTransactions", ptx);
  //              }
  //
  //              if (!readyBySender.containsKey(ptx.getSender())
  //                  || !readyBySender.get(ptx.getSender()).containsKey(ptx.getNonce())
  //                  || !readyBySender.get(ptx.getSender()).get(ptx.getNonce()).equals(ptx)) {
  //                LOG.error("PrioritizedTransaction {} not found in ReadyBySender", ptx);
  //              }
  //
  //              if (sparseBySender.containsKey(ptx.getSender())
  //                  && sparseBySender.get(ptx.getSender()).containsKey(ptx.getNonce())
  //                  && sparseBySender.get(ptx.getSender()).get(ptx.getNonce()).equals(ptx)) {
  //                LOG.error("PrioritizedTransaction {} found in SparseBySender", ptx);
  //              }
  //            });
  //
  //    prioritizedTransactions.consistencyCheck();
  //  }
}

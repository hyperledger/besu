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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionAddedResult;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPrioritizedTransactions {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractPrioritizedTransactions.class);

  protected final Clock clock;
  protected final TransactionPoolConfiguration poolConfig;

  protected final Map<Hash, PendingTransaction> prioritizedPendingTransactions;

  protected final TreeSet<PendingTransaction> orderByFee;
  protected final Map<Address, Long> expectedNonceForSender;

  protected final BiFunction<PendingTransaction, PendingTransaction, Boolean>
      transactionReplacementTester;

  public AbstractPrioritizedTransactions(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    this.poolConfig = poolConfig;
    this.prioritizedPendingTransactions = new HashMap<>(poolConfig.getTxPoolMaxSize());
    this.expectedNonceForSender = new HashMap<>();
    this.clock = clock;
    this.transactionReplacementTester = transactionReplacementTester;
    this.orderByFee = new TreeSet<>(this::compareByFee);
  }

  protected abstract int compareByFee(final PendingTransaction pt1, final PendingTransaction pt2);

  public PrioritizeResult prioritizeTransaction(
      final PendingTransaction addedReadyTransaction,
      final long senderNonce,
      final TransactionAddedResult addResult) {

    final var prioritizedTxReplaced =
        addResult
            .maybeReplacedTransaction()
            .filter(replacedTx -> prioritizedPendingTransactions.containsKey(replacedTx.getHash()))
            .map(
                replacedTx -> {
                  traceLambda(
                      LOG,
                      "Replace existing transaction {}, with new transaction {}",
                      replacedTx::toTraceLog,
                      addedReadyTransaction::toTraceLog);

                  removeReplacedPrioritizedTransaction(replacedTx);
                  addPrioritizedTransaction(addedReadyTransaction, true);
                  return true;
                })
            .orElse(false);

    if (prioritizedTxReplaced) {
      return PrioritizeResult.REPLACEMENT;
    }

    final var expectedNonce =
        expectedNonceForSender.getOrDefault(addedReadyTransaction.getSender(), senderNonce);

    // only add to prioritized if it appends to the already prioritized for its sender, without
    // gaps
    if (addedReadyTransaction.getNonce() != expectedNonce) {
      traceLambda(
          LOG,
          "Not adding transaction {} to prioritized list since expected next nonce for this sender is {}",
          addedReadyTransaction::toTraceLog,
          () -> expectedNonce);
      return PrioritizeResult.NOT_PRIORITIZED;
    }

    if (prioritizedPendingTransactions.size() >= poolConfig.getMaxPrioritizedTransactions()) {
      LOG.trace("Max number of prioritized transactions reached");

      final var currentLeastPriorityTx = orderByFee.first();
      if (compareByFee(addedReadyTransaction, currentLeastPriorityTx) <= 0) {
        traceLambda(
            LOG,
            "Not adding incoming transaction {} to the prioritized list, "
                + "since it is less valuable than the current least priority transactions {}",
            addedReadyTransaction::toTraceLog,
            currentLeastPriorityTx::toTraceLog);
        return PrioritizeResult.NOT_PRIORITIZED;
      }
      if (currentLeastPriorityTx.getSender().equals(addedReadyTransaction.getSender())) {
        traceLambda(
            LOG,
            "Not adding incoming transaction {} to the prioritized list, "
                + "since it is from the same sender as the least valuable one {}",
            addedReadyTransaction::toTraceLog,
            currentLeastPriorityTx::toTraceLog);
        return PrioritizeResult.NOT_PRIORITIZED;
      }

      traceLambda(
          LOG,
          "Demote transactions for the sender of the current least priority transaction {}, "
              + "to make space for the incoming transaction {}",
          currentLeastPriorityTx::toTraceLog,
          addedReadyTransaction::toTraceLog);
      addPrioritizedTransaction(addedReadyTransaction);
      return PrioritizeResult.prioritizedDemotingTransaction(currentLeastPriorityTx);
    }

    addPrioritizedTransaction(addedReadyTransaction);
    return PrioritizeResult.PRIORITIZED;
  }

  public void demoteTransactions(
      final Address sender,
      final Collection<PendingTransaction> demotedTransactions,
      final Optional<Long> maybeLastValidSenderNonce) {

    for (final var pendingTransaction : demotedTransactions) {
      if (prioritizedPendingTransactions.remove(pendingTransaction.getHash()) != null) {
        removeFromOrderedTransactions(pendingTransaction, false);
        traceLambda(LOG, "Demoted transaction {}", pendingTransaction::toTraceLog);
      } else {
        break;
      }
    }

    maybeLastValidSenderNonce.ifPresentOrElse(
        lastValidNonce -> expectedNonceForSender.put(sender, lastValidNonce),
        () -> expectedNonceForSender.remove(sender));
  }

  public int size() {
    return prioritizedPendingTransactions.size();
  }

  public boolean containsTransaction(final Transaction transaction) {
    return prioritizedPendingTransactions.containsKey(transaction.getHash());
  }

  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return Optional.ofNullable(prioritizedPendingTransactions.get(transactionHash))
        .map(PendingTransaction::getTransaction);
  }

  private void removeReplacedPrioritizedTransaction(final PendingTransaction replacedTx) {
    final PendingTransaction removedPendingTransaction =
        prioritizedPendingTransactions.remove(replacedTx.getHash());
    removeFromOrderedTransactions(removedPendingTransaction, false);
  }

  protected abstract void removeFromOrderedTransactions(
      final PendingTransaction removedPendingTx, final boolean addedToBlock);

  public Iterator<PendingTransaction> prioritizedTransactions() {
    return orderByFee.descendingIterator();
  }

  public void addPrioritizedTransaction(final PendingTransaction prioritizedTx) {
    addPrioritizedTransaction(prioritizedTx, false);
  }

  private void addPrioritizedTransaction(
      final PendingTransaction prioritizedTx, final boolean isReplacement) {
    prioritizedPendingTransactions.put(prioritizedTx.getHash(), prioritizedTx);
    orderByFee.add(prioritizedTx);
    if (!isReplacement) {
      expectedNonceForSender.put(prioritizedTx.getSender(), prioritizedTx.getNonce() + 1);
    }
  }

  public void removeConfirmedTransactions(
      final Map<Address, Optional<Long>> orderedConfirmedNonceBySender,
      final List<PendingTransaction> confirmedTransactions) {

    confirmedTransactions.stream()
        .map(PendingTransaction::getHash)
        .map(prioritizedPendingTransactions::remove)
        .filter(Objects::nonNull)
        .forEach(tx -> removeFromOrderedTransactions(tx, true));

    // update expected nonce for senders
    for (final var confirmedNonceEntry : orderedConfirmedNonceBySender.entrySet()) {
      expectedNonceForSender.computeIfPresent(
          confirmedNonceEntry.getKey(),
          (sender, expectedNonce) -> {
            if (confirmedNonceEntry.getValue().get() >= expectedNonce - 1) {
              // all the current prioritized transactions for the sender are confirmed, remove the
              // entry
              return null;
            }
            return expectedNonce;
          });
    }
  }

  public abstract void manageBlockAdded(final BlockHeader blockHeader, final FeeMarket feeMarket);

  public boolean isPromotable(final PendingTransaction pendingTransaction) {
    return !prioritizedPendingTransactions.containsKey(pendingTransaction.getHash());
  }

  public String toTraceLog() {
    return "Prioritized size "
        + prioritizedPendingTransactions.size()
        + " content in order "
        + StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(prioritizedTransactions(), Spliterator.ORDERED),
                false)
            .map(PendingTransaction::toTraceLog)
            .collect(Collectors.joining("; "))
        + ", Expected nonce for sender size "
        + expectedNonceForSender.size()
        + " content "
        + expectedNonceForSender;
  }

  public abstract String logStats();

  public void reset() {
    prioritizedPendingTransactions.clear();
    orderByFee.clear();
    expectedNonceForSender.clear();
  }

  public void consistencyCheck() {
    if (prioritizedPendingTransactions.size() != orderByFee.size()) {
      LOG.error(
          "PrioritizedTransaction != OrderByFee ({} != {})",
          prioritizedPendingTransactions.size(),
          orderByFee.size());
    }

    orderByFee.stream()
        .filter(tx -> !prioritizedPendingTransactions.containsKey(tx.getHash()))
        .forEach(
            tx -> LOG.error("OrderByFee transaction {} not found in PrioritizedTransactions", tx));

    prioritizedPendingTransactions.values().stream()
        .collect(
            Collectors.groupingBy(
                PendingTransaction::getSender,
                Collectors.reducing(
                    0L, PendingTransaction::getNonce, (Long a, Long b) -> a > b ? a : b)))
        .forEach(
            (sender, nonce) -> {
              if (!expectedNonceForSender.containsKey(sender)) {
                LOG.error("Sender {} not found in expected nonce", sender);
              } else if (!expectedNonceForSender.get(sender).equals(nonce + 1)) {
                LOG.error(
                    "Current expected nonce {} for sender {} does not match PrioritizedTransactions nonce {}",
                    expectedNonceForSender.get(sender),
                    sender,
                    nonce + 1);
                LOG.info(toTraceLog());
              }
            });
  }

  public static class PrioritizeResult {
    static final PrioritizeResult REPLACEMENT = new PrioritizeResult(true, true, null);
    static final PrioritizeResult NOT_PRIORITIZED = new PrioritizeResult(false, false, null);
    static final PrioritizeResult PRIORITIZED = new PrioritizeResult(true, false, null);
    final boolean prioritized;
    final boolean replacement;
    final Optional<PendingTransaction> maybeDemotedTransaction;

    private PrioritizeResult(
        final boolean prioritized,
        final boolean replacement,
        final PendingTransaction demotedTransaction) {
      this.prioritized = prioritized;
      this.replacement = replacement;
      this.maybeDemotedTransaction = Optional.ofNullable(demotedTransaction);
    }

    static PrioritizeResult prioritizedDemotingTransaction(
        final PendingTransaction demotedTransaction) {
      return new PrioritizeResult(true, false, demotedTransaction);
    }

    public boolean isPrioritized() {
      return prioritized;
    }

    public boolean isReplacement() {
      return replacement;
    }

    public Optional<PendingTransaction> maybeDemotedTransaction() {
      return maybeDemotedTransaction;
    }

    public String toMetricLabel() {
      if (replacement) {
        return "replaced";
      }
      if (prioritized) {
        return maybeDemotedTransaction
            .map(unused -> "prioritized_with_demoting")
            .orElse("prioritized");
      }
      return "not_prioritized";
    }
  }
}

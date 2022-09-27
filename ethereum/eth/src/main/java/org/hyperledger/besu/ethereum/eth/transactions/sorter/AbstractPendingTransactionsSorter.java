/*
 * Copyright ConsenSys AG.
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

import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.ADDED;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.LOWER_NONCE_INVALID_TRANSACTION_KNOWN;
import static org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionAddedStatus.REJECTED_UNDERPRICED_REPLACEMENT;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.AccountTransactionOrder;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionDroppedListener;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactionListener;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionsForSenderInfo;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the current set of pending transactions with the ability to iterate them based on priority
 * for mining or look-up by hash.
 *
 * <p>This class is safe for use across multiple threads.
 */
public abstract class AbstractPendingTransactionsSorter {
  private static final int DEFAULT_LOWEST_INVALID_KNOWN_NONCE_CACHE = 10_000;
  private static final Logger LOG =
      LoggerFactory.getLogger(AbstractPendingTransactionsSorter.class);

  protected final Clock clock;
  protected final TransactionPoolConfiguration poolConfig;

  protected final Object lock = new Object();
  protected final Map<Hash, TransactionInfo> pendingTransactions = new ConcurrentHashMap<>();

  protected final Map<Address, TransactionsForSenderInfo> transactionsBySender =
      new ConcurrentHashMap<>();

  protected final LowestInvalidNonceCache lowestInvalidKnownNonceCache =
      new LowestInvalidNonceCache(DEFAULT_LOWEST_INVALID_KNOWN_NONCE_CACHE);
  protected final Subscribers<PendingTransactionListener> pendingTransactionSubscribers =
      Subscribers.create();

  protected final Subscribers<PendingTransactionDroppedListener> transactionDroppedListeners =
      Subscribers.create();

  protected final LabelledMetric<Counter> transactionRemovedCounter;
  protected final Counter localTransactionAddedCounter;
  protected final Counter remoteTransactionAddedCounter;

  protected final TransactionPoolReplacementHandler transactionReplacementHandler;
  protected final Supplier<BlockHeader> chainHeadHeaderSupplier;

  public AbstractPendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      final Clock clock,
      final MetricsSystem metricsSystem,
      final Supplier<BlockHeader> chainHeadHeaderSupplier) {
    this.poolConfig = poolConfig;
    this.clock = clock;
    this.chainHeadHeaderSupplier = chainHeadHeaderSupplier;
    this.transactionReplacementHandler =
        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());
    final LabelledMetric<Counter> transactionAddedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_added_total",
            "Count of transactions added to the transaction pool",
            "source");
    localTransactionAddedCounter = transactionAddedCounter.labels("local");
    remoteTransactionAddedCounter = transactionAddedCounter.labels("remote");

    transactionRemovedCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_removed_total",
            "Count of transactions removed from the transaction pool",
            "source",
            "operation");

    metricsSystem.createIntegerGauge(
        BesuMetricCategory.TRANSACTION_POOL,
        "transactions",
        "Current size of the transaction pool",
        pendingTransactions::size);
  }

  public void evictOldTransactions() {
    final Instant removeTransactionsBefore =
        clock.instant().minus(poolConfig.getPendingTxRetentionPeriod(), ChronoUnit.HOURS);

    pendingTransactions.values().stream()
        .filter(transaction -> transaction.getAddedToPoolAt().isBefore(removeTransactionsBefore))
        .forEach(
            transactionInfo -> {
              traceLambda(LOG, "Evicted {} due to age", transactionInfo::toTraceLog);
              removeTransaction(transactionInfo.getTransaction());
            });
  }

  public List<Transaction> getLocalTransactions() {
    return pendingTransactions.values().stream()
        .filter(TransactionInfo::isReceivedFromLocalSource)
        .map(TransactionInfo::getTransaction)
        .collect(Collectors.toList());
  }

  public TransactionAddedStatus addRemoteTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {

    if (lowestInvalidKnownNonceCache.hasInvalidLowerNonce(transaction)) {
      debugLambda(
          LOG,
          "Dropping transaction {} since the sender has an invalid transaction with lower nonce",
          transaction::toTraceLog);
      return LOWER_NONCE_INVALID_TRANSACTION_KNOWN;
    }

    final TransactionInfo transactionInfo =
        new TransactionInfo(transaction, false, clock.instant());
    final TransactionAddedStatus transactionAddedStatus =
        addTransaction(transactionInfo, maybeSenderAccount);
    if (transactionAddedStatus.equals(ADDED)) {
      lowestInvalidKnownNonceCache.registerValidTransaction(transaction);
      remoteTransactionAddedCounter.inc();
    }
    return transactionAddedStatus;
  }

  @VisibleForTesting
  public TransactionAddedStatus addLocalTransaction(
      final Transaction transaction, final Optional<Account> maybeSenderAccount) {
    final TransactionAddedStatus transactionAdded =
        addTransaction(new TransactionInfo(transaction, true, clock.instant()), maybeSenderAccount);
    if (transactionAdded.equals(ADDED)) {
      localTransactionAddedCounter.inc();
    }
    return transactionAdded;
  }

  public void removeTransaction(final Transaction transaction) {
    doRemoveTransaction(transaction, false);
    notifyTransactionDropped(transaction);
  }

  public void transactionAddedToBlock(final Transaction transaction) {
    doRemoveTransaction(transaction, true);
    lowestInvalidKnownNonceCache.registerValidTransaction(transaction);
  }

  protected void incrementTransactionRemovedCounter(
      final boolean receivedFromLocalSource, final boolean addedToBlock) {
    final String location = receivedFromLocalSource ? "local" : "remote";
    final String operation = addedToBlock ? "addedToBlock" : "dropped";
    transactionRemovedCounter.labels(location, operation).inc();
  }

  // There's a small edge case here we could encounter.
  // When we pass an upgrade block that has a new transaction type, we start allowing transactions
  // of that new type into our pool.
  // If we then reorg to a block lower than the upgrade block height _and_ we create a block, that
  // block could end up with transactions of the new type.
  // This seems like it would be very rare but worth it to document that we don't handle that case
  // right now.
  public void selectTransactions(final TransactionSelector selector) {
    synchronized (lock) {
      final Set<Transaction> transactionsToRemove = new HashSet<>();
      final Map<Address, AccountTransactionOrder> accountTransactions = new HashMap<>();
      final Iterator<TransactionInfo> prioritizedTransactions = prioritizedTransactions();
      while (prioritizedTransactions.hasNext()) {
        final TransactionInfo highestPriorityTransactionInfo = prioritizedTransactions.next();
        final AccountTransactionOrder accountTransactionOrder =
            accountTransactions.computeIfAbsent(
                highestPriorityTransactionInfo.getSender(), this::createSenderTransactionOrder);

        for (final Transaction transactionToProcess :
            accountTransactionOrder.transactionsToProcess(
                highestPriorityTransactionInfo.getTransaction())) {
          final TransactionSelectionResult result =
              selector.evaluateTransaction(transactionToProcess);
          switch (result) {
            case DELETE_TRANSACTION_AND_CONTINUE:
              transactionsToRemove.add(transactionToProcess);
              signalInvalidTransaction(transactionToProcess).forEach(transactionsToRemove::add);
              break;
            case CONTINUE:
              break;
            case COMPLETE_OPERATION:
              transactionsToRemove.forEach(this::removeTransaction);
              return;
            default:
              throw new RuntimeException("Illegal value for TransactionSelectionResult.");
          }
        }
      }
      transactionsToRemove.forEach(this::removeTransaction);
    }
  }

  protected AccountTransactionOrder createSenderTransactionOrder(final Address address) {
    return new AccountTransactionOrder(
        transactionsBySender
            .get(address)
            .streamTransactionInfos()
            .map(TransactionInfo::getTransaction));
  }

  protected TransactionAddedStatus addTransactionForSenderAndNonce(
      final TransactionInfo transactionInfo, final Optional<Account> maybeSenderAccount) {

    TransactionsForSenderInfo txsSenderInfo =
        transactionsBySender.computeIfAbsent(
            transactionInfo.getSender(),
            address -> new TransactionsForSenderInfo(maybeSenderAccount));

    TransactionInfo existingTxInfo =
        txsSenderInfo.getTransactionInfoForNonce(transactionInfo.getNonce());

    final Optional<Transaction> maybeReplacedTransaction;
    if (existingTxInfo != null) {
      if (!transactionReplacementHandler.shouldReplace(
          existingTxInfo, transactionInfo, chainHeadHeaderSupplier.get())) {
        traceLambda(
            LOG, "Reject underpriced transaction replacement {}", transactionInfo::toTraceLog);
        return REJECTED_UNDERPRICED_REPLACEMENT;
      }
      traceLambda(
          LOG,
          "Replace existing transaction {}, with new transaction {}",
          existingTxInfo::toTraceLog,
          transactionInfo::toTraceLog);
      maybeReplacedTransaction = Optional.of(existingTxInfo.getTransaction());
    } else {
      maybeReplacedTransaction = Optional.empty();
    }

    txsSenderInfo.updateSenderAccount(maybeSenderAccount);
    txsSenderInfo.addTransactionToTrack(transactionInfo);
    traceLambda(LOG, "Tracked transaction by sender {}", txsSenderInfo::toTraceLog);
    maybeReplacedTransaction.ifPresent(this::removeTransaction);
    return ADDED;
  }

  protected void removeTransactionInfoTrackedBySenderAndNonce(
      final TransactionInfo transactionInfo) {
    final Transaction transaction = transactionInfo.getTransaction();
    Optional.ofNullable(transactionsBySender.get(transaction.getSender()))
        .ifPresent(
            transactionsForSender -> {
              transactionsForSender.removeTrackedTransactionInfo(transactionInfo);
              if (transactionsForSender.transactionCount() == 0) {
                LOG.trace(
                    "Removing sender {} from transactionBySender since no more tracked transactions",
                    transaction.getSender());
                transactionsBySender.remove(transaction.getSender());
              } else {
                traceLambda(
                    LOG,
                    "Tracked transaction by sender {} after the removal of {}",
                    transactionsForSender::toTraceLog,
                    transaction::toTraceLog);
              }
            });
  }

  protected void notifyTransactionAdded(final Transaction transaction) {
    pendingTransactionSubscribers.forEach(listener -> listener.onTransactionAdded(transaction));
  }

  protected void notifyTransactionDropped(final Transaction transaction) {
    transactionDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
  }

  public long maxSize() {
    return poolConfig.getTxPoolMaxSize();
  }

  public int size() {
    return pendingTransactions.size();
  }

  public boolean containsTransaction(final Hash transactionHash) {
    return pendingTransactions.containsKey(transactionHash);
  }

  public Optional<Transaction> getTransactionByHash(final Hash transactionHash) {
    return Optional.ofNullable(pendingTransactions.get(transactionHash))
        .map(TransactionInfo::getTransaction);
  }

  public Set<TransactionInfo> getTransactionInfo() {
    return new HashSet<>(pendingTransactions.values());
  }

  public long subscribePendingTransactions(final PendingTransactionListener listener) {
    return pendingTransactionSubscribers.subscribe(listener);
  }

  public void unsubscribePendingTransactions(final long id) {
    pendingTransactionSubscribers.unsubscribe(id);
  }

  public long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return transactionDroppedListeners.subscribe(listener);
  }

  public void unsubscribeDroppedTransactions(final long id) {
    transactionDroppedListeners.unsubscribe(id);
  }

  public OptionalLong getNextNonceForSender(final Address sender) {
    final TransactionsForSenderInfo transactionsForSenderInfo = transactionsBySender.get(sender);
    return transactionsForSenderInfo == null
        ? OptionalLong.empty()
        : transactionsForSenderInfo.maybeNextNonce();
  }

  public abstract void manageBlockAdded(final Block block);

  protected abstract void doRemoveTransaction(
      final Transaction transaction, final boolean addedToBlock);

  protected abstract Iterator<TransactionInfo> prioritizedTransactions();

  protected abstract TransactionAddedStatus addTransaction(
      final TransactionInfo transactionInfo, final Optional<Account> maybeSenderAccount);

  Optional<TransactionInfo> lowestValueTxForRemovalBySender(
      final NavigableSet<TransactionInfo> txSet) {
    return txSet.descendingSet().stream()
        .filter(
            tx ->
                transactionsBySender
                    .get(tx.getSender())
                    .maybeLastTx()
                    .filter(tx::equals)
                    .isPresent())
        .findFirst();
  }

  public String toTraceLog(
      final boolean withTransactionsBySender, final boolean withLowestInvalidNonce) {
    synchronized (lock) {
      StringBuilder sb =
          new StringBuilder(
              "Transactions in order { "
                  + StreamSupport.stream(
                          Spliterators.spliteratorUnknownSize(
                              prioritizedTransactions(), Spliterator.ORDERED),
                          false)
                      .map(
                          txInfo -> {
                            TransactionsForSenderInfo txsSenderInfo =
                                transactionsBySender.get(txInfo.getSender());
                            long nonceDistance =
                                txInfo.getNonce() - txsSenderInfo.getSenderAccountNonce();
                            return "nonceDistance: "
                                + nonceDistance
                                + ", senderAccount: "
                                + txsSenderInfo.getSenderAccount()
                                + ", "
                                + txInfo.toTraceLog();
                          })
                      .collect(Collectors.joining("; "))
                  + " }");

      if (withTransactionsBySender) {
        sb.append(
            ", Transactions by sender { "
                + transactionsBySender.entrySet().stream()
                    .map(e -> "(" + e.getKey() + ") " + e.getValue().toTraceLog())
                    .collect(Collectors.joining("; "))
                + " }");
      }
      if (withLowestInvalidNonce) {
        sb.append(
            ", Lowest invalid nonce by sender cache {"
                + lowestInvalidKnownNonceCache.toTraceLog()
                + "}");
      }
      return sb.toString();
    }
  }

  public List<Transaction> signalInvalidTransaction(final Transaction transaction) {
    final long invalidNonce = lowestInvalidKnownNonceCache.registerInvalidTransaction(transaction);

    TransactionsForSenderInfo txsForSender = transactionsBySender.get(transaction.getSender());
    if (txsForSender != null) {
      return txsForSender
          .streamTransactionInfos()
          .filter(txInfo -> txInfo.getTransaction().getNonce() > invalidNonce)
          .peek(
              txInfo ->
                  traceLambda(
                      LOG,
                      "Transaction {} piked for removal since there is a lowest invalid nonce {} for the sender",
                      txInfo::toTraceLog,
                      () -> invalidNonce))
          .map(TransactionInfo::getTransaction)
          .collect(Collectors.toList());
    }
    return List.of();
  }

  /**
   * Tracks the additional metadata associated with transactions to enable prioritization for mining
   * and deciding which transactions to drop when the transaction pool reaches its size limit.
   */
  public static class TransactionInfo {

    private static final AtomicLong TRANSACTIONS_ADDED = new AtomicLong();
    private final Transaction transaction;
    private final boolean receivedFromLocalSource;
    private final Instant addedToPoolAt;
    private final long sequence; // Allows prioritization based on order transactions are added

    public TransactionInfo(
        final Transaction transaction,
        final boolean receivedFromLocalSource,
        final Instant addedToPoolAt) {
      this.transaction = transaction;
      this.receivedFromLocalSource = receivedFromLocalSource;
      this.addedToPoolAt = addedToPoolAt;
      this.sequence = TRANSACTIONS_ADDED.getAndIncrement();
    }

    public Transaction getTransaction() {
      return transaction;
    }

    public Wei getGasPrice() {
      return transaction.getGasPrice().orElse(Wei.ZERO);
    }

    public long getSequence() {
      return sequence;
    }

    public long getNonce() {
      return transaction.getNonce();
    }

    public Address getSender() {
      return transaction.getSender();
    }

    public boolean isReceivedFromLocalSource() {
      return receivedFromLocalSource;
    }

    public Hash getHash() {
      return transaction.getHash();
    }

    public Instant getAddedToPoolAt() {
      return addedToPoolAt;
    }

    public static List<Transaction> toTransactionList(
        final Collection<TransactionInfo> transactionsInfo) {
      return transactionsInfo.stream()
          .map(TransactionInfo::getTransaction)
          .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      TransactionInfo that = (TransactionInfo) o;

      return sequence == that.sequence;
    }

    @Override
    public int hashCode() {
      return 31 * (int) (sequence ^ (sequence >>> 32));
    }

    public String toTraceLog() {
      return "{sequence: "
          + sequence
          + ", addedAt: "
          + addedToPoolAt
          + ", "
          + transaction.toTraceLog()
          + "}";
    }
  }

  public enum TransactionSelectionResult {
    DELETE_TRANSACTION_AND_CONTINUE,
    CONTINUE,
    COMPLETE_OPERATION
  }

  @FunctionalInterface
  public interface TransactionSelector {

    TransactionSelectionResult evaluateTransaction(final Transaction transaction);
  }

  public enum TransactionAddedStatus {
    ALREADY_KNOWN(TransactionInvalidReason.TRANSACTION_ALREADY_KNOWN),
    REJECTED_UNDERPRICED_REPLACEMENT(TransactionInvalidReason.TRANSACTION_REPLACEMENT_UNDERPRICED),
    NONCE_TOO_FAR_IN_FUTURE_FOR_SENDER(),
    LOWER_NONCE_INVALID_TRANSACTION_KNOWN(),
    ADDED();

    private final Optional<TransactionInvalidReason> invalidReason;

    TransactionAddedStatus() {
      this.invalidReason = Optional.empty();
    }

    TransactionAddedStatus(final TransactionInvalidReason invalidReason) {
      this.invalidReason = Optional.of(invalidReason);
    }

    public Optional<TransactionInvalidReason> getInvalidReason() {
      return invalidReason;
    }
  }

  private static class LowestInvalidNonceCache {
    private final int maxSize;
    private final Map<Address, InvalidNonceStatus> lowestInvalidKnownNonceBySender;
    private final NavigableSet<InvalidNonceStatus> evictionOrder = new TreeSet<>();

    public LowestInvalidNonceCache(final int maxSize) {
      this.maxSize = maxSize;
      this.lowestInvalidKnownNonceBySender = new HashMap<>(maxSize);
    }

    synchronized long registerInvalidTransaction(final Transaction transaction) {
      final Address sender = transaction.getSender();
      final long invalidNonce = transaction.getNonce();
      final InvalidNonceStatus currStatus = lowestInvalidKnownNonceBySender.get(sender);
      if (currStatus == null) {
        final InvalidNonceStatus newStatus = new InvalidNonceStatus(sender, invalidNonce);
        addInvalidNonceStatus(newStatus);
        traceLambda(
            LOG,
            "Added invalid nonce status {}, cache status {}",
            newStatus::toString,
            this::toString);
        return invalidNonce;
      }

      updateInvalidNonceStatus(
          currStatus,
          status -> {
            if (invalidNonce < currStatus.nonce) {
              currStatus.updateNonce(invalidNonce);
            } else {
              currStatus.newHit();
            }
          });
      traceLambda(
          LOG,
          "Updated invalid nonce status {}, cache status {}",
          currStatus::toString,
          this::toString);

      return currStatus.nonce;
    }

    synchronized void registerValidTransaction(final Transaction transaction) {
      final InvalidNonceStatus currStatus =
          lowestInvalidKnownNonceBySender.get(transaction.getSender());
      if (currStatus != null) {
        evictionOrder.remove(currStatus);
        lowestInvalidKnownNonceBySender.remove(transaction.getSender());
        traceLambda(
            LOG,
            "Valid transaction, removed invalid nonce status {}, cache status {}",
            currStatus::toString,
            this::toString);
      }
    }

    synchronized boolean hasInvalidLowerNonce(final Transaction transaction) {
      final InvalidNonceStatus currStatus =
          lowestInvalidKnownNonceBySender.get(transaction.getSender());
      if (currStatus != null && transaction.getNonce() > currStatus.nonce) {
        updateInvalidNonceStatus(currStatus, status -> status.newHit());
        traceLambda(
            LOG,
            "New hit for invalid nonce status {}, cache status {}",
            currStatus::toString,
            this::toString);
        return true;
      }
      return false;
    }

    private void updateInvalidNonceStatus(
        final InvalidNonceStatus status, final Consumer<InvalidNonceStatus> updateAction) {
      evictionOrder.remove(status);
      updateAction.accept(status);
      evictionOrder.add(status);
    }

    private void addInvalidNonceStatus(final InvalidNonceStatus newStatus) {
      if (lowestInvalidKnownNonceBySender.size() >= maxSize) {
        final InvalidNonceStatus statusToEvict = evictionOrder.pollFirst();
        lowestInvalidKnownNonceBySender.remove(statusToEvict.address);
        traceLambda(
            LOG,
            "Evicted invalid nonce status {}, cache status {}",
            statusToEvict::toString,
            this::toString);
      }
      lowestInvalidKnownNonceBySender.put(newStatus.address, newStatus);
      evictionOrder.add(newStatus);
    }

    synchronized String toTraceLog() {
      return "by eviction order "
          + StreamSupport.stream(evictionOrder.spliterator(), false)
              .map(InvalidNonceStatus::toString)
              .collect(Collectors.joining("; "));
    }

    @Override
    public String toString() {
      return "LowestInvalidNonceCache{"
          + "maxSize: "
          + maxSize
          + ", currentSize: "
          + lowestInvalidKnownNonceBySender.size()
          + ", evictionOrder: [size: "
          + evictionOrder.size()
          + ", first evictable: "
          + evictionOrder.first()
          + "]"
          + '}';
    }

    private static class InvalidNonceStatus implements Comparable<InvalidNonceStatus> {
      final Address address;
      long nonce;
      long hits;
      long lastUpdate;

      InvalidNonceStatus(final Address address, final long nonce) {
        this.address = address;
        this.nonce = nonce;
        this.hits = 1L;
        this.lastUpdate = System.currentTimeMillis();
      }

      void updateNonce(final long nonce) {
        this.nonce = nonce;
        newHit();
      }

      void newHit() {
        this.hits++;
        this.lastUpdate = System.currentTimeMillis();
      }

      @Override
      public boolean equals(final Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        InvalidNonceStatus that = (InvalidNonceStatus) o;

        return address.equals(that.address);
      }

      @Override
      public int hashCode() {
        return address.hashCode();
      }

      /**
       * An InvalidNonceStatus is smaller than another when it has fewer hits and was last access
       * earlier, the address is the last tiebreaker
       *
       * @param o the object to be compared.
       * @return 0 if they are equal, negative if this is smaller, positive if this is greater
       */
      @Override
      public int compareTo(final InvalidNonceStatus o) {
        final int cmpHits = Long.compare(this.hits, o.hits);
        if (cmpHits != 0) {
          return cmpHits;
        }
        final int cmpLastUpdate = Long.compare(this.lastUpdate, o.lastUpdate);
        if (cmpLastUpdate != 0) {
          return cmpLastUpdate;
        }
        return this.address.compareTo(o.address);
      }

      @Override
      public String toString() {
        return "{"
            + "address="
            + address
            + ", nonce="
            + nonce
            + ", hits="
            + hits
            + ", lastUpdate="
            + lastUpdate
            + '}';
      }
    }
  }
}

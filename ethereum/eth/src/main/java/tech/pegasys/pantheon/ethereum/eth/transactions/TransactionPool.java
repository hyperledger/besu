/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.eth.transactions;

import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedObserver;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidationParams;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Logger;

/**
 * Maintains the set of pending transactions received from JSON-RPC or other nodes. Transactions are
 * removed automatically when they are included in a block on the canonical chain and re-added if a
 * re-org removes them from the canonical chain again.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class TransactionPool implements BlockAddedObserver {

  private static final Logger LOG = getLogger();

  private static final long SYNC_TOLERANCE = 100L;
  private static final String REMOTE = "remote";
  private static final String LOCAL = "local";
  private final PendingTransactions pendingTransactions;
  private final ProtocolSchedule<?> protocolSchedule;
  private final ProtocolContext<?> protocolContext;
  private final TransactionBatchAddedListener transactionBatchAddedListener;
  private final SyncState syncState;
  private final Wei minTransactionGasPrice;
  private final LabelledMetric<Counter> duplicateTransactionCounter;
  private final PeerTransactionTracker peerTransactionTracker;

  public TransactionPool(
      final PendingTransactions pendingTransactions,
      final ProtocolSchedule<?> protocolSchedule,
      final ProtocolContext<?> protocolContext,
      final TransactionBatchAddedListener transactionBatchAddedListener,
      final SyncState syncState,
      final EthContext ethContext,
      final PeerTransactionTracker peerTransactionTracker,
      final Wei minTransactionGasPrice,
      final MetricsSystem metricsSystem) {
    this.pendingTransactions = pendingTransactions;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.transactionBatchAddedListener = transactionBatchAddedListener;
    this.syncState = syncState;
    this.peerTransactionTracker = peerTransactionTracker;
    this.minTransactionGasPrice = minTransactionGasPrice;

    duplicateTransactionCounter =
        metricsSystem.createLabelledCounter(
            PantheonMetricCategory.TRANSACTION_POOL,
            "transactions_duplicates_total",
            "Total number of duplicate transactions received",
            "source");

    ethContext.getEthPeers().subscribeConnect(this::handleConnect);
  }

  private void handleConnect(final EthPeer peer) {
    final List<Transaction> localTransactions = getLocalTransactions();
    for (final Transaction transaction : localTransactions) {
      peerTransactionTracker.addToPeerSendQueue(peer, transaction);
    }
  }

  public List<Transaction> getLocalTransactions() {
    return pendingTransactions.getLocalTransactions();
  }

  public ValidationResult<TransactionInvalidReason> addLocalTransaction(
      final Transaction transaction) {
    if (transaction.getGasPrice().compareTo(minTransactionGasPrice) < 0) {
      return ValidationResult.invalid(TransactionInvalidReason.GAS_PRICE_TOO_LOW);
    }
    final ValidationResult<TransactionInvalidReason> validationResult =
        validateTransaction(transaction);

    validationResult.ifValid(
        () -> {
          final boolean added = pendingTransactions.addLocalTransaction(transaction);
          if (added) {
            transactionBatchAddedListener.onTransactionsAdded(singletonList(transaction));
          } else {
            duplicateTransactionCounter.labels(LOCAL).inc();
          }
        });
    return validationResult;
  }

  public void addRemoteTransactions(final Collection<Transaction> transactions) {
    if (!syncState.isInSync(SYNC_TOLERANCE)) {
      return;
    }
    final Set<Transaction> addedTransactions = new HashSet<>();
    for (final Transaction transaction : transactions) {
      if (pendingTransactions.containsTransaction(transaction.hash())) {
        // We already have this transaction, don't even validate it.
        duplicateTransactionCounter.labels(REMOTE).inc();
        continue;
      }
      if (transaction.getGasPrice().compareTo(minTransactionGasPrice) < 0) {
        continue;
      }
      final ValidationResult<TransactionInvalidReason> validationResult =
          validateTransaction(transaction);
      if (validationResult.isValid()) {
        final boolean added = pendingTransactions.addRemoteTransaction(transaction);
        if (added) {
          addedTransactions.add(transaction);
        } else {
          duplicateTransactionCounter.labels(REMOTE).inc();
        }
      } else {
        LOG.trace(
            "Validation failed ({}) for transaction {}. Discarding.",
            validationResult.getInvalidReason(),
            transaction);
      }
    }
    if (!addedTransactions.isEmpty()) {
      transactionBatchAddedListener.onTransactionsAdded(addedTransactions);
    }
  }

  public long subscribePendingTransactions(final PendingTransactionListener listener) {
    return pendingTransactions.subscribePendingTransactions(listener);
  }

  public void unsubscribePendingTransactions(final long id) {
    pendingTransactions.unsubscribePendingTransactions(id);
  }

  public long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return pendingTransactions.subscribeDroppedTransactions(listener);
  }

  public void unsubscribeDroppedTransactions(final long id) {
    pendingTransactions.unsubscribeDroppedTransactions(id);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    event.getAddedTransactions().forEach(pendingTransactions::transactionAddedToBlock);
    addRemoteTransactions(event.getRemovedTransactions());
  }

  private TransactionValidator getTransactionValidator() {
    return protocolSchedule
        .getByBlockNumber(protocolContext.getBlockchain().getChainHeadBlockNumber())
        .getTransactionValidator();
  }

  public PendingTransactions getPendingTransactions() {
    return pendingTransactions;
  }

  private ValidationResult<TransactionInvalidReason> validateTransaction(
      final Transaction transaction) {
    final ValidationResult<TransactionInvalidReason> basicValidationResult =
        getTransactionValidator().validate(transaction);
    if (!basicValidationResult.isValid()) {
      return basicValidationResult;
    }

    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader();
    if (transaction.getGasLimit() > chainHeadBlockHeader.getGasLimit()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT,
          String.format(
              "Transaction gas limit of %s exceeds block gas limit of %s",
              transaction.getGasLimit(), chainHeadBlockHeader.getGasLimit()));
    }

    return protocolContext
        .getWorldStateArchive()
        .get(chainHeadBlockHeader.getStateRoot())
        .map(
            worldState -> {
              final Account senderAccount = worldState.get(transaction.getSender());
              return getTransactionValidator()
                  .validateForSender(
                      transaction, senderAccount, TransactionValidationParams.transactionPool());
            })
        .orElseGet(() -> ValidationResult.invalid(CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE));
  }

  private BlockHeader getChainHeadBlockHeader() {
    final MutableBlockchain blockchain = protocolContext.getBlockchain();
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }

  public interface TransactionBatchAddedListener {

    void onTransactionsAdded(Iterable<Transaction> transactions);
  }
}

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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.LogManager.getLogger;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.fees.BaseFee;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.TransactionPriceCalculator;
import org.hyperledger.besu.ethereum.core.transaction.EIP1559Transaction;
import org.hyperledger.besu.ethereum.core.transaction.FrontierTransaction;
import org.hyperledger.besu.ethereum.core.transaction.TypedTransaction;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions.TransactionAddedStatus;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
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

  private static final String REMOTE = "remote";
  private static final String LOCAL = "local";
  private final PendingTransactions pendingTransactions;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final Wei minTransactionGasPrice;
  private final LabelledMetric<Counter> duplicateTransactionCounter;
  private final PeerTransactionTracker peerTransactionTracker;
  private final Optional<PeerPendingTransactionTracker> maybePeerPendingTransactionTracker;
  private final Optional<EIP1559> eip1559;
  private final TransactionPoolConfiguration configuration;

  public TransactionPool(
      final PendingTransactions pendingTransactions,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final PeerTransactionTracker peerTransactionTracker,
      final Optional<PeerPendingTransactionTracker> maybePeerPendingTransactionTracker,
      final Wei minTransactionGasPrice,
      final MetricsSystem metricsSystem,
      final Optional<EIP1559> eip1559,
      final TransactionPoolConfiguration configuration) {
    this.pendingTransactions = pendingTransactions;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.peerTransactionTracker = peerTransactionTracker;
    this.maybePeerPendingTransactionTracker = maybePeerPendingTransactionTracker;
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.eip1559 = eip1559;
    this.configuration = configuration;

    duplicateTransactionCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.TRANSACTION_POOL,
            "transactions_duplicates_total",
            "Total number of duplicate transactions received",
            "source");

    ethContext.getEthPeers().subscribeConnect(this::handleConnect);
  }

  void handleConnect(final EthPeer peer) {
    pendingTransactions
        .getLocalTransactions()
        .forEach(transaction -> peerTransactionTracker.addToPeerSendQueue(peer, transaction));

    maybePeerPendingTransactionTracker
        .filter(
            peerPendingTransactionTracker ->
                peerPendingTransactionTracker.isPeerSupported(peer, EthProtocol.ETH65))
        .ifPresent(
            peerPendingTransactionTracker ->
                pendingTransactions
                    .getNewPooledHashes()
                    .forEach(hash -> peerPendingTransactionTracker.addToPeerSendQueue(peer, hash)));
  }

  public boolean addTransactionHash(final Hash transactionHash) {
    return pendingTransactions.addTransactionHash(transactionHash);
  }

  public ValidationResult<TransactionInvalidReason> addLocalTransaction(
      final FrontierTransaction transaction) {
    final Wei transactionGasPrice = minTransactionGasPrice(transaction);
    if (transactionGasPrice.compareTo(minTransactionGasPrice) < 0) {
      return ValidationResult.invalid(TransactionInvalidReason.GAS_PRICE_TOO_LOW);
    }
    if (!configuration.getTxFeeCap().isZero()
        && transactionGasPrice.compareTo(configuration.getTxFeeCap()) > 0) {
      return ValidationResult.invalid(TransactionInvalidReason.TX_FEECAP_EXCEEDED);
    }

    final ValidationResult<TransactionInvalidReason> validationResult =
        validateTransaction(transaction);
    if (validationResult.isValid()) {
      final TransactionAddedStatus transactionAddedStatus =
          pendingTransactions.addLocalTransaction(transaction);
      if (!transactionAddedStatus.equals(TransactionAddedStatus.ADDED)) {
        duplicateTransactionCounter.labels(LOCAL).inc();
        return ValidationResult.invalid(transactionAddedStatus.getInvalidReason().orElseThrow());
      }
      final Collection<FrontierTransaction> txs = singletonList(transaction);
      transactionBatchAddedListener.onTransactionsAdded(txs);
      pendingTransactionBatchAddedListener.ifPresent(it -> it.onTransactionsAdded(txs));
    }

    return validationResult;
  }

  public ValidationResult<TransactionInvalidReason> addLocalTransaction(
      final EIP1559Transaction transaction) {
    final ValidationResult<TransactionInvalidReason> validationResult =
        validateTransaction(transaction);
    if (validationResult.isValid()) {
      final TransactionAddedStatus transactionAddedStatus =
          pendingTransactions.addLocalTransaction(transaction);
      if (!transactionAddedStatus.equals(TransactionAddedStatus.ADDED)) {
        duplicateTransactionCounter.labels(LOCAL).inc();
        return ValidationResult.invalid(transactionAddedStatus.getInvalidReason().orElseThrow());
      }
      final Collection<EIP1559Transaction> txs = singletonList(transaction);
      transactionBatchAddedListener.onTransactionsAdded(txs);
      pendingTransactionBatchAddedListener.ifPresent(it -> it.onTransactionsAdded(txs));
    }

    return validationResult;
  }

  public void addRemoteTransactions(final Collection<FrontierTransaction> transactions) {
    if (!syncState.isInSync(SYNC_TOLERANCE)) {
      return;
    }
    final Set<FrontierTransaction> addedTransactions = new HashSet<>();
    for (final FrontierTransaction transaction : transactions) {
      pendingTransactions.tryEvictTransactionHash(transaction.getHash());
      if (pendingTransactions.containsTransaction(transaction.getHash())) {
        // We already have this transaction, don't even validate it.
        duplicateTransactionCounter.labels(REMOTE).inc();
        continue;
      }
      final Wei transactionGasPrice = minTransactionGasPrice(transaction);
      if (transactionGasPrice.compareTo(minTransactionGasPrice) < 0) {
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

  public void addRemoteTransactions(final Collection<EIP1559Transaction> transactions) {
    if (!syncState.isInSync(SYNC_TOLERANCE)) {
      return;
    }
    final Set<? extends TypedTransaction> addedTransactions = new HashSet<>();
    transactions.stream().map(transaction -> {
      if (transaction in)
            }
            )
    if (!addedTransactions.isEmpty()) {
      transactionBatchAddedListener.onTransactionsAdded(addedTransactions);
    }
  }

  public Optional<EIP1559Transaction> addRemoteTransaction(
          final FrontierTransaction eip1559Transaction) {
    pendingTransactions.tryEvictTransactionHash(eip1559Transaction.getHash());
    if (pendingTransactions.containsTransaction(eip1559Transaction.getHash())) {
      // We already have this transaction, don't even validate it.
      duplicateTransactionCounter.labels(REMOTE).inc();
      return Optional.empty();
    }
    final Wei transactionGasPrice = minTransactionGasPrice(eip1559Transaction);
    if (transactionGasPrice.compareTo(minTransactionGasPrice) < 0) {
      return Optional.empty();
    }
    final ValidationResult<TransactionInvalidReason> validationResult =
            validateTransaction(eip1559Transaction);
    if (validationResult.isValid()) {
      final boolean added = pendingTransactions.addRemoteTransaction(eip1559Transaction);
      if (added) {
        return Optional.of(eip1559Transaction);
      } else {
        duplicateTransactionCounter.labels(REMOTE).inc();
      }
    } else {
      LOG.trace(
              "Validation failed ({}) for transaction {}. Discarding.",
              validationResult.getInvalidReason(),
              eip1559Transaction);
    }
    return Optional.empty();
  }

  public Optional<EIP1559Transaction> addRemoteTransaction(
      final EIP1559Transaction eip1559Transaction) {
    pendingTransactions.tryEvictTransactionHash(eip1559Transaction.getHash());
    if (pendingTransactions.containsTransaction(eip1559Transaction.getHash())) {
      // We already have this transaction, don't even validate it.
      duplicateTransactionCounter.labels(REMOTE).inc();
      return Optional.empty();
    }
    final Wei transactionGasPrice = minTransactionGasPrice(eip1559Transaction);
    if (transactionGasPrice.compareTo(minTransactionGasPrice) < 0) {
      return Optional.empty();
    }
    final ValidationResult<TransactionInvalidReason> validationResult =
        validateTransaction(eip1559Transaction);
    if (validationResult.isValid()) {
      final boolean added = pendingTransactions.addRemoteTransaction(eip1559Transaction);
      if (added) {
        return Optional.of(eip1559Transaction);
      } else {
        duplicateTransactionCounter.labels(REMOTE).inc();
      }
    } else {
      LOG.trace(
          "Validation failed ({}) for transaction {}. Discarding.",
          validationResult.getInvalidReason(),
          eip1559Transaction);
    }
    return Optional.empty();
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
  public void onBlockAdded(final BlockAddedEvent event) {
    event.getAddedTransactions().forEach(pendingTransactions::transactionAddedToBlock);
    addRemoteTransactions(event.getRemovedTransactions());
  }

  private MainnetTransactionValidator getTransactionValidator() {
    return protocolSchedule
        .getByBlockNumber(protocolContext.getBlockchain().getChainHeadBlockNumber())
        .getTransactionValidator();
  }

  public PendingTransactions getPendingTransactions() {
    return pendingTransactions;
  }

  private ValidationResult<TransactionInvalidReason> validateTransaction(
      final FrontierTransaction transaction) {
    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader();
    final ValidationResult<TransactionInvalidReason> basicValidationResult =
        getTransactionValidator().validate(transaction);
    if (!basicValidationResult.isValid()) {
      return basicValidationResult;
    }

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

  private ValidationResult<TransactionInvalidReason> validateTransaction(
      final EIP1559Transaction transaction) {
    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader();
    final Long baseFee = chainHeadBlockHeader.getBaseFee().get();
    final ValidationResult<TransactionInvalidReason> basicValidationResult =
        getTransactionValidator().validate(transaction, baseFee);
    if (!basicValidationResult.isValid()) {
      return basicValidationResult;
    }

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
                      transaction,
                      senderAccount,
                      TransactionValidationParams.transactionPool(),
                      baseFee);
            })
        .orElseGet(() -> ValidationResult.invalid(CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE));
  }

  public Optional<TypedTransaction> getTransactionByHash(final Hash hash) {
    return pendingTransactions.getTransactionByHash(hash);
  }

  private BlockHeader getChainHeadBlockHeader() {
    final MutableBlockchain blockchain = protocolContext.getBlockchain();
    return blockchain.getBlockHeader(blockchain.getChainHeadHash()).get();
  }


  private Wei minTransactionGasPrice(final TypedTransaction transaction) {
    // EIP-1559 enablement guard block
    if (!ExperimentalEIPs.eip1559Enabled || this.eip1559.isEmpty()) {
      return TransactionPriceCalculator.frontier().price(transaction);
    }

    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader();
    // Compute transaction price using EIP-1559 rules if chain head is after fork
    return eip1559
        .filter(definitelyEIP1559 -> definitelyEIP1559.isEIP1559(chainHeadBlockHeader.getNumber()))
        .map(
            __ ->
                BaseFee.minTransactionPriceInNextBlock(
                    transaction, chainHeadBlockHeader::getBaseFee))
        .orElseGet(() -> TransactionPriceCalculator.frontier().price(transaction));
  }
}

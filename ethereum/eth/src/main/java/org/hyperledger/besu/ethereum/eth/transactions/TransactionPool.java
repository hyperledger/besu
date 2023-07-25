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

import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.CHAIN_HEAD_NOT_AVAILABLE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INTERNAL_ERROR;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.TRANSACTION_ALREADY_KNOWN;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.util.Subscribers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains the set of pending transactions received from JSON-RPC or other nodes. Transactions are
 * removed automatically when they are included in a block on the canonical chain and re-added if a
 * re-org removes them from the canonical chain again.
 *
 * <p>This class is safe for use across multiple threads.
 */
public class TransactionPool implements BlockAddedObserver {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPool.class);
  private static final Logger LOG_FOR_REPLAY = LoggerFactory.getLogger("LOG_FOR_REPLAY");
  private final Supplier<PendingTransactions> pendingTransactionsSupplier;
  private volatile PendingTransactions pendingTransactions;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final TransactionBroadcaster transactionBroadcaster;
  private final MiningParameters miningParameters;
  private final TransactionPoolMetrics metrics;
  private final TransactionPoolConfiguration configuration;
  private final AtomicBoolean isPoolEnabled = new AtomicBoolean(false);
  private final PendingTransactionsListenersProxy pendingTransactionsListenersProxy =
      new PendingTransactionsListenersProxy();
  private volatile OptionalLong subscribeConnectId = OptionalLong.empty();
  private final SaveRestoreManager saveRestoreManager = new SaveRestoreManager();

  public TransactionPool(
      final Supplier<PendingTransactions> pendingTransactionsSupplier,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionBroadcaster transactionBroadcaster,
      final EthContext ethContext,
      final MiningParameters miningParameters,
      final TransactionPoolMetrics metrics,
      final TransactionPoolConfiguration configuration) {
    this.pendingTransactionsSupplier = pendingTransactionsSupplier;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.transactionBroadcaster = transactionBroadcaster;
    this.miningParameters = miningParameters;
    this.metrics = metrics;
    this.configuration = configuration;
    initLogForReplay();
  }

  private void initLogForReplay() {
    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("{},{},{},{}")
        .addArgument(() -> getChainHeadBlockHeader().map(BlockHeader::getNumber).orElse(0L))
        .addArgument(
            () ->
                getChainHeadBlockHeader()
                    .flatMap(BlockHeader::getBaseFee)
                    .map(Wei::getAsBigInteger)
                    .orElse(BigInteger.ZERO))
        .addArgument(() -> getChainHeadBlockHeader().map(BlockHeader::getGasUsed).orElse(0L))
        .addArgument(() -> getChainHeadBlockHeader().map(BlockHeader::getGasLimit).orElse(0L))
        .log();
  }

  @VisibleForTesting
  void handleConnect(final EthPeer peer) {
    transactionBroadcaster.relayTransactionPoolTo(
        peer, pendingTransactions.getPendingTransactions());
  }

  public ValidationResult<TransactionInvalidReason> addTransactionViaApi(
      final Transaction transaction) {

    if (configuration.getDisableLocalTransactions()) {
      final var result = addRemoteTransaction(transaction);
      if (result.isValid()) {
        transactionBroadcaster.onTransactionsAdded(List.of(transaction));
      }
      return result;
    }
    return addLocalTransaction(transaction);
  }

  ValidationResult<TransactionInvalidReason> addLocalTransaction(final Transaction transaction) {
    final ValidationResultAndAccount validationResult = validateLocalTransaction(transaction);

    if (validationResult.result.isValid()) {

      final TransactionAddedResult transactionAddedResult =
          pendingTransactions.addLocalTransaction(transaction, validationResult.maybeAccount);

      if (transactionAddedResult.isRejected()) {
        final var rejectReason =
            transactionAddedResult
                .maybeInvalidReason()
                .orElseGet(
                    () -> {
                      LOG.warn("Missing invalid reason for status {}", transactionAddedResult);
                      return INTERNAL_ERROR;
                    });
        return ValidationResult.invalid(rejectReason);
      }

      transactionBroadcaster.onTransactionsAdded(List.of(transaction));
    } else {
      metrics.incrementRejected(true, validationResult.result.getInvalidReason(), "txpool");
    }

    return validationResult.result;
  }

  private Optional<Wei> getMaxGasPrice(final Transaction transaction) {
    return transaction.getGasPrice().map(Optional::of).orElse(transaction.getMaxFeePerGas());
  }

  private boolean isMaxGasPriceBelowConfiguredMinGasPrice(final Transaction transaction) {
    return getMaxGasPrice(transaction)
        .map(g -> g.lessThan(miningParameters.getMinTransactionGasPrice()))
        .orElse(true);
  }

  private Stream<Transaction> sortedBySenderAndNonce(final Collection<Transaction> transactions) {
    return transactions.stream()
        .sorted(Comparator.comparing(Transaction::getSender).thenComparing(Transaction::getNonce));
  }

  public Map<Hash, ValidationResult<TransactionInvalidReason>> addRemoteTransactions(
      final Collection<Transaction> transactions) {
    final long started = System.currentTimeMillis();
    final int initialCount = transactions.size();
    final List<Transaction> addedTransactions = new ArrayList<>(initialCount);
    LOG.debug("Adding {} remote transactions", initialCount);

    final var validationResults =
        sortedBySenderAndNonce(transactions)
            .collect(
                Collectors.toMap(
                    Transaction::getHash,
                    transaction -> {
                      final var result = addRemoteTransaction(transaction);
                      if (result.isValid()) {
                        addedTransactions.add(transaction);
                      }
                      return result;
                    }));

    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("S,{}")
        .addArgument(() -> pendingTransactions.logStats())
        .log();

    LOG.atDebug()
        .setMessage(
            "Added {} transactions to the pool in {}ms, {} not added, current pool stats {}")
        .addArgument(addedTransactions::size)
        .addArgument(() -> System.currentTimeMillis() - started)
        .addArgument(() -> initialCount - addedTransactions.size())
        .addArgument(pendingTransactions::logStats)
        .log();

    if (!addedTransactions.isEmpty()) {
      transactionBroadcaster.onTransactionsAdded(addedTransactions);
    }
    return validationResults;
  }

  private ValidationResult<TransactionInvalidReason> addRemoteTransaction(
      final Transaction transaction) {
    if (pendingTransactions.containsTransaction(transaction)) {
      LOG.atTrace()
          .setMessage("Discard already present transaction {}")
          .addArgument(transaction::toTraceLog)
          .log();
      // We already have this transaction, don't even validate it.
      metrics.incrementRejected(false, TRANSACTION_ALREADY_KNOWN, "txpool");
      return ValidationResult.invalid(TRANSACTION_ALREADY_KNOWN);
    }

    final ValidationResultAndAccount validationResult = validateRemoteTransaction(transaction);

    if (validationResult.result.isValid()) {
      final TransactionAddedResult status =
          pendingTransactions.addRemoteTransaction(transaction, validationResult.maybeAccount);
      if (status.isSuccess()) {
        LOG.atTrace()
            .setMessage("Added remote transaction {}")
            .addArgument(transaction::toTraceLog)
            .log();
      } else {
        final var rejectReason =
            status
                .maybeInvalidReason()
                .orElseGet(
                    () -> {
                      LOG.warn("Missing invalid reason for status {}", status);
                      return INTERNAL_ERROR;
                    });
        LOG.atTrace()
            .setMessage("Transaction {} rejected reason {}")
            .addArgument(transaction::toTraceLog)
            .addArgument(rejectReason)
            .log();
        metrics.incrementRejected(false, rejectReason, "txpool");
        return ValidationResult.invalid(rejectReason);
      }
    } else {
      LOG.atTrace()
          .setMessage("Discard invalid transaction {}, reason {}")
          .addArgument(transaction::toTraceLog)
          .addArgument(validationResult.result::getInvalidReason)
          .log();
      metrics.incrementRejected(false, validationResult.result.getInvalidReason(), "txpool");
      pendingTransactions.signalInvalidAndRemoveDependentTransactions(transaction);
    }

    return validationResult.result;
  }

  public long subscribePendingTransactions(final PendingTransactionAddedListener listener) {
    return pendingTransactionsListenersProxy.onAddedListeners.subscribe(listener);
  }

  public void unsubscribePendingTransactions(final long id) {
    pendingTransactionsListenersProxy.onAddedListeners.unsubscribe(id);
  }

  public long subscribeDroppedTransactions(final PendingTransactionDroppedListener listener) {
    return pendingTransactionsListenersProxy.onDroppedListeners.subscribe(listener);
  }

  public void unsubscribeDroppedTransactions(final long id) {
    pendingTransactionsListenersProxy.onDroppedListeners.unsubscribe(id);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    if (isPoolEnabled.get()) {
      LOG.trace("Block added event {}", event);
      if (event.getEventType().equals(BlockAddedEvent.EventType.HEAD_ADVANCED)
          || event.getEventType().equals(BlockAddedEvent.EventType.CHAIN_REORG)) {

        pendingTransactions.manageBlockAdded(
            event.getBlock().getHeader(),
            event.getAddedTransactions(),
            event.getRemovedTransactions(),
            protocolSchedule.getByBlockHeader(event.getBlock().getHeader()).getFeeMarket());
        reAddTransactions(event.getRemovedTransactions());
      }
    }
  }

  private void reAddTransactions(final List<Transaction> reAddTransactions) {
    if (!reAddTransactions.isEmpty()) {
      var txsByOrigin =
          reAddTransactions.stream()
              .collect(
                  Collectors.partitioningBy(
                      tx ->
                          configuration.getDisableLocalTransactions()
                              ? false
                              : pendingTransactions.isLocalSender(tx.getSender())));
      var reAddLocalTxs = txsByOrigin.get(true);
      var reAddRemoteTxs = txsByOrigin.get(false);
      if (!reAddLocalTxs.isEmpty()) {
        logReAddedTransactions(reAddLocalTxs, "local");
        sortedBySenderAndNonce(reAddLocalTxs).forEach(this::addLocalTransaction);
      }
      if (!reAddRemoteTxs.isEmpty()) {
        logReAddedTransactions(reAddRemoteTxs, "remote");
        addRemoteTransactions(reAddRemoteTxs);
      }
    }
  }

  private static void logReAddedTransactions(
      final List<Transaction> reAddedTxs, final String source) {
    LOG.atTrace()
        .setMessage("Re-adding {} {} transactions from a block event: {}")
        .addArgument(reAddedTxs::size)
        .addArgument(source)
        .addArgument(
            () ->
                reAddedTxs.stream().map(Transaction::toTraceLog).collect(Collectors.joining("; ")))
        .log();
  }

  private TransactionValidator getTransactionValidator() {
    return protocolSchedule
        .getByBlockHeader(protocolContext.getBlockchain().getChainHeadHeader())
        .getTransactionValidatorFactory()
        .get();
  }

  private ValidationResultAndAccount validateLocalTransaction(final Transaction transaction) {
    return validateTransaction(transaction, true);
  }

  private ValidationResultAndAccount validateRemoteTransaction(final Transaction transaction) {
    return validateTransaction(transaction, false);
  }

  private ValidationResultAndAccount validateTransaction(
      final Transaction transaction, final boolean isLocal) {

    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader().orElse(null);
    if (chainHeadBlockHeader == null) {
      LOG.atTrace()
          .setMessage("rejecting transaction {} due to chain head not available yet")
          .addArgument(transaction::getHash)
          .log();
      return ValidationResultAndAccount.invalid(CHAIN_HEAD_NOT_AVAILABLE);
    }

    final FeeMarket feeMarket =
        protocolSchedule.getByBlockHeader(chainHeadBlockHeader).getFeeMarket();

    final TransactionInvalidReason priceInvalidReason =
        validatePrice(transaction, isLocal, feeMarket);
    if (priceInvalidReason != null) {
      return ValidationResultAndAccount.invalid(priceInvalidReason);
    }

    final ValidationResult<TransactionInvalidReason> basicValidationResult =
        getTransactionValidator()
            .validate(
                transaction,
                chainHeadBlockHeader.getBaseFee(),
                TransactionValidationParams.transactionPool());
    if (!basicValidationResult.isValid()) {
      return new ValidationResultAndAccount(basicValidationResult);
    }

    if (isLocal
        && strictReplayProtectionShouldBeEnforceLocally(chainHeadBlockHeader)
        && transaction.getChainId().isEmpty()) {
      // Strict replay protection is enabled but the tx is not replay-protected
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURE_REQUIRED);
    }
    if (transaction.getGasLimit() > chainHeadBlockHeader.getGasLimit()) {
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT,
          String.format(
              "Transaction gas limit of %s exceeds block gas limit of %s",
              transaction.getGasLimit(), chainHeadBlockHeader.getGasLimit()));
    }
    if (transaction.getType().equals(TransactionType.EIP1559) && !feeMarket.implementsBaseFee()) {
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.INVALID_TRANSACTION_FORMAT,
          "EIP-1559 transaction are not allowed yet");
    }

    try (final var worldState =
        protocolContext
            .getWorldStateArchive()
            .getMutable(chainHeadBlockHeader, false)
            .orElseThrow()) {
      final Account senderAccount = worldState.get(transaction.getSender());
      return new ValidationResultAndAccount(
          senderAccount,
          getTransactionValidator()
              .validateForSender(
                  transaction, senderAccount, TransactionValidationParams.transactionPool()));
    } catch (MerkleTrieException ex) {
      LOG.debug(
          "MerkleTrieException while validating transaction for sender {}",
          transaction.getSender());
      return ValidationResultAndAccount.invalid(CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE);
    } catch (Exception ex) {
      return ValidationResultAndAccount.invalid(CHAIN_HEAD_WORLD_STATE_NOT_AVAILABLE);
    }
  }

  private TransactionInvalidReason validatePrice(
      final Transaction transaction, final boolean isLocal, final FeeMarket feeMarket) {

    if (isLocal) {
      if (!configuration.getTxFeeCap().isZero()
          && getMaxGasPrice(transaction).get().greaterThan(configuration.getTxFeeCap())) {
        return TransactionInvalidReason.TX_FEECAP_EXCEEDED;
      }
      // allow local transactions to be below minGas as long as we are mining
      // or at least gas price is above the configured floor
      if ((!miningParameters.isMiningEnabled()
              && isMaxGasPriceBelowConfiguredMinGasPrice(transaction))
          || !feeMarket.satisfiesFloorTxFee(transaction)) {
        return TransactionInvalidReason.GAS_PRICE_TOO_LOW;
      }
    } else {
      if (isMaxGasPriceBelowConfiguredMinGasPrice(transaction)) {
        LOG.atTrace()
            .setMessage("Discard transaction {} below min gas price {}")
            .addArgument(transaction::toTraceLog)
            .addArgument(miningParameters::getMinTransactionGasPrice)
            .log();
        return TransactionInvalidReason.GAS_PRICE_TOO_LOW;
      }
    }
    return null;
  }

  private boolean strictReplayProtectionShouldBeEnforceLocally(
      final BlockHeader chainHeadBlockHeader) {
    return configuration.getStrictTransactionReplayProtectionEnabled()
        && protocolSchedule.getChainId().isPresent()
        && transactionReplaySupportedAtBlock(chainHeadBlockHeader);
  }

  private boolean transactionReplaySupportedAtBlock(final BlockHeader blockHeader) {
    return protocolSchedule.getByBlockHeader(blockHeader).isReplayProtectionSupported();
  }

  public Optional<Transaction> getTransactionByHash(final Hash hash) {
    return pendingTransactions.getTransactionByHash(hash);
  }

  private Optional<BlockHeader> getChainHeadBlockHeader() {
    final MutableBlockchain blockchain = protocolContext.getBlockchain();
    return blockchain.getBlockHeader(blockchain.getChainHeadHash());
  }

  public boolean isLocalSender(final Address sender) {
    return pendingTransactions.isLocalSender(sender);
  }

  public int count() {
    return pendingTransactions.size();
  }

  public Collection<PendingTransaction> getPendingTransactions() {
    return pendingTransactions.getPendingTransactions();
  }

  public OptionalLong getNextNonceForSender(final Address address) {
    return pendingTransactions.getNextNonceForSender(address);
  }

  public long maxSize() {
    return pendingTransactions.maxSize();
  }

  public void evictOldTransactions() {
    pendingTransactions.evictOldTransactions();
  }

  public void selectTransactions(
      final PendingTransactions.TransactionSelector transactionSelector) {
    pendingTransactions.selectTransactions(transactionSelector);
  }

  public String logStats() {
    return pendingTransactions.logStats();
  }

  @VisibleForTesting
  Class<? extends PendingTransactions> pendingTransactionsImplementation() {
    return pendingTransactions.getClass();
  }

  public interface TransactionBatchAddedListener {

    void onTransactionsAdded(Collection<Transaction> transactions);
  }

  private static class ValidationResultAndAccount {
    final ValidationResult<TransactionInvalidReason> result;
    final Optional<Account> maybeAccount;

    ValidationResultAndAccount(
        final Account account, final ValidationResult<TransactionInvalidReason> result) {
      this.result = result;
      this.maybeAccount =
          Optional.ofNullable(account)
              .map(
                  acct -> new SimpleAccount(acct.getAddress(), acct.getNonce(), acct.getBalance()));
    }

    ValidationResultAndAccount(final ValidationResult<TransactionInvalidReason> result) {
      this.result = result;
      this.maybeAccount = Optional.empty();
    }

    static ValidationResultAndAccount invalid(
        final TransactionInvalidReason reason, final String message) {
      return new ValidationResultAndAccount(ValidationResult.invalid(reason, message));
    }

    static ValidationResultAndAccount invalid(final TransactionInvalidReason reason) {
      return new ValidationResultAndAccount(ValidationResult.invalid(reason));
    }
  }

  public CompletableFuture<Void> setEnabled() {
    if (!isEnabled()) {
      pendingTransactions = pendingTransactionsSupplier.get();
      pendingTransactionsListenersProxy.subscribe();
      isPoolEnabled.set(true);
      subscribeConnectId =
          OptionalLong.of(ethContext.getEthPeers().subscribeConnect(this::handleConnect));
      return saveRestoreManager
          .loadFromDisk()
          .exceptionally(
              t -> {
                LOG.error("Error while restoring transaction pool from disk", t);
                return null;
              });
    }
    return CompletableFuture.completedFuture(null);
  }

  public CompletableFuture<Void> setDisabled() {
    if (isEnabled()) {
      isPoolEnabled.set(false);
      subscribeConnectId.ifPresent(ethContext.getEthPeers()::unsubscribeConnect);
      pendingTransactionsListenersProxy.unsubscribe();
      final PendingTransactions pendingTransactionsToSave = pendingTransactions;
      pendingTransactions = new DisabledPendingTransactions();
      return saveRestoreManager
          .saveToDisk(pendingTransactionsToSave)
          .exceptionally(
              t -> {
                LOG.error("Error while saving transaction pool to disk", t);
                return null;
              });
    }
    return CompletableFuture.completedFuture(null);
  }

  public boolean isEnabled() {
    return isPoolEnabled.get();
  }

  class PendingTransactionsListenersProxy {
    private final Subscribers<PendingTransactionAddedListener> onAddedListeners =
        Subscribers.create();
    private final Subscribers<PendingTransactionDroppedListener> onDroppedListeners =
        Subscribers.create();

    private volatile long onAddedListenerId;
    private volatile long onDroppedListenerId;

    void subscribe() {
      onAddedListenerId = pendingTransactions.subscribePendingTransactions(this::onAdded);
      onDroppedListenerId = pendingTransactions.subscribeDroppedTransactions(this::onDropped);
    }

    void unsubscribe() {
      pendingTransactions.unsubscribePendingTransactions(onAddedListenerId);
      pendingTransactions.unsubscribeDroppedTransactions(onDroppedListenerId);
    }

    private void onDropped(final Transaction transaction) {
      onDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction));
    }

    private void onAdded(final Transaction transaction) {
      onAddedListeners.forEach(listener -> listener.onTransactionAdded(transaction));
    }
  }

  class SaveRestoreManager {
    private final Lock diskAccessLock = new ReentrantLock();
    private final AtomicReference<CompletableFuture<Void>> writeInProgress =
        new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicReference<CompletableFuture<Void>> readInProgress =
        new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    CompletableFuture<Void> saveToDisk(final PendingTransactions pendingTransactionsToSave) {
      return serializeAndDedupOperation(
          () -> executeSaveToDisk(pendingTransactionsToSave), writeInProgress);
    }

    CompletableFuture<Void> loadFromDisk() {
      return serializeAndDedupOperation(this::executeLoadFromDisk, readInProgress);
    }

    private CompletableFuture<Void> serializeAndDedupOperation(
        final Runnable operation,
        final AtomicReference<CompletableFuture<Void>> operationInProgress) {
      if (configuration.getEnableSaveRestore()) {
        try {
          if (diskAccessLock.tryLock(1, TimeUnit.MINUTES)) {
            try {
              if (!operationInProgress.get().isDone()) {
                isCancelled.set(true);
                try {
                  operationInProgress.get().get();
                } catch (ExecutionException ee) {
                  // nothing to do
                }
              }

              isCancelled.set(false);
              operationInProgress.set(CompletableFuture.runAsync(operation));
              return operationInProgress.get();
            } catch (InterruptedException ie) {
              isCancelled.set(false);
            } finally {
              diskAccessLock.unlock();
            }
          } else {
            CompletableFuture.failedFuture(
                new TimeoutException("Timeout waiting for disk access lock"));
          }
        } catch (InterruptedException ie) {
          return CompletableFuture.failedFuture(ie);
        }
      }
      return CompletableFuture.completedFuture(null);
    }

    private void executeSaveToDisk(final PendingTransactions pendingTransactionsToSave) {
      final File saveFile = configuration.getSaveFile();
      try (final BufferedWriter bw =
          new BufferedWriter(new FileWriter(saveFile, StandardCharsets.US_ASCII))) {
        final var allTxs = pendingTransactionsToSave.getPendingTransactions();
        LOG.info("Saving {} transactions to file {}", allTxs.size(), saveFile);

        final long savedTxs =
            allTxs.parallelStream()
                .takeWhile(unused -> !isCancelled.get())
                .map(
                    ptx -> {
                      final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
                      ptx.getTransaction().writeTo(rlp);
                      return (ptx.isReceivedFromLocalSource() ? "l" : "r")
                          + rlp.encoded().toBase64String();
                    })
                .mapToInt(
                    line -> {
                      synchronized (bw) {
                        try {
                          bw.write(line);
                          bw.newLine();
                        } catch (IOException e) {
                          throw new RuntimeException(e);
                        }
                      }
                      return 1;
                    })
                .sum();
        if (isCancelled.get()) {
          LOG.info(
              "Saved {} transactions to file {}, before operation was cancelled",
              savedTxs,
              saveFile);
        } else {
          LOG.info("Saved {} transactions to file {}", savedTxs, saveFile);
        }
      } catch (IOException e) {
        LOG.error("Error while saving txpool content to disk", e);
      }
    }

    private void executeLoadFromDisk() {
      if (configuration.getEnableSaveRestore()) {
        final File saveFile = configuration.getSaveFile();
        if (saveFile.exists()) {
          LOG.info("Loading transaction pool content from file {}", saveFile);
          try (final BufferedReader br =
              new BufferedReader(new FileReader(saveFile, StandardCharsets.US_ASCII))) {
            final IntSummaryStatistics stats =
                br.lines()
                    .takeWhile(unused -> !isCancelled.get())
                    .mapToInt(
                        line -> {
                          final boolean isLocal = line.charAt(0) == 'l';
                          final Transaction tx =
                              Transaction.readFrom(Bytes.fromBase64String(line.substring(1)));

                          final ValidationResult<TransactionInvalidReason> result;
                          if (isLocal && !configuration.getDisableLocalTransactions()) {
                            result = addLocalTransaction(tx);
                          } else {
                            result = addRemoteTransaction(tx);
                          }

                          return result.isValid() ? 1 : 0;
                        })
                    .summaryStatistics();

            if (isCancelled.get()) {
              LOG.info(
                  "Added {} transactions of {} loaded from file {}, before operation was cancelled",
                  stats.getSum(),
                  stats.getCount(),
                  saveFile);
            } else {
              LOG.info(
                  "Added {} transactions of {} loaded from file {}",
                  stats.getSum(),
                  stats.getCount(),
                  saveFile);
            }
          } catch (IOException e) {
            LOG.error("Error while saving txpool content to disk", e);
          }
        }
        saveFile.delete();
      }
    }
  }
}

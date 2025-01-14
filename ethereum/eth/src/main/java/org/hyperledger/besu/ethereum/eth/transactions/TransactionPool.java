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
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
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
  private final BlobCache cacheForBlobsOfTransactionsAddedToABlock;
  private volatile PendingTransactions pendingTransactions = new DisabledPendingTransactions();
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final EthContext ethContext;
  private final TransactionBroadcaster transactionBroadcaster;
  private final TransactionPoolMetrics metrics;
  private final TransactionPoolConfiguration configuration;
  private final AtomicBoolean isPoolEnabled = new AtomicBoolean(false);
  private final PendingTransactionsListenersProxy pendingTransactionsListenersProxy =
      new PendingTransactionsListenersProxy();
  private volatile OptionalLong subscribeConnectId = OptionalLong.empty();
  private final SaveRestoreManager saveRestoreManager = new SaveRestoreManager();
  private final Set<Address> localSenders = ConcurrentHashMap.newKeySet();
  private final EthScheduler.OrderedProcessor<BlockAddedEvent> blockAddedEventOrderedProcessor;
  private final ListMultimap<VersionedHash, BlobsWithCommitments.BlobQuad>
      mapOfBlobsInTransactionPool =
          Multimaps.synchronizedListMultimap(
              Multimaps.newListMultimap(new HashMap<>(), () -> new ArrayList<>(1)));

  public TransactionPool(
      final Supplier<PendingTransactions> pendingTransactionsSupplier,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionBroadcaster transactionBroadcaster,
      final EthContext ethContext,
      final TransactionPoolMetrics metrics,
      final TransactionPoolConfiguration configuration,
      final BlobCache blobCache) {
    this.pendingTransactionsSupplier = pendingTransactionsSupplier;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethContext = ethContext;
    this.transactionBroadcaster = transactionBroadcaster;
    this.metrics = metrics;
    this.configuration = configuration;
    this.blockAddedEventOrderedProcessor =
        ethContext.getScheduler().createOrderedProcessor(this::processBlockAddedEvent);
    this.cacheForBlobsOfTransactionsAddedToABlock = blobCache;
    initializeBlobMetrics();
    initLogForReplay();
    subscribePendingTransactions(this::mapBlobsOnTransactionAdded);
    subscribeDroppedTransactions(
        (transaction, reason) -> unmapBlobsOnTransactionDropped(transaction));
    subscribeDroppedTransactions(transactionBroadcaster);
  }

  private void initLogForReplay() {
    // log the initial block header data
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
  }

  @VisibleForTesting
  void handleConnect(final EthPeer peer) {
    transactionBroadcaster.relayTransactionPoolTo(
        peer, pendingTransactions.getPendingTransactions());
  }

  public ValidationResult<TransactionInvalidReason> addTransactionViaApi(
      final Transaction transaction) {

    final var result = addTransaction(transaction, true);
    if (result.isValid()) {
      localSenders.add(transaction.getSender());
      transactionBroadcaster.onTransactionsAdded(List.of(transaction));
    }
    return result;
  }

  public Map<Hash, ValidationResult<TransactionInvalidReason>> addRemoteTransactions(
      final Collection<Transaction> transactions) {
    final long started = System.currentTimeMillis();
    final int initialCount = transactions.size();
    final List<Transaction> addedTransactions = new ArrayList<>(initialCount);
    LOG.trace("Adding {} remote transactions", initialCount);

    final var validationResults =
        sortedBySenderAndNonce(transactions)
            .collect(
                Collectors.toMap(
                    Transaction::getHash,
                    transaction -> {
                      final var result = addTransaction(transaction, false);
                      if (result.isValid()) {
                        addedTransactions.add(transaction);
                      }
                      return result;
                    },
                    (transaction1, transaction2) -> transaction1));

    LOG_FOR_REPLAY
        .atTrace()
        .setMessage("S,{}")
        .addArgument(() -> pendingTransactions.logStats())
        .log();

    LOG.atTrace()
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

  private ValidationResult<TransactionInvalidReason> addTransaction(
      final Transaction transaction, final boolean isLocal) {

    final boolean hasPriority = isPriorityTransaction(transaction, isLocal);

    if (pendingTransactions.containsTransaction(transaction)) {
      LOG.atTrace()
          .setMessage("Discard already present transaction {}")
          .addArgument(transaction::toTraceLog)
          .log();
      // We already have this transaction, don't even validate it.
      metrics.incrementRejected(isLocal, hasPriority, TRANSACTION_ALREADY_KNOWN, "txpool");
      return ValidationResult.invalid(TRANSACTION_ALREADY_KNOWN);
    }

    final ValidationResultAndAccount validationResult =
        validateTransaction(transaction, isLocal, hasPriority);

    if (validationResult.result.isValid()) {
      final TransactionAddedResult status =
          pendingTransactions.addTransaction(
              PendingTransaction.newPendingTransaction(transaction, isLocal, hasPriority),
              validationResult.maybeAccount);
      if (status.isSuccess()) {
        LOG.atTrace()
            .setMessage("Added {} transaction {}")
            .addArgument(() -> isLocal ? "local" : "remote")
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
        metrics.incrementRejected(isLocal, hasPriority, rejectReason, "txpool");
        return ValidationResult.invalid(rejectReason);
      }
    } else {
      LOG.atTrace()
          .setMessage("Discard invalid transaction {}, reason {}, because {}")
          .addArgument(transaction::toTraceLog)
          .addArgument(validationResult.result::getInvalidReason)
          .addArgument(validationResult.result::getErrorMessage)
          .log();
      metrics.incrementRejected(
          isLocal, hasPriority, validationResult.result.getInvalidReason(), "txpool");
    }

    return validationResult.result;
  }

  private Optional<Wei> getMaxGasPrice(final Transaction transaction) {
    return transaction.getGasPrice().map(Optional::of).orElse(transaction.getMaxFeePerGas());
  }

  private boolean isMaxGasPriceBelowConfiguredMinGasPrice(final Transaction transaction) {
    return getMaxGasPrice(transaction)
        .map(g -> g.lessThan(configuration.getMinGasPrice()))
        .orElse(true);
  }

  private Stream<Transaction> sortedBySenderAndNonce(final Collection<Transaction> transactions) {
    return transactions.stream()
        .sorted(Comparator.comparing(Transaction::getSender).thenComparing(Transaction::getNonce));
  }

  private boolean isPriorityTransaction(final Transaction transaction, final boolean isLocal) {
    if (isLocal && !configuration.getNoLocalPriority()) {
      // unless no-local-priority option is specified, senders of local sent txs are prioritized
      return true;
    }
    // otherwise check if the sender belongs to the priority list
    return configuration.getPrioritySenders().contains(transaction.getSender());
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
      if (event.getEventType().equals(BlockAddedEvent.EventType.HEAD_ADVANCED)
          || event.getEventType().equals(BlockAddedEvent.EventType.CHAIN_REORG)) {

        blockAddedEventOrderedProcessor.submit(event);
      }
    }
  }

  private void processBlockAddedEvent(final BlockAddedEvent e) {
    final long started = System.currentTimeMillis();
    pendingTransactions.manageBlockAdded(
        e.getBlock().getHeader(),
        e.getAddedTransactions(),
        e.getRemovedTransactions(),
        protocolSchedule.getByBlockHeader(e.getBlock().getHeader()).getFeeMarket());
    reAddTransactions(e.getRemovedTransactions());
    LOG.atTrace()
        .setMessage("Block added event {} processed in {}ms")
        .addArgument(e)
        .addArgument(() -> System.currentTimeMillis() - started)
        .log();
  }

  private void reAddTransactions(final List<Transaction> reAddTransactions) {
    if (!reAddTransactions.isEmpty()) {
      // if adding a blob tx, and it is missing its blob, is a re-org and we should restore the blob
      // from cache.
      var txsByOrigin =
          reAddTransactions.stream()
              .map(t -> pendingTransactions.restoreBlob(t).orElse(t))
              .collect(Collectors.partitioningBy(tx -> isLocalSender(tx.getSender())));
      var reAddLocalTxs = txsByOrigin.get(true);
      var reAddRemoteTxs = txsByOrigin.get(false);
      if (!reAddLocalTxs.isEmpty()) {
        logReAddedTransactions(reAddLocalTxs, "local");
        sortedBySenderAndNonce(reAddLocalTxs).forEach(this::addTransactionViaApi);
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

  private ValidationResultAndAccount validateTransaction(
      final Transaction transaction, final boolean isLocal, final boolean hasPriority) {

    final BlockHeader chainHeadBlockHeader = getChainHeadBlockHeader().orElse(null);
    if (chainHeadBlockHeader == null) {
      LOG.atWarn()
          .setMessage("rejecting transaction {} due to chain head not available yet")
          .addArgument(transaction::getHash)
          .log();
      return ValidationResultAndAccount.invalid(CHAIN_HEAD_NOT_AVAILABLE);
    }

    final FeeMarket feeMarket =
        protocolSchedule.getByBlockHeader(chainHeadBlockHeader).getFeeMarket();
    final TransactionInvalidReason priceInvalidReason =
        validatePrice(transaction, isLocal, hasPriority, feeMarket);
    if (priceInvalidReason != null) {
      return ValidationResultAndAccount.invalid(priceInvalidReason);
    }

    final ValidationResult<TransactionInvalidReason> basicValidationResult =
        getTransactionValidator()
            .validate(
                transaction,
                chainHeadBlockHeader.getBaseFee(),
                Optional.of(
                    Wei.ZERO), // TransactionValidationParams.transactionPool() allows underpriced
                // txs
                TransactionValidationParams.transactionPool());
    if (!basicValidationResult.isValid()) {
      return new ValidationResultAndAccount(basicValidationResult);
    }

    if (hasPriority
        && strictReplayProtectionShouldBeEnforcedLocally(chainHeadBlockHeader)
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
    } else if (transaction.getType().equals(TransactionType.BLOB)
        && transaction.getBlobsWithCommitments().isEmpty()) {
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.INVALID_BLOBS, "Blob transaction must have at least one blob");
    }

    // Call the transaction validator plugin
    final Optional<String> maybePluginInvalid =
        configuration
            .getTransactionPoolValidatorService()
            .createTransactionValidator()
            .validateTransaction(transaction, isLocal, hasPriority);
    if (maybePluginInvalid.isPresent()) {
      return ValidationResultAndAccount.invalid(
          TransactionInvalidReason.PLUGIN_TX_POOL_VALIDATOR, maybePluginInvalid.get());
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
      final Transaction transaction,
      final boolean isLocal,
      final boolean hasPriority,
      final FeeMarket feeMarket) {

    if (isLocal) {
      if (!configuration.getTxFeeCap().isZero()
          && getMaxGasPrice(transaction).get().greaterThan(configuration.getTxFeeCap())) {
        return TransactionInvalidReason.TX_FEECAP_EXCEEDED;
      }
    }
    if (hasPriority) {
      // allow priority transactions to be below minGas as long as the gas price is above the
      // configured floor
      if (!feeMarket.satisfiesFloorTxFee(transaction)) {
        return TransactionInvalidReason.GAS_PRICE_TOO_LOW;
      }
    } else {
      if (isMaxGasPriceBelowConfiguredMinGasPrice(transaction)) {
        LOG.atTrace()
            .setMessage("Discard transaction {} below min gas price {}")
            .addArgument(transaction::toTraceLog)
            .addArgument(configuration::getMinGasPrice)
            .log();
        return TransactionInvalidReason.GAS_PRICE_TOO_LOW;
      }
    }
    return null;
  }

  private boolean strictReplayProtectionShouldBeEnforcedLocally(
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

    // Optimistically get the block header for the chain head without taking a lock,
    // but revert to the safe implementation if it returns an empty optional. (It's
    // possible the chain head has been updated but the block is still being persisted
    // to storage/cache under the lock).
    return blockchain
        .getBlockHeader(blockchain.getChainHeadHash())
        .or(() -> blockchain.getBlockHeaderSafe(blockchain.getChainHeadHash()));
  }

  private boolean isLocalSender(final Address sender) {
    return localSenders.contains(sender);
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
      final CompletableFuture<Void> saveOperation =
          saveRestoreManager
              .saveToDisk(pendingTransactions)
              .exceptionally(
                  t -> {
                    LOG.error("Error while saving transaction pool to disk", t);
                    return null;
                  });
      pendingTransactions = new DisabledPendingTransactions();
      return saveOperation;
    }
    return CompletableFuture.completedFuture(null);
  }

  private void mapBlobsOnTransactionAdded(
      final org.hyperledger.besu.datatypes.Transaction transaction) {
    final Optional<BlobsWithCommitments> maybeBlobsWithCommitments =
        transaction.getBlobsWithCommitments();
    if (maybeBlobsWithCommitments.isEmpty()) {
      return;
    }
    final List<BlobsWithCommitments.BlobQuad> blobQuads =
        maybeBlobsWithCommitments.get().getBlobQuads();

    blobQuads.forEach(bq -> mapOfBlobsInTransactionPool.put(bq.versionedHash(), bq));
  }

  private void unmapBlobsOnTransactionDropped(
      final org.hyperledger.besu.datatypes.Transaction transaction) {
    final Optional<BlobsWithCommitments> maybeBlobsWithCommitments =
        transaction.getBlobsWithCommitments();
    if (maybeBlobsWithCommitments.isEmpty()) {
      return;
    }
    final List<BlobsWithCommitments.BlobQuad> blobQuads =
        maybeBlobsWithCommitments.get().getBlobQuads();

    blobQuads.forEach(bq -> mapOfBlobsInTransactionPool.remove(bq.versionedHash(), bq));
  }

  public BlobsWithCommitments.BlobQuad getBlobQuad(final VersionedHash vh) {
    try {
      // returns an empty list if the key is not present, so getFirst() will throw
      return mapOfBlobsInTransactionPool.get(vh).getFirst();
    } catch (NoSuchElementException e) {
      // do nothing
    }
    return cacheForBlobsOfTransactionsAddedToABlock.get(vh);
  }

  public boolean isEnabled() {
    return isPoolEnabled.get();
  }

  public int getBlobCacheSize() {
    return (int) cacheForBlobsOfTransactionsAddedToABlock.size();
  }

  public int getBlobMapSize() {
    return mapOfBlobsInTransactionPool.size();
  }

  private void initializeBlobMetrics() {
    metrics.createBlobCacheSizeMetric(this::getBlobCacheSize);
    metrics.createBlobMapSizeMetric(this::getBlobMapSize);
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
      onDroppedListenerId =
          pendingTransactions.subscribeDroppedTransactions(
              (transaction, reason) -> onDropped(transaction, reason));
    }

    void unsubscribe() {
      pendingTransactions.unsubscribePendingTransactions(onAddedListenerId);
      pendingTransactions.unsubscribeDroppedTransactions(onDroppedListenerId);
    }

    private void onDropped(final Transaction transaction, final RemovalReason reason) {
      onDroppedListeners.forEach(listener -> listener.onTransactionDropped(transaction, reason));
    }

    private void onAdded(final Transaction transaction) {
      onAddedListeners.forEach(listener -> listener.onTransactionAdded(transaction));
    }
  }

  class SaveRestoreManager {
    private final Semaphore diskAccessLock = new Semaphore(1, true);
    private final AtomicReference<CompletableFuture<Void>> writeInProgress =
        new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicReference<CompletableFuture<Void>> readInProgress =
        new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    CompletableFuture<Void> saveToDisk(final PendingTransactions pendingTransactionsToSave) {
      cancelInProgressReadOperation();
      return serializeAndDedupOperation(
          () -> executeSaveToDisk(pendingTransactionsToSave), writeInProgress);
    }

    CompletableFuture<Void> loadFromDisk() {
      return serializeAndDedupOperation(this::executeLoadFromDisk, readInProgress);
    }

    private void cancelInProgressReadOperation() {
      if (!readInProgress.get().isDone()) {
        LOG.debug("Cancelling in progress read operation");
        isCancelled.set(true);
        try {
          waitUntilReadOperationIsCancelled();
          LOG.debug("In progress read operation cancelled");
        } catch (InterruptedException | ExecutionException e) {
          LOG.warn("Error while cancelling in progress read operation", e);
          throw new RuntimeException(e);
        }
      }
    }

    private void waitUntilReadOperationIsCancelled()
        throws InterruptedException, ExecutionException {
      readInProgress.get().get();
    }

    private CompletableFuture<Void> serializeAndDedupOperation(
        final Runnable operation,
        final AtomicReference<CompletableFuture<Void>> operationInProgress) {
      if (configuration.getEnableSaveRestore()) {
        try {
          if (diskAccessLock.tryAcquire(1, TimeUnit.MINUTES)) {

            isCancelled.set(false);
            operationInProgress.set(
                CompletableFuture.runAsync(operation)
                    .whenComplete((res, err) -> diskAccessLock.release()));
            return operationInProgress.get();
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
      final boolean appending = saveFile.exists();
      try (final BufferedWriter bw =
          new BufferedWriter(new FileWriter(saveFile, StandardCharsets.US_ASCII, appending))) {
        final var allTxs = pendingTransactionsToSave.getPendingTransactions();
        LOG.info(
            "{} {} transactions to file {}",
            appending ? "Appending" : "Saving",
            allTxs.size(),
            saveFile);

        final long processedTxCount =
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
              "{} {} transactions to file {}, before operation was cancelled",
              appending ? "Appended" : "Saved",
              processedTxCount,
              saveFile);
        } else {
          LOG.info(
              "{} {} transactions to file {}",
              appending ? "Appended" : "Saved",
              processedTxCount,
              saveFile);
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
            final Map<String, Long> stats =
                br.lines()
                    .takeWhile(unused -> !isCancelled.get())
                    .map(
                        line -> {
                          final boolean isLocal = line.charAt(0) == 'l';
                          final Transaction tx =
                              Transaction.readFrom(Bytes.fromBase64String(line.substring(1)));

                          final ValidationResult<TransactionInvalidReason> result =
                              addTransaction(tx, isLocal);
                          return result.isValid() ? "OK" : result.getInvalidReason().name();
                        })
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            br.close();

            final var added = stats.getOrDefault("OK", 0L);
            final var processedLines = stats.values().stream().mapToLong(Long::longValue).sum();

            LOG.debug("Restored transactions stats {}", stats);

            if (isCancelled.get()) {
              LOG.info(
                  "Added {} transactions of {} loaded from file {}, before operation was cancelled",
                  added,
                  processedLines,
                  saveFile);
              removeProcessedLines(saveFile, processedLines);
            } else {
              LOG.info(
                  "Added {} transactions of {} loaded from file {}, deleting file",
                  added,
                  processedLines,
                  saveFile);
              saveFile.delete();
            }
          } catch (IOException e) {
            LOG.error("Error while saving txpool content to disk", e);
          }
        }
      }
    }

    private void removeProcessedLines(final File saveFile, final long processedLines)
        throws IOException {

      LOG.debug("Removing processed lines from save file");

      final var tmp = File.createTempFile(saveFile.getName(), ".tmp");

      try (final BufferedReader reader =
              Files.newBufferedReader(saveFile.toPath(), StandardCharsets.US_ASCII);
          final BufferedWriter writer =
              Files.newBufferedWriter(tmp.toPath(), StandardCharsets.US_ASCII)) {
        reader
            .lines()
            .skip(processedLines)
            .forEach(
                line -> {
                  try {
                    writer.write(line);
                    writer.newLine();
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
      }

      saveFile.delete();
      Files.move(tmp.toPath(), saveFile.toPath());
    }
  }
}

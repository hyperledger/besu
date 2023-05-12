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

import org.hyperledger.besu.datatypes.Hash;
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
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.fluent.SimpleAccount;
import org.hyperledger.besu.plugin.data.TransactionType;

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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
  private final PendingTransactions pendingTransactions;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;
  private final TransactionBroadcaster transactionBroadcaster;
  private final MiningParameters miningParameters;
  private final TransactionPoolMetrics metrics;
  private final TransactionPoolConfiguration configuration;
  private final AtomicBoolean isPoolEnabled = new AtomicBoolean(true);

  public TransactionPool(
      final PendingTransactions pendingTransactions,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionBroadcaster transactionBroadcaster,
      final EthContext ethContext,
      final MiningParameters miningParameters,
      final TransactionPoolMetrics metrics,
      final TransactionPoolConfiguration configuration) {
    this.pendingTransactions = pendingTransactions;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.transactionBroadcaster = transactionBroadcaster;
    this.miningParameters = miningParameters;
    this.metrics = metrics;
    this.configuration = configuration;
    ethContext.getEthPeers().subscribeConnect(this::handleConnect);
    initLogForReplay();
    CompletableFuture.runAsync(this::loadFromDisk);
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

  public void saveToDisk() {
    if (configuration.getEnableSaveRestore()) {
      final File saveFile = configuration.getSaveFile();
      LOG.info("Saving transaction pool content to file {}", saveFile);
      try (final BufferedWriter bw =
          new BufferedWriter(new FileWriter(saveFile, StandardCharsets.US_ASCII))) {
        final var allTxs = pendingTransactions.getPendingTransactions();
        allTxs.parallelStream()
            .map(
                ptx -> {
                  final BytesValueRLPOutput rlp = new BytesValueRLPOutput();
                  ptx.getTransaction().writeTo(rlp);
                  return (ptx.isReceivedFromLocalSource() ? "l" : "r")
                      + rlp.encoded().toBase64String();
                })
            .forEach(
                line -> {
                  synchronized (bw) {
                    try {
                      bw.write(line);
                      bw.newLine();
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  }
                });
        LOG.info("Saved {} transactions to file {}", allTxs.size(), saveFile);
      } catch (IOException e) {
        LOG.error("Error while saving txpool content to disk", e);
      }
    }
  }

  public void loadFromDisk() {
    if (configuration.getEnableSaveRestore()) {
      final File saveFile = configuration.getSaveFile();
      if (saveFile.exists()) {
        LOG.info("Loading transaction pool content from file {}", saveFile);
        try (final BufferedReader br =
            new BufferedReader(new FileReader(saveFile, StandardCharsets.US_ASCII))) {
          final IntSummaryStatistics stats =
              br.lines()
                  .parallel()
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
          LOG.info(
              "Added {} transactions of {} loaded from file {}",
              stats.getSum(),
              stats.getCount(),
              saveFile);
        } catch (IOException e) {
          LOG.error("Error while saving txpool content to disk", e);
        }
      }
      saveFile.delete();
    }
  }

  void handleConnect(final EthPeer peer) {
    transactionBroadcaster.relayTransactionPoolTo(peer);
  }

  public void reset() {
    pendingTransactions.reset();
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

  public void addRemoteTransactions(final Collection<Transaction> transactions) {
    final long started = System.currentTimeMillis();
    final int initialCount = transactions.size();
    final List<Transaction> addedTransactions = new ArrayList<>(initialCount);
    LOG.debug("Adding {} remote transactions", initialCount);

    sortedBySenderAndNonce(transactions)
        .forEach(
            transaction -> {
              final var result = addRemoteTransaction(transaction);
              if (result.isValid()) {
                addedTransactions.add(transaction);
              }
            });

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
    LOG.trace("Block added event {}", event);
    if (event.getEventType().equals(BlockAddedEvent.EventType.HEAD_ADVANCED)
        || event.getEventType().equals(BlockAddedEvent.EventType.CHAIN_REORG)) {
      if (isPoolEnabled.get()) {
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

  private MainnetTransactionValidator getTransactionValidator() {
    return protocolSchedule
        .getByBlockHeader(protocolContext.getBlockchain().getChainHeadHeader())
        .getTransactionValidator();
  }

  public PendingTransactions getPendingTransactions() {
    return pendingTransactions;
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

  public void setEnabled() {
    isPoolEnabled.set(true);
  }

  public void setDisabled() {
    isPoolEnabled.set(false);
  }

  public boolean isEnabled() {
    return isPoolEnabled.get();
  }
}

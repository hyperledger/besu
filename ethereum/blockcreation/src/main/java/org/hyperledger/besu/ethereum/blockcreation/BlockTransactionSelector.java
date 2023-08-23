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
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.LogsWrapper;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelectorFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for extracting transactions from PendingTransactions and determining if the
 * transaction is suitable for inclusion in the block defined by the provided
 * ProcessableBlockHeader.
 *
 * <p>If a transaction is suitable for inclusion, the world state must be updated, and a receipt
 * generated.
 *
 * <p>The output from this class's execution will be:
 *
 * <ul>
 *   <li>A list of transactions to include in the block being constructed.
 *   <li>A list of receipts for inclusion in the block.
 *   <li>The root hash of the world state at the completion of transaction execution.
 *   <li>The amount of gas consumed when executing all transactions.
 *   <li>A list of transactions evaluated but not included in the block being constructed.
 * </ul>
 *
 * Once "used" this class must be discarded and another created. This class contains state which is
 * not cleared between executions of buildTransactionListForBlock().
 */
public class BlockTransactionSelector {

  public static class TransactionSelectionResults {
    private final List<Transaction> selectedTransactions = Lists.newArrayList();
    private final Map<TransactionType, List<Transaction>> transactionsByType =
        new EnumMap<>(TransactionType.class);
    private final List<TransactionReceipt> receipts = Lists.newArrayList();
    private final Map<Transaction, TransactionSelectionResult> notSelectedTransactions =
        new HashMap<>();
    private long cumulativeGasUsed = 0;
    private long cumulativeBlobGasUsed = 0;

    private void updateSelected(
        final Transaction transaction,
        final TransactionReceipt receipt,
        final long gasUsed,
        final long blobGasUsed) {
      selectedTransactions.add(transaction);
      transactionsByType
          .computeIfAbsent(transaction.getType(), type -> new ArrayList<>())
          .add(transaction);
      receipts.add(receipt);
      cumulativeGasUsed += gasUsed;
      cumulativeBlobGasUsed += blobGasUsed;
      LOG.atTrace()
          .setMessage(
              "New selected transaction {}, total transactions {}, cumulative gas used {}, cumulative blob gas used {}")
          .addArgument(transaction::toTraceLog)
          .addArgument(selectedTransactions::size)
          .addArgument(cumulativeGasUsed)
          .addArgument(cumulativeBlobGasUsed)
          .log();
    }

    public void updateNotSelected(
        final Transaction transaction, final TransactionSelectionResult res) {
      notSelectedTransactions.put(transaction, res);
    }

    public List<Transaction> getSelectedTransactions() {
      return selectedTransactions;
    }

    public List<Transaction> getTransactionsByType(final TransactionType type) {
      return transactionsByType.getOrDefault(type, List.of());
    }

    public List<TransactionReceipt> getReceipts() {
      return receipts;
    }

    public long getCumulativeGasUsed() {
      return cumulativeGasUsed;
    }

    public long getCumulativeBlobGasUsed() {
      return cumulativeBlobGasUsed;
    }

    public Map<Transaction, TransactionSelectionResult> getNotSelectedTransactions() {
      return notSelectedTransactions;
    }

    public void logSelectionStats() {
      if (LOG.isDebugEnabled()) {
        final Map<TransactionSelectionResult, Long> notSelectedStats =
            notSelectedTransactions.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        LOG.debug(
            "Selection stats: Totals[Evaluated={}, Selected={}, NotSelected={}, Discarded={}]; Detailed[{}]",
            selectedTransactions.size() + notSelectedTransactions.size(),
            selectedTransactions.size(),
            notSelectedStats.size(),
            notSelectedStats.entrySet().stream()
                .filter(e -> e.getKey().discard())
                .map(Map.Entry::getValue)
                .mapToInt(Long::intValue)
                .sum(),
            notSelectedStats.entrySet().stream()
                .map(e -> e.getKey().toString() + "=" + e.getValue())
                .sorted()
                .collect(Collectors.joining(", ")));
      }
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TransactionSelectionResults that = (TransactionSelectionResults) o;
      return cumulativeGasUsed == that.cumulativeGasUsed
          && cumulativeBlobGasUsed == that.cumulativeBlobGasUsed
          && selectedTransactions.equals(that.selectedTransactions)
          && notSelectedTransactions.equals(that.notSelectedTransactions)
          && receipts.equals(that.receipts);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          selectedTransactions,
          notSelectedTransactions,
          receipts,
          cumulativeGasUsed,
          cumulativeBlobGasUsed);
    }

    public String toTraceLog() {
      return "cumulativeGasUsed="
          + cumulativeGasUsed
          + ", cumulativeBlobGasUsed="
          + cumulativeBlobGasUsed
          + ", selectedTransactions="
          + selectedTransactions.stream()
              .map(Transaction::toTraceLog)
              .collect(Collectors.joining("; "))
          + ", notSelectedTransactions="
          + notSelectedTransactions.entrySet().stream()
              .map(e -> e.getValue() + ":" + e.getKey().toTraceLog())
              .collect(Collectors.joining(";"));
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(BlockTransactionSelector.class);
  private static final TransactionSelector ALWAYS_SELECT =
      (_1, _2, _3, _4) -> TransactionSelectionResult.SELECTED;
  private final Wei minTransactionGasPrice;
  private final Double minBlockOccupancyRatio;
  private final Supplier<Boolean> isCancelled;
  private final MainnetTransactionProcessor transactionProcessor;
  private final ProcessableBlockHeader processableBlockHeader;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final TransactionPool transactionPool;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private final Address miningBeneficiary;
  private final Wei blobGasPrice;
  private final FeeMarket feeMarket;
  private final GasCalculator gasCalculator;
  private final GasLimitCalculator gasLimitCalculator;
  private final TransactionSelector transactionSelector;
  private final TransactionSelectionResults transactionSelectionResults =
      new TransactionSelectionResults();

  public BlockTransactionSelector(
      final MainnetTransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final TransactionPool transactionPool,
      final ProcessableBlockHeader processableBlockHeader,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final Wei minTransactionGasPrice,
      final Double minBlockOccupancyRatio,
      final Supplier<Boolean> isCancelled,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final FeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final Optional<TransactionSelectorFactory> transactionSelectorFactory) {
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.worldState = worldState;
    this.transactionPool = transactionPool;
    this.processableBlockHeader = processableBlockHeader;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.isCancelled = isCancelled;
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.minBlockOccupancyRatio = minBlockOccupancyRatio;
    this.miningBeneficiary = miningBeneficiary;
    this.blobGasPrice = blobGasPrice;
    this.feeMarket = feeMarket;
    this.gasCalculator = gasCalculator;
    this.gasLimitCalculator = gasLimitCalculator;
    this.transactionSelector =
        transactionSelectorFactory.map(TransactionSelectorFactory::create).orElse(ALWAYS_SELECT);
  }

  /*
  This function iterates over (potentially) all transactions in the PendingTransactions, this is a
  long-running process. If running in a thread, it can be cancelled via the isCancelled supplier (which will result
  in this throwing an CancellationException).
   */
  public TransactionSelectionResults buildTransactionListForBlock() {
    LOG.atDebug()
        .setMessage("Transaction pool stats {}")
        .addArgument(transactionPool::logStats)
        .log();
    transactionPool.selectTransactions(
        pendingTransaction -> {
          final var res = evaluateTransaction(pendingTransaction);
          if (!res.selected()) {
            transactionSelectionResults.updateNotSelected(pendingTransaction, res);
          }
          return res;
        });
    LOG.atTrace()
        .setMessage("Transaction selection result result {}")
        .addArgument(transactionSelectionResults::toTraceLog)
        .log();
    return transactionSelectionResults;
  }

  /**
   * Evaluate the given transactions and return the result of that evaluation.
   *
   * @param transactions The set of transactions to evaluate.
   * @return The {@code TransactionSelectionResults} results of transaction evaluation.
   */
  public TransactionSelectionResults evaluateTransactions(final List<Transaction> transactions) {
    transactions.forEach(
        transaction -> {
          final var res = evaluateTransaction(transaction);
          if (!res.selected()) {
            transactionSelectionResults.updateNotSelected(transaction, res);
          }
        });
    return transactionSelectionResults;
  }

  /*
   * Passed into the PendingTransactions, and is called on each transaction until sufficient
   * transactions are found which fill a block worth of gas.
   *
   * This function will continue to be called until the block under construction is suitably
   * full (in terms of gasLimit) and the provided transaction's gasLimit does not fit within
   * the space remaining in the block.
   *
   */
  private TransactionSelectionResult evaluateTransaction(final Transaction transaction) {
    if (isCancelled.get()) {
      throw new CancellationException("Cancelled during transaction selection.");
    }

    if (transactionTooLargeForBlock(transaction)) {
      LOG.atTrace()
          .setMessage("Transaction {} too large to select for block creation")
          .addArgument(transaction::toTraceLog)
          .log();
      if (blockOccupancyAboveThreshold()) {
        LOG.trace("Block occupancy above threshold, completing operation");
        return TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD;
      } else if (blockFull()) {
        LOG.trace("Block full, completing operation");
        return TransactionSelectionResult.BLOCK_FULL;
      } else {
        return TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS;
      }
    }

    if (transactionCurrentPriceBelowMin(transaction)) {
      return TransactionSelectionResult.CURRENT_TX_PRICE_BELOW_MIN;
    }
    if (transactionDataPriceBelowMin(transaction)) {
      return TransactionSelectionResult.DATA_PRICE_BELOW_CURRENT_MIN;
    }

    final WorldUpdater worldStateUpdater = worldState.updater();
    final BlockHashLookup blockHashLookup =
        new CachingBlockHashLookup(processableBlockHeader, blockchain);

    final TransactionProcessingResult effectiveResult =
        transactionProcessor.processTransaction(
            blockchain,
            worldStateUpdater,
            processableBlockHeader,
            transaction,
            miningBeneficiary,
            blockHashLookup,
            false,
            TransactionValidationParams.mining(),
            blobGasPrice);

    if (!effectiveResult.isInvalid()) {

      final long gasUsedByTransaction =
          transaction.getGasLimit() - effectiveResult.getGasRemaining();

      final long cumulativeGasUsed =
          transactionSelectionResults.getCumulativeGasUsed() + gasUsedByTransaction;

      // check if the transaction is valid also for an optional configured plugin
      final TransactionSelectionResult txSelectionResult =
          transactionSelector.selectTransaction(
              transaction,
              effectiveResult.getStatus() == TransactionProcessingResult.Status.SUCCESSFUL,
              getLogs(effectiveResult.getLogs()),
              cumulativeGasUsed);

      if (txSelectionResult.equals(TransactionSelectionResult.SELECTED)) {

        worldStateUpdater.commit();
        final TransactionReceipt receipt =
            transactionReceiptFactory.create(
                transaction.getType(), effectiveResult, worldState, cumulativeGasUsed);

        final long blobGasUsed = gasCalculator.blobGasCost(transaction.getBlobCount());

        transactionSelectionResults.updateSelected(
            transaction, receipt, gasUsedByTransaction, blobGasUsed);

        LOG.atTrace()
            .setMessage("Selected {} for block creation")
            .addArgument(transaction::toTraceLog)
            .log();

        return TransactionSelectionResult.SELECTED;
      }

      // the transaction is not valid for the plugin
      return txSelectionResult;
    }

    return transactionSelectionResultForInvalidResult(
        transaction, effectiveResult.getValidationResult());
  }

  private List<org.hyperledger.besu.plugin.data.Log> getLogs(final List<Log> logs) {
    return logs.stream().map(LogsWrapper::new).collect(Collectors.toList());
  }

  private boolean transactionDataPriceBelowMin(final Transaction transaction) {
    if (transaction.getType().supportsBlob()) {
      if (transaction.getMaxFeePerBlobGas().orElseThrow().lessThan(blobGasPrice)) {
        return true;
      }
    }
    return false;
  }

  private boolean transactionCurrentPriceBelowMin(final Transaction transaction) {
    // Here we only care about EIP1159 since for Frontier and local transactions the checks
    // that we do when accepting them in the pool are enough
    if (transaction.getType().supports1559FeeMarket()
        && !transactionPool.isLocalSender(transaction.getSender())) {

      // For EIP1559 transactions, the price is dynamic and depends on network conditions, so we can
      // only calculate at this time the current minimum price the transaction is willing to pay
      // and if it is above the minimum accepted by the node.
      // If below we do not delete the transaction, since when we added the transaction to the pool,
      // we assured sure that the maxFeePerGas is >= of the minimum price accepted by the node
      // and so the price of the transaction could satisfy this rule in the future
      final Wei currentMinTransactionGasPriceInBlock =
          feeMarket
              .getTransactionPriceCalculator()
              .price(transaction, processableBlockHeader.getBaseFee());
      if (minTransactionGasPrice.compareTo(currentMinTransactionGasPriceInBlock) > 0) {
        LOG.trace(
            "Current gas fee of {} is lower than configured minimum {}, skipping",
            transaction,
            minTransactionGasPrice);
        return true;
      }
    }
    return false;
  }

  private TransactionSelectionResult transactionSelectionResultForInvalidResult(
      final Transaction transaction,
      final ValidationResult<TransactionInvalidReason> invalidReasonValidationResult) {

    final TransactionInvalidReason invalidReason = invalidReasonValidationResult.getInvalidReason();
    // If the invalid reason is transient, then leave the transaction in the pool and continue
    if (isTransientValidationError(invalidReason)) {
      LOG.atTrace()
          .setMessage("Transient validation error {} for transaction {} keeping it in the pool")
          .addArgument(invalidReason)
          .addArgument(transaction::toTraceLog)
          .log();
      return TransactionSelectionResult.invalidTransient(invalidReason.name());
    }
    // If the transaction was invalid for any other reason, delete it, and continue.
    LOG.atTrace()
        .setMessage("Delete invalid transaction {}, reason {}")
        .addArgument(transaction::toTraceLog)
        .addArgument(invalidReason)
        .log();
    return TransactionSelectionResult.invalid(invalidReason.name());
  }

  private boolean isTransientValidationError(final TransactionInvalidReason invalidReason) {
    return invalidReason.equals(TransactionInvalidReason.GAS_PRICE_BELOW_CURRENT_BASE_FEE)
        || invalidReason.equals(TransactionInvalidReason.NONCE_TOO_HIGH);
  }

  private boolean transactionTooLargeForBlock(final Transaction transaction) {
    final long blobGasUsed = gasCalculator.blobGasCost(transaction.getBlobCount());

    if (blobGasUsed
        > gasLimitCalculator.currentBlobGasLimit()
            - transactionSelectionResults.getCumulativeBlobGasUsed()) {
      return true;
    }

    return transaction.getGasLimit() + blobGasUsed
        > processableBlockHeader.getGasLimit() - transactionSelectionResults.getCumulativeGasUsed();
  }

  private boolean blockOccupancyAboveThreshold() {
    final long gasAvailable = processableBlockHeader.getGasLimit();

    final long gasUsed = transactionSelectionResults.getCumulativeGasUsed();
    final long gasRemaining = gasAvailable - gasUsed;
    final double occupancyRatio = (double) gasUsed / (double) gasAvailable;

    LOG.trace(
        "Min block occupancy ratio {}, gas used {}, available {}, remaining {}, used/available {}",
        minBlockOccupancyRatio,
        gasUsed,
        gasAvailable,
        gasRemaining,
        occupancyRatio);

    return occupancyRatio >= minBlockOccupancyRatio;
  }

  private boolean blockFull() {
    final long gasAvailable = processableBlockHeader.getGasLimit();
    final long gasUsed = transactionSelectionResults.getCumulativeGasUsed();

    final long gasRemaining = gasAvailable - gasUsed;

    if (gasRemaining < gasCalculator.getMinimumTransactionCost()) {
      LOG.trace(
          "Block full, remaining gas {} is less than minimum transaction gas cost {}",
          gasRemaining,
          gasCalculator.getMinimumTransactionCost());
      return true;
    }
    return false;
  }
}

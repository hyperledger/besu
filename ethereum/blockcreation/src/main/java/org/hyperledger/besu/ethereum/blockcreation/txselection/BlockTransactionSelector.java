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
package org.hyperledger.besu.ethereum.blockcreation.txselection;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AbstractTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlobPriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlockSizeTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.PriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.ProcessingResultTransactionSelector;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelectorFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

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
  private static final Logger LOG = LoggerFactory.getLogger(BlockTransactionSelector.class);
  private final Supplier<Boolean> isCancelled;
  private final MainnetTransactionProcessor transactionProcessor;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private final BlockSelectionContext blockSelectionContext;
  private final TransactionSelectionResults transactionSelectionResults =
      new TransactionSelectionResults();
  private final List<AbstractTransactionSelector> transactionSelectors;
  private final List<TransactionSelector> externalTransactionSelectors;

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
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.isCancelled = isCancelled;
    this.blockSelectionContext =
        new BlockSelectionContext(
            gasCalculator,
            gasLimitCalculator,
            minTransactionGasPrice,
            minBlockOccupancyRatio,
            processableBlockHeader,
            feeMarket,
            blobGasPrice,
            miningBeneficiary,
            transactionPool);
    transactionSelectors = createTransactionSelectors(blockSelectionContext);
    externalTransactionSelectors =
        transactionSelectorFactory.map(TransactionSelectorFactory::create).orElse(List.of());
  }

  /**
   * Builds a list of transactions for a block by iterating over all transactions in the
   * PendingTransactions pool. This operation can be long-running and, if executed in a separate
   * thread, can be cancelled via the isCancelled supplier, which would result in a
   * CancellationException.
   *
   * @return The {@code TransactionSelectionResults} containing the results of transaction
   *     evaluation.
   */
  public TransactionSelectionResults buildTransactionListForBlock() {
    LOG.atDebug()
        .setMessage("Transaction pool stats {}")
        .addArgument(blockSelectionContext.transactionPool().logStats())
        .log();
    blockSelectionContext
        .transactionPool()
        .selectTransactions(
            pendingTransaction -> {
              final var res = evaluateTransaction(pendingTransaction);
              if (!res.selected()) {
                transactionSelectionResults.updateNotSelected(
                    pendingTransaction.getTransaction(), res);
              }
              return res;
            });
    LOG.atTrace()
        .setMessage("Transaction selection result {}")
        .addArgument(transactionSelectionResults::toTraceLog)
        .log();
    return transactionSelectionResults;
  }

  /**
   * Evaluates a list of transactions and updates the selection results accordingly. If a
   * transaction is not selected during the evaluation, it is updated as not selected in the
   * transaction selection results.
   *
   * @param transactions The list of transactions to be evaluated.
   * @return The {@code TransactionSelectionResults} containing the results of the transaction
   *     evaluations.
   */
  public TransactionSelectionResults evaluateTransactions(final List<Transaction> transactions) {
    transactions.forEach(
        transaction -> {
          final var res = evaluateTransaction(new PendingTransaction.Local(transaction));
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
  private TransactionSelectionResult evaluateTransaction(
      final PendingTransaction pendingTransaction) {
    if (isCancelled.get()) {
      throw new CancellationException("Cancelled during transaction selection.");
    }

    final Transaction transaction = pendingTransaction.getTransaction();

    TransactionSelectionResult selectionResult =
        evaluateTransactionPreProcessing(pendingTransaction);
    if (!selectionResult.selected()) {
      return selectionResult;
    }

    final WorldUpdater worldStateUpdater = worldState.updater();
    final BlockHashLookup blockHashLookup =
        new CachingBlockHashLookup(blockSelectionContext.processableBlockHeader(), blockchain);

    final TransactionProcessingResult effectiveResult =
        transactionProcessor.processTransaction(
            blockchain,
            worldStateUpdater,
            blockSelectionContext.processableBlockHeader(),
            transaction,
            blockSelectionContext.miningBeneficiary(),
            blockHashLookup,
            false,
            TransactionValidationParams.mining(),
            blockSelectionContext.blobGasPrice());

    var transactionWithProcessingContextResult =
        evaluateTransactionPostProcessing(pendingTransaction, effectiveResult);
    if (!transactionWithProcessingContextResult.selected()) {
      return transactionWithProcessingContextResult;
    }

    final long gasUsedByTransaction = transaction.getGasLimit() - effectiveResult.getGasRemaining();
    final long cumulativeGasUsed =
        transactionSelectionResults.getCumulativeGasUsed() + gasUsedByTransaction;

    worldStateUpdater.commit();
    final TransactionReceipt receipt =
        transactionReceiptFactory.create(
            transaction.getType(), effectiveResult, worldState, cumulativeGasUsed);

    final long blobGasUsed =
        blockSelectionContext.gasCalculator().blobGasCost(transaction.getBlobCount());

    transactionSelectionResults.updateSelected(
        transaction, receipt, gasUsedByTransaction, blobGasUsed);

    LOG.atTrace()
        .setMessage("Selected {} for block creation")
        .addArgument(transaction::toTraceLog)
        .log();

    return TransactionSelectionResult.SELECTED;
  }

  /**
   * This method evaluates a transaction by pre-processing it through a series of selectors. It
   * first processes the transaction through internal selectors, and if the transaction is selected,
   * it then processes it through external selectors. If the transaction is selected by all
   * selectors, it returns SELECTED.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult evaluateTransactionPreProcessing(
      final PendingTransaction pendingTransaction) {

    // Process the transaction through internal selectors
    for (var selector : transactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPreProcessing(
              pendingTransaction, transactionSelectionResults);
      // If the transaction is not selected by any internal selector, return the result
      if (!result.equals(TransactionSelectionResult.SELECTED)) {
        return result;
      }
    }

    // Process the transaction through external selectors
    for (var selector : externalTransactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPreProcessing(pendingTransaction);
      // If the transaction is not selected by any external selector, return the result
      if (!result.equals(TransactionSelectionResult.SELECTED)) {
        return result;
      }
    }
    // If the transaction is selected by all selectors, return SELECTED
    return TransactionSelectionResult.SELECTED;
  }

  /**
   * This method evaluates a transaction by processing it through a series of selectors. Each
   * selector may use the transaction and/or the result of the transaction processing to decide
   * whether the transaction should be included in a block. If the transaction is selected by all
   * selectors, it returns SELECTED.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @param processingResult The result of the transaction processing.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult evaluateTransactionPostProcessing(
      final PendingTransaction pendingTransaction,
      final TransactionProcessingResult processingResult) {

    // Process the transaction through internal selectors
    for (var selector : transactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPostProcessing(
              pendingTransaction, transactionSelectionResults, processingResult);
      // If the transaction is not selected by any selector, return the result
      if (!result.equals(TransactionSelectionResult.SELECTED)) {
        return result;
      }
    }

    // Process the transaction through external selectors
    for (var selector : externalTransactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPostProcessing(pendingTransaction, processingResult);
      // If the transaction is not selected by any external selector, return the result
      if (!result.equals(TransactionSelectionResult.SELECTED)) {
        return result;
      }
    }

    // If the transaction is selected by all selectors, return SELECTED
    return TransactionSelectionResult.SELECTED;
  }

  private List<AbstractTransactionSelector> createTransactionSelectors(
      final BlockSelectionContext context) {
    return List.of(
        new BlockSizeTransactionSelector(context),
        new PriceTransactionSelector(context),
        new BlobPriceTransactionSelector(context),
        new ProcessingResultTransactionSelector(context));
  }
}

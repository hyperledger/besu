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

import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.BLOCK_SELECTION_TIMEOUT;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTED;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AbstractTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AllAcceptingTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlobPriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlockSizeTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.MinPriorityFeePerGasTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.PriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.ProcessingResultTransactionSelector;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
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
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final PluginTransactionSelector pluginTransactionSelector;
  private final OperationTracer pluginOperationTracer;
  private final EthScheduler ethScheduler;
  private final AtomicBoolean isTimeout = new AtomicBoolean(false);
  private WorldUpdater blockWorldStateUpdater;

  public BlockTransactionSelector(
      final MiningParameters miningParameters,
      final MainnetTransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final TransactionPool transactionPool,
      final ProcessableBlockHeader processableBlockHeader,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final Supplier<Boolean> isCancelled,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final FeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final GasLimitCalculator gasLimitCalculator,
      final Optional<PluginTransactionSelectorFactory> transactionSelectorFactory,
      final EthScheduler ethScheduler) {
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.worldState = worldState;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.isCancelled = isCancelled;
    this.ethScheduler = ethScheduler;
    this.blockSelectionContext =
        new BlockSelectionContext(
            miningParameters,
            gasCalculator,
            gasLimitCalculator,
            processableBlockHeader,
            feeMarket,
            blobGasPrice,
            miningBeneficiary,
            transactionPool);
    transactionSelectors = createTransactionSelectors(blockSelectionContext);
    pluginTransactionSelector =
        transactionSelectorFactory
            .map(PluginTransactionSelectorFactory::create)
            .orElse(AllAcceptingTransactionSelector.INSTANCE);
    pluginOperationTracer = pluginTransactionSelector.getOperationTracer();
    blockWorldStateUpdater = worldState.updater();
  }

  private List<AbstractTransactionSelector> createTransactionSelectors(
      final BlockSelectionContext context) {
    return List.of(
        new BlockSizeTransactionSelector(context),
        new PriceTransactionSelector(context),
        new BlobPriceTransactionSelector(context),
        new MinPriorityFeePerGasTransactionSelector(context),
        new ProcessingResultTransactionSelector(context));
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
    timeLimitedSelection();
    LOG.atTrace()
        .setMessage("Transaction selection result {}")
        .addArgument(transactionSelectionResults::toTraceLog)
        .log();
    return transactionSelectionResults;
  }

  private void timeLimitedSelection() {
    final long blockTxsSelectionMaxTime =
        blockSelectionContext.miningParameters().getUnstable().getBlockTxsSelectionMaxTime();
    final var txSelection =
        ethScheduler.scheduleBlockCreationTask(
            () ->
                blockSelectionContext
                    .transactionPool()
                    .selectTransactions(this::evaluateTransaction));

    try {
      txSelection.get(blockTxsSelectionMaxTime, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException e) {
      if (isCancelled.get()) {
        throw new CancellationException("Cancelled during transaction selection");
      }
      LOG.warn("Error during block transaction selection", e);
    } catch (TimeoutException e) {
      // synchronize since we want to be sure that there is no concurrent state update
      synchronized (isTimeout) {
        isTimeout.set(true);
      }
      LOG.warn(
          "Interrupting transaction selection since it is taking more than the max configured time of "
              + blockTxsSelectionMaxTime
              + "ms",
          e);
    }
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
        transaction -> evaluateTransaction(new PendingTransaction.Local.Priority(transaction)));
    return transactionSelectionResults;
  }

  /**
   * Passed into the PendingTransactions, and is called on each transaction until sufficient
   * transactions are found which fill a block worth of gas. This function will continue to be
   * called until the block under construction is suitably full (in terms of gasLimit) and the
   * provided transaction's gasLimit does not fit within the space remaining in the block.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @return The result of the transaction evaluation process.
   * @throws CancellationException if the transaction selection process is cancelled.
   */
  private TransactionSelectionResult evaluateTransaction(
      final PendingTransaction pendingTransaction) {
    checkCancellation();

    TransactionSelectionResult selectionResult = evaluatePreProcessing(pendingTransaction);
    if (!selectionResult.selected()) {
      return handleTransactionNotSelected(pendingTransaction, selectionResult);
    }

    final WorldUpdater txWorldStateUpdater = blockWorldStateUpdater.updater();
    final TransactionProcessingResult processingResult =
        processTransaction(pendingTransaction, txWorldStateUpdater);

    var postProcessingSelectionResult =
        evaluatePostProcessing(pendingTransaction, processingResult);

    if (postProcessingSelectionResult.selected()) {
      return handleTransactionSelected(pendingTransaction, processingResult, txWorldStateUpdater);
    }
    return handleTransactionNotSelected(
        pendingTransaction, postProcessingSelectionResult, txWorldStateUpdater);
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
  private TransactionSelectionResult evaluatePreProcessing(
      final PendingTransaction pendingTransaction) {

    for (var selector : transactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPreProcessing(
              pendingTransaction, transactionSelectionResults);
      if (!result.equals(SELECTED)) {
        return result;
      }
    }
    return pluginTransactionSelector.evaluateTransactionPreProcessing(pendingTransaction);
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
  private TransactionSelectionResult evaluatePostProcessing(
      final PendingTransaction pendingTransaction,
      final TransactionProcessingResult processingResult) {

    for (var selector : transactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPostProcessing(
              pendingTransaction, transactionSelectionResults, processingResult);
      if (!result.equals(SELECTED)) {
        return result;
      }
    }
    return pluginTransactionSelector.evaluateTransactionPostProcessing(
        pendingTransaction, processingResult);
  }

  /**
   * Processes a transaction
   *
   * @param pendingTransaction The transaction to be processed.
   * @param worldStateUpdater The world state updater.
   * @return The result of the transaction processing.
   */
  private TransactionProcessingResult processTransaction(
      final PendingTransaction pendingTransaction, final WorldUpdater worldStateUpdater) {
    final BlockHashLookup blockHashLookup =
        new CachingBlockHashLookup(blockSelectionContext.processableBlockHeader(), blockchain);
    return transactionProcessor.processTransaction(
        blockchain,
        worldStateUpdater,
        blockSelectionContext.processableBlockHeader(),
        pendingTransaction.getTransaction(),
        blockSelectionContext.miningBeneficiary(),
        pluginOperationTracer,
        blockHashLookup,
        false,
        TransactionValidationParams.mining(),
        blockSelectionContext.blobGasPrice());
  }

  /**
   * Handles a selected transaction by committing the world state updates, creating a transaction
   * receipt, updating the TransactionSelectionResults with the selected transaction, and notifying
   * the external transaction selector.
   *
   * @param pendingTransaction The pending transaction.
   * @param processingResult The result of the transaction processing.
   * @param txWorldStateUpdater The world state updater.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult handleTransactionSelected(
      final PendingTransaction pendingTransaction,
      final TransactionProcessingResult processingResult,
      final WorldUpdater txWorldStateUpdater) {
    final Transaction transaction = pendingTransaction.getTransaction();

    final long gasUsedByTransaction =
        transaction.getGasLimit() - processingResult.getGasRemaining();
    final long cumulativeGasUsed =
        transactionSelectionResults.getCumulativeGasUsed() + gasUsedByTransaction;
    final long blobGasUsed =
        blockSelectionContext.gasCalculator().blobGasCost(transaction.getBlobCount());

    final boolean tooLate;

    // only add this tx to the selected set if it is not too late,
    // this need to be done synchronously to avoid that a concurrent timeout
    // could start packing a block while we are updating the state here
    synchronized (isTimeout) {
      if (!isTimeout.get()) {
        txWorldStateUpdater.commit();
        blockWorldStateUpdater.commit();
        final TransactionReceipt receipt =
            transactionReceiptFactory.create(
                transaction.getType(), processingResult, worldState, cumulativeGasUsed);

        transactionSelectionResults.updateSelected(
            pendingTransaction.getTransaction(), receipt, gasUsedByTransaction, blobGasUsed);
        tooLate = false;
      } else {
        tooLate = true;
      }
    }

    if (tooLate) {
      // even if this tx passed all the checks, it is too late to include it in this block,
      // so we need to treat it as not selected
      LOG.atTrace()
          .setMessage("{} processed too late for block creation")
          .addArgument(transaction::toTraceLog)
          .log();
      // do not rely on the presence of this result, since by the time it is added, the code
      // reading it could have been already executed by another thread
      return handleTransactionNotSelected(
          pendingTransaction, BLOCK_SELECTION_TIMEOUT, txWorldStateUpdater);
    }

    pluginTransactionSelector.onTransactionSelected(pendingTransaction, processingResult);
    blockWorldStateUpdater = worldState.updater();
    LOG.atTrace()
        .setMessage("Selected {} for block creation")
        .addArgument(transaction::toTraceLog)
        .log();
    return SELECTED;
  }

  /**
   * Handles the scenario when a transaction is not selected. It updates the
   * TransactionSelectionResults with the unselected transaction, and notifies the external
   * transaction selector.
   *
   * @param pendingTransaction The unselected pending transaction.
   * @param selectionResult The result of the transaction selection process.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult handleTransactionNotSelected(
      final PendingTransaction pendingTransaction,
      final TransactionSelectionResult selectionResult) {

    transactionSelectionResults.updateNotSelected(
        pendingTransaction.getTransaction(), selectionResult);
    pluginTransactionSelector.onTransactionNotSelected(pendingTransaction, selectionResult);
    return selectionResult;
  }

  private TransactionSelectionResult handleTransactionNotSelected(
      final PendingTransaction pendingTransaction,
      final TransactionSelectionResult selectionResult,
      final WorldUpdater txWorldStateUpdater) {
    txWorldStateUpdater.revert();
    return handleTransactionNotSelected(pendingTransaction, selectionResult);
  }

  private void checkCancellation() {
    if (isCancelled.get()) {
      throw new CancellationException("Cancelled during transaction selection.");
    }
  }
}

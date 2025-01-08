/*
 * Copyright contributors to Hyperledger Besu.
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
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.BLOCK_SELECTION_TIMEOUT_INVALID_TX;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.INVALID_TX_EVALUATION_TOO_LONG;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTED;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.TX_EVALUATION_TOO_LONG;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AbstractTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlobPriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlobSizeTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlockSizeTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.MinPriorityFeePerGasTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.PriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.ProcessingResultTransactionSelector;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
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
import org.hyperledger.besu.ethereum.mainnet.blockhash.BlockHashProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.google.common.base.Stopwatch;
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
  private final BlockAwareOperationTracer operationTracer;
  private final EthScheduler ethScheduler;
  private final AtomicBoolean isTimeout = new AtomicBoolean(false);
  private final long blockTxsSelectionMaxTime;
  private WorldUpdater blockWorldStateUpdater;
  private volatile TransactionEvaluationContext currTxEvaluationContext;

  public BlockTransactionSelector(
      final MiningConfiguration miningConfiguration,
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
      final BlockHashProcessor blockHashProcessor,
      final PluginTransactionSelector pluginTransactionSelector,
      final EthScheduler ethScheduler) {
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.worldState = worldState;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.isCancelled = isCancelled;
    this.ethScheduler = ethScheduler;
    this.blockSelectionContext =
        new BlockSelectionContext(
            miningConfiguration,
            gasCalculator,
            gasLimitCalculator,
            blockHashProcessor,
            processableBlockHeader,
            feeMarket,
            blobGasPrice,
            miningBeneficiary,
            transactionPool);
    transactionSelectors = createTransactionSelectors(blockSelectionContext);
    this.pluginTransactionSelector = pluginTransactionSelector;
    this.operationTracer =
        new InterruptibleOperationTracer(pluginTransactionSelector.getOperationTracer());
    blockWorldStateUpdater = worldState.updater();
    blockTxsSelectionMaxTime = miningConfiguration.getBlockTxsSelectionMaxTime();
  }

  private List<AbstractTransactionSelector> createTransactionSelectors(
      final BlockSelectionContext context) {
    return List.of(
        new BlockSizeTransactionSelector(context),
        new BlobSizeTransactionSelector(context),
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
        .addArgument(blockSelectionContext.transactionPool()::logStats)
        .log();
    timeLimitedSelection();
    LOG.atTrace()
        .setMessage("Transaction selection result {}")
        .addArgument(transactionSelectionResults::toTraceLog)
        .log();
    return transactionSelectionResults;
  }

  private void timeLimitedSelection() {
    final var txSelectionTask =
        new FutureTask<Void>(
            () ->
                blockSelectionContext
                    .transactionPool()
                    .selectTransactions(this::evaluateTransaction),
            null);
    ethScheduler.scheduleBlockCreationTask(txSelectionTask);

    try {
      txSelectionTask.get(blockTxsSelectionMaxTime, TimeUnit.MILLISECONDS);
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

      cancelEvaluatingTxWithGraceTime(txSelectionTask);

      LOG.warn(
          "Interrupting the selection of transactions for block inclusion as it exceeds the maximum configured duration of "
              + blockTxsSelectionMaxTime
              + "ms",
          e);
    }
  }

  private void cancelEvaluatingTxWithGraceTime(final FutureTask<Void> txSelectionTask) {
    final long elapsedTime =
        currTxEvaluationContext.getEvaluationTimer().elapsed(TimeUnit.MILLISECONDS);
    // adding 100ms so we are sure it take strictly more than the block selection max time
    final long txRemainingTime = (blockTxsSelectionMaxTime - elapsedTime) + 100;

    LOG.atDebug()
        .setMessage(
            "Transaction {} is processing for {}ms, giving it {}ms grace time, before considering it taking too much time to execute")
        .addArgument(currTxEvaluationContext.getPendingTransaction()::toTraceLog)
        .addArgument(elapsedTime)
        .addArgument(txRemainingTime)
        .log();

    ethScheduler.scheduleFutureTask(
        () -> {
          if (!txSelectionTask.isDone()) {
            LOG.atDebug()
                .setMessage(
                    "Transaction {} is still processing after the grace time, total processing time {}ms,"
                        + " greater than max block selection time of {}ms, forcing an interrupt")
                .addArgument(currTxEvaluationContext.getPendingTransaction()::toTraceLog)
                .addArgument(
                    () ->
                        currTxEvaluationContext.getEvaluationTimer().elapsed(TimeUnit.MILLISECONDS))
                .addArgument(blockTxsSelectionMaxTime)
                .log();

            txSelectionTask.cancel(true);
          }
        },
        Duration.ofMillis(txRemainingTime));
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

    final TransactionEvaluationContext evaluationContext =
        createTransactionEvaluationContext(pendingTransaction);
    currTxEvaluationContext = evaluationContext;

    TransactionSelectionResult selectionResult = evaluatePreProcessing(evaluationContext);
    if (!selectionResult.selected()) {
      return handleTransactionNotSelected(evaluationContext, selectionResult);
    }

    final WorldUpdater txWorldStateUpdater = blockWorldStateUpdater.updater();
    final TransactionProcessingResult processingResult =
        processTransaction(pendingTransaction, txWorldStateUpdater);

    var postProcessingSelectionResult = evaluatePostProcessing(evaluationContext, processingResult);

    if (postProcessingSelectionResult.selected()) {
      return handleTransactionSelected(evaluationContext, processingResult, txWorldStateUpdater);
    }
    return handleTransactionNotSelected(
        evaluationContext, postProcessingSelectionResult, txWorldStateUpdater);
  }

  private TransactionEvaluationContext createTransactionEvaluationContext(
      final PendingTransaction pendingTransaction) {
    final Wei transactionGasPriceInBlock =
        blockSelectionContext
            .feeMarket()
            .getTransactionPriceCalculator()
            .price(
                pendingTransaction.getTransaction(),
                blockSelectionContext.pendingBlockHeader().getBaseFee());

    return new TransactionEvaluationContext(
        blockSelectionContext.pendingBlockHeader(),
        pendingTransaction,
        Stopwatch.createStarted(),
        transactionGasPriceInBlock,
        blockSelectionContext.miningConfiguration().getMinTransactionGasPrice());
  }

  /**
   * This method evaluates a transaction by pre-processing it through a series of selectors. It
   * first processes the transaction through internal selectors, and if the transaction is selected,
   * it then processes it through external selectors. If the transaction is selected by all
   * selectors, it returns SELECTED.
   *
   * @param evaluationContext The current selection session data.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult evaluatePreProcessing(
      final TransactionEvaluationContext evaluationContext) {

    for (var selector : transactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPreProcessing(evaluationContext, transactionSelectionResults);
      if (!result.equals(SELECTED)) {
        return result;
      }
    }
    return pluginTransactionSelector.evaluateTransactionPreProcessing(evaluationContext);
  }

  /**
   * This method evaluates a transaction by processing it through a series of selectors. Each
   * selector may use the transaction and/or the result of the transaction processing to decide
   * whether the transaction should be included in a block. If the transaction is selected by all
   * selectors, it returns SELECTED.
   *
   * @param evaluationContext The current selection session data.
   * @param processingResult The result of the transaction processing.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult evaluatePostProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionProcessingResult processingResult) {

    for (var selector : transactionSelectors) {
      TransactionSelectionResult result =
          selector.evaluateTransactionPostProcessing(
              evaluationContext, transactionSelectionResults, processingResult);
      if (!result.equals(SELECTED)) {
        return result;
      }
    }
    return pluginTransactionSelector.evaluateTransactionPostProcessing(
        evaluationContext, processingResult);
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
        blockSelectionContext
            .blockHashProcessor()
            .createBlockHashLookup(blockchain, blockSelectionContext.pendingBlockHeader());
    return transactionProcessor.processTransaction(
        worldStateUpdater,
        blockSelectionContext.pendingBlockHeader(),
        pendingTransaction.getTransaction(),
        blockSelectionContext.miningBeneficiary(),
        operationTracer,
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
   * @param evaluationContext The current selection session data.
   * @param processingResult The result of the transaction processing.
   * @param txWorldStateUpdater The world state updater.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult handleTransactionSelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionProcessingResult processingResult,
      final WorldUpdater txWorldStateUpdater) {
    final Transaction transaction = evaluationContext.getTransaction();

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
      tooLate = isTimeout.get();
      if (!tooLate) {
        txWorldStateUpdater.commit();
        blockWorldStateUpdater.commit();
        final TransactionReceipt receipt =
            transactionReceiptFactory.create(
                transaction.getType(), processingResult, worldState, cumulativeGasUsed);

        transactionSelectionResults.updateSelected(
            transaction, receipt, gasUsedByTransaction, blobGasUsed);
      }
    }

    if (tooLate) {
      // even if this tx passed all the checks, it is too late to include it in this block,
      // so we need to treat it as not selected

      // do not rely on the presence of this result, since by the time it is added, the code
      // reading it could have been already executed by another thread
      return handleTransactionNotSelected(
          evaluationContext, BLOCK_SELECTION_TIMEOUT, txWorldStateUpdater);
    }

    pluginTransactionSelector.onTransactionSelected(evaluationContext, processingResult);
    blockWorldStateUpdater = worldState.updater();
    LOG.atTrace()
        .setMessage("Selected {} for block creation, evaluated in {}")
        .addArgument(transaction::toTraceLog)
        .addArgument(evaluationContext.getEvaluationTimer())
        .log();
    return SELECTED;
  }

  /**
   * Handles the scenario when a transaction is not selected. It updates the
   * TransactionSelectionResults with the unselected transaction, and notifies the external
   * transaction selector.
   *
   * @param evaluationContext The current selection session data.
   * @param selectionResult The result of the transaction selection process.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult handleTransactionNotSelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult selectionResult) {

    final var pendingTransaction = evaluationContext.getPendingTransaction();

    // check if this tx took too much to evaluate, and in case it was invalid remove it from the
    // pool, otherwise penalize it. Not synchronized since there is no state change here.
    final TransactionSelectionResult actualResult =
        isTimeout.get()
            ? rewriteSelectionResultForTimeout(evaluationContext, selectionResult)
            : selectionResult;

    transactionSelectionResults.updateNotSelected(evaluationContext.getTransaction(), actualResult);
    pluginTransactionSelector.onTransactionNotSelected(evaluationContext, actualResult);
    LOG.atTrace()
        .setMessage(
            "Not selected {} for block creation with result {} (original result {}), evaluated in {}")
        .addArgument(pendingTransaction::toTraceLog)
        .addArgument(actualResult)
        .addArgument(selectionResult)
        .addArgument(evaluationContext.getEvaluationTimer())
        .log();

    return actualResult;
  }

  /**
   * In case of a block creation timeout, we rewrite the selection result, so we can easily spot
   * what happened looking at the transaction selection results.
   *
   * @param evaluationContext The current selection session data.
   * @param selectionResult The result of the transaction selection process.
   * @return the rewritten selection result
   */
  private TransactionSelectionResult rewriteSelectionResultForTimeout(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult selectionResult) {

    if (transactionTookTooLong(evaluationContext, selectionResult)) {
      return selectionResult.discard() ? INVALID_TX_EVALUATION_TOO_LONG : TX_EVALUATION_TOO_LONG;
    }

    return selectionResult.discard() ? BLOCK_SELECTION_TIMEOUT_INVALID_TX : BLOCK_SELECTION_TIMEOUT;
  }

  /**
   * Check if the evaluation of this tx took more than the block creation max time, because if true
   * we want to penalize it. We penalize it, instead of directly removing, because it could happen
   * that the tx will evaluate in time next time. Invalid txs are always removed.
   *
   * @param evaluationContext The current selection session data.
   * @param selectionResult The result of the transaction selection process.
   * @return true if the evaluation of this tx took more than the block creation max time
   */
  private boolean transactionTookTooLong(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult selectionResult) {
    final var evaluationTimer = evaluationContext.getEvaluationTimer();
    if (evaluationTimer.elapsed(TimeUnit.MILLISECONDS) > blockTxsSelectionMaxTime) {
      LOG.atWarn()
          .setMessage(
              "Transaction {} is too late for inclusion, with result {}, evaluated in {} that is over the max limit of {}ms"
                  + ", {}")
          .addArgument(evaluationContext.getPendingTransaction()::getHash)
          .addArgument(selectionResult)
          .addArgument(evaluationTimer)
          .addArgument(blockTxsSelectionMaxTime)
          .addArgument(
              selectionResult.discard() ? "removing it from the pool" : "penalizing it in the pool")
          .log();
      return true;
    }
    LOG.atTrace()
        .setMessage("Transaction {} is too late for inclusion")
        .addArgument(evaluationContext.getPendingTransaction()::toTraceLog)
        .log();

    return false;
  }

  private TransactionSelectionResult handleTransactionNotSelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult selectionResult,
      final WorldUpdater txWorldStateUpdater) {
    txWorldStateUpdater.revert();
    return handleTransactionNotSelected(evaluationContext, selectionResult);
  }

  private void checkCancellation() {
    if (isCancelled.get()) {
      throw new CancellationException("Cancelled during transaction selection.");
    }
  }
}

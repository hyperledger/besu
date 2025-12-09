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
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.INTERNAL_ERROR;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.INVALID_TX_EVALUATION_TOO_LONG;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.PLUGIN_SELECTION_TIMEOUT;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.PLUGIN_SELECTION_TIMEOUT_INVALID_TX;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTED;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTION_CANCELLED;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.TX_EVALUATION_TOO_LONG;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AbstractTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlobPriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlobSizeTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlockRlpSizeTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.BlockSizeTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.MinPriorityFeePerGasTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.PriceTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.ProcessingResultTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.SkipSenderTransactionSelector;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BlockAccessListBuilder;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.storage.MutableWorldState;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;
import org.hyperledger.besu.plugin.services.txselection.BlockTransactionSelectionService;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
@SuppressWarnings("unchecked")
public class BlockTransactionSelector implements BlockTransactionSelectionService {
  private static final Logger LOG = LoggerFactory.getLogger(BlockTransactionSelector.class);
  private static final long CANCELLATION_GRACE_TIME_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
  private final AtomicBoolean isCancelled = new AtomicBoolean(false);
  private final MainnetTransactionProcessor transactionProcessor;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private final BlockSelectionContext blockSelectionContext;
  private final TransactionSelectionResults transactionSelectionResults =
      new TransactionSelectionResults();
  private final List<AbstractTransactionSelector> transactionSelectors;
  private final SelectorsStateManager selectorsStateManager;
  private final TransactionSelectionService transactionSelectionService;
  private final PluginTransactionSelector pluginTransactionSelector;
  private final BlockAwareOperationTracer operationTracer;
  private final EthScheduler ethScheduler;
  private final AtomicBoolean isTimeout = new AtomicBoolean(false);
  private final long blockTxsSelectionMaxTimeNanos;
  private final long pluginTxsSelectionMaxTimeNanos;
  private final Optional<BlockAccessList.BlockAccessListBuilder> maybeBlockAccessListBuilder;
  private WorldUpdater blockWorldStateUpdater;
  private WorldUpdater txWorldStateUpdater;
  private volatile TransactionEvaluationContext currTxEvaluationContext;
  private final List<Runnable> selectedTxPendingActions = new ArrayList<>(1);
  private final AtomicInteger currentTxnLocation = new AtomicInteger(0);
  private volatile TransactionSelectionResult validTxSelectionTimeoutResult;
  private volatile TransactionSelectionResult invalidTxSelectionTimeoutResult;
  private volatile FutureTask<Void> currTxSelectionTask;

  public BlockTransactionSelector(
      final MiningConfiguration miningConfiguration,
      final MainnetTransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final TransactionPool transactionPool,
      final ProcessableBlockHeader processableBlockHeader,
      final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final ProtocolSpec protocolSpec,
      final PluginTransactionSelector pluginTransactionSelector,
      final EthScheduler ethScheduler,
      final SelectorsStateManager selectorsStateManager,
      final Optional<BlockAccessList.BlockAccessListBuilder> maybeBlockAccessListBuilder) {
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.worldState = worldState;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.ethScheduler = ethScheduler;
    this.blockSelectionContext =
        new BlockSelectionContext(
            miningConfiguration,
            processableBlockHeader,
            protocolSpec,
            blobGasPrice,
            miningBeneficiary,
            transactionPool);
    this.selectorsStateManager = selectorsStateManager;
    this.transactionSelectionService = miningConfiguration.getTransactionSelectionService();
    this.transactionSelectors =
        createTransactionSelectors(blockSelectionContext, selectorsStateManager);
    this.pluginTransactionSelector = pluginTransactionSelector;
    this.operationTracer =
        new InterruptibleOperationTracer(pluginTransactionSelector.getOperationTracer());
    this.blockWorldStateUpdater = worldState.updater();
    this.txWorldStateUpdater = blockWorldStateUpdater.updater();
    final var blockTxsSelectionMaxTime =
        miningConfiguration.getBlockTxsSelectionMaxTime(protocolSpec.isPoS());
    this.blockTxsSelectionMaxTimeNanos = blockTxsSelectionMaxTime.toNanos();
    this.pluginTxsSelectionMaxTimeNanos =
        miningConfiguration.getPluginTxsSelectionMaxTime(blockTxsSelectionMaxTime).toNanos();
    this.maybeBlockAccessListBuilder = maybeBlockAccessListBuilder;
  }

  private List<AbstractTransactionSelector> createTransactionSelectors(
      final BlockSelectionContext context, final SelectorsStateManager selectorsStateManager) {
    return List.of(
        new SkipSenderTransactionSelector(context),
        new BlockSizeTransactionSelector(context, selectorsStateManager),
        new BlobSizeTransactionSelector(context, selectorsStateManager),
        new PriceTransactionSelector(context),
        new BlobPriceTransactionSelector(context),
        new MinPriorityFeePerGasTransactionSelector(context),
        new BlockRlpSizeTransactionSelector(context, selectorsStateManager),
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
    timeLimitedSelection();
    LOG.atTrace()
        .setMessage("Transaction selection result {}")
        .addArgument(transactionSelectionResults::toTraceLog)
        .log();
    return transactionSelectionResults;
  }

  public void cancel() {
    isCancelled.set(true);
    if (currTxSelectionTask != null) {
      currTxSelectionTask.cancel(true);
      LOG.debug("Cancelled transaction selection task");
    }
  }

  private void timeLimitedSelection() {
    final long startTime = System.nanoTime();

    selectorsStateManager.blockSelectionStarted();

    pluginTimeLimitedSelection(startTime);

    final long elapsedPluginTxsSelectionTime = System.nanoTime() - startTime;
    final long remainingSelectionTime =
        blockTxsSelectionMaxTimeNanos - elapsedPluginTxsSelectionTime;
    LOG.atTrace()
        .setMessage(
            "Plugin transaction selection took: {}ms of max {}ms, remaining block selection time {}ms of max {}ms")
        .addArgument(() -> nanosToMillis(elapsedPluginTxsSelectionTime))
        .addArgument(() -> nanosToMillis(pluginTxsSelectionMaxTimeNanos))
        .addArgument(() -> nanosToMillis(remainingSelectionTime))
        .addArgument(() -> nanosToMillis(blockTxsSelectionMaxTimeNanos))
        .log();

    // reset timeout status for next selection run
    isTimeout.set(false);

    internalTimeLimitedSelection(remainingSelectionTime);
  }

  private void internalTimeLimitedSelection(final long remainingSelectionTime) {
    validTxSelectionTimeoutResult = BLOCK_SELECTION_TIMEOUT;
    invalidTxSelectionTimeoutResult = BLOCK_SELECTION_TIMEOUT_INVALID_TX;

    currTxSelectionTask =
        new FutureTask<>(
            () -> {
              LOG.atDebug()
                  .setMessage(
                      "Starting internal pool transaction selection, run time capped at {}ms, stats {}")
                  .addArgument(() -> nanosToMillis(remainingSelectionTime))
                  .addArgument(blockSelectionContext.transactionPool()::logStats)
                  .log();
              blockSelectionContext.transactionPool().selectTransactions(this::evaluateTransaction);
            },
            null);
    ethScheduler.scheduleBlockCreationTask(
        blockSelectionContext.pendingBlockHeader().getNumber(), currTxSelectionTask);

    try {
      currTxSelectionTask.get(remainingSelectionTime, TimeUnit.NANOSECONDS);
    } catch (InterruptedException | ExecutionException | CancellationException e) {
      if (isCancelled.get()) {
        LOG.debug(
            "Transaction selection cancelled during execution, finalizing with current progress");
      } else {
        LOG.warn("Error during block transaction selection", e);
        // force rollback
        rollback();
      }
    } catch (TimeoutException e) {
      // synchronize since we want to be sure that there is no concurrent state update
      synchronized (isTimeout) {
        isTimeout.set(true);
      }

      cancelEvaluatingTxWithGraceTime(currTxSelectionTask);

      LOG.warn(
          "Interrupting the internal selection of transactions for block inclusion as it exceeds"
              + " the allowed max duration of {}ms",
          nanosToMillis(remainingSelectionTime));
    }
  }

  private void pluginTimeLimitedSelection(final long startTime) {
    validTxSelectionTimeoutResult = PLUGIN_SELECTION_TIMEOUT;
    invalidTxSelectionTimeoutResult = PLUGIN_SELECTION_TIMEOUT_INVALID_TX;

    final CountDownLatch pluginSelectionDone = new CountDownLatch(1);

    currTxSelectionTask =
        new FutureTask<>(
            () -> {
              try {
                LOG.atDebug()
                    .setMessage("Starting plugin transaction selection, run time capped at {}ms")
                    .addArgument(() -> nanosToMillis(pluginTxsSelectionMaxTimeNanos))
                    .log();
                transactionSelectionService.selectPendingTransactions(
                    this, blockSelectionContext.pendingBlockHeader());
              } finally {
                pluginSelectionDone.countDown();
              }
            },
            null);

    ethScheduler.scheduleBlockCreationTask(
        blockSelectionContext.pendingBlockHeader().getNumber(), currTxSelectionTask);

    try {
      currTxSelectionTask.get(pluginTxsSelectionMaxTimeNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException | ExecutionException | CancellationException e) {
      if (isCancelled.get()) {
        throw new CancellationException("Cancelled during plugin transaction selection");
      }
      LOG.error("Unhandled exception during plugin transaction selection", e);
      // force a rollback
      rollback();
    } catch (TimeoutException e) {
      // synchronize since we want to be sure that there is no concurrent state update
      synchronized (isTimeout) {
        isTimeout.set(true);
      }

      // cancelling the task and interrupting the thread running it
      currTxSelectionTask.cancel(true);
      final long elapsedPluginTxsSelectionTime = System.nanoTime() - startTime;
      LOG.warn(
          "Interrupting the plugin selection of transactions for block inclusion after {}ms,"
              + " as it exceeds the maximum configured duration of {}ms",
          nanosToMillis(elapsedPluginTxsSelectionTime),
          nanosToMillis(pluginTxsSelectionMaxTimeNanos));

      final var remainingSelectionTime =
          blockTxsSelectionMaxTimeNanos - elapsedPluginTxsSelectionTime;

      LOG.atTrace()
          .setMessage(
              "Plugin transaction selection state {}, waiting {}ms for the thread to process the interrupt")
          .addArgument(currTxSelectionTask::state)
          .addArgument(() -> nanosToMillis(remainingSelectionTime))
          .log();

      try {
        // need to wait for the thread to fully process the interrupt,
        // before proceeding, to avoid overlapping executions.
        pluginSelectionDone.await(remainingSelectionTime, TimeUnit.NANOSECONDS);

        LOG.atTrace()
            .setMessage("Plugin selection cancellation processed in {}ms, task status {}")
            .addArgument(
                () ->
                    nanosToMillis((System.nanoTime() - startTime) - elapsedPluginTxsSelectionTime))
            .addArgument(currTxSelectionTask.state())
            .log();

      } catch (InterruptedException ex) {
        LOG.warn(
            "Interrupted after waiting {}ms for the cancellation of plugin transaction selection task",
            nanosToMillis(remainingSelectionTime),
            ex);
        throw new RuntimeException(ex);
      }
    }
  }

  private void cancelEvaluatingTxWithGraceTime(final FutureTask<Void> txSelectionTask) {
    final long txRemainingTime;
    if (currTxEvaluationContext != null) {
      final long txElapsedTime =
          currTxEvaluationContext.getEvaluationTimer().elapsed(TimeUnit.NANOSECONDS);
      // adding a grace time so we are sure it take strictly more than the block selection max time
      txRemainingTime =
          (blockTxsSelectionMaxTimeNanos - txElapsedTime) + CANCELLATION_GRACE_TIME_NANOS;

      LOG.atDebug()
          .setMessage(
              "Transaction {} is processing for {}ms, giving it {}ms grace time, before considering it taking too much time to execute")
          .addArgument(currTxEvaluationContext.getPendingTransaction()::toTraceLog)
          .addArgument(() -> nanosToMillis(txElapsedTime))
          .addArgument(() -> nanosToMillis(txRemainingTime))
          .log();
    } else {
      LOG.atWarn()
          .setMessage("Cancelling transaction selection before starting any evaluation")
          .log();
      txRemainingTime = blockTxsSelectionMaxTimeNanos + CANCELLATION_GRACE_TIME_NANOS;
    }
    ethScheduler.scheduleFutureTask(
        () -> {
          if (txSelectionTask.isDone()) {
            if (currTxEvaluationContext != null) {
              LOG.atDebug()
                  .setMessage(
                      "Transaction {} processed within the grace time, total processing time {}ms,"
                          + " nothing to do and no penalization applied")
                  .addArgument(currTxEvaluationContext.getPendingTransaction()::toTraceLog)
                  .addArgument(
                      () ->
                          currTxEvaluationContext
                              .getEvaluationTimer()
                              .elapsed(TimeUnit.MILLISECONDS))
                  .log();
            }
          } else {
            if (currTxEvaluationContext != null) {
              LOG.atDebug()
                  .setMessage(
                      "Transaction {} is still processing after the grace time, total processing time {}ms,"
                          + " greater than max block selection time of {}ms, forcing an interrupt")
                  .addArgument(currTxEvaluationContext.getPendingTransaction()::toTraceLog)
                  .addArgument(
                      () ->
                          currTxEvaluationContext
                              .getEvaluationTimer()
                              .elapsed(TimeUnit.MILLISECONDS))
                  .addArgument(() -> nanosToMillis(blockTxsSelectionMaxTimeNanos))
                  .log();
            } else {
              LOG.atDebug()
                  .setMessage(
                      "No transaction context when grace time expired, cancelling task anyway")
                  .log();
            }
            txSelectionTask.cancel(true);
          }
        },
        Duration.ofNanos(txRemainingTime));
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
    selectorsStateManager.blockSelectionStarted();

    transactions.forEach(
        transaction -> evaluateTransaction(new PendingTransaction.Local.Priority(transaction)));

    return transactionSelectionResults;
  }

  private TransactionSelectionResult evaluateTransaction(
      final PendingTransaction pendingTransaction) {

    TransactionSelectionResult evaluationResult;
    try {
      evaluationResult = evaluatePendingTransaction(pendingTransaction);
    } catch (Throwable t) {
      LOG.error("Unhandled exception evaluating transaction {}", pendingTransaction, t);
      evaluationResult = INTERNAL_ERROR;
    }

    if (evaluationResult.selected()) {
      return commit() ? evaluationResult : BLOCK_SELECTION_TIMEOUT;
    } else {
      rollback();
      return evaluationResult;
    }
  }

  /**
   * Passed into the PendingTransactions, and is called on each transaction until sufficient
   * transactions are found which fill a block worth of gas. This function will continue to be
   * called until the block under construction is suitably full (in terms of gasLimit) and the
   * provided transaction's gasLimit does not fit within the space remaining in the block.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @return The result of the transaction evaluation process.
   */
  @Override
  public TransactionSelectionResult evaluatePendingTransaction(
      final org.hyperledger.besu.datatypes.PendingTransaction pendingTransaction) {

    LOG.atTrace().setMessage("Starting evaluation of {}").addArgument(pendingTransaction).log();

    final TransactionEvaluationContext evaluationContext =
        createTransactionEvaluationContext(pendingTransaction);
    currTxEvaluationContext = evaluationContext;

    if (isCancelled.get()) {
      return handleTransactionNotSelected(evaluationContext, SELECTION_CANCELLED);
    }

    TransactionSelectionResult selectionResult = evaluatePreProcessing(evaluationContext);
    if (!selectionResult.selected()) {
      return handleTransactionNotSelected(evaluationContext, selectionResult);
    }

    final TransactionProcessingResult processingResult =
        processTransaction(evaluationContext.getTransaction());

    txWorldStateUpdater.markTransactionBoundary();

    var postProcessingSelectionResult = evaluatePostProcessing(evaluationContext, processingResult);

    return postProcessingSelectionResult.selected()
        ? handleTransactionSelected(evaluationContext, processingResult)
        : handleTransactionNotSelected(evaluationContext, postProcessingSelectionResult);
  }

  @Override
  public boolean commit() {
    // only add this tx to the selected set if it is not too late,
    // this needs to be done synchronously to avoid that a concurrent timeout
    // could start packing a block while we are updating the state here
    final boolean isTooLate;
    synchronized (isTimeout) {
      isTooLate = isTimeout.get();
      if (!isTooLate) {
        for (final var pendingAction : selectedTxPendingActions) {
          pendingAction.run();
        }
        selectorsStateManager.commit();
        txWorldStateUpdater.commit();
        blockWorldStateUpdater.commit();
        blockWorldStateUpdater.markTransactionBoundary();
      }
    }

    selectedTxPendingActions.clear();
    blockWorldStateUpdater = worldState.updater();
    txWorldStateUpdater = blockWorldStateUpdater.updater();

    return !isTooLate;
  }

  @Override
  public void rollback() {
    selectedTxPendingActions.clear();
    selectorsStateManager.rollback();
    txWorldStateUpdater = blockWorldStateUpdater.updater();
  }

  private TransactionEvaluationContext createTransactionEvaluationContext(
      final org.hyperledger.besu.datatypes.PendingTransaction pendingTransaction) {
    final Wei transactionGasPriceInBlock =
        blockSelectionContext
            .feeMarket()
            .getTransactionPriceCalculator()
            .price(
                (Transaction) pendingTransaction.getTransaction(),
                blockSelectionContext.pendingBlockHeader().getBaseFee());

    return new TransactionEvaluationContext(
        blockSelectionContext.pendingBlockHeader(),
        pendingTransaction,
        Stopwatch.createStarted(),
        transactionGasPriceInBlock,
        blockSelectionContext.miningConfiguration().getMinTransactionGasPrice(),
        () -> isTimeout.get() || isCancelled.get());
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
          selector.evaluateTransactionPreProcessing(evaluationContext);
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
          selector.evaluateTransactionPostProcessing(evaluationContext, processingResult);
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
   * @param transaction The transaction to be processed.
   * @return The result of the transaction processing.
   */
  private TransactionProcessingResult processTransaction(final Transaction transaction) {
    final BlockHashLookup blockHashLookup =
        blockSelectionContext
            .preExecutionProcessor()
            .createBlockHashLookup(blockchain, blockSelectionContext.pendingBlockHeader());
    final Optional<AccessLocationTracker> transactionLocationTracker =
        maybeBlockAccessListBuilder.map(
            b ->
                BlockAccessListBuilder.createTransactionAccessLocationTracker(
                    currentTxnLocation.get()));
    final TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            txWorldStateUpdater,
            blockSelectionContext.pendingBlockHeader(),
            transaction,
            blockSelectionContext.miningBeneficiary(),
            operationTracer,
            blockHashLookup,
            TransactionValidationParams.mining(),
            blockSelectionContext.blobGasPrice(),
            transactionLocationTracker);
    return result;
  }

  /**
   * Handles a selected transaction by committing the world state updates, creating a transaction
   * receipt, updating the TransactionSelectionResults with the selected transaction, and notifying
   * the external transaction selector.
   *
   * @param evaluationContext The current selection session data.
   * @param processingResult The result of the transaction processing.
   * @return The result of the transaction selection process.
   */
  private TransactionSelectionResult handleTransactionSelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionProcessingResult processingResult) {
    final Transaction transaction = evaluationContext.getTransaction();

    final long gasUsedByTransaction =
        transaction.getGasLimit() - processingResult.getGasRemaining();

    // queue the creation of the receipt and the update of the final results
    // these actions will be performed on commit if the pending tx is definitely selected
    selectedTxPendingActions.add(
        () -> {
          final long cumulativeGasUsed =
              transactionSelectionResults.getCumulativeGasUsed() + gasUsedByTransaction;

          final TransactionReceipt receipt =
              transactionReceiptFactory.create(
                  transaction.getType(), processingResult, cumulativeGasUsed);

          maybeBlockAccessListBuilder.ifPresent(
              blockAccessListBuilder ->
                  processingResult
                      .getPartialBlockAccessView()
                      .ifPresent(blockAccessListBuilder::apply));

          transactionSelectionResults.updateSelected(transaction, receipt, gasUsedByTransaction);

          notifySelected(evaluationContext, processingResult);
          LOG.atTrace()
              .setMessage("Selected and commited {} with location {} for block creation")
              .addArgument(transaction::toTraceLog)
              .addArgument(currentTxnLocation.get())
              .log();
          currentTxnLocation.incrementAndGet();
        });

    if (isTimeout.get()) {
      // even if this tx passed all the checks, it is too late to include it in this block,
      // so we need to treat it as not selected

      // do not rely on the presence of this result, since by the time it is added, the code
      // reading it could have been already executed by another thread
      return handleTransactionNotSelected(evaluationContext, BLOCK_SELECTION_TIMEOUT);
    }

    LOG.atTrace()
        .setMessage(
            "Potentially selected {} with location {} for block creation, evaluated in {}, waiting for commit")
        .addArgument(transaction::toTraceLog)
        .addArgument(currentTxnLocation.get())
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
    notifyNotSelected(evaluationContext, actualResult);

    LOG.atTrace()
        .setMessage("Not selected {} for block creation with result {}{}, evaluated in {}")
        .addArgument(pendingTransaction::toTraceLog)
        .addArgument(actualResult)
        .addArgument(
            () ->
                selectionResult.equals(actualResult)
                    ? ""
                    : " (original result " + selectionResult + ")")
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

    return selectionResult.discard()
        ? invalidTxSelectionTimeoutResult
        : validTxSelectionTimeoutResult;
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
    if (evaluationTimer.elapsed(TimeUnit.NANOSECONDS) > blockTxsSelectionMaxTimeNanos) {
      LOG.atWarn()
          .setMessage(
              "Transaction {} is too late for inclusion, with result {}, evaluated in {} that is over the max limit of {}ms"
                  + ", {}")
          .addArgument(evaluationContext.getTransaction()::getHash)
          .addArgument(selectionResult)
          .addArgument(evaluationTimer)
          .addArgument(() -> nanosToMillis(blockTxsSelectionMaxTimeNanos))
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

  private void notifySelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionProcessingResult processingResult) {

    for (var selector : transactionSelectors) {
      selector.onTransactionSelected(evaluationContext, processingResult);
    }
    pluginTransactionSelector.onTransactionSelected(evaluationContext, processingResult);
  }

  private void notifyNotSelected(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResult selectionResult) {

    for (var selector : transactionSelectors) {
      selector.onTransactionNotSelected(evaluationContext, selectionResult);
    }
    pluginTransactionSelector.onTransactionNotSelected(evaluationContext, selectionResult);
  }

  private long nanosToMillis(final long nanos) {
    return TimeUnit.NANOSECONDS.toMillis(nanos);
  }
}

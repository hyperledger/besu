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
package org.hyperledger.besu.ethereum.blockcreation.evaluation;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
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
  private static final TransactionSelector ALWAYS_SELECT =
      (_1, _2, _3, _4) -> TransactionSelectionResult.SELECTED;
  private final Supplier<Boolean> isCancelled;
  private final MainnetTransactionProcessor transactionProcessor;
  private final Blockchain blockchain;
  private final MutableWorldState worldState;
  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;
  private final BlockSelectionContext blockSelectionContext;

  @SuppressWarnings("UnusedVariable")
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
            transactionPool, transactionSelectionResults);
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
        .addArgument(blockSelectionContext.transactionPool().logStats())
        .log();
    blockSelectionContext.transactionPool().selectTransactions(
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

    TransactionEvaluatorsFactory.TransactionSelectionSelectorsList evaluators =
        TransactionEvaluatorsFactory.createEvaluators(blockSelectionContext);

    TransactionSelectionResult preProcessingSelectionResult =
        evaluateTransaction(transaction, evaluators.getTransactionSelectors());

    if (!preProcessingSelectionResult.selected()) {
      return preProcessingSelectionResult;
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

    if (effectiveResult.isInvalid()) {
      return transactionSelectionResultForInvalidResult(
          transaction, effectiveResult.getValidationResult());
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

  private TransactionSelectionResult evaluateTransaction(
      final Transaction transaction, final List<TransactionSelector> selectors) {
    for (TransactionSelector selector : selectors) {
      TransactionSelectionResult result =
          selector.evaluate(transaction);
      if (!result.equals(TransactionSelectionResult.SELECTED)) {
        return result;
      }
    }
    return TransactionSelectionResult.SELECTED;
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
}

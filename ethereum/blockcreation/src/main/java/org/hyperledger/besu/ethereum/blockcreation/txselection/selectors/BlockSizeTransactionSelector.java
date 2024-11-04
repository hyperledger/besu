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
package org.hyperledger.besu.ethereum.blockcreation.txselection.selectors;

import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionEvaluationContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends AbstractTransactionSelector and provides a specific implementation for
 * evaluating transactions based on block size. It checks if a transaction is too large for the
 * block and determines the selection result accordingly.
 */
public class BlockSizeTransactionSelector extends AbstractTransactionSelector {
  private static final Logger LOG = LoggerFactory.getLogger(BlockSizeTransactionSelector.class);

  public BlockSizeTransactionSelector(final BlockSelectionContext context) {
    super(context);
  }

  /**
   * Evaluates a transaction considering other transactions in the same block. If the transaction is
   * too large for the block returns a selection result based on block occupancy.
   *
   * @param evaluationContext The current selection session data.
   * @param transactionSelectionResults The results of other transaction evaluations in the same
   *     block.
   * @return The result of the transaction selection.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResults transactionSelectionResults) {

    if (transactionTooLargeForBlock(
        evaluationContext.getTransaction(), transactionSelectionResults)) {
      LOG.atTrace()
          .setMessage("Transaction {} too large to select for block creation")
          .addArgument(evaluationContext.getPendingTransaction()::toTraceLog)
          .log();
      if (blockOccupancyAboveThreshold(transactionSelectionResults)) {
        LOG.trace("Block occupancy above threshold, completing operation");
        return TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD;
      } else if (blockFull(transactionSelectionResults)) {
        LOG.trace("Block full, completing operation");
        return TransactionSelectionResult.BLOCK_FULL;
      } else {
        return TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS;
      }
    }
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResults blockTransactionResults,
      final TransactionProcessingResult processingResult) {
    // All necessary checks were done in the pre-processing method, so nothing to do here.
    return TransactionSelectionResult.SELECTED;
  }

  /**
   * Checks if the transaction is too large for the block.
   *
   * @param transaction The transaction to be checked.
   * @param transactionSelectionResults The results of other transaction evaluations in the same
   *     block.
   * @return True if the transaction is too large for the block, false otherwise.
   */
  private boolean transactionTooLargeForBlock(
      final Transaction transaction,
      final TransactionSelectionResults transactionSelectionResults) {

    return transaction.getGasLimit()
        > context.pendingBlockHeader().getGasLimit()
            - transactionSelectionResults.getCumulativeGasUsed();
  }

  /**
   * Checks if the block occupancy is above the threshold.
   *
   * @param transactionSelectionResults The results of other transaction evaluations in the same
   *     block.
   * @return True if the block occupancy is above the threshold, false otherwise.
   */
  private boolean blockOccupancyAboveThreshold(
      final TransactionSelectionResults transactionSelectionResults) {
    final long gasAvailable = context.pendingBlockHeader().getGasLimit();

    final long gasUsed = transactionSelectionResults.getCumulativeGasUsed();
    final long gasRemaining = gasAvailable - gasUsed;
    final double occupancyRatio = (double) gasUsed / (double) gasAvailable;

    LOG.trace(
        "Min block occupancy ratio {}, gas used {}, available {}, remaining {}, used/available {}",
        context.miningConfiguration().getMinBlockOccupancyRatio(),
        gasUsed,
        gasAvailable,
        gasRemaining,
        occupancyRatio);

    return occupancyRatio >= context.miningConfiguration().getMinBlockOccupancyRatio();
  }

  /**
   * Checks if the block is full.
   *
   * @param transactionSelectionResults The results of other transaction evaluations in the same
   *     block.
   * @return True if the block is full, false otherwise.
   */
  private boolean blockFull(final TransactionSelectionResults transactionSelectionResults) {
    final long gasAvailable = context.pendingBlockHeader().getGasLimit();
    final long gasUsed = transactionSelectionResults.getCumulativeGasUsed();

    final long gasRemaining = gasAvailable - gasUsed;

    if (gasRemaining < context.gasCalculator().getMinimumTransactionCost()) {
      LOG.trace(
          "Block full, remaining gas {} is less than minimum transaction gas cost {}",
          gasRemaining,
          context.gasCalculator().getMinimumTransactionCost());
      return true;
    }
    return false;
  }
}

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
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends AbstractTransactionSelector and provides a specific implementation for
 * evaluating transactions based on block size. It checks if a transaction is too large for the
 * block and determines the selection result accordingly.
 */
public class BlockSizeTransactionSelector
    extends AbstractStatefulTransactionSelector<
        BlockSizeTransactionSelector.BlockSizeWorkingState> {
  private static final Logger LOG = LoggerFactory.getLogger(BlockSizeTransactionSelector.class);
  public static final long MAX_HEADER_SIZE = 1024L;

  private final long blockGasLimit;

  public BlockSizeTransactionSelector(
      final BlockSelectionContext context, final SelectorsStateManager selectorsStateManager) {
    super(
        context,
        selectorsStateManager,
        new BlockSizeWorkingState(0L, MAX_HEADER_SIZE),
        BlockSizeWorkingState::duplicate);
    this.blockGasLimit = context.pendingBlockHeader().getGasLimit();
  }

  /**
   * Evaluates a transaction considering other transactions in the same block. If the transaction is
   * too large for the block returns a selection result based on block occupancy.
   *
   * @param evaluationContext The current selection session data.
   * @return The result of the transaction selection.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext evaluationContext) {

    final BlockSizeWorkingState workingState = getWorkingState();
    final long cumulativeGasUsed = workingState.cumulativeGasUsed;
    final long cumulativeBlockSize = workingState.cumulativeBlockSize;

    if (transactionTooLargeForBlock(evaluationContext.getTransaction(), cumulativeGasUsed)) {
      LOG.atTrace()
          .setMessage("Transaction {} too large to select for block creation")
          .addArgument(evaluationContext.getPendingTransaction()::toTraceLog)
          .log();
      if (blockOccupancyAboveThreshold(cumulativeGasUsed)) {
        LOG.trace("Block occupancy above threshold, completing operation");
        return TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD;
      } else if (blockFull(cumulativeGasUsed)) {
        LOG.trace("Block full, completing operation");
        return TransactionSelectionResult.BLOCK_FULL;
      } else if (blockSizeAboveThreshold(evaluationContext.getTransaction(), cumulativeBlockSize)) {
        LOG.trace("Block size exceeds block limit, completing operation");
        return TransactionSelectionResult.BLOCK_SIZE_ABOVE_THRESHOLD;
      } else {
        return TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS;
      }
    }
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionProcessingResult processingResult) {
    final long gasUsedByTransaction =
        evaluationContext.getTransaction().getGasLimit() - processingResult.getGasRemaining();
    final BlockSizeWorkingState currentWorkingState = getWorkingState();
    final BlockSizeWorkingState updatedWorkingState =
        new BlockSizeWorkingState(
            currentWorkingState.cumulativeGasUsed + gasUsedByTransaction,
            currentWorkingState.cumulativeBlockSize()
                + evaluationContext.getTransaction().getSize());
    setWorkingState(updatedWorkingState);

    return TransactionSelectionResult.SELECTED;
  }

  /**
   * Checks if the transaction is too large for the block.
   *
   * @param transaction The transaction to be checked. block.
   * @param cumulativeGasUsed The cumulative gas used by previous txs.
   * @return True if the transaction is too large for the block, false otherwise.
   */
  private boolean transactionTooLargeForBlock(
      final Transaction transaction, final long cumulativeGasUsed) {

    return transaction.getGasLimit() > blockGasLimit - cumulativeGasUsed;
  }

  /**
   * Checks if the block occupancy is above the threshold.
   *
   * @param cumulativeGasUsed The cumulative gas used by previous txs.
   * @return True if the block occupancy is above the threshold, false otherwise.
   */
  private boolean blockOccupancyAboveThreshold(final long cumulativeGasUsed) {
    final long gasRemaining = blockGasLimit - cumulativeGasUsed;
    final double occupancyRatio = (double) cumulativeGasUsed / (double) blockGasLimit;

    LOG.trace(
        "Min block occupancy ratio {}, gas used {}, available {}, remaining {}, used/available {}",
        context.miningConfiguration().getMinBlockOccupancyRatio(),
        cumulativeGasUsed,
        blockGasLimit,
        gasRemaining,
        occupancyRatio);

    return occupancyRatio >= context.miningConfiguration().getMinBlockOccupancyRatio();
  }

  /**
   * Checks if the block is full.
   *
   * @param cumulativeGasUsed The cumulative gas used by previous txs.
   * @return True if the block is full, false otherwise.
   */
  private boolean blockFull(final long cumulativeGasUsed) {
    final long gasRemaining = blockGasLimit - cumulativeGasUsed;

    if (gasRemaining < context.gasCalculator().getMinimumTransactionCost()) {
      LOG.trace(
          "Block full, remaining gas {} is less than minimum transaction gas cost {}",
          gasRemaining,
          context.gasCalculator().getMinimumTransactionCost());
      return true;
    }
    return false;
  }

  /**
   * Checks if the block max size has been reached
   *
   * @param transaction The transaction to be checked.
   * @param cumulativeBlockSize The cumulative block size used by previous txs.
   * @return True if the transaction would go over the block size limit, false otherwise.
   */
  private boolean blockSizeAboveThreshold(
      final Transaction transaction, final long cumulativeBlockSize) {
    final long blockSizeRemaining = context.gasCalculator().maxRlpBlockSize() - cumulativeBlockSize;

    if (blockSizeRemaining < transaction.getSize()) {
      LOG.trace(
          "Block full, transaction bytes gas {} is less than minimum transaction gas cost {}",
          blockSizeRemaining,
          context.gasCalculator().getMinimumTransactionCost());
      return true;
    }
    return false;
  }

  public record BlockSizeWorkingState(long cumulativeGasUsed, long cumulativeBlockSize) {

    static BlockSizeWorkingState duplicate(final BlockSizeWorkingState state) {
      return new BlockSizeWorkingState(state.cumulativeGasUsed, state.cumulativeBlockSize);
    }
  }
}

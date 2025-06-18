/*
 * Copyright contributors to Besu.
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
 * A transaction selector that evaluates transactions based on the RLP size of the block ensure that
 * it does not exceed the maximum RLP block size in EIP-7934
 */
public class BlockRlpSizeTransactionSelector extends AbstractStatefulTransactionSelector<Long> {
  public static final long MAX_HEADER_SIZE = 1024L;
  private static final Logger LOG = LoggerFactory.getLogger(BlockRlpSizeTransactionSelector.class);

  public BlockRlpSizeTransactionSelector(
      final BlockSelectionContext context, final SelectorsStateManager selectorsStateManager) {
    super(
        context,
        selectorsStateManager,
        MAX_HEADER_SIZE,
        SelectorsStateManager.StateDuplicator::duplicateLong);
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext evaluationContext) {
    final long cumulativeBlockSize = getWorkingState();
    if (blockSizeAboveThreshold(evaluationContext.getTransaction(), cumulativeBlockSize)) {
      LOG.trace("Block RLP size above threshold, completing operation");
      return TransactionSelectionResult.TOO_LARGE_FOR_REMAINING_BLOCK_SIZE;
    }
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionProcessingResult processingResult) {
    setWorkingState(getWorkingState() + evaluationContext.getTransaction().getSize());

    return TransactionSelectionResult.SELECTED;
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
    final long blockSizeRemaining = context.maxRlpBlockSize() - cumulativeBlockSize;

    if (blockSizeRemaining < transaction.getSize()) {
      LOG.trace(
          "Transaction with size of {} bytes too large for block limit of {} bytes",
          blockSizeRemaining,
          context.maxRlpBlockSize());
      return true;
    }
    return false;
  }
}

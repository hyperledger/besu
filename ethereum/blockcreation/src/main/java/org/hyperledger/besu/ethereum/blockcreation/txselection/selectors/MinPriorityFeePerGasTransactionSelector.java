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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionEvaluationContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

/** This class is responsible for selecting transactions based on the minimum priority fee. */
public class MinPriorityFeePerGasTransactionSelector extends AbstractTransactionSelector {

  /**
   * Constructor for MinPriorityFeeSelector.
   *
   * @param context The context of block selection.
   */
  public MinPriorityFeePerGasTransactionSelector(final BlockSelectionContext context) {
    super(context);
  }

  /**
   * Evaluates a transaction before processing.
   *
   * @param evaluationContext The current selection session data.
   * @param transactionSelectionResults The results of other transaction evaluations in the same
   *     block.
   * @return TransactionSelectionResult. If the priority fee is below the minimum, it returns an
   *     invalid transient result. Otherwise, it returns a selected result.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResults transactionSelectionResults) {
    if (isPriorityFeePriceBelowMinimum(evaluationContext.getPendingTransaction())) {
      return TransactionSelectionResult.PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN;
    }
    return TransactionSelectionResult.SELECTED;
  }

  /**
   * Checks if the priority fee price is below the minimum.
   *
   * @param pendingTransaction The transaction to check.
   * @return boolean. Returns true if the minimum priority fee price is below the minimum, false
   *     otherwise.
   */
  private boolean isPriorityFeePriceBelowMinimum(final PendingTransaction pendingTransaction) {
    // Priority txs are exempt from this check
    if (pendingTransaction.hasPriority()) {
      return false;
    }
    Wei priorityFeePerGas =
        pendingTransaction
            .getTransaction()
            .getEffectivePriorityFeePerGas(context.pendingBlockHeader().getBaseFee());
    return priorityFeePerGas.lessThan(context.miningConfiguration().getMinPriorityFeePerGas());
  }

  /**
   * No evaluation is performed post-processing.
   *
   * @param evaluationContext The current selection session data.
   * @param processingResult The result of the transaction processing.
   * @return Always returns SELECTED.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResults blockTransactionResults,
      final TransactionProcessingResult processingResult) {
    return TransactionSelectionResult.SELECTED;
  }
}

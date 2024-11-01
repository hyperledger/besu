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
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends AbstractTransactionSelector and provides a specific implementation for
 * evaluating transactions based on transaction price. It checks if a transaction's current price is
 * below the minimum and determines the selection result accordingly.
 */
public class PriceTransactionSelector extends AbstractTransactionSelector {
  private static final Logger LOG = LoggerFactory.getLogger(PriceTransactionSelector.class);

  public PriceTransactionSelector(final BlockSelectionContext context) {
    super(context);
  }

  /**
   * Evaluates a transaction considering its price. If the transaction's current price is below the
   * minimum, it returns a selection result indicating the reason.
   *
   * @param evaluationContext The current selection session data.
   * @param ignored The results of other transaction evaluations in the same block.
   * @return The result of the transaction selection.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResults ignored) {
    if (transactionCurrentPriceBelowMin(evaluationContext)) {
      return TransactionSelectionResult.CURRENT_TX_PRICE_BELOW_MIN;
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
   * Checks if the transaction's current price is below the minimum.
   *
   * @param evaluationContext The current selection session data.
   * @return True if the transaction's current price is below the minimum, false otherwise.
   */
  private boolean transactionCurrentPriceBelowMin(
      final TransactionEvaluationContext evaluationContext) {
    final PendingTransaction pendingTransaction = evaluationContext.getPendingTransaction();
    // Priority txs are exempt from this check
    if (!pendingTransaction.hasPriority()) {

      if (context
              .miningConfiguration()
              .getMinTransactionGasPrice()
              .compareTo(evaluationContext.getTransactionGasPrice())
          > 0) {
        LOG.atTrace()
            .setMessage(
                "Current gas price of {} is {} and lower than the configured minimum {}, skipping")
            .addArgument(pendingTransaction::toTraceLog)
            .addArgument(evaluationContext.getTransactionGasPrice()::toHumanReadableString)
            .addArgument(
                context.miningConfiguration().getMinTransactionGasPrice()::toHumanReadableString)
            .log();
        return true;
      }
    }
    return false;
  }
}

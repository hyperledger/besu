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
package org.hyperledger.besu.ethereum.blockcreation.txselection.selectors;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Transaction;
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
   * @param pendingTransaction The transaction to be evaluated.
   * @param ignored The results of other transaction evaluations in the same block.
   * @return The result of the transaction selection.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final PendingTransaction pendingTransaction, final TransactionSelectionResults ignored) {
    if (transactionCurrentPriceBelowMin(pendingTransaction)) {
      return TransactionSelectionResult.CURRENT_TX_PRICE_BELOW_MIN;
    }
    return TransactionSelectionResult.SELECTED;
  }

  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final PendingTransaction pendingTransaction,
      final TransactionSelectionResults blockTransactionResults,
      final TransactionProcessingResult processingResult) {
    // All necessary checks were done in the pre-processing method, so nothing to do here.
    return TransactionSelectionResult.SELECTED;
  }

  /**
   * Checks if the transaction's current price is below the minimum.
   *
   * @param pendingTransaction The transaction to be checked.
   * @return True if the transaction's current price is below the minimum, false otherwise.
   */
  private boolean transactionCurrentPriceBelowMin(final PendingTransaction pendingTransaction) {
    final Transaction transaction = pendingTransaction.getTransaction();
    // Here we only care about EIP1159 since for Frontier and local transactions the checks
    // that we do when accepting them in the pool are enough
    if (transaction.getType().supports1559FeeMarket() && !pendingTransaction.hasPriority()) {

      // For EIP1559 transactions, the price is dynamic and depends on network conditions, so we can
      // only calculate at this time the current minimum price the transaction is willing to pay
      // and if it is above the minimum accepted by the node.
      // If below we do not delete the transaction, since when we added the transaction to the pool,
      // we assured sure that the maxFeePerGas is >= of the minimum price accepted by the node
      // and so the price of the transaction could satisfy this rule in the future
      final Wei currentMinTransactionGasPriceInBlock =
          context
              .feeMarket()
              .getTransactionPriceCalculator()
              .price(transaction, context.processableBlockHeader().getBaseFee());
      if (context
              .miningParameters()
              .getMinTransactionGasPrice()
              .compareTo(currentMinTransactionGasPriceInBlock)
          > 0) {
        LOG.trace(
            "Current gas fee of {} is lower than configured minimum {}, skipping",
            pendingTransaction,
            context.miningParameters().getMinTransactionGasPrice());
        return true;
      }
    }
    return false;
  }
}

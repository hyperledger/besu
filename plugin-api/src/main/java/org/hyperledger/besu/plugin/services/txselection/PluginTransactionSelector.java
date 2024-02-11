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

package org.hyperledger.besu.plugin.services.txselection;

import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

/** Interface for the transaction selector */
@Unstable
public interface PluginTransactionSelector {

  /**
   * Method that returns an OperationTracer that will be used when executing transactions that are
   * candidates to be added to a block.
   *
   * @return OperationTracer to be used to trace candidate transactions
   */
  default BlockAwareOperationTracer getOperationTracer() {
    return BlockAwareOperationTracer.NO_TRACING;
  }

  /**
   * Method called to decide whether a transaction is added to a block. The result can also indicate
   * that no further transactions can be added to the block.
   *
   * @param evaluationContext The current selection context
   * @return TransactionSelectionResult that indicates whether to include the transaction
   */
  TransactionSelectionResult evaluateTransactionPreProcessing(
      TransactionEvaluationContext<? extends PendingTransaction> evaluationContext);

  /**
   * Method called to decide whether a processed transaction is added to a block. The result can
   * also indicate that no further transactions can be added to the block.
   *
   * @param evaluationContext The current selection context
   * @param processingResult the transaction processing result
   * @return TransactionSelectionResult that indicates whether to include the transaction
   */
  TransactionSelectionResult evaluateTransactionPostProcessing(
      TransactionEvaluationContext<? extends PendingTransaction> evaluationContext,
      TransactionProcessingResult processingResult);

  /**
   * Method called when a transaction is selected to be added to a block.
   *
   * @param evaluationContext The current selection context
   * @param processingResult The result of processing the selected transaction.
   */
  default void onTransactionSelected(
      final TransactionEvaluationContext<? extends PendingTransaction> evaluationContext,
      final TransactionProcessingResult processingResult) {}
  /**
   * Method called when a transaction is not selected to be added to a block.
   *
   * @param evaluationContext The current selection context
   * @param transactionSelectionResult The transaction selection result
   */
  default void onTransactionNotSelected(
      final TransactionEvaluationContext<? extends PendingTransaction> evaluationContext,
      final TransactionSelectionResult transactionSelectionResult) {}
}

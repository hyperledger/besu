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
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

/**
 * This class represents an abstract transaction selector which provides methods to evaluate
 * transactions.
 */
public abstract class AbstractTransactionSelector {
  final BlockSelectionContext context;

  public AbstractTransactionSelector(final BlockSelectionContext context) {
    this.context = context;
  }

  /**
   * Evaluates a transaction in the context of other transactions in the same block.
   *
   * @param evaluationContext The current selection session data.
   * @param blockTransactionResults The results of other transaction evaluations in the same block.
   * @return The result of the transaction evaluation
   */
  public abstract TransactionSelectionResult evaluateTransactionPreProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResults blockTransactionResults);

  /**
   * Evaluates a transaction considering other transactions in the same block and a transaction
   * processing result.
   *
   * @param evaluationContext The current selection session data.
   * @param blockTransactionResults The results of other transaction evaluations in the same block.
   * @param processingResult The result of transaction processing.
   * @return The result of the transaction evaluation
   */
  public abstract TransactionSelectionResult evaluateTransactionPostProcessing(
      final TransactionEvaluationContext evaluationContext,
      final TransactionSelectionResults blockTransactionResults,
      final TransactionProcessingResult processingResult);
}

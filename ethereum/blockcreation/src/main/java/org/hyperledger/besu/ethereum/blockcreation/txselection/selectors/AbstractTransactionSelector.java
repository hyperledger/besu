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

import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Transaction;
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
   * @param transaction The transaction to be evaluated within a block.
   * @param blockTransactionResults The results of other transaction evaluations in the same block.
   * @return The result of the transaction evaluation
   */
  public abstract TransactionSelectionResult selectTransactionPreProcessing(
      final Transaction transaction, final TransactionSelectionResults blockTransactionResults);

  /**
   * Evaluates a transaction considering other transactions in the same block and a transaction
   * processing result.
   *
   * @param transaction The transaction to be evaluated.
   * @param blockTransactionResults The results of other transaction evaluations in the same block.
   * @param processingResult The result of transaction processing.
   * @return The result of the transaction evaluation
   */
  public abstract TransactionSelectionResult selectTransactionPostProcessing(
      final Transaction transaction,
      final TransactionSelectionResults blockTransactionResults,
      final TransactionProcessingResult processingResult);
}

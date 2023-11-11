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

import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;

/** A TransactionSelector that unconditionally selects all transactions. */
public class AllAcceptingTransactionSelector implements PluginTransactionSelector {
  public static final AllAcceptingTransactionSelector INSTANCE =
      new AllAcceptingTransactionSelector();

  private AllAcceptingTransactionSelector() {}

  /**
   * Always selects the transaction in the pre-processing stage.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @return Always SELECTED.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPreProcessing(
      final PendingTransaction pendingTransaction) {
    return TransactionSelectionResult.SELECTED;
  }

  /**
   * Always selects the transaction in the post-processing stage.
   *
   * @param pendingTransaction The transaction to be evaluated.
   * @param processingResult The result of the transaction processing.
   * @return Always SELECTED.
   */
  @Override
  public TransactionSelectionResult evaluateTransactionPostProcessing(
      final PendingTransaction pendingTransaction,
      final TransactionProcessingResult processingResult) {
    return TransactionSelectionResult.SELECTED;
  }
}

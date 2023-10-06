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
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.data.TransactionProcessingResult;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

/** Interface for the transaction selector */
@Unstable
public interface TransactionSelector {
  /**
   * Method called to decide whether a transaction is added to a block. The result can also indicate
   * that no further transactions can be added to the block.
   *
   * @param pendingTransaction candidate transaction
   * @return TransactionSelectionResult that indicates whether to include the transaction
   */
  TransactionSelectionResult evaluateTransactionPreProcessing(
      PendingTransaction pendingTransaction);

  /**
   * Method called to decide whether a processed transaction is added to a block. The result can
   * also indicate that no further transactions can be added to the block.
   *
   * @param pendingTransaction candidate transaction
   * @param processingResult the transaction processing result
   * @return TransactionSelectionResult that indicates whether to include the transaction
   */
  TransactionSelectionResult evaluateTransactionPostProcessing(
      PendingTransaction pendingTransaction, TransactionProcessingResult processingResult);

  /**
   * Method to get the tracer for the transaction selection for the current block
   *
   * @return the tracer
   */
  default OperationTracer getTracer() {
    return OperationTracer.NO_TRACING;
  }
}

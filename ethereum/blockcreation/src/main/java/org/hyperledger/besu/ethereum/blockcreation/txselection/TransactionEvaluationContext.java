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
package org.hyperledger.besu.ethereum.blockcreation.txselection;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;

import com.google.common.base.Stopwatch;

public class TransactionEvaluationContext
    implements org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext<
        PendingTransaction> {
  private final ProcessableBlockHeader pendingBlockHeader;
  private final PendingTransaction pendingTransaction;
  private final Stopwatch evaluationTimer;
  private final Wei transactionGasPrice;
  private final Wei minGasPrice;

  public TransactionEvaluationContext(
      final ProcessableBlockHeader pendingBlockHeader,
      final PendingTransaction pendingTransaction,
      final Stopwatch evaluationTimer,
      final Wei transactionGasPrice,
      final Wei minGasPrice) {
    this.pendingBlockHeader = pendingBlockHeader;
    this.pendingTransaction = pendingTransaction;
    this.evaluationTimer = evaluationTimer;
    this.transactionGasPrice = transactionGasPrice;
    this.minGasPrice = minGasPrice;
  }

  public Transaction getTransaction() {
    return pendingTransaction.getTransaction();
  }

  @Override
  public ProcessableBlockHeader getPendingBlockHeader() {
    return pendingBlockHeader;
  }

  @Override
  public PendingTransaction getPendingTransaction() {
    return pendingTransaction;
  }

  @Override
  public Stopwatch getEvaluationTimer() {
    return evaluationTimer;
  }

  @Override
  public Wei getTransactionGasPrice() {
    return transactionGasPrice;
  }

  @Override
  public Wei getMinGasPrice() {
    return minGasPrice;
  }
}

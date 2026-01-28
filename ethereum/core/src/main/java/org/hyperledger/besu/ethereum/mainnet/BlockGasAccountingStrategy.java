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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

/**
 * Strategy interface for calculating gas to add to a block's cumulative gas used. This allows
 * different hard forks to use different gas accounting methods.
 *
 * <p>Prior to EIP-7778: Block gas is calculated POST-refund (gasLimit - gasRemaining), which
 * includes the benefit of gas refunds from SSTORE operations.
 *
 * <p>EIP-7778 (Amsterdam+): Block gas is calculated PRE-refund (estimateGasUsedByTransaction),
 * preventing block gas limit circumvention through refund credits.
 */
@FunctionalInterface
public interface BlockGasAccountingStrategy {

  /**
   * Calculate the gas to add to the block's cumulative gas used for a transaction.
   *
   * @param transaction the transaction being processed
   * @param result the transaction processing result
   * @return the gas amount to add to the block's cumulative gas used
   */
  long calculateBlockGas(Transaction transaction, TransactionProcessingResult result);

  /**
   * Frontier through BPO5: Uses post-refund gas (gasLimit - gasRemaining). This is the traditional
   * Ethereum behavior where refunds reduce the effective gas used for block limit purposes.
   */
  BlockGasAccountingStrategy FRONTIER = (tx, result) -> tx.getGasLimit() - result.getGasRemaining();

  /**
   * EIP-7778 (Amsterdam+): Uses pre-refund gas (estimateGasUsedByTransaction). This prevents block
   * gas limit circumvention by not crediting refunds back to the block's gas budget.
   */
  BlockGasAccountingStrategy EIP7778 = (tx, result) -> result.getEstimateGasUsedByTransaction();
}

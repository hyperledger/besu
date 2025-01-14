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
package org.hyperledger.besu.evm.refundcalculator;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** Refund calculator for the Prague hard fork. */
public class PragueRefundCalculator extends FrontierRefundCalculator implements RefundCalculator {

  /** Instantiates a new Prague refund calculator. */
  public PragueRefundCalculator() {
    // empty
  }

  @Override
  public long calculateGasRefund(
      final GasCalculator gasCalculator,
      final Transaction transaction,
      final MessageFrame initialFrame,
      final long codeDelegationRefund) {
    final long refundAllowance =
        calculateRefundAllowance(gasCalculator, transaction, initialFrame, codeDelegationRefund);

    final long executionGasUsed =
        transaction.getGasLimit() - initialFrame.getRemainingGas() - refundAllowance;
    final long transactionFloorCost = gasCalculator.transactionFloorCost(transaction.getPayload());
    final long totalGasUsed = Math.max(executionGasUsed, transactionFloorCost);
    return transaction.getGasLimit() - totalGasUsed;
  }
}

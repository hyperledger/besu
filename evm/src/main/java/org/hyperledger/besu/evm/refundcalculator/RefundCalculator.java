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

/** Refund calculator, which calculates how much gas is refunded at the end of a transaction */
public interface RefundCalculator {
  /**
   * Calculate the gas refund for a transaction.
   *
   * @param gasCalculator the gas calculator
   * @param transaction the transaction
   * @param initialFrame the initial frame
   * @param codeDelegationRefund the code delegation refund
   * @return the gas refund
   */
  long calculateGasRefund(
      GasCalculator gasCalculator,
      Transaction transaction,
      MessageFrame initialFrame,
      long codeDelegationRefund);
}

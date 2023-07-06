/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.evm.operation;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The Jump operation. */
public class JumpOperation extends AbstractFixedCostOperation {

  private static final Operation.OperationResult invalidJumpResponse =
      new Operation.OperationResult(8L, ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
  private static final OperationResult jumpResponse = new OperationResult(8L, null, 0);

  /**
   * Instantiates a new Jump operation.
   *
   * @param gasCalculator the gas calculator
   */
  public JumpOperation(final GasCalculator gasCalculator) {
    super(0x56, "JUMP", 2, 0, gasCalculator, gasCalculator.getMidTierGasCost());
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    return staticOperation(frame);
  }

  /**
   * Performs Jump operation.
   *
   * @param frame the frame
   * @return the operation result
   */
  public static OperationResult staticOperation(final MessageFrame frame) {
    final int jumpDestination;
    final Bytes bytes = frame.popStackItem().trimLeadingZeros();
    try {
      jumpDestination = bytes.toInt();
    } catch (final RuntimeException iae) {
      return invalidJumpResponse;
    }
    final Code code = frame.getCode();
    if (code.isJumpDestInvalid(jumpDestination)) {
      return invalidJumpResponse;
    } else {
      frame.setPC(jumpDestination);
      return jumpResponse;
    }
  }
}

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

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;

public class JumpOperation extends AbstractFixedCostOperation {

  private final Operation.OperationResult invalidJumpResponse;
  private final OperationResult jumpResponse;

  public JumpOperation(final GasCalculator gasCalculator) {
    super(0x56, "JUMP", 2, 0, 1, gasCalculator, gasCalculator.getMidTierGasCost());
    invalidJumpResponse =
        new Operation.OperationResult(
            OptionalLong.of(gasCost), Optional.of(ExceptionalHaltReason.INVALID_JUMP_DESTINATION));
    jumpResponse = new OperationResult(OptionalLong.of(gasCost), Optional.empty(), 0);
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
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

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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class JumpiOperation extends AbstractFixedCostOperation {

  private final OperationResult invalidJumpResponse;

  public JumpiOperation(final GasCalculator gasCalculator) {
    super(0x57, "JUMPI", 2, 0, true, 1, gasCalculator, gasCalculator.getHighTierGasCost());
    invalidJumpResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INVALID_JUMP_DESTINATION));
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    final UInt256 jumpDestination = UInt256.fromBytes(frame.popStackItem());
    final Bytes32 condition = frame.popStackItem();

    // If condition is zero (false), no jump is will be performed. Therefore skip the test.
    if (condition.isZero()) {
      frame.setPC(frame.getPC() + 1);
    } else {
      final Code code = frame.getCode();
      if (!code.isValidJumpDestination(evm, frame, jumpDestination)) {
        return invalidJumpResponse;
      }
      frame.setPC(jumpDestination.intValue());
    }

    return successResponse;
  }
}

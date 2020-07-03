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

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class JumpiOperation extends AbstractOperation {

  private final OperationResult successResponse;
  private final OperationResult oogResponse;
  private final OperationResult underflowResponse;
  private final OperationResult invalidJumpResponse;
  private final Gas gasCost;

  public JumpiOperation(final GasCalculator gasCalculator) {
    super(0x57, "JUMPI", 2, 0, true, 1, gasCalculator);
    gasCost = gasCalculator().getHighTierGasCost();
    successResponse = new OperationResult(Optional.of(gasCost), Optional.empty());
    oogResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    invalidJumpResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INVALID_JUMP_DESTINATION));
    underflowResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getHighTierGasCost();
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    if (frame.stackSize() < 2) {
      return underflowResponse;
    }
    if (frame.getRemainingGas().compareTo(gasCost) < 0) {
      return oogResponse;
    }

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

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame, final EVM evm) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void execute(final MessageFrame frame) {
    throw new UnsupportedOperationException();
  }
}

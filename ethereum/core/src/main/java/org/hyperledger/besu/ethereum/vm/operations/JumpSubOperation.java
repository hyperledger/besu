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

import java.util.EnumSet;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

public class JumpSubOperation extends AbstractOperation {

  public JumpSubOperation(final GasCalculator gasCalculator) {
    super(0xb3, "JUMPSUB", 1, 0, true, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getMidTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 location = UInt256.fromBytes(frame.popStackItem());
    frame.pushReturnStackItem(frame.getPC() + 1);
    frame.setPC(location.intValue());
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    final Code code = frame.getCode();

    if (frame.isReturnStackFull()) {
      return Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
    } else if (frame.stackSize() <= 0) {
      return Optional.of(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
    } else {
      final UInt256 potentialJumpSubDestination = UInt256.fromBytes(frame.getStackItem(0));
      if (!code.isValidJumpSubDestination(evm, frame, potentialJumpSubDestination)) {
        return Optional.of(ExceptionalHaltReason.INVALID_JUMP_DESTINATION);
      }
    }
    return Optional.empty();
  }
}

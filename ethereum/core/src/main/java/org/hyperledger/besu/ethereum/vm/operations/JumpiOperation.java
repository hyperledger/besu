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

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class JumpiOperation extends AbstractOperation {

  public JumpiOperation(final GasCalculator gasCalculator) {
    super(0x57, "JUMPI", 2, 0, true, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getHighTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 jumpDestination = UInt256.fromBytes(frame.popStackItem());
    final Bytes32 condition = frame.popStackItem();

    if (!condition.isZero()) {
      frame.setPC(jumpDestination.intValue());
    } else {
      frame.setPC(frame.getPC() + getOpSize());
    }
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    // If condition is zero (false), no jump is will be performed. Therefore skip the test.
    if (frame.getStackItem(1).isZero()) {
      return Optional.empty();
    }

    final Code code = frame.getCode();
    final UInt256 potentialJumpDestination = UInt256.fromBytes(frame.getStackItem(0));
    return !code.isValidJumpDestination(evm, frame, potentialJumpDestination)
        ? Optional.of(ExceptionalHaltReason.INVALID_JUMP_DESTINATION)
        : Optional.empty();
  }
}

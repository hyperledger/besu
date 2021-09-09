/*
 * Copyright contributors to Hyperledger Besu
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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class RevertOperation extends AbstractOperation {

  public RevertOperation(final GasCalculator gasCalculator) {
    super(0xFD, "REVERT", 2, 0, false, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final UInt256 from = UInt256.fromBytes(frame.popStackItem());
    final UInt256 length = UInt256.fromBytes(frame.popStackItem());

    final Gas cost = gasCalculator().memoryExpansionGasCost(frame, from, length);
    final Optional<Gas> optionalCost = Optional.of(cost);
    if (frame.getRemainingGas().compareTo(cost) < 0) {
      return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    final Bytes reason = frame.readMemory(from, length);
    frame.setOutputData(reason);
    frame.setRevertReason(reason);
    frame.setState(MessageFrame.State.REVERT);
    return new OperationResult(optionalCost, Optional.empty());
  }
}

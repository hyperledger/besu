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

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.units.bigints.UInt256;

public class ExpOperation extends AbstractOperation {

  public ExpOperation(final GasCalculator gasCalculator) {
    super(0x0A, "EXP", 2, 1, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final UInt256 number = UInt256.fromBytes(frame.popStackItem());
    final UInt256 power = UInt256.fromBytes(frame.popStackItem());

    final int numBytes = (power.bitLength() + 7) / 8;

    final long cost = gasCalculator().expOperationGasCost(numBytes);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(
          OptionalLong.of(cost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    final UInt256 result = number.pow(power);

    frame.pushStackItem(result);
    return new OperationResult(OptionalLong.of(cost), Optional.empty());
  }
}

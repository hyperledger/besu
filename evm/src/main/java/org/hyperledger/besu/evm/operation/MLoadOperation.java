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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;

public class MLoadOperation extends AbstractOperation {

  public MLoadOperation(final GasCalculator gasCalculator) {
    super(0x51, "MLOAD", 1, 1, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final long location = clampedToLong(frame.popStackItem());

    final long cost = gasCalculator().mLoadOperationGasCost(frame, location);
    if (frame.getRemainingGas() < cost) {
      return new OperationResult(
          OptionalLong.of(cost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    final Bytes value = frame.readMutableMemory(location, 32, true).copy();

    frame.pushStackItem(value);
    return new OperationResult(OptionalLong.of(cost), Optional.empty());
  }
}

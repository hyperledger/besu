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
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class MStore8Operation extends AbstractOperation {

  public MStore8Operation(final GasCalculator gasCalculator) {
    super(0x53, "MSTORE8", 2, 0, false, 1, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final UInt256 location = frame.popStackItem();
    final Bytes32 value = frame.popStackItem();

    final Gas cost = gasCalculator().mStore8OperationGasCost(frame, location);
    final Optional<Gas> optionalCost = Optional.of(cost);
    if (frame.getRemainingGas().compareTo(cost) < 0) {
      return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    frame.writeMemory(location, value.get(Bytes32.SIZE - 1), true);
    return new OperationResult(optionalCost, Optional.empty());
  }
}

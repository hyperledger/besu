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

import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;

import java.util.Optional;

public class ReturnSubOperation extends AbstractFixedCostOperation {

  private final OperationResult invalidRetsubResponse;

  public ReturnSubOperation(final GasCalculator gasCalculator) {
    super(0x5d, "RETURNSUB", 0, 0, true, 1, gasCalculator, gasCalculator.getLowTierGasCost());
    invalidRetsubResponse =
        new OperationResult(
            Optional.of(gasCalculator.getLowTierGasCost()),
            Optional.of(ExceptionalHaltReason.INVALID_RETSUB));
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    if (frame.isReturnStackEmpty()) {
      return invalidRetsubResponse;
    }

    frame.setPC(frame.popReturnStackItem());

    return successResponse;
  }
}

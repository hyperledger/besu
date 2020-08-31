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

public class BeginSubOperation extends AbstractFixedCostOperation {

  public static final int OPCODE = 0x5c;
  private final OperationResult invalidEntryResponse;

  public BeginSubOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "BEGINSUB", 0, 0, false, 1, gasCalculator, gasCalculator.getBaseTierGasCost());
    invalidEntryResponse =
        new OperationResult(
            Optional.of(gasCalculator.getBaseTierGasCost()),
            Optional.of(ExceptionalHaltReason.INVALID_SUB_ROUTINE_ENTRY));
  }

  @Override
  public OperationResult executeFixedCostOperation(final MessageFrame frame, final EVM evm) {
    return invalidEntryResponse;
  }
}

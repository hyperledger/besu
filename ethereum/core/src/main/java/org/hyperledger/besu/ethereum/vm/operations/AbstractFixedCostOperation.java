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
 *
 */

package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.GasCalculator;

import java.util.Optional;

abstract class AbstractFixedCostOperation extends AbstractOperation {

  protected final OperationResult successResponse;
  protected final OperationResult outOfGasResponse;
  protected final Gas gasCost;

  protected AbstractFixedCostOperation(
      final int opcode,
      final String name,
      final int stackItemsConsumed,
      final int stackItemsProduced,
      final boolean updatesProgramCounter,
      final int opSize,
      final GasCalculator gasCalculator,
      final Gas fixedCost) {
    super(
        opcode,
        name,
        stackItemsConsumed,
        stackItemsProduced,
        updatesProgramCounter,
        opSize,
        gasCalculator);
    gasCost = fixedCost;
    successResponse = new OperationResult(Optional.of(gasCost), Optional.empty());
    outOfGasResponse =
        new OperationResult(
            Optional.of(gasCost), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
  }
}

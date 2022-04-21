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

public class InvalidOperation extends AbstractOperation {

  protected final OperationResult invalidOperation;

  public InvalidOperation(final GasCalculator gasCalculator) {
    this(0xFE, gasCalculator);
  }

  public InvalidOperation(final int opcode, final GasCalculator gasCalculator) {
    super(opcode, "INVALID", -1, -1, 1, gasCalculator);
    invalidOperation =
        new OperationResult(
            OptionalLong.of(0L), Optional.of(ExceptionalHaltReason.INVALID_OPERATION));
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    return invalidOperation;
  }
}

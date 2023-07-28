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
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/** The type Ret F operation. */
public class RetFOperation extends AbstractOperation {

  /** The Opcode. */
  public static final int OPCODE = 0xe4;
  /** The Ret F success. */
  static final OperationResult retfSuccess = new OperationResult(4, null);

  /**
   * Instantiates a new Ret F operation.
   *
   * @param gasCalculator the gas calculator
   */
  public RetFOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "RETF", 0, 0, gasCalculator);
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    var exception = frame.returnFunction();
    if (exception == null) {
      return retfSuccess;
    } else {
      return new OperationResult(retfSuccess.gasCost, exception);
    }
  }
}

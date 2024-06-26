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
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.bytes.Bytes;

/** The Push operation. */
public class PushOperation extends AbstractOperation {

  /** The constant PUSH_BASE. */
  public static final int PUSH_BASE = 0x5F;

  /** The constant PUSH_MAX. */
  public static final int PUSH_MAX = 0x7F;

  private final int length;

  /**
   * Instantiates a new Push operation.
   *
   * @param length the length
   * @param gasCalculator the gas calculator
   */
  public PushOperation(final int length, final GasCalculator gasCalculator) {
    super(PUSH_BASE + length, "PUSH" + length, 0, 1, gasCalculator);
    this.length = length;
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final byte[] code = frame.getCode().getBytes().toArrayUnsafe();
    int pc = frame.getPC();

    int copyStart = pc + 1;

    long gasCost = gasCalculator().pushOperationGasCost(frame, copyStart, length, code.length);
    Bytes push;
    if (code.length <= copyStart) {
      push = Bytes.EMPTY;
    } else {
      final int copyLength = Math.min(length, code.length - pc - 1);
      push = Bytes.wrap(code, copyStart, copyLength);
    }
    frame.pushStackItem(push);
    frame.setPC(pc + length);
    return new OperationResult(gasCost, null);
  }
}

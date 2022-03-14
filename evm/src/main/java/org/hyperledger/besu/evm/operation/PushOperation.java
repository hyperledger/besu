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

import static java.lang.Math.min;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;

public class PushOperation extends AbstractFixedCostOperation {

  public static final int PUSH_BASE = 0x5F;

  private final int length;

  private final OperationResult pushResponse;

  public PushOperation(final int length, final GasCalculator gasCalculator) {
    super(
        PUSH_BASE + length,
        "PUSH" + length,
        0,
        1,
        length + 1,
        gasCalculator,
        gasCalculator.getVeryLowTierGasCost());
    this.length = length;
    pushResponse = new OperationResult(OptionalLong.of(gasCost), Optional.empty(), length + 1);
  }

  @Override
  public Operation.OperationResult executeFixedCostOperation(
      final MessageFrame frame, final EVM evm) {
    final int pc = frame.getPC();
    final Bytes code = frame.getCode().getBytes();

    final int copyLength = min(length, code.size() - pc - 1);
    frame.pushStackItem(code.slice(pc + 1, copyLength));

    return pushResponse;
  }
}

/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

public class Sha3Operation extends AbstractOperation {

  public Sha3Operation(final GasCalculator gasCalculator) {
    super(0x20, "SHA3", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = frame.getStackItem(0).asUInt256();
    final UInt256 length = frame.getStackItem(1).asUInt256();

    return gasCalculator().sha3OperationGasCost(frame, offset, length);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 from = frame.popStackItem().asUInt256();
    final UInt256 length = frame.popStackItem().asUInt256();

    final BytesValue bytes = frame.readMemory(from, length);
    frame.pushStackItem(Hash.hash(bytes));
  }
}

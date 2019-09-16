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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.vm.AbstractOperation;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.uint.Counter;
import org.hyperledger.besu.util.uint.UInt256;
import org.hyperledger.besu.util.uint.UInt256Value;

public class ByteOperation extends AbstractOperation {

  public ByteOperation(final GasCalculator gasCalculator) {
    super(0x1A, "BYTE", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  private UInt256 getByte(final UInt256 seq, final UInt256 offset) {
    if (!offset.fitsInt()) {
      return UInt256.ZERO;
    }

    final int index = offset.toInt();
    if (index >= 32) {
      return UInt256.ZERO;
    }

    final byte b = seq.getBytes().get(index);
    final Counter<UInt256> res = UInt256.newCounter();
    res.getBytes().set(UInt256Value.SIZE - 1, b);
    return res.get();
  }

  @Override
  public void execute(final MessageFrame frame) {

    final UInt256 value0 = frame.popStackItem().asUInt256();
    final UInt256 value1 = frame.popStackItem().asUInt256();

    // Stack items are reversed for the BYTE operation.
    final UInt256 result = getByte(value1, value0);

    frame.pushStackItem(result.getBytes());
  }
}

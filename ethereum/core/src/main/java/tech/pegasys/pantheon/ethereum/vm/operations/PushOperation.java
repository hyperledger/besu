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

import static java.lang.Math.min;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.MutableBytes32;

public class PushOperation extends AbstractOperation {

  private final int length;

  public PushOperation(final int length, final GasCalculator gasCalculator) {
    super(0x60 + length - 1, "PUSH" + length, 0, 1, false, length + 1, gasCalculator);
    this.length = length;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final int pc = frame.getPC();
    final BytesValue code = frame.getCode().getBytes();

    final int copyLength = min(length, code.size() - pc - 1);
    final MutableBytes32 bytes = MutableBytes32.create();
    code.slice(pc + 1, copyLength).copyTo(bytes, bytes.size() - length);
    frame.pushStackItem(bytes);
  }
}

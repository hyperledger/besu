/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm.v2.operation;

import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;

/**
 * Shows the v2 stack to tracers. Tracers read the stack through the frame's v1 stack accessors, so
 * when tracing, the v2 interpreter copies its stack into the v1 stack before each tracer call.
 */
public final class TracerStackV2 {

  private TracerStackV2() {}

  /**
   * Makes the frame's v1 stack hold the same items as its v2 stack. Only the items above the
   * deepest one that differs are replaced, as an operation changes only the top of the stack.
   *
   * @param frame the frame the v2 interpreter is running
   */
  public static void copyToV1Stack(final MessageFrame frame) {
    final long[] stack = frame.stackDataV2();
    final int top = frame.stackTopV2();
    final int v1Size = frame.stackSize();
    final int limit = Math.min(v1Size, top);
    int same = 0;
    while (same < limit && isSameWord(frame.getStackItem(v1Size - 1 - same), stack, same)) {
      same++;
    }
    if (v1Size > same) {
      frame.popStackItems(v1Size - same);
    }
    for (int index = same; index < top; index++) {
      frame.pushStackItem(StackUtil.readBytes32At(stack, top, top - 1 - index));
    }
  }

  private static boolean isSameWord(final Bytes item, final long[] stack, final int index) {
    final int offset = index << 2;
    return item.size() == 32
        && item.getLong(0) == stack[offset]
        && item.getLong(8) == stack[offset + 1]
        && item.getLong(16) == stack[offset + 2]
        && item.getLong(24) == stack[offset + 3];
  }
}

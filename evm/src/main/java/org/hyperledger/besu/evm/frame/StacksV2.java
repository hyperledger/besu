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
package org.hyperledger.besu.evm.frame;

import java.util.Deque;

/**
 * The EVM v2 stacks of a thread, one per call depth. A full stack is 32 KB, too large for the
 * thread's allocation buffer, so allocating one per frame contends on the heap lock with every
 * other allocating thread.
 */
final class StacksV2 {

  // Depths 0 to 20 hold 99.99% of mainnet frames. Deeper frames allocate, so that a deep call
  // chain does not leave megabytes of stacks on the thread.
  static final int CACHED_DEPTHS = 21;

  private static final ThreadLocal<StacksV2> STACKS = ThreadLocal.withInitial(StacksV2::new);

  private final long[][] stacks = new long[CACHED_DEPTHS][];
  // The frame stack of the frame that last took each stack
  private final Deque<?>[] owners = new Deque<?>[CACHED_DEPTHS];

  private StacksV2() {}

  /**
   * Returns a stack for the frame about to be pushed onto the frame stack. Its contents are
   * undefined.
   *
   * @param frameStack the frame stack the frame is pushed onto
   * @param length the length of the stack in longs
   * @return the stack
   */
  static long[] take(final Deque<MessageFrame> frameStack, final int length) {
    final int depth = frameStack.size();
    if (depth >= CACHED_DEPTHS) {
      return new long[length];
    }
    return STACKS.get().takeAt(frameStack, depth, length);
  }

  private long[] takeAt(final Deque<MessageFrame> frameStack, final int depth, final int length) {
    long[] stack = stacks[depth];
    // The last frame at this depth has finished once its frame stack is no deeper than this depth.
    // An unfinished one belongs to another EVM run on this thread and keeps its stack.
    if (stack == null || stack.length != length || owners[depth].size() > depth) {
      stack = new long[length];
      stacks[depth] = stack;
    }
    owners[depth] = frameStack;
    return stack;
  }
}

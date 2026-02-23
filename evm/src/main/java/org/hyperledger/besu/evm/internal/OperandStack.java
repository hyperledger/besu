/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.internal;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.evm.UInt256;

import java.util.Arrays;

/**
 * EVM operand stack backed by a {@code UInt256[]} array.
 *
 * <p>All access methods ({@link #popUnsafe()}, {@link #pushUnsafe}, {@link #peekUnsafe}, {@link
 * #overwriteUnsafe}, {@link #shrinkUnsafe}, {@link #getUnsafe}) skip bounds checks for maximum
 * performance. Callers must pre-validate via {@link #hasItems} / {@link #hasSpace}.
 *
 * <p>Instances are pooled per-thread via {@link #borrow} / {@link #release} to avoid allocating the
 * backing array on every CALL. The pool tracks peak usage with an exponential moving average and
 * periodically shrinks to reclaim memory after usage spikes subside.
 */
public class OperandStack {

  private static final int DEFAULT_MAX_SIZE = 1024;

  /**
   * Thread-local pool with EMA-based shrinking. Replaces a simple {@code Deque<OperandStack>} to
   * bound memory: after a deep CALL chain spike, the pool shrinks back within ~32 maintenance
   * cycles rather than holding stacks forever.
   */
  static final class StackPool {
    private static final int INITIAL_CAPACITY = 16;
    private static final int MAINTENANCE_INTERVAL = 256;

    OperandStack[] stacks;
    int size; // available stacks in array
    int capacity;
    int outstanding; // currently borrowed (borrows - releases)
    int peakThisCycle; // max(outstanding) since last maintenance
    int peakEmaX16; // EMA of peak, fixed-point <<4
    int idleCount; // times outstanding hit 0 since last maintenance

    StackPool() {
      capacity = INITIAL_CAPACITY;
      stacks = new OperandStack[INITIAL_CAPACITY];
      for (int i = 0; i < INITIAL_CAPACITY; i++) {
        stacks[i] = new OperandStack(DEFAULT_MAX_SIZE);
      }
      size = INITIAL_CAPACITY;
    }

    OperandStack borrow() {
      outstanding++;
      if (outstanding > peakThisCycle) {
        peakThisCycle = outstanding;
      }
      if (size > 0) {
        return stacks[--size];
      }
      return new OperandStack(DEFAULT_MAX_SIZE);
    }

    void release(final OperandStack s) {
      s.reset();
      outstanding--;
      if (size < capacity) {
        stacks[size++] = s;
      }
      // else: pool full, discard stack (GC reclaims)

      if (outstanding == 0) {
        if (++idleCount >= MAINTENANCE_INTERVAL) {
          maintain();
        }
      }
    }

    void maintain() {
      // Update EMA: alpha = 1/4 → peakEma = 3/4 * old + 1/4 * new
      peakEmaX16 = (peakEmaX16 * 3 + (peakThisCycle << 4) + 2) >> 2;
      peakThisCycle = 0;
      idleCount = 0;

      int smoothedPeak = (peakEmaX16 + 8) >> 4;
      int target = nextPowerOf2(Math.max(smoothedPeak * 2, INITIAL_CAPACITY));

      if (target != capacity) {
        OperandStack[] newArr = new OperandStack[target];
        int keep = Math.min(size, target);
        System.arraycopy(stacks, 0, newArr, 0, keep);
        stacks = newArr;
        size = keep;
        capacity = target;
      }
    }
  }

  private static int nextPowerOf2(final int n) {
    if (n <= 1) {
      return 1;
    }
    return Integer.highestOneBit(n - 1) << 1;
  }

  private static final ThreadLocal<StackPool> POOL = ThreadLocal.withInitial(StackPool::new);

  /**
   * Borrows an OperandStack from the thread-local pool, or creates a new one if the pool is empty.
   *
   * @param maxSize the max stack size
   * @return a reset OperandStack ready for use
   */
  public static OperandStack borrow(final int maxSize) {
    if (maxSize == DEFAULT_MAX_SIZE) {
      return POOL.get().borrow();
    }
    return new OperandStack(maxSize);
  }

  /**
   * Returns an OperandStack to the thread-local pool for reuse.
   *
   * @param stack the stack to return
   */
  public static void release(final OperandStack stack) {
    if (stack.maxSize == DEFAULT_MAX_SIZE) {
      POOL.get().release(stack);
    }
  }

  private final UInt256[] entries;
  private final int maxSize;
  private int top = -1;

  /**
   * Instantiates a new Operand stack.
   *
   * @param maxSize the max size (number of UInt256 entries)
   */
  public OperandStack(final int maxSize) {
    checkArgument(maxSize > 0, "max size must be positive");
    this.maxSize = maxSize;
    this.entries = new UInt256[maxSize];
  }

  // ---------------------------------------------------------------------------
  // Validation helpers (called once before opcode execution)
  // ---------------------------------------------------------------------------

  /**
   * Returns true if the stack has at least {@code n} items.
   *
   * @param n the number of items required
   * @return true if the stack contains at least n items
   */
  public boolean hasItems(final int n) {
    return top + 1 >= n;
  }

  /**
   * Returns true if the stack has space for {@code n} more items.
   *
   * @param n the number of additional items
   * @return true if the stack can accommodate n more items
   */
  public boolean hasSpace(final int n) {
    return top + 1 + n <= maxSize;
  }

  // ---------------------------------------------------------------------------
  // Stack access methods (no bounds checks — caller must pre-validate)
  // ---------------------------------------------------------------------------

  /**
   * Get operand at offset from top without bounds check.
   *
   * @param offset the offset (0 = top)
   * @return the operand
   */
  public UInt256 getUnsafe(final int offset) {
    return entries[top - offset];
  }

  /**
   * Pop without bounds check.
   *
   * @return the operand
   */
  public UInt256 popUnsafe() {
    return entries[top--];
  }

  /**
   * Push without bounds check.
   *
   * @param operand the operand
   */
  public void pushUnsafe(final UInt256 operand) {
    entries[++top] = operand;
  }

  /**
   * Peek at depth from top without bounds check.
   *
   * @param depth the depth (0 = top)
   * @return the operand
   */
  public UInt256 peekUnsafe(final int depth) {
    return entries[top - depth];
  }

  /**
   * Overwrite entry at depth from top without bounds check.
   *
   * @param depth the depth (0 = top)
   * @param operand the operand
   */
  public void overwriteUnsafe(final int depth, final UInt256 operand) {
    entries[top - depth] = operand;
  }

  /**
   * Shrink the stack by n entries without bounds check.
   *
   * @param n the number of entries to remove
   */
  public void shrinkUnsafe(final int n) {
    top -= n;
  }

  // ---------------------------------------------------------------------------
  // Query methods
  // ---------------------------------------------------------------------------

  /**
   * Resets the stack to empty state for reuse. Nulls out references so the GC can reclaim UInt256
   * objects from the previous use.
   */
  public void reset() {
    if (top >= 0) {
      Arrays.fill(entries, 0, top + 1, null);
    }
    top = -1;
  }

  /**
   * Size of entries.
   *
   * @return the size
   */
  public int size() {
    return top + 1;
  }

  /**
   * Is stack full.
   *
   * @return the boolean
   */
  public boolean isFull() {
    return top + 1 >= maxSize;
  }

  /**
   * Is stack empty.
   *
   * @return the boolean
   */
  public boolean isEmpty() {
    return top < 0;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i <= top; ++i) {
      builder.append(String.format("%n0x%04X ", i)).append(entries[i]);
    }
    return builder.toString();
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int i = 0; i <= top; i++) {
      result = 31 * result + entries[i].hashCode();
    }
    return result;
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof OperandStack that)) {
      return false;
    }
    if (this.top != that.top) {
      return false;
    }
    for (int i = 0; i <= top; i++) {
      if (!this.entries[i].equals(that.entries[i])) {
        return false;
      }
    }
    return true;
  }
}

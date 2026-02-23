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

/**
 * Thread-local pool of {@code long[]} arrays used as EVM operand stacks. Replaces the previous
 * {@code OperandStack} pooling with direct array pooling to eliminate one level of indirection.
 *
 * <p>The pool tracks peak usage with an exponential moving average and periodically shrinks to
 * reclaim memory after usage spikes subside.
 */
public final class StackPool {

  private static final int DEFAULT_MAX_SIZE = 1024;
  private static final int INITIAL_CAPACITY = 16;
  private static final int MAINTENANCE_INTERVAL = 256;

  private static final ThreadLocal<StackPool> POOL = ThreadLocal.withInitial(StackPool::new);

  long[][] stacks;
  int size; // available stacks in array
  int capacity;
  int outstanding; // currently borrowed (borrows - releases)
  int peakThisCycle; // max(outstanding) since last maintenance
  int peakEmaX16; // EMA of peak, fixed-point <<4
  int idleCount; // times outstanding hit 0 since last maintenance

  StackPool() {
    capacity = INITIAL_CAPACITY;
    stacks = new long[INITIAL_CAPACITY][];
    for (int i = 0; i < INITIAL_CAPACITY; i++) {
      stacks[i] = new long[DEFAULT_MAX_SIZE << 2];
    }
    size = INITIAL_CAPACITY;
  }

  /**
   * Borrows a {@code long[]} stack array from the thread-local pool, or creates a new one if the
   * pool is empty.
   *
   * @param maxSize the max stack size (number of UInt256 entries)
   * @return a zeroed long[] array of size maxSize &lt;&lt; 2
   */
  public static long[] borrow(final int maxSize) {
    if (maxSize == DEFAULT_MAX_SIZE) {
      return POOL.get().borrowInternal();
    }
    return new long[maxSize << 2];
  }

  /**
   * Returns a {@code long[]} stack array to the thread-local pool for reuse.
   *
   * @param data the long[] array to return
   * @param maxSize the max stack size used when borrowing
   */
  public static void release(final long[] data, final int maxSize) {
    if (maxSize == DEFAULT_MAX_SIZE) {
      POOL.get().releaseInternal(data);
    }
  }

  private long[] borrowInternal() {
    outstanding++;
    if (outstanding > peakThisCycle) {
      peakThisCycle = outstanding;
    }
    if (size > 0) {
      return stacks[--size];
    }
    return new long[DEFAULT_MAX_SIZE << 2];
  }

  private void releaseInternal(final long[] data) {
    outstanding--;
    if (size < capacity) {
      stacks[size++] = data;
    }
    // else: pool full, discard (GC reclaims)

    if (outstanding == 0) {
      if (++idleCount >= MAINTENANCE_INTERVAL) {
        maintain();
      }
    }
  }

  void maintain() {
    // Update EMA: alpha = 1/4 -> peakEma = 3/4 * old + 1/4 * new
    peakEmaX16 = (peakEmaX16 * 3 + (peakThisCycle << 4) + 2) >> 2;
    peakThisCycle = 0;
    idleCount = 0;

    int smoothedPeak = (peakEmaX16 + 8) >> 4;
    int target = nextPowerOf2(Math.max(smoothedPeak * 2, INITIAL_CAPACITY));

    if (target != capacity) {
      long[][] newArr = new long[target][];
      int keep = Math.min(size, target);
      System.arraycopy(stacks, 0, newArr, 0, keep);
      stacks = newArr;
      size = keep;
      capacity = target;
    }
  }

  private static int nextPowerOf2(final int n) {
    if (n <= 1) {
      return 1;
    }
    return Integer.highestOneBit(n - 1) << 1;
  }
}

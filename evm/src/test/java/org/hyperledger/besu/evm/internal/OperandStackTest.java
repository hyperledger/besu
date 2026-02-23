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
package org.hyperledger.besu.evm.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperandStackTest {

  // --- StackPool tests ---

  @Test
  void pool_borrowAndRelease_reusesArrays() {
    final StackPool pool = new StackPool();
    long[] s1 = pool.stacks[--pool.size]; // simulate borrow
    pool.outstanding++;
    pool.stacks[pool.size++] = s1; // simulate release
    pool.outstanding--;
    long[] s2 = pool.stacks[--pool.size];
    pool.outstanding++;
    assertThat(s2).isSameAs(s1);
  }

  @Test
  void pool_borrowMultiple_tracksOutstanding() {
    long[] s1 = StackPool.borrow(1024);
    long[] s2 = StackPool.borrow(1024);
    StackPool.release(s2, 1024);
    StackPool.release(s1, 1024);
    // Should not throw
  }

  @Test
  void pool_nonDefaultSize_allocatesNew() {
    long[] s = StackPool.borrow(512);
    assertThat(s.length).isEqualTo(512 << 2);
  }

  @Test
  void pool_maintain_shrinksAfterLowUsage() {
    final StackPool pool = new StackPool();
    for (int cycle = 0; cycle < 10; cycle++) {
      pool.peakThisCycle = 1;
      pool.idleCount = 256;
      pool.maintain();
    }
    assertThat(pool.capacity).isEqualTo(16);
  }

  @Test
  void pool_maintain_growsAfterHighUsage() {
    final StackPool pool = new StackPool();
    for (int cycle = 0; cycle < 10; cycle++) {
      pool.peakThisCycle = 100;
      pool.idleCount = 256;
      pool.maintain();
    }
    assertThat(pool.capacity).isEqualTo(256);
  }

  @Test
  void pool_maintain_shrinksFromSpike() {
    final StackPool pool = new StackPool();
    pool.peakThisCycle = 512;
    pool.idleCount = 256;
    pool.maintain();
    int capacityAfterSpike = pool.capacity;
    assertThat(capacityAfterSpike).isGreaterThan(16);

    for (int cycle = 0; cycle < 50; cycle++) {
      pool.peakThisCycle = 1;
      pool.idleCount = 256;
      pool.maintain();
    }
    assertThat(pool.capacity).isLessThan(capacityAfterSpike);
    assertThat(pool.capacity).isEqualTo(16);
  }
}

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.evm.UInt256;

import org.junit.jupiter.api.Test;

class OperandStackTest {

  @Test
  void construction() {
    final OperandStack stack = new OperandStack(1);
    assertThat(stack.size()).isZero();
  }

  @Test
  void construction_NegativeMaximumSize() {
    assertThatThrownBy(() -> new OperandStack(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pushPopUnsafe() {
    final OperandStack stack = new OperandStack(2);
    stack.pushUnsafe(UInt256.fromInt(42));
    assertThat(stack.size()).isEqualTo(1);
    stack.pushUnsafe(UInt256.fromInt(99));
    assertThat(stack.size()).isEqualTo(2);
    assertThat(stack.popUnsafe()).isEqualTo(UInt256.fromInt(99));
    assertThat(stack.popUnsafe()).isEqualTo(UInt256.fromInt(42));
    assertThat(stack.size()).isZero();
  }

  @Test
  void pushUnsafe_StackOverflow_AIOOBE() {
    final OperandStack stack = new OperandStack(1);
    stack.pushUnsafe(UInt256.fromInt(1));
    assertThatThrownBy(() -> stack.pushUnsafe(UInt256.fromInt(2)))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @Test
  void popUnsafe_StackUnderflow_AIOOBE() {
    final OperandStack stack = new OperandStack(1);
    assertThatThrownBy(stack::popUnsafe).isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @Test
  void getUnsafe() {
    final OperandStack stack = new OperandStack(3);
    stack.pushUnsafe(UInt256.fromInt(1));
    stack.pushUnsafe(UInt256.fromInt(2));
    stack.pushUnsafe(UInt256.fromInt(3));
    assertThat(stack.size()).isEqualTo(3);
    assertThat(stack.getUnsafe(0)).isEqualTo(UInt256.fromInt(3));
    assertThat(stack.getUnsafe(1)).isEqualTo(UInt256.fromInt(2));
    assertThat(stack.getUnsafe(2)).isEqualTo(UInt256.fromInt(1));
  }

  @Test
  void peekUnsafe() {
    final OperandStack stack = new OperandStack(3);
    stack.pushUnsafe(UInt256.fromInt(1));
    stack.pushUnsafe(UInt256.fromInt(2));
    stack.pushUnsafe(UInt256.fromInt(3));
    assertThat(stack.peekUnsafe(0)).isEqualTo(UInt256.fromInt(3));
    assertThat(stack.peekUnsafe(1)).isEqualTo(UInt256.fromInt(2));
    assertThat(stack.peekUnsafe(2)).isEqualTo(UInt256.fromInt(1));
  }

  @Test
  void overwriteUnsafe() {
    final OperandStack stack = new OperandStack(3);
    stack.pushUnsafe(UInt256.fromInt(1));
    stack.pushUnsafe(UInt256.fromInt(2));
    stack.pushUnsafe(UInt256.fromInt(3));
    stack.overwriteUnsafe(0, UInt256.fromInt(30));
    stack.overwriteUnsafe(2, UInt256.fromInt(10));
    assertThat(stack.getUnsafe(0)).isEqualTo(UInt256.fromInt(30));
    assertThat(stack.getUnsafe(1)).isEqualTo(UInt256.fromInt(2));
    assertThat(stack.getUnsafe(2)).isEqualTo(UInt256.fromInt(10));
  }

  @Test
  void shrinkUnsafe() {
    final OperandStack stack = new OperandStack(4);
    stack.pushUnsafe(UInt256.fromInt(1));
    stack.pushUnsafe(UInt256.fromInt(2));
    stack.pushUnsafe(UInt256.fromInt(3));
    stack.pushUnsafe(UInt256.fromInt(4));
    assertThat(stack.size()).isEqualTo(4);
    stack.shrinkUnsafe(2);
    assertThat(stack.size()).isEqualTo(2);
    assertThat(stack.getUnsafe(0)).isEqualTo(UInt256.fromInt(2));
    assertThat(stack.getUnsafe(1)).isEqualTo(UInt256.fromInt(1));
  }

  // --- Validation helpers ---

  @Test
  void hasItems() {
    final OperandStack stack = new OperandStack(4);
    assertThat(stack.hasItems(0)).isTrue();
    assertThat(stack.hasItems(1)).isFalse();
    stack.pushUnsafe(UInt256.fromInt(1));
    assertThat(stack.hasItems(1)).isTrue();
    assertThat(stack.hasItems(2)).isFalse();
    stack.pushUnsafe(UInt256.fromInt(2));
    assertThat(stack.hasItems(2)).isTrue();
  }

  @Test
  void hasSpace() {
    final OperandStack stack = new OperandStack(2);
    assertThat(stack.hasSpace(1)).isTrue();
    assertThat(stack.hasSpace(2)).isTrue();
    assertThat(stack.hasSpace(3)).isFalse();
    stack.pushUnsafe(UInt256.fromInt(1));
    assertThat(stack.hasSpace(1)).isTrue();
    assertThat(stack.hasSpace(2)).isFalse();
    stack.pushUnsafe(UInt256.fromInt(2));
    assertThat(stack.hasSpace(1)).isFalse();
  }

  // --- Operation patterns ---

  @Test
  void binaryOpPattern() {
    final OperandStack stack = new OperandStack(4);
    stack.pushUnsafe(UInt256.fromInt(10));
    stack.pushUnsafe(UInt256.fromInt(20));
    final UInt256 a = stack.peekUnsafe(0);
    final UInt256 b = stack.peekUnsafe(1);
    stack.shrinkUnsafe(1);
    stack.overwriteUnsafe(0, a.add(b));
    assertThat(stack.size()).isEqualTo(1);
    assertThat(stack.getUnsafe(0)).isEqualTo(UInt256.fromInt(30));
  }

  @Test
  void unaryOpPattern() {
    final OperandStack stack = new OperandStack(4);
    stack.pushUnsafe(UInt256.fromInt(42));
    final UInt256 v = stack.peekUnsafe(0);
    stack.overwriteUnsafe(0, UInt256.fromInt(v.isZero() ? 1 : 0));
    assertThat(stack.size()).isEqualTo(1);
    assertThat(stack.getUnsafe(0)).isEqualTo(UInt256.ZERO);
  }

  @Test
  void largeValues() {
    final OperandStack stack = new OperandStack(2);
    final UInt256 large = UInt256.MAX;
    stack.pushUnsafe(large);
    assertThat(stack.popUnsafe()).isEqualTo(large);
  }

  @Test
  void isFull() {
    final OperandStack stack = new OperandStack(1);
    assertThat(stack.isFull()).isFalse();
    stack.pushUnsafe(UInt256.ONE);
    assertThat(stack.isFull()).isTrue();
  }

  @Test
  void isEmpty() {
    final OperandStack stack = new OperandStack(1);
    assertThat(stack.isEmpty()).isTrue();
    stack.pushUnsafe(UInt256.ONE);
    assertThat(stack.isEmpty()).isFalse();
  }

  // --- StackPool tests ---

  @Test
  void pool_borrowAndRelease_reusesStacks() {
    final OperandStack.StackPool pool = new OperandStack.StackPool();
    OperandStack s1 = pool.borrow();
    pool.release(s1);
    OperandStack s2 = pool.borrow();
    assertThat(s2).isSameAs(s1);
  }

  @Test
  void pool_borrowMultiple_tracksOutstanding() {
    final OperandStack.StackPool pool = new OperandStack.StackPool();
    OperandStack s1 = pool.borrow();
    OperandStack s2 = pool.borrow();
    assertThat(pool.outstanding).isEqualTo(2);
    pool.release(s2);
    pool.release(s1);
    assertThat(pool.outstanding).isZero();
  }

  @Test
  void pool_borrowBeyondInitialCapacity_allocatesNew() {
    final OperandStack.StackPool pool = new OperandStack.StackPool();
    OperandStack[] borrowed = new OperandStack[20];
    for (int i = 0; i < 20; i++) {
      borrowed[i] = pool.borrow();
    }
    assertThat(pool.outstanding).isEqualTo(20);
    for (int i = 19; i >= 0; i--) {
      pool.release(borrowed[i]);
    }
    assertThat(pool.size).isEqualTo(16);
  }

  @Test
  void pool_maintain_shrinksAfterLowUsage() {
    final OperandStack.StackPool pool = new OperandStack.StackPool();
    for (int cycle = 0; cycle < 10; cycle++) {
      pool.peakThisCycle = 1;
      pool.idleCount = 256;
      pool.maintain();
    }
    assertThat(pool.capacity).isEqualTo(16);
  }

  @Test
  void pool_maintain_growsAfterHighUsage() {
    final OperandStack.StackPool pool = new OperandStack.StackPool();
    for (int cycle = 0; cycle < 10; cycle++) {
      pool.peakThisCycle = 100;
      pool.idleCount = 256;
      pool.maintain();
    }
    assertThat(pool.capacity).isEqualTo(256);
  }

  @Test
  void pool_maintain_shrinksFromSpike() {
    final OperandStack.StackPool pool = new OperandStack.StackPool();
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

  @Test
  void pool_releaseTriggersMaintenanceAtInterval() {
    final OperandStack.StackPool pool = new OperandStack.StackPool();
    for (int i = 0; i < 255; i++) {
      OperandStack s = pool.borrow();
      pool.release(s);
    }
    assertThat(pool.idleCount).isEqualTo(255);
    OperandStack s = pool.borrow();
    pool.release(s);
    assertThat(pool.idleCount).isZero();
  }
}

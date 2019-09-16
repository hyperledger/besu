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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.util.bytes.Bytes32;

import java.util.Arrays;

/**
 * An {@link OperandStack} implementations whose capacity is pre-allocated.
 *
 * <p>The {@code PreAllocatedOperandStack} pre-allocates its internal storage to hold the max number
 * it is capable of storing.
 */
public class PreAllocatedOperandStack implements OperandStack {

  private final Bytes32[] entries;

  private final int maxSize;

  private int top;

  public PreAllocatedOperandStack(final int maxSize) {
    if (maxSize < 0) {
      throw new IllegalArgumentException(
          String.format("max size (%d) must be non-negative", maxSize));
    }
    this.entries = new Bytes32[maxSize];
    this.maxSize = maxSize;
    this.top = -1;
  }

  @Override
  public Bytes32 get(final int offset) {
    if (offset < 0 || offset >= size()) {
      throw new IndexOutOfBoundsException();
    }

    return entries[top - offset];
  }

  @Override
  public Bytes32 pop() {
    if (top < 0) {
      throw new IllegalStateException("operand stack underflow");
    }

    final Bytes32 removed = entries[top];
    entries[top--] = null;
    return removed;
  }

  @Override
  public void push(final Bytes32 operand) {
    final int nextTop = top + 1;
    if (nextTop == maxSize) {
      throw new IllegalStateException("operand stack overflow");
    }
    entries[nextTop] = operand;
    top = nextTop;
  }

  @Override
  public void set(final int offset, final Bytes32 operand) {
    if (offset < 0 || offset >= size()) {
      throw new IndexOutOfBoundsException();
    }

    entries[top - offset] = operand;
  }

  @Override
  public int size() {
    return top + 1;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < size(); ++i) {
      builder.append(String.format("\n0x%04X ", i)).append(get(i));
    }
    return builder.toString();
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(entries);
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof PreAllocatedOperandStack)) {
      return false;
    }

    final PreAllocatedOperandStack that = (PreAllocatedOperandStack) other;
    return Arrays.deepEquals(this.entries, that.entries);
  }
}

/*
 * Copyright contributors to Hyperledger Besu
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

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * An operand stack for the Ethereum Virtual machine (EVM).
 *
 * <p>The operand stack is responsible for storing the current operands that the EVM can execute. It
 * is assumed to have a fixed size.
 */
public class FixedStack<T> {

  private final T[] entries;

  private final int maxSize;

  private int top;

  public static class UnderflowException extends RuntimeException {
    // Don't create a stack trace since these are not "errors" per say but are using exceptions to
    // throw rare control flow conditions (EVM stack overflow) that are expected to be seen in
    // normal
    // operations.
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  public static class OverflowException extends RuntimeException {
    // Don't create a stack trace since these are not "errors" per say but are using exceptions to
    // throw rare control flow conditions (EVM stack overflow) that are expected to be seen in
    // normal
    // operations.
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  @SuppressWarnings("unchecked")
  public FixedStack(final int maxSize, final Class<T> klass) {
    checkArgument(maxSize >= 0, "max size must be non-negative");

    this.entries = (T[]) Array.newInstance(klass, maxSize);
    this.maxSize = maxSize;
    this.top = -1;
  }

  public T get(final int offset) {
    if (offset < 0 || offset >= size()) {
      throw new UnderflowException();
    }

    return entries[top - offset];
  }

  public T pop() {
    if (top < 0) {
      throw new UnderflowException();
    }

    final T removed = entries[top];
    entries[top--] = null;
    return removed;
  }

  /**
   * Pops the specified number of operands from the stack.
   *
   * @param items the number of operands to pop off the stack
   * @throws IllegalArgumentException if the items to pop is negative.
   * @throws UnderflowException when the items to pop is greater than {@link #size()}
   */
  public void bulkPop(final int items) {
    if (items < 0) {
      throw new IllegalArgumentException(
          String.format("requested number of items to bulk pop (%d) is negative", items));
    }
    checkArgument(items > 0, "number of items to pop must be greater than 0");
    if (items > size()) {
      throw new UnderflowException();
    }

    for (int i = 0; i < items; ++i) {
      pop();
    }
  }

  public void push(final T operand) {
    final int nextTop = top + 1;
    if (nextTop == maxSize) {
      throw new OverflowException();
    }
    entries[nextTop] = operand;
    top = nextTop;
  }

  public void set(final int offset, final T operand) {
    if (offset < 0 || offset >= size()) {
      throw new IndexOutOfBoundsException();
    }

    entries[top - offset] = operand;
  }

  public int size() {
    return top + 1;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < entries.length; ++i) {
      builder.append(String.format("\n0x%04X ", i)).append(entries[i]);
    }
    return builder.toString();
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(entries);
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof FixedStack)) {
      return false;
    }

    final FixedStack<T> that = (FixedStack<T>) other;
    return Arrays.deepEquals(this.entries, that.entries);
  }

  public boolean isFull() {
    return top + 1 >= maxSize;
  }

  public boolean isEmpty() {
    return top < 0;
  }
}

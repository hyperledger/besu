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
import java.util.Objects;

/**
 * An operand stack for the Ethereum Virtual machine (EVM). The stack grows 32 entries at a time if
 * it expands past the top of the allocated stack, up to maxSize.
 *
 * <p>The operand stack is responsible for storing the current operands that the EVM can execute. It
 * is assumed to have a fixed maximum size but may have a smaller memory footprint.
 *
 * @param <T> the type parameter
 */
public class FlexStack<T> {

  private static final int INCREMENT = 32;

  private T[] entries;

  private final int maxSize;
  private int currentCapacity;

  private int top;

  /**
   * Instantiates a new Flex stack.
   *
   * @param maxSize the max size
   * @param klass the klass
   */
  @SuppressWarnings("unchecked")
  public FlexStack(final int maxSize, final Class<T> klass) {
    checkArgument(maxSize > 0, "max size must be positive");

    this.currentCapacity = Math.min(INCREMENT, maxSize);
    this.entries = (T[]) Array.newInstance(klass, currentCapacity);
    this.maxSize = maxSize;
    this.top = -1;
  }

  /**
   * Get operand.
   *
   * @param offset the offset
   * @return the operand
   */
  public T get(final int offset) {
    if (offset < 0 || offset >= size()) {
      throw new UnderflowException();
    }

    return entries[top - offset];
  }

  /**
   * Pop operand.
   *
   * @return the operand
   */
  public T pop() {
    if (top < 0) {
      throw new UnderflowException();
    }

    final T removed = entries[top];
    entries[top--] = null;
    return removed;
  }

  /**
   * Peek and return type T.
   *
   * @return the T entry
   */
  public T peek() {
    if (top < 0) {
      return null;
    } else {
      return entries[top];
    }
  }

  /**
   * Pops the specified number of operands from the stack.
   *
   * @param items the number of operands to pop off the stack
   * @throws IllegalArgumentException if the items to pop is negative.
   * @throws UnderflowException when the items to pop is greater than {@link #size()}
   */
  public void bulkPop(final int items) {
    checkArgument(items > 0, "number of items to pop must be greater than 0");
    if (items > size()) {
      throw new UnderflowException();
    }

    Arrays.fill(entries, top - items + 1, top + 1, null);
    top -= items;
  }

  /**
   * Trims the "middle" section of items out of the stack. Items below the cutpoint remains, and of
   * the items above only the itemsToKeep items remain. All items in the middle are removed.
   *
   * @param cutPoint Point at which to start removing items
   * @param itemsToKeep itemsToKeep Number of items on top to place at the cutPoint
   * @throws IllegalArgumentException if the cutPoint or items to keep is negative.
   * @throws UnderflowException If there are less than itemsToKeep above the cutPoint
   */
  public void preserveTop(final int cutPoint, final int itemsToKeep) {
    checkArgument(cutPoint >= 0, "cutPoint must be positive");
    checkArgument(itemsToKeep >= 0, "itemsToKeep must be positive");
    if (itemsToKeep == 0) {
      if (cutPoint < size()) {
        bulkPop(top - cutPoint);
      }
    } else {
      int targetSize = cutPoint + itemsToKeep;
      int currentSize = size();
      if (targetSize > currentSize) {
        throw new UnderflowException();
      } else if (targetSize < currentSize) {
        System.arraycopy(entries, currentSize - itemsToKeep, entries, cutPoint, itemsToKeep);
        Arrays.fill(entries, targetSize, currentSize, null);
        top = targetSize - 1;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void expandEntries(final int nextSize) {
    var nextEntries = (T[]) Array.newInstance(entries.getClass().getComponentType(), nextSize);
    System.arraycopy(entries, 0, nextEntries, 0, currentCapacity);
    entries = nextEntries;
    currentCapacity = nextSize;
  }

  /**
   * Push operand.
   *
   * @param operand the operand
   */
  public void push(final T operand) {
    final int nextTop = top + 1;
    if (nextTop >= maxSize) {
      throw new OverflowException();
    }
    if (nextTop >= currentCapacity) {
      expandEntries(Math.min(currentCapacity + INCREMENT, maxSize));
    }
    entries[nextTop] = operand;
    top = nextTop;
  }

  /**
   * Set operand.
   *
   * @param offset the offset
   * @param operand the operand
   */
  public void set(final int offset, final T operand) {
    if (offset < 0) {
      throw new UnderflowException();
    } else if (offset > top) {
      throw new OverflowException();
    }

    entries[top - offset] = operand;
  }

  /**
   * Size of entries.
   *
   * @return the size
   */
  public int size() {
    return top + 1;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < top; ++i) {
      builder.append(String.format("%n0x%04X ", i)).append(entries[i]);
    }
    return builder.toString();
  }

  @Override
  public int hashCode() {
    int result = 1;

    for (int i = 0; i < currentCapacity; i++) {
      result = 31 * result + (entries[i] == null ? 0 : entries[i].hashCode());
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof FlexStack)) {
      return false;
    }

    final FlexStack<T> that = (FlexStack<T>) other;
    if (this.currentCapacity != that.currentCapacity) {
      return false;
    }
    for (int i = 0; i < currentCapacity; i++) {
      if (!Objects.deepEquals(this.entries[i], that.entries[i])) {
        return false;
      }
    }
    return true;
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
}

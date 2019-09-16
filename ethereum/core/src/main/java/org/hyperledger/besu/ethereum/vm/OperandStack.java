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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.util.bytes.Bytes32;

/**
 * An operand stack for the Ethereum Virtual machine (EVM).
 *
 * <p>The operand stack is responsible for storing the current operands that the EVM can execute. It
 * is assumed to have a fixed size.
 */
public interface OperandStack {

  /**
   * Returns the operand located at the offset from the top of the stack.
   *
   * @param offset the position relative to the top of the stack of the operand to return
   * @return the operand located at the specified offset
   * @throws IndexOutOfBoundsException if the offset is out of range (offset &lt; 0 || offset &gt;=
   *     {@link #size()})
   */
  Bytes32 get(int offset);

  /**
   * Removes the operand at the top of the stack.
   *
   * @return the operand removed from the top of the stack
   * @throws IllegalStateException if the stack is empty (e.g. a stack underflow occurs)
   */
  Bytes32 pop();

  /**
   * Pops the specified number of operands from the stack.
   *
   * @param items the number of operands to pop off the stack
   * @throws IllegalArgumentException if the items to pop is negative.
   * @throws IllegalStateException when the items to pop is greater than {@link #size()}
   */
  default void bulkPop(final int items) {
    if (items < 0) {
      throw new IllegalArgumentException(
          String.format("requested number of items to bulk pop (%d) is negative", items));
    }
    checkArgument(items > 0, "number of items to pop must be greater than 0");
    if (items > size()) {
      throw new IllegalStateException(
          String.format("requested to bulk pop %d items off a stack of size %d", items, size()));
    }

    for (int i = 0; i < items; ++i) {
      pop();
    }
  }

  /**
   * Pushes the operand onto the stack.
   *
   * @param operand the operand to push on the stack
   * @throws IllegalStateException when the stack is at capacity (e.g. a stack overflow occurs)
   */
  public void push(Bytes32 operand);

  /**
   * Sets the ith item from the top of the stack to the value.
   *
   * @param index the position relative to the top of the stack to set
   * @param operand the new operand that replaces the operand at the current offset
   * @throws IndexOutOfBoundsException if the offset is out of range (offset &lt; 0 || offset &gt;=
   *     {@link #size()})
   */
  void set(int index, Bytes32 operand);

  /**
   * Returns the current number of operands in the stack.
   *
   * @return the current number of operands in the stack
   */
  int size();
}

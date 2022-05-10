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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.evm.internal.FixedStack.OverflowException;
import org.hyperledger.besu.evm.internal.FixedStack.UnderflowException;
import org.hyperledger.besu.evm.internal.OperandStack;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class OperandStackTest {

  @Test
  public void construction() {
    final OperandStack stack = new OperandStack(1);
    assertThat(stack.size()).isEqualTo(0);
  }

  @Test
  public void construction_NegativeMaximumSize() {
    assertThatThrownBy(() -> new OperandStack(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void push_StackOverflow() {
    final OperandStack stack = new OperandStack(1);
    stack.push(UInt256.fromHexString("0x01"));
    final UInt256 operand = UInt256.fromHexString("0x02");
    assertThatThrownBy(() -> stack.push(operand)).isInstanceOf(OverflowException.class);
  }

  @Test
  public void pop_StackUnderflow() {
    final OperandStack stack = new OperandStack(1);
    assertThatThrownBy(stack::pop).isInstanceOf(UnderflowException.class);
  }

  @Test
  public void pushPop() {
    final OperandStack stack = new OperandStack(1);
    stack.push(UInt256.fromHexString("0x01"));
    assertThat(stack.size()).isEqualTo(1);
    assertThat(stack.pop()).isEqualTo(Bytes32.fromHexString("0x01"));
  }

  @Test
  public void get_NegativeOffset() {
    final OperandStack stack = new OperandStack(1);
    assertThatThrownBy(() -> stack.get(-1)).isInstanceOf(UnderflowException.class);
  }

  @Test
  public void get_IndexGreaterThanSize() {
    final OperandStack stack = new OperandStack(1);
    stack.push(UInt256.fromHexString("0x01"));
    assertThatThrownBy(() -> stack.get(2)).isInstanceOf(UnderflowException.class);
  }

  @Test
  public void get() {
    final OperandStack stack = new OperandStack(3);
    stack.push(UInt256.fromHexString("0x01"));
    stack.push(UInt256.fromHexString("0x02"));
    stack.push(UInt256.fromHexString("0x03"));
    assertThat(stack.size()).isEqualTo(3);
    assertThat(stack.get(0)).isEqualTo(Bytes32.fromHexString("0x03"));
    assertThat(stack.get(1)).isEqualTo(Bytes32.fromHexString("0x02"));
    assertThat(stack.get(2)).isEqualTo(Bytes32.fromHexString("0x01"));
  }

  @Test
  public void set_NegativeOffset() {
    final OperandStack stack = new OperandStack(1);
    final Bytes32 operand = Bytes32.fromHexString("0x01");
    assertThatThrownBy(() -> stack.set(-1, operand)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  public void set_IndexGreaterThanSize() {
    final OperandStack stack = new OperandStack(1);
    stack.push(UInt256.fromHexString("0x01"));
    final Bytes32 operand = Bytes32.fromHexString("0x01");
    assertThatThrownBy(() -> stack.set(2, operand)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  public void set() {
    final OperandStack stack = new OperandStack(3);
    stack.push(UInt256.fromHexString("0x01"));
    stack.push(UInt256.fromHexString("0x02"));
    stack.push(UInt256.fromHexString("0x03"));
    stack.set(2, UInt256.fromHexString("0x04"));
    assertThat(stack.size()).isEqualTo(3);
    assertThat(stack.get(0)).isEqualTo(Bytes32.fromHexString("0x03"));
    assertThat(stack.get(1)).isEqualTo(Bytes32.fromHexString("0x02"));
    assertThat(stack.get(2)).isEqualTo(Bytes32.fromHexString("0x04"));
  }
}

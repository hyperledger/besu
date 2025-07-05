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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.MessageFrame;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class AddOperationTest {
  @Test
  void add_zeroPlusZero_shouldBeZero() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Bytes a = Bytes32.fromHexString("0x00");
    final Bytes b = Bytes32.fromHexString("0x00");
    when(frame.popStackItem()).thenReturn(a, b);

    final Operation.OperationResult result = AddOperation.staticOperation(frame);

    verify(frame).pushStackItem(Bytes32.fromHexString("0x00"));
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void add_onePlusZero_shouldBeOne() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Bytes a = Bytes32.fromHexString("0x01");
    final Bytes b = Bytes32.fromHexString("0x00");
    when(frame.popStackItem()).thenReturn(a, b);

    final Operation.OperationResult result = AddOperation.staticOperation(frame);

    verify(frame).pushStackItem(Bytes32.fromHexString("0x01"));
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void add_maxPlusZero_shouldBeMax() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Bytes max =
        Bytes32.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    when(frame.popStackItem()).thenReturn(max, Bytes32.fromHexString("0x00"));

    final Operation.OperationResult result = AddOperation.staticOperation(frame);

    verify(frame).pushStackItem(max);
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void add_maxPlusOne_shouldWrapAroundToZero() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Bytes max =
        Bytes32.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    final Bytes one = Bytes32.fromHexString("0x01");
    when(frame.popStackItem()).thenReturn(max, one);

    final Operation.OperationResult result = AddOperation.staticOperation(frame);

    verify(frame).pushStackItem(Bytes32.fromHexString("0x00"));
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void add_partialValues_shouldBeCorrect() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Bytes a =
        Bytes32.fromHexString("0x00000000000000000000000000000000000000000000000000000000000000ff");
    final Bytes b =
        Bytes32.fromHexString("0x00000000000000000000000000000000000000000000000000000000000000ff");
    final Bytes expected =
        Bytes32.fromHexString("0x00000000000000000000000000000000000000000000000000000000000001fe");
    when(frame.popStackItem()).thenReturn(a, b);

    final Operation.OperationResult result = AddOperation.staticOperation(frame);

    verify(frame).pushStackItem(expected);
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void add_leadingZerosShouldBePreserved() {
    final MessageFrame frame = mock(MessageFrame.class);
    final Bytes a =
        Bytes.fromHexString("0x00000000000000000000000000000000000000000000000000000000000000ff");
    final Bytes b = Bytes.fromHexString("0x01");
    final Bytes expected =
        Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000100");
    when(frame.popStackItem()).thenReturn(a, b);

    final Operation.OperationResult result = AddOperation.staticOperation(frame);

    verify(frame).pushStackItem(expected);
    assertThat(result.getHaltReason()).isNull();
  }
}

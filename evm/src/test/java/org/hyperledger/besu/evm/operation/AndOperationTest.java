/*
 * Copyright contributors to Besu.
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
import org.junit.jupiter.api.Test;

class AndOperationTest {

  @Test
  void and_zeroAndZero_shouldBeZero() {
    final MessageFrame frame = mock(MessageFrame.class);

    final Bytes op1 = Bytes.fromHexString("0x00");
    final Bytes op2 = Bytes.fromHexString("0x00");
    when(frame.popStackItem()).thenReturn(op2, op1);

    final Operation.OperationResult result = AndOperation.staticOperation(frame);

    verify(frame)
        .pushStackItem(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"));
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void and_allOnesAndZero_shouldBeZero() {
    final MessageFrame frame = mock(MessageFrame.class);

    final Bytes op1 =
        Bytes.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    final Bytes op2 = Bytes.fromHexString("0x00");
    when(frame.popStackItem()).thenReturn(op2, op1);

    final Operation.OperationResult result = AndOperation.staticOperation(frame);

    verify(frame)
        .pushStackItem(
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"));
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void and_allOnesAndAllOnes_shouldBeAllOnes() {
    final MessageFrame frame = mock(MessageFrame.class);

    final Bytes op1 =
        Bytes.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    final Bytes op2 =
        Bytes.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    when(frame.popStackItem()).thenReturn(op2, op1);

    final Operation.OperationResult result = AndOperation.staticOperation(frame);

    verify(frame)
        .pushStackItem(
            Bytes.fromHexString(
                "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void and_partialOverlap_shouldBeCorrect() {
    final MessageFrame frame = mock(MessageFrame.class);

    final Bytes op1 =
        Bytes.fromHexString("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0f0");
    final Bytes op2 =
        Bytes.fromHexString("0x0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f");
    when(frame.popStackItem()).thenReturn(op2, op1);

    final Bytes expected =
        Bytes.fromHexString("0x0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0000");

    final Operation.OperationResult result = AndOperation.staticOperation(frame);

    verify(frame).pushStackItem(expected);
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void and_leadingZeros_shouldBePreserved() {
    final MessageFrame frame = mock(MessageFrame.class);

    final Bytes op1 =
        Bytes.fromHexString("0x00000000000000000000000000000000000000000000000000000000000000ff");
    final Bytes op2 =
        Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000f0f");
    when(frame.popStackItem()).thenReturn(op2, op1);

    final Bytes expected =
        Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000000f");

    final Operation.OperationResult result = AndOperation.staticOperation(frame);

    verify(frame).pushStackItem(expected);
    assertThat(result.getHaltReason()).isNull();
  }
}

/*
 * Copyright contributors to Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.evm.frame.MessageFrame;

import org.junit.jupiter.api.Test;

public class MulOperationTest extends BaseOperationTest {

  @Test
  void mul_zeroTimesZero_shouldBeZero() {
    final MessageFrame frame = mock(MessageFrame.class);
    popStackItemsFromHexString(frame, "0x00", "0x00");

    final Operation.OperationResult result = MulOperation.staticOperation(frame);

    verifyPushStackItemFromHexString(frame, "0x00");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void mul_oneTimesZero_shouldBeZero() {
    final MessageFrame frame = mock(MessageFrame.class);
    popStackItemsFromHexString(frame, "0x01", "0x00");

    final Operation.OperationResult result = MulOperation.staticOperation(frame);

    verifyPushStackItemFromHexString(frame, "0x00");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void mul_oneTimesOne_shouldBeOne() {
    final MessageFrame frame = mock(MessageFrame.class);
    popStackItemsFromHexString(frame, "0x01", "0x01");

    final Operation.OperationResult result = MulOperation.staticOperation(frame);

    verifyPushStackItemFromHexString(frame, "0x01");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void mul_maxTimesTwo_shouldWrap() {
    final MessageFrame frame = mock(MessageFrame.class);
    popStackItemsFromHexString(
        frame, "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "0x02");

    final Operation.OperationResult result = MulOperation.staticOperation(frame);

    verifyPushStackItemFromHexString(
        frame, "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void mul_partial_shouldBeCorrect() {
    final MessageFrame frame = mock(MessageFrame.class);
    popStackItemsFromHexString(
        frame,
        "0x00000000000000000000000000000000000000000000000000000000000000ff",
        "0x000000000000000000000000000000000000000000000000000000000000000f");

    final Operation.OperationResult result = MulOperation.staticOperation(frame);

    verifyPushStackItemFromHexString(
        frame, "0x0000000000000000000000000000000000000000000000000000000000000ef1");
    assertThat(result.getHaltReason()).isNull();
  }

  @Test
  void mul_leadingZeros_shouldBePreserved() {
    final MessageFrame frame = mock(MessageFrame.class);
    popStackItemsFromHexString(
        frame, "0x0000000000000000000000000000000000000000000000000000000000000100", "0x01");

    final Operation.OperationResult result = MulOperation.staticOperation(frame);

    verifyPushStackItemFromHexString(
        frame, "0x0000000000000000000000000000000000000000000000000000000000000100");
    assertThat(result.getHaltReason()).isNull();
  }
}

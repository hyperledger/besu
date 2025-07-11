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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BaseOperationTest {

  void popStackItemsFromHexString(final MessageFrame mockMessageFrame, final String... hexStrings) {
    final Deque<Bytes> stack = new ArrayDeque<>();

    // enqueue like a stack bottom-to-top
    for (String hex : hexStrings) {
      stack.addLast(Bytes.fromHexStringLenient(hex));
    }

    // pop from top
    when(mockMessageFrame.popStackItem()).thenAnswer(inv -> stack.removeFirst());
  }

  void verifyPushStackItemFromHexString(
      final MessageFrame mockMessageFrame, final String hexString) {
    verify(mockMessageFrame).pushStackItem(Bytes32.fromHexStringLenient(hexString));
  }
}

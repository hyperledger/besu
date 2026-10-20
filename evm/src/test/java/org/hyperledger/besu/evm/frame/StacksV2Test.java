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
package org.hyperledger.besu.evm.frame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;

class StacksV2Test {

  private static final int LENGTH = 1024 * 4;

  @Test
  void nextFrameAtTheSameDepthReusesTheStack() {
    final Deque<MessageFrame> frameStack = new ArrayDeque<>();
    frameStack.addFirst(mock(MessageFrame.class));

    final long[] first = StacksV2.take(frameStack, LENGTH);
    frameStack.addFirst(mock(MessageFrame.class));
    frameStack.removeFirst();
    final long[] second = StacksV2.take(frameStack, LENGTH);

    assertThat(second).isSameAs(first);
  }

  @Test
  void nextTransactionReusesTheStack() {
    final long[] first = StacksV2.take(new ArrayDeque<>(), LENGTH);
    final long[] second = StacksV2.take(new ArrayDeque<>(), LENGTH);

    assertThat(second).isSameAs(first);
  }

  @Test
  void childFrameGetsItsOwnStack() {
    final Deque<MessageFrame> frameStack = new ArrayDeque<>();

    final long[] parent = StacksV2.take(frameStack, LENGTH);
    frameStack.addFirst(mock(MessageFrame.class));
    final long[] child = StacksV2.take(frameStack, LENGTH);

    assertThat(child).isNotSameAs(parent);
  }

  @Test
  void runningFrameOfAnotherFrameStackKeepsItsStack() {
    final Deque<MessageFrame> outer = new ArrayDeque<>();
    final long[] running = StacksV2.take(outer, LENGTH);
    outer.addFirst(mock(MessageFrame.class));

    final long[] nested = StacksV2.take(new ArrayDeque<>(), LENGTH);

    assertThat(nested).isNotSameAs(running);
  }

  @Test
  void otherLengthGetsANewStack() {
    final long[] first = StacksV2.take(new ArrayDeque<>(), LENGTH);
    final long[] second = StacksV2.take(new ArrayDeque<>(), 16 * 4);

    assertThat(second).isNotSameAs(first).hasSize(16 * 4);
  }

  @Test
  void framesBelowTheCachedDepthsAllocate() {
    final Deque<MessageFrame> frameStack = new ArrayDeque<>();
    for (int i = 0; i < StacksV2.CACHED_DEPTHS; i++) {
      frameStack.addFirst(mock(MessageFrame.class));
    }

    final long[] first = StacksV2.take(frameStack, LENGTH);
    final long[] second = StacksV2.take(frameStack, LENGTH);

    assertThat(second).isNotSameAs(first);
  }
}

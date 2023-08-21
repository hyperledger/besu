/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.evm.processor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Deque;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
abstract class AbstractMessageProcessorTest<T extends AbstractMessageProcessor> {

  @Mock MessageFrame messageFrame;
  @Mock OperationTracer operationTracer;
  @Mock Deque<MessageFrame> messageFrameStack;
  @Mock WorldUpdater worldUpdater;

  @Test
  void shouldNotTraceContextIfStackSizeIsZero() {
    when(messageFrame.getMessageStackSize()).thenReturn(0);
    when(messageFrame.getState())
        .thenReturn(MessageFrame.State.COMPLETED_SUCCESS, MessageFrame.State.COMPLETED_FAILED);
    when(messageFrame.getMessageFrameStack()).thenReturn(messageFrameStack);

    getAbstractMessageProcessor().process(messageFrame, operationTracer);

    verify(operationTracer, never()).traceContextEnter(messageFrame);
    verify(operationTracer, never()).traceContextExit(messageFrame);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 5, 15, Integer.MAX_VALUE})
  void shouldTraceContextIfStackSizeIsGreaterZeroAndSuccess(final int stackSize) {
    when(messageFrame.getMessageStackSize()).thenReturn(stackSize);
    when(messageFrame.getState()).thenReturn(MessageFrame.State.COMPLETED_SUCCESS);
    when(messageFrame.getMessageFrameStack()).thenReturn(messageFrameStack);
    when(messageFrame.getWorldUpdater()).thenReturn(worldUpdater);

    getAbstractMessageProcessor().process(messageFrame, operationTracer);

    verify(operationTracer, times(1)).traceContextEnter(messageFrame);
    verify(operationTracer, times(1)).traceContextExit(messageFrame);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 5, 15, Integer.MAX_VALUE})
  void shouldTraceContextIfStackSizeIsGreaterZeroAndFailure(final int stackSize) {
    when(messageFrame.getMessageStackSize()).thenReturn(stackSize);
    when(messageFrame.getState()).thenReturn(MessageFrame.State.COMPLETED_FAILED);
    when(messageFrame.getMessageFrameStack()).thenReturn(messageFrameStack);

    getAbstractMessageProcessor().process(messageFrame, operationTracer);

    verify(operationTracer, times(1)).traceContextEnter(messageFrame);
    verify(operationTracer, times(1)).traceContextExit(messageFrame);
  }

  protected abstract T getAbstractMessageProcessor();
}

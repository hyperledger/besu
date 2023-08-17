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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallCodeOperation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.operation.SStoreOperation;
import org.hyperledger.besu.evm.tracing.EstimateGasOperationTracer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EstimateGasOperationTracerTest {

  private EstimateGasOperationTracer operationTracer;
  private MessageFrameTestFixture messageFrameTestFixture;

  @BeforeEach
  public void setUp() {
    operationTracer = new EstimateGasOperationTracer();
    messageFrameTestFixture = new MessageFrameTestFixture();
  }

  @Test
  public void shouldDetectChangeInDepthDuringExecution() {

    final OperationResult testResult = new OperationResult(6, null);

    assertThat(operationTracer.getMaxDepth()).isZero();

    final MessageFrame firstFrame = messageFrameTestFixture.build();
    operationTracer.tracePostExecution(firstFrame, testResult);
    assertThat(operationTracer.getMaxDepth()).isZero();

    final MessageFrame secondFrame = messageFrameTestFixture.parentFrame(firstFrame).build();
    operationTracer.tracePostExecution(secondFrame, testResult);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(1);
    firstFrame.getMessageFrameStack().removeFirst();

    final MessageFrame thirdFrame = messageFrameTestFixture.parentFrame(firstFrame).build();
    operationTracer.tracePostExecution(thirdFrame, testResult);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(1);

    final MessageFrame fourthFrame = messageFrameTestFixture.parentFrame(thirdFrame).build();
    operationTracer.tracePostExecution(fourthFrame, testResult);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(2);
    firstFrame.getMessageFrameStack().removeFirst();
    firstFrame.getMessageFrameStack().removeFirst();

    final MessageFrame fifthFrame = messageFrameTestFixture.build();
    operationTracer.tracePostExecution(fifthFrame, testResult);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(2);
  }

  @Test
  public void shouldDetectMinimumGasRemainingForSStoreOperation() {

    final OperationResult testResult = new OperationResult(6, null);
    final long minimumGasRemaining = 2300L;

    assertThat(operationTracer.getStipendNeeded()).isZero();

    final MessageFrame firstFrame = messageFrameTestFixture.build();
    firstFrame.setCurrentOperation(mock(CallCodeOperation.class));
    operationTracer.tracePostExecution(firstFrame, testResult);
    assertThat(operationTracer.getStipendNeeded()).isZero();

    final MessageFrame secondFrame = messageFrameTestFixture.build();
    secondFrame.setCurrentOperation(
        new SStoreOperation(mock(GasCalculator.class), minimumGasRemaining));
    operationTracer.tracePostExecution(secondFrame, testResult);
    assertThat(operationTracer.getStipendNeeded()).isEqualTo(minimumGasRemaining);

    operationTracer.tracePostExecution(secondFrame, testResult);
    assertThat(operationTracer.getStipendNeeded()).isEqualTo(minimumGasRemaining);
  }
}

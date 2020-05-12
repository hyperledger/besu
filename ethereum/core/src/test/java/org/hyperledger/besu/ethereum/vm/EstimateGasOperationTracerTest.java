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

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.vm.OperationTracer.ExecuteOperation;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;
import org.hyperledger.besu.ethereum.vm.operations.CallCodeOperation;
import org.hyperledger.besu.ethereum.vm.operations.SStoreOperation;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class EstimateGasOperationTracerTest {

  private EstimateGasOperationTracer operationTracer;
  private MessageFrameTestFixture messageFrameTestFixture;

  @Before
  public void setUp() {
    operationTracer = new EstimateGasOperationTracer();
    messageFrameTestFixture = new MessageFrameTestFixture();
  }

  @Test
  public void shouldDetectChangeInDepthDuringExecution() throws ExceptionalHaltException {

    final ExecuteOperation noExecutionOperation = mock(ExecuteOperation.class);

    assertThat(operationTracer.getMaxDepth()).isEqualTo(0);

    final MessageFrame firstFrame = messageFrameTestFixture.depth(0).build();
    operationTracer.traceExecution(firstFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(0);

    final MessageFrame secondFrame = messageFrameTestFixture.depth(1).build();
    operationTracer.traceExecution(secondFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(1);

    final MessageFrame thirdFrame = messageFrameTestFixture.depth(1).build();
    operationTracer.traceExecution(thirdFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(1);

    final MessageFrame fourthFrame = messageFrameTestFixture.depth(2).build();
    operationTracer.traceExecution(fourthFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(2);

    final MessageFrame fifthFrame = messageFrameTestFixture.depth(0).build();
    operationTracer.traceExecution(fifthFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getMaxDepth()).isEqualTo(2);
  }

  @Test
  public void shouldDetectMinimumGasRemainingForSStoreOperation() throws ExceptionalHaltException {

    final ExecuteOperation noExecutionOperation = mock(ExecuteOperation.class);
    final Gas minimumGasRemaining = Gas.of(2300);

    assertThat(operationTracer.getStipendNeeded()).isEqualTo(Gas.ZERO);

    final MessageFrame firstFrame = messageFrameTestFixture.build();
    firstFrame.setCurrentOperation(mock(CallCodeOperation.class));
    operationTracer.traceExecution(firstFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getStipendNeeded()).isEqualTo(Gas.ZERO);

    final MessageFrame secondFrame = messageFrameTestFixture.build();
    secondFrame.setCurrentOperation(
        new SStoreOperation(mock(GasCalculator.class), minimumGasRemaining));
    operationTracer.traceExecution(secondFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getStipendNeeded()).isEqualTo(minimumGasRemaining);

    operationTracer.traceExecution(secondFrame, Optional.empty(), noExecutionOperation);
    assertThat(operationTracer.getStipendNeeded()).isEqualTo(minimumGasRemaining);
  }
}

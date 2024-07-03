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
package org.hyperledger.besu.services.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.noop.NoOpMetricsSystem.NO_OP_COUNTER;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessingStageTest {

  private final Pipe<String> inputPipe =
      new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER, NO_OP_COUNTER, "input_pipe");
  private final Pipe<String> outputPipe =
      new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER, NO_OP_COUNTER, "output_pipe");
  @Mock private Processor<String, String> singleStep;
  private ProcessingStage<String, String> stage;

  @BeforeEach
  public void setUp() {
    stage = new ProcessingStage<>("name", inputPipe, outputPipe, singleStep);
    lenient()
        .doAnswer(
            invocation -> {
              outputPipe.put(inputPipe.get().toLowerCase(Locale.UK));
              return 1;
            })
        .when(singleStep)
        .processNextInput(inputPipe, outputPipe);
  }

  @Test
  public void shouldCallSingleStepStageForEachInput() {
    when(singleStep.attemptFinalization(outputPipe)).thenReturn(true);
    inputPipe.put("A");
    inputPipe.put("B");
    inputPipe.put("C");
    inputPipe.close();

    stage.run();

    assertThat(outputPipe.poll()).isEqualTo("a");
    assertThat(outputPipe.poll()).isEqualTo("b");
    assertThat(outputPipe.poll()).isEqualTo("c");
    assertThat(outputPipe.poll()).isNull();

    verify(singleStep, times(3)).processNextInput(inputPipe, outputPipe);
  }

  @Test
  public void shouldFinalizeSingleStepStageAndCloseOutputPipeWhenInputCloses() {
    when(singleStep.attemptFinalization(outputPipe)).thenReturn(true);
    inputPipe.close();

    stage.run();

    verify(singleStep).attemptFinalization(outputPipe);
    verifyNoMoreInteractions(singleStep);
    assertThat(outputPipe.isOpen()).isFalse();
  }

  @Test
  public void shouldAbortIfPipeIsCancelledWhileAttemptingToFinalise() {
    when(singleStep.attemptFinalization(outputPipe))
        .thenAnswer(
            invocation -> {
              inputPipe.abort();
              return false;
            });
    inputPipe.close();

    stage.run();

    verify(singleStep).attemptFinalization(outputPipe);
    verify(singleStep).abort();
    verifyNoMoreInteractions(singleStep);
    assertThat(outputPipe.isOpen()).isFalse();
  }

  @Test
  public void shouldAbortProcessorIfReadPipeIsAborted() {
    when(singleStep.attemptFinalization(outputPipe)).thenReturn(true);
    inputPipe.abort();
    stage.run();

    verify(singleStep).abort();
    verify(singleStep).attemptFinalization(outputPipe);
    assertThat(outputPipe.isOpen()).isFalse();
  }
}

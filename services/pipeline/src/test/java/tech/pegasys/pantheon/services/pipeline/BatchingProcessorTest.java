/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.services.pipeline;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem.NO_OP_COUNTER;

import java.util.List;

import org.junit.Test;

public class BatchingProcessorTest {

  private final Pipe<Integer> inputPipe = new Pipe<>(10, NO_OP_COUNTER);
  private final Pipe<List<Integer>> outputPipe = new Pipe<>(10, NO_OP_COUNTER);
  private final BatchingProcessor<Integer> stage = new BatchingProcessor<>(3);

  @Test
  public void shouldCreateBatches() {
    for (int i = 1; i <= 8; i++) {
      inputPipe.put(i);
    }
    inputPipe.close();

    stage.processNextInput(inputPipe, outputPipe);

    assertThat(outputPipe.poll()).isEqualTo(asList(1, 2, 3));
    assertThat(outputPipe.poll()).isNull();

    stage.processNextInput(inputPipe, outputPipe);
    assertThat(outputPipe.poll()).isEqualTo(asList(4, 5, 6));
    assertThat(outputPipe.poll()).isNull();

    stage.processNextInput(inputPipe, outputPipe);
    assertThat(outputPipe.poll()).isEqualTo(asList(7, 8));
    assertThat(outputPipe.poll()).isNull();
  }

  @Test
  public void shouldNotOutputItemWhenInputIsClosed() {
    @SuppressWarnings("unchecked")
    final WritePipe<List<Integer>> outputPipe = mock(WritePipe.class);
    inputPipe.close();
    stage.processNextInput(inputPipe, outputPipe);
    verifyZeroInteractions(outputPipe);
  }
}

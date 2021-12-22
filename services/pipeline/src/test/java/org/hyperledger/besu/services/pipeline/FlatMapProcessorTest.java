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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.Test;

public class FlatMapProcessorTest {

  private final Pipe<String> input = new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER, NO_OP_COUNTER);
  private final Pipe<String> output = new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER, NO_OP_COUNTER);

  @SuppressWarnings("unchecked")
  private final Function<String, Stream<String>> mapper = mock(Function.class);

  private final FlatMapProcessor<String, String> stage = new FlatMapProcessor<>(mapper);

  @Test
  public void shouldOutputItemsFromReturnedStreamIndividually() {
    when(mapper.apply("A")).thenReturn(Stream.of("a", "b", "c"));
    input.put("A");

    stage.processNextInput(input, output);

    assertThat(output.poll()).isEqualTo("a");
    assertThat(output.poll()).isEqualTo("b");
    assertThat(output.poll()).isEqualTo("c");
    assertThat(output.poll()).isNull();
    verify(mapper).apply("A");
  }

  @Test
  public void shouldSkipProcessingWhenInputIsClosed() {
    input.close();
    stage.processNextInput(input, output);
    verifyNoInteractions(mapper);
  }
}

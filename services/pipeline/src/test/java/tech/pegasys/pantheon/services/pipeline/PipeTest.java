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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.metrics.Counter;

import org.junit.Test;

public class PipeTest {
  private final Counter itemCounter = mock(Counter.class);
  private final Pipe<String> pipe = new Pipe<>(5, itemCounter);

  @Test
  public void shouldNotHaveMoreWhenEmptyAndClosed() {
    pipe.close();
    assertThat(pipe.hasMore()).isFalse();
  }

  @Test
  public void shouldHaveMoreWhenNotEmptyAndClosed() {
    pipe.put("A");
    pipe.close();

    assertThat(pipe.hasMore()).isTrue();

    pipe.get();

    assertThat(pipe.hasMore()).isFalse();
  }

  @Test
  public void shouldNotHaveMoreWhenAbortedEvenIfNotEmpty() {
    pipe.put("A");
    pipe.abort();

    assertThat(pipe.hasMore()).isFalse();
  }

  @Test
  public void shouldLimitBatchMaximumSize() {
    pipe.put("a");
    pipe.put("b");
    pipe.put("c");
    pipe.put("d");

    assertThat(pipe.getBatch(3)).containsExactly("a", "b", "c");
  }

  @Test
  public void shouldNotWaitToReachMaximumSizeBeforeReturningBatch() {
    pipe.put("a");
    assertThat(pipe.getBatch(3)).containsExactly("a");
  }

  @Test
  public void shouldNotBeOpenAfterAbort() {
    pipe.abort();
    assertThat(pipe.isOpen()).isFalse();
  }

  @Test
  public void shouldIncrementCounterWhenItemAddedToPipe() {
    pipe.put("A");
    verify(itemCounter).inc();
    pipe.put("B");
    verify(itemCounter, times(2)).inc();
  }
}

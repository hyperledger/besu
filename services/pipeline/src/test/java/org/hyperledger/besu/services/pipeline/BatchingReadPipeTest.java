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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.noop.NoOpMetricsSystem.NO_OP_COUNTER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class BatchingReadPipeTest {

  private final Pipe<String> source =
      new Pipe<>(10, NO_OP_COUNTER, NO_OP_COUNTER, NO_OP_COUNTER, "source_pipe");
  private final Counter batchCounter = mock(Counter.class);
  private final BatchingReadPipe<String> batchingPipe =
      new BatchingReadPipe<>(source, 3, batchCounter);

  @Test
  public void shouldGetABatchOfAvailableItems() {
    source.put("a");
    source.put("b");
    source.put("c");
    source.put("d");

    assertThat(batchingPipe.get()).containsExactly("a", "b", "c");
  }

  @Test
  public void shouldNotWaitToFillBatch() {
    source.put("a");
    assertThat(batchingPipe.get()).containsExactly("a");
  }

  @Test
  public void shouldPollForNextBatchWhenAvailable() {
    source.put("a");
    assertThat(batchingPipe.poll()).containsExactly("a");
  }

  @Test
  public void shouldReturnNullFromPollWhenNoItemsAvailable() {
    assertThat(batchingPipe.poll()).isNull();
  }

  @Test
  public void shouldHaveMoreItemsWhileSourceHasMoreItems() {
    assertThat(batchingPipe.hasMore()).isTrue();

    source.put("a");
    source.put("b");
    source.put("c");

    assertThat(batchingPipe.hasMore()).isTrue();

    source.close();

    assertThat(batchingPipe.hasMore()).isTrue();

    assertThat(batchingPipe.get()).containsExactly("a", "b", "c");

    assertThat(batchingPipe.hasMore()).isFalse();

    assertThat(batchingPipe.get()).isNull();
    assertThat(batchingPipe.poll()).isNull();
  }

  @Test
  public void shouldAddAtMostOneItemWhenDraining() {
    // Collecting a batch of batches is pretty silly so only ever drain one batch at a time.
    source.put("a");
    source.put("b");
    source.put("c");
    source.put("1");
    source.put("2");
    source.put("3");

    final List<List<String>> output = new ArrayList<>();
    batchingPipe.drainTo(output, 6);
    // Note still only 3 items in the batch.
    assertThat(output).containsExactly(asList("a", "b", "c"));
  }

  @Test
  public void shouldCountBatchesReturnedFromGet() {
    source.put("a");
    source.close();
    batchingPipe.get();
    assertThat(batchingPipe.get()).isNull();

    verify(batchCounter, times(1)).inc();
  }

  @Test
  public void shouldCountBatchesReturnedFromPoll() {
    assertThat(batchingPipe.poll()).isNull();
    verifyNoInteractions(batchCounter);

    source.put("a");
    batchingPipe.poll();

    verify(batchCounter, times(1)).inc();
  }

  @Test
  public void shouldCountBatchesReturnedFromDrainTo() {
    final List<List<String>> output = new ArrayList<>();
    batchingPipe.drainTo(output, 3);
    verifyNoInteractions(batchCounter);

    source.put("a");
    batchingPipe.drainTo(output, 3);

    verify(batchCounter, times(1)).inc();
  }
}

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

import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class BatchingReadPipe<T> implements ReadPipe<List<T>> {

  private final ReadPipe<T> input;
  private final Supplier<Integer> maximumBatchSize;
  private final Counter batchCounter;

  public BatchingReadPipe(
      final ReadPipe<T> input, final int maximumBatchSize, final Counter batchCounter) {
    this(input, () -> maximumBatchSize, batchCounter);
  }

  public BatchingReadPipe(
      final ReadPipe<T> input,
      final Supplier<Integer> maximumBatchSize,
      final Counter batchCounter) {
    this.input = input;
    this.maximumBatchSize = maximumBatchSize;
    this.batchCounter = batchCounter;
  }

  @Override
  public boolean hasMore() {
    return input.hasMore();
  }

  @Override
  public boolean isAborted() {
    return input.isAborted();
  }

  @Override
  public List<T> get() {
    final T firstItem = input.get();
    if (firstItem == null) {
      // Contract of get is to explicitly return null when no more items are available.
      // An empty list is not a suitable thing to return here.
      return null;
    }
    final List<T> batch = new ArrayList<>();
    batch.add(firstItem);
    input.drainTo(batch, maximumBatchSize.get() - 1);
    batchCounter.inc();
    return batch;
  }

  @Override
  public List<T> poll() {
    final List<T> batch = new ArrayList<>();
    input.drainTo(batch, maximumBatchSize.get());
    if (batch.isEmpty()) {
      // Poll has to return null if the pipe is empty
      return null;
    }
    batchCounter.inc();
    return batch;
  }

  @Override
  public void drainTo(final Collection<List<T>> output, final int maxElements) {
    final List<T> nextBatch = poll();
    if (nextBatch != null) {
      output.add(nextBatch);
    }
  }
}

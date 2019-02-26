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
package tech.pegasys.pantheon.services.queue;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RocksDbTaskQueueTest extends AbstractTaskQueueTest<RocksDbTaskQueue<BytesValue>> {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Override
  protected RocksDbTaskQueue<BytesValue> createQueue() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    return createQueue(dataDir);
  }

  private RocksDbTaskQueue<BytesValue> createQueue(final Path dataDir) {
    return RocksDbTaskQueue.create(
        dataDir, Function.identity(), Function.identity(), new NoOpMetricsSystem());
  }

  @Test
  public void shouldIgnoreExistingData() throws Exception {
    final Path dataDir = folder.newFolder().toPath();
    try (final RocksDbTaskQueue<BytesValue> queue = createQueue(dataDir)) {
      queue.enqueue(BytesValue.of(1));
      queue.enqueue(BytesValue.of(2));
      queue.enqueue(BytesValue.of(3));
    }

    try (final RocksDbTaskQueue<BytesValue> resumedQueue = createQueue(dataDir)) {
      assertThat(resumedQueue.dequeue()).isEqualTo(null);

      resumedQueue.enqueue(BytesValue.of(50));
      assertThat(resumedQueue.dequeue().getData()).isEqualTo(BytesValue.of(50));

      resumedQueue.enqueue(BytesValue.of(60));
      assertThat(resumedQueue.dequeue().getData()).isEqualTo(BytesValue.of(60));
    }
  }
}

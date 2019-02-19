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

import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.queue.TaskQueue.Task;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import com.google.common.io.Files;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class RocksDbTaskQueueBenchmark {

  private File tempDir;
  private RocksDbTaskQueue<BytesValue> queue;

  @Setup(Level.Trial)
  public void prepare() {
    System.out.println("YAY!");
    tempDir = Files.createTempDir();
    queue =
        RocksDbTaskQueue.create(
            tempDir.toPath(), Function.identity(), Function.identity(), new NoOpMetricsSystem());
    for (int i = 0; i < 1_000_000; i++) {
      queue.enqueue(UInt256.of(i).getBytes());
    }
  }

  @TearDown
  public void tearDown() throws IOException {
    queue.close();
    MoreFiles.deleteRecursively(tempDir.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
  }

  @Benchmark
  public Task<BytesValue> dequeue() {
    return queue.dequeue();
  }
}

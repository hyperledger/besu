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
package org.hyperledger.besu.ethereum.eth.manager.bounded;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import org.junit.jupiter.api.Test;

public class BoundedQueueTest {

  @Test
  public void offerShouldAcceptNewElements() {
    int size = 10;
    final BoundedQueue queue = new BoundedQueue(size, "test", new NoOpMetricsSystem());
    for (int i = 0; i < size; i++) {
      final Runnable task = () -> {};
      assertThat(queue.offer(task)).isTrue();
      assertThat(queue).contains(task);
      assertThat(queue.size()).isEqualTo(i + 1);
    }
  }

  @Test
  public void offerShouldMakeARoomAndAcceptNewElementAtFullCapacity() {
    final BoundedQueue queue = new BoundedQueue(2, "test", new NoOpMetricsSystem());
    final Runnable task1 = () -> {};
    final Runnable task2 = () -> {};
    final Runnable task3 = () -> {};
    assertThat(queue.offer(task1)).isTrue();
    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue).contains(task1);
    assertThat(queue.offer(task2)).isTrue();
    assertThat(queue.size()).isEqualTo(2);
    assertThat(queue).contains(task2);
    assertThat(queue.offer(task3)).isTrue();
    assertThat(queue).doesNotContain(task1);
    assertThat(queue).contains(task2);
    assertThat(queue).contains(task3);
    assertThat(queue.size()).isEqualTo(2);
  }
}

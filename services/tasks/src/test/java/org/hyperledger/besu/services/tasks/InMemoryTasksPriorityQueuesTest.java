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
package org.hyperledger.besu.services.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

public class InMemoryTasksPriorityQueuesTest {

  @Test
  public void shouldBePossibleToAddElementsAndRetrieveThemInPriorityOrder() {
    InMemoryTasksPriorityQueues<Item> queue = new InMemoryTasksPriorityQueues<>();

    queue.add(item(1, 1));
    queue.add(item(2, 30));
    queue.add(item(2, 10));
    queue.add(item(5, 1));
    queue.add(item(99, Integer.MAX_VALUE));
    queue.add(item(1, 20));

    List<Item> items = new ArrayList<>();
    while (!queue.isEmpty()) {
      items.add(queue.remove().getData());
    }

    assertThat(items)
        .containsExactly(
            item(99, Integer.MAX_VALUE),
            item(5, 1),
            item(2, 10),
            item(2, 30),
            item(1, 1),
            item(1, 20));
  }

  @Test
  public void shouldNotInsertItemsToClosedQueue() {
    InMemoryTasksPriorityQueues<Item> queue = new InMemoryTasksPriorityQueues<>();

    queue.add(item(1, 1));

    queue.close();

    final Item item = item(2, 2);
    assertThatThrownBy(() -> queue.add(item)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldContainTaskUntilFinished() {
    InMemoryTasksPriorityQueues<Item> queue = new InMemoryTasksPriorityQueues<>();
    queue.add(item(1, 1));

    final Item item = item(2, 3);
    queue.add(item);

    assertThat(queue.contains(item)).isTrue();

    final Task<Item> removed = queue.remove();
    assertThat(removed.getData()).isEqualTo(item);

    assertThat(queue.contains(item)).isTrue();

    removed.markCompleted();

    assertThat(queue.contains(item)).isFalse();
  }

  @Test
  public void shouldPutFailedItemBackIntoQueue() {
    InMemoryTasksPriorityQueues<Item> queue = new InMemoryTasksPriorityQueues<>();
    queue.add(item(1, 1));

    final Item item = item(2, 3);
    queue.add(item);

    assertThat(queue.contains(item)).isTrue();

    Task<Item> removed = queue.remove();
    assertThat(removed.getData()).isEqualTo(item);

    assertThat(queue.contains(item)).isTrue();

    removed.markFailed();

    assertThat(queue.contains(item)).isTrue();

    removed = queue.remove();
    assertThat(removed.getData()).isEqualTo(item);
  }

  @Test
  public void shouldNotPutFailedItemBackIntoIfItWasCompletedAlreadyQueue() {
    InMemoryTasksPriorityQueues<Item> queue = new InMemoryTasksPriorityQueues<>();
    queue.add(item(1, 1));

    final Item item = item(2, 3);
    queue.add(item);

    assertThat(queue.contains(item)).isTrue();

    Task<Item> removed = queue.remove();
    assertThat(removed.getData()).isEqualTo(item);

    assertThat(queue.contains(item)).isTrue();

    removed.markCompleted();
    assertThat(queue.contains(item)).isFalse();

    removed.markFailed();
    assertThat(queue.contains(item)).isFalse();

    removed = queue.remove();
    assertThat(removed.getData()).isNotEqualTo(item);
  }

  private Item item(final int depth, final int priority) {
    return new Item(depth, priority);
  }

  static class Item implements TasksPriorityProvider {
    private final int depth;
    private final long priority;

    public Item(final int depth, final long priority) {
      this.depth = depth;
      this.priority = priority;
    }

    @Override
    public long getPriority() {
      return priority;
    }

    @Override
    public int getDepth() {
      return depth;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Item item = (Item) o;
      return depth == item.depth && priority == item.priority;
    }

    @Override
    public int hashCode() {
      return Objects.hash(depth, priority);
    }

    @Override
    public String toString() {
      return "Item{" + "depth=" + depth + ", priority=" + priority + '}';
    }
  }
}

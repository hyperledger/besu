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
package org.hyperledger.besu.consensus.common.bft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.hyperledger.besu.consensus.common.bft.events.BftEvent;
import org.hyperledger.besu.consensus.common.bft.events.BftEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class BftEventQueueTest {
  private static final int MAX_QUEUE_SIZE = 1000;

  private static class DummyBftEvent implements BftEvent {
    @Override
    public BftEvents.Type getType() {
      return null;
    }
  }

  @Test
  public void addPoll() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);

    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
    final DummyBftEvent dummyEvent = new DummyBftEvent();
    queue.add(dummyEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyEvent);
  }

  @Test
  public void queueOrdering() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);

    final DummyBftEvent dummyEvent1 = new DummyBftEvent();
    final DummyBftEvent dummyEvent2 = new DummyBftEvent();
    final DummyBftEvent dummyEvent3 = new DummyBftEvent();
    assertThatCode(
            () -> {
              queue.add(dummyEvent1);
              queue.add(dummyEvent2);
              queue.add(dummyEvent3);
            })
        .doesNotThrowAnyException();

    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyEvent1);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyEvent2);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyEvent3);
  }

  @Test
  public void addSizeLimit() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);

    for (int i = 0; i <= 1000; i++) {
      final DummyBftEvent dummyEvent = new DummyBftEvent();
      queue.add(dummyEvent);
    }

    final DummyBftEvent dummyEventDiscard = new DummyBftEvent();
    queue.add(dummyEventDiscard);

    final List<BftEvent> drain = new ArrayList<>();
    for (int i = 0; i <= 1000; i++) {
      drain.add(queue.poll(0, TimeUnit.MICROSECONDS));
    }
    assertThat(drain).doesNotContainNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
  }
}

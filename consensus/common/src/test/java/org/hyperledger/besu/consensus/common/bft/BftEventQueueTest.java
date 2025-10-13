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

import org.junit.jupiter.api.Test;

public class BftEventQueueTest {
  private static final int MAX_QUEUE_SIZE = 1000;

  private static class DummyBftEvent implements BftEvent {
    @Override
    public BftEvents.Type getType() {
      return BftEvents.Type.MESSAGE;
    }
  }

  private static class DummyRoundExpiryBftEvent implements BftEvent {
    @Override
    public BftEvents.Type getType() {
      return BftEvents.Type.ROUND_EXPIRY;
    }
  }

  private static class DummyNewChainHeadBftEvent implements BftEvent {
    @Override
    public BftEvents.Type getType() {
      return BftEvents.Type.NEW_CHAIN_HEAD;
    }
  }

  private static class DummyBlockTimerExpiryBftEvent implements BftEvent {
    @Override
    public BftEvents.Type getType() {
      return BftEvents.Type.BLOCK_TIMER_EXPIRY;
    }
  }

  @Test
  public void addPoll() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);
    queue.start();

    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
    final DummyBftEvent dummyMessageEvent = new DummyBftEvent();
    final DummyRoundExpiryBftEvent dummyRoundTimerEvent = new DummyRoundExpiryBftEvent();
    final DummyNewChainHeadBftEvent dummyNewChainHeadEvent = new DummyNewChainHeadBftEvent();
    queue.add(dummyMessageEvent);
    queue.add(dummyRoundTimerEvent);
    queue.add(dummyNewChainHeadEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyMessageEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyRoundTimerEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyNewChainHeadEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
  }

  @Test
  public void queueOrdering() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);
    queue.start();

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
    queue.start();

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

  @Test
  public void doNotAddUntilStarted() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);

    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
    final DummyBftEvent dummyMessageEvent = new DummyBftEvent();
    final DummyRoundExpiryBftEvent dummyRoundTimerEvent = new DummyRoundExpiryBftEvent();
    final DummyNewChainHeadBftEvent dummyNewChainHeadEvent = new DummyNewChainHeadBftEvent();

    // BFT event queue needs starting before it will queue up most types of event. This prevents
    // the queue filling with irrelevant messages until BFT mining has started
    queue.add(dummyMessageEvent);
    queue.add(dummyRoundTimerEvent);
    queue.add(dummyNewChainHeadEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
  }

  @Test
  public void supportsStopAndRestart() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);
    queue.start();

    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
    final DummyBftEvent dummyMessageEvent = new DummyBftEvent();
    final DummyRoundExpiryBftEvent dummyRoundTimerEvent = new DummyRoundExpiryBftEvent();
    final DummyNewChainHeadBftEvent dummyNewChainHeadEvent = new DummyNewChainHeadBftEvent();

    queue.add(dummyMessageEvent);
    queue.add(dummyRoundTimerEvent);
    queue.add(dummyNewChainHeadEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNotNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNotNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNotNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();

    queue.stop();
    queue.add(dummyMessageEvent);
    queue.add(dummyRoundTimerEvent);
    queue.add(dummyNewChainHeadEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();

    queue.start();
    queue.add(dummyMessageEvent);
    queue.add(dummyRoundTimerEvent);
    queue.add(dummyNewChainHeadEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNotNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNotNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNotNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
  }

  @Test
  public void alwaysAddBlockTimerExpiryEvents() throws InterruptedException {
    final BftEventQueue queue = new BftEventQueue(MAX_QUEUE_SIZE);

    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
    final DummyBlockTimerExpiryBftEvent dummyBlockTimerEvent = new DummyBlockTimerExpiryBftEvent();

    // Block expiry events need processing in order for the mining coordinator to start,
    // so those should be handled regardless of whether the queue has been started
    queue.add(dummyBlockTimerEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyBlockTimerEvent);
  }
}

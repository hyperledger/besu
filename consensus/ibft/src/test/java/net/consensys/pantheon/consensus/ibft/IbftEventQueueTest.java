package net.consensys.pantheon.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import net.consensys.pantheon.consensus.ibft.IbftEvents.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class IbftEventQueueTest {

  private static class DummyIbftEvent implements IbftEvent {
    @Override
    public Type getType() {
      return null;
    }
  }

  @Test
  public void addPoll() throws InterruptedException {
    final IbftEventQueue queue = new IbftEventQueue();

    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
    final DummyIbftEvent dummyEvent = new DummyIbftEvent();
    queue.add(dummyEvent);
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isEqualTo(dummyEvent);
  }

  @Test
  public void queueOrdering() throws InterruptedException {
    final IbftEventQueue queue = new IbftEventQueue();

    final DummyIbftEvent dummyEvent1 = new DummyIbftEvent();
    final DummyIbftEvent dummyEvent2 = new DummyIbftEvent();
    final DummyIbftEvent dummyEvent3 = new DummyIbftEvent();
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
    final IbftEventQueue queue = new IbftEventQueue();

    for (int i = 0; i <= 1000; i++) {
      final DummyIbftEvent dummyEvent = new DummyIbftEvent();
      queue.add(dummyEvent);
    }

    final DummyIbftEvent dummyEventDiscard = new DummyIbftEvent();
    queue.add(dummyEventDiscard);

    final List<IbftEvent> drain = new ArrayList<>();
    for (int i = 0; i <= 1000; i++) {
      drain.add(queue.poll(0, TimeUnit.MICROSECONDS));
    }
    assertThat(drain).doesNotContainNull();
    assertThat(queue.poll(0, TimeUnit.MICROSECONDS)).isNull();
  }
}

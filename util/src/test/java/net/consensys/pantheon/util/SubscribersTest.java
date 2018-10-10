package net.consensys.pantheon.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Test;

public class SubscribersTest {
  private final Runnable subscriber1 = mock(Runnable.class);
  private final Runnable subscriber2 = mock(Runnable.class);
  private final Subscribers<Runnable> subscribers = new Subscribers<>();

  @Test
  public void shouldAddSubscriber() {
    subscribers.subscribe(subscriber1);

    assertThat(subscribers.getSubscriberCount()).isEqualTo(1);

    subscribers.forEach(Runnable::run);
    verify(subscriber1).run();
  }

  @Test
  public void shouldRemoveSubscriber() {
    final long id = subscribers.subscribe(subscriber1);
    subscribers.subscribe(subscriber2);
    assertThat(subscribers.unsubscribe(id)).isTrue();

    assertThat(subscribers.getSubscriberCount()).isEqualTo(1);
    subscribers.forEach(Runnable::run);
    verifyZeroInteractions(subscriber1);
    verify(subscriber2).run();
  }

  @Test
  public void shouldTrackMultipleSubscribers() {
    final Runnable subscriber3 = mock(Runnable.class);
    subscribers.subscribe(subscriber1);
    subscribers.subscribe(subscriber2);
    subscribers.subscribe(subscriber3);

    assertThat(subscribers.getSubscriberCount()).isEqualTo(3);
    subscribers.forEach(Runnable::run);
    verify(subscriber1).run();
    verify(subscriber2).run();
    verify(subscriber3).run();
  }
}

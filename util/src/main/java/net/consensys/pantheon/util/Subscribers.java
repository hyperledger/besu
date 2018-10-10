package net.consensys.pantheon.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Tracks subscribers that should be notified when some event occurred. This class is safe to use
 * from multiple threads.
 *
 * <p>Each subscriber is assigned a unique ID which can be used to unsubscribe. This approach was
 * chosen over using the subscriber's object identity to eliminate a common trap believing that
 * method references are equal when they refer to the same method. For example, if object identity
 * were used to track subscribers it would be possible to write the incorrect code:
 *
 * <pre>
 * <code>subscribers.subscribe(this::onSomeEvent);
 * subscribers.unsubscribe(this::onSomeEvent);</code>
 * </pre>
 *
 * <p>Since the two separate <code>this:onSomeEvent</code> are not equal, the subscriber wouldn't be
 * removed. This bug is avoided by assigning each subscriber a unique ID and using that to
 * unsubscribe.
 *
 * @param <T> the type of subscribers
 */
public class Subscribers<T> {

  private final AtomicLong subscriberId = new AtomicLong();
  private final Map<Long, T> subscribers = new ConcurrentHashMap<>();

  /**
   * Add a subscriber to the list.
   *
   * @param subscriber the subscriber to add
   * @return the ID assigned to this subscriber
   */
  public long subscribe(final T subscriber) {
    final long id = subscriberId.getAndIncrement();
    subscribers.put(id, subscriber);
    return id;
  }

  /**
   * Remove a subscriber from the list.
   *
   * @param subscriberId the ID of the subscriber to remove
   * @return <code>true</code> if a subscriber with that ID was found and removed, otherwise <code>
   *     false</code>
   */
  public boolean unsubscribe(final long subscriberId) {
    return subscribers.remove(subscriberId) != null;
  }

  /**
   * Iterate through the current list of subscribers. This is typically used to deliver events e.g.:
   *
   * <pre>
   * <code>subscribers.forEach(subscriber -&gt; subscriber.onEvent());</code>
   * </pre>
   *
   * @param action the action to perform for each subscriber
   */
  public void forEach(final Consumer<T> action) {
    subscribers.values().forEach(action);
  }

  /**
   * Get the current subscriber count.
   *
   * @return the current number of subscribers.
   */
  public int getSubscriberCount() {
    return subscribers.size();
  }
}

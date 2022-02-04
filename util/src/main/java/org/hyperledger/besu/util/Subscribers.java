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
package org.hyperledger.besu.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>Since the two separate <code>this::onSomeEvent</code> are not equal, the subscriber wouldn't
 * be removed. This bug is avoided by assigning each subscriber a unique ID and using that to
 * unsubscribe.
 *
 * @param <T> the type of subscribers
 */
public class Subscribers<T> {
  private static final Subscribers<?> NONE = new EmptySubscribers<>();
  private static final Logger LOG = LoggerFactory.getLogger(Subscribers.class);

  private final AtomicLong subscriberId = new AtomicLong();
  private final Map<Long, T> subscribers = new ConcurrentHashMap<>();

  private final boolean suppressCallbackExceptions;

  private Subscribers(final boolean suppressCallbackExceptions) {
    this.suppressCallbackExceptions = suppressCallbackExceptions;
  }

  @SuppressWarnings("unchecked")
  public static <T> Subscribers<T> none() {
    return (Subscribers<T>) NONE;
  }

  public static <T> Subscribers<T> create() {
    return new Subscribers<T>(false);
  }

  public static <T> Subscribers<T> create(final boolean catchCallbackExceptions) {
    return new Subscribers<T>(catchCallbackExceptions);
  }

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
    ImmutableSet.copyOf(
            // we copy here to ensure that our callback, which may add another subscriber on this
            // event, won't trigger that new additional subscriber
            subscribers.values())
        .forEach(
            subscriber -> {
              try {
                action.accept(subscriber);
              } catch (final Exception e) {
                if (suppressCallbackExceptions) {
                  LOG.error("Error in callback: ", e);
                } else {
                  throw e;
                }
              }
            });
  }

  /**
   * Get the current subscriber count.
   *
   * @return the current number of subscribers.
   */
  public int getSubscriberCount() {
    return subscribers.size();
  }

  private static class EmptySubscribers<T> extends Subscribers<T> {

    private EmptySubscribers() {
      super(false);
    }

    @Override
    public long subscribe(final T subscriber) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean unsubscribe(final long subscriberId) {
      return false;
    }

    @Override
    public void forEach(final Consumer<T> action) {
      return;
    }

    @Override
    public int getSubscriberCount() {
      return 0;
    }
  }
}

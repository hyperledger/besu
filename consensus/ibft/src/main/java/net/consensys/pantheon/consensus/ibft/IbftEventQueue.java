package net.consensys.pantheon.consensus.ibft;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Threadsafe queue that lets parts of the system inform the Ibft infrastructure about events */
public class IbftEventQueue {
  private final BlockingQueue<IbftEvent> queue = new LinkedBlockingQueue<>();

  private static final int MAX_QUEUE_SIZE = 1000;
  private static final Logger LOGGER = LogManager.getLogger(IbftEventQueue.class);

  /**
   * Put an Ibft event onto the queue
   *
   * @param event Provided ibft event
   */
  public void add(final IbftEvent event) {
    if (queue.size() > MAX_QUEUE_SIZE) {
      LOGGER.warn("Queue size exceeded trying to add new ibft event {}", event.toString());
    } else {
      queue.add(event);
    }
  }

  public int size() {
    return queue.size();
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }

  /**
   * Blocking request for the next item available on the queue that will timeout after a specified
   * period
   *
   * @param timeout number of time units after which this operation should timeout
   * @param unit the time units in which to count
   * @return The next IbftEvent to become available on the queue or null if the expiry passes
   * @throws InterruptedException If the underlying queue implementation is interrupted
   */
  @Nullable
  public IbftEvent poll(final long timeout, final TimeUnit unit) throws InterruptedException {
    return queue.poll(timeout, unit);
  }
}

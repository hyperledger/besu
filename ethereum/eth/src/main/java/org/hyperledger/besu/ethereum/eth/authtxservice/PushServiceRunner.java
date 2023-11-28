package org.hyperledger.besu.ethereum.eth.authtxservice;

import org.hyperledger.besu.datatypes.Hash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmCallback;
import com.rabbitmq.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class EvictTransactionsTask extends TimerTask {
  private static final Logger LOG = LoggerFactory.getLogger(EvictTransactionsTask.class);

  boolean isRunning = false;
  final PushServiceRunner service;

  public EvictTransactionsTask(final PushServiceRunner service) {
    this.service = service;
  }

  public boolean isRunning() {
    return isRunning;
  }

  @Override
  public void run() {
    if (service.getSharedChannelIsRunning().get()) {
      return;
    }
    Connection connection = service.getConnectionManager().getConnection();
    if (!connection.isOpen()
        || (connection.isOpen() && service.getPendingTransactions().isEmpty())) {
      return;
    }
    service.getSharedChannelIsRunning().set(true);
    try {
      Thread.sleep(1000L);
      Channel ch = service.getConnectionManager().getOrCreatePublishBatchChannel();
      ch.confirmSelect();
      isRunning = true;

      final ConcurrentNavigableMap<Long, String> pendingToPublish = new ConcurrentSkipListMap<>();
      long sequence = 1;
      for (Map.Entry<Hash, String> entry : service.getPendingTransactions().entrySet()) {
        pendingToPublish.put(sequence++, entry.getValue());
        service.getPendingTransactions().remove(entry.getKey());
      }

      ConfirmCallback ackPending =
          (sequenceNumber, multiple) -> {
            if (multiple) {
              ConcurrentNavigableMap<Long, String> confirmed =
                  pendingToPublish.headMap(sequenceNumber, true);
              confirmed.clear();
            } else {
              pendingToPublish.remove(sequenceNumber);
            }
          };

      ConfirmCallback nackPending =
          (sequenceNumber, multiple) -> {
            String body = pendingToPublish.get(sequenceNumber);
            LOG.error(
                "Transaction %s has been nack-ed. Sequence number: %d - multiple: %b",
                body, sequenceNumber, multiple);

            ackPending.handle(sequenceNumber, multiple);
          };

      ch.addConfirmListener(ackPending, nackPending);

      long start = System.nanoTime();
      int batchSize = pendingToPublish.size();

      for (Map.Entry<Long, String> entry : pendingToPublish.entrySet()) {
        ch.basicPublish(
            AuthTxServiceConfiguration.exchange,
            AuthTxServiceConfiguration.publishTransactionCreatedRoutingKey,
            null,
            entry.getValue().getBytes(StandardCharsets.UTF_8));
      }

      if (!waitUntilDone(Duration.ofSeconds(15), () -> pendingToPublish.isEmpty())) {
        throw new IllegalStateException("Transactions not be confirmed in 15 seconds");
      }

      long end = System.nanoTime();
      LOG.info(
          "Published {} messages and handled confirms asynchronously in {} ms",
          batchSize,
          Duration.ofNanos(end - start).toMillis());
      // close thread and channel
      System.out.println("Finished all threads");
      if (ch.isOpen()) ch.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (TimeoutException e) {
      throw new RuntimeException(e);
    } finally {
      isRunning = false;
      service.getSharedChannelIsRunning().set(false);
    }
    service.close();
  }

  /** Util for wait until task is done */
  static boolean waitUntilDone(final Duration timeout, final BooleanSupplier condition)
      throws InterruptedException {
    int waited = 0;
    while (!condition.getAsBoolean() && waited < timeout.toMillis()) {
      Thread.sleep(100L);
      waited = +100;
    }
    return condition.getAsBoolean();
  }
}

public class PushServiceRunner implements AutoCloseable {
  final ConnectionManager connectionManager;
  final ConcurrentHashMap<Hash, String> pendingTransactions;
  private Timer schedulerTaskTimer;
  private EvictTransactionsTask evictTxTask;
  private final AtomicBoolean sharedChannelIsRunning;
  private boolean isClosed = false;

  public PushServiceRunner(
      final ConnectionManager connectionManager,
      final ConcurrentHashMap<Hash, String> pendingTransactions) {
    super();
    sharedChannelIsRunning = new AtomicBoolean(false);
    this.connectionManager = connectionManager;
    this.pendingTransactions = pendingTransactions;
    this.createScheduler();
  }

  public boolean isClosed() {
    return isClosed;
  }

  public AtomicBoolean getSharedChannelIsRunning() {
    return sharedChannelIsRunning;
  }

  @Override
  public void close() {
    if (this.schedulerTaskTimer != null) {
      this.schedulerTaskTimer.cancel();
      this.setClosed();
    }
  }

  public void setClosed() {
    this.isClosed = true;
  }

  /** Create new scheduler with existing task cancels an existing one */
  private void createScheduler() {
    if (this.schedulerTaskTimer != null) {
      this.schedulerTaskTimer.cancel();
    }
    this.schedulerTaskTimer = new Timer();
    this.evictTxTask = new EvictTransactionsTask(this);
    this.schedulerTaskTimer.scheduleAtFixedRate(this.evictTxTask, 5000, 5000);
  }

  /** Try recreate the scheduler and run again if already is running the task */
  public boolean tryRunAgain() {
    if (this.sharedChannelIsRunning.get()) return false;
    this.createScheduler();
    return true;
  }

  public ConnectionManager getConnectionManager() {
    return connectionManager;
  }

  public ConcurrentHashMap<Hash, String> getPendingTransactions() {
    return pendingTransactions;
  }
}

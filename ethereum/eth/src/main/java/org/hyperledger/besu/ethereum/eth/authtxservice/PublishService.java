package org.hyperledger.besu.ethereum.eth.authtxservice;

import org.hyperledger.besu.datatypes.Hash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PublishService {
  private static final Logger LOG = LoggerFactory.getLogger(PublishService.class);
  private final ConnectionManager connectionManager;
  private final ConcurrentHashMap<Hash, String> pendingTransactions = new ConcurrentHashMap<>();
  private PushServiceRunner serviceRunner;

  public PublishService(final ConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  /** Schedule tasks to evict pending transactions */
  private void scheduleTask() {
    if (serviceRunner == null || (serviceRunner != null && serviceRunner.isClosed())) {
      serviceRunner = new PushServiceRunner(connectionManager, pendingTransactions);
    } else {
      serviceRunner.tryRunAgain();
    }
  }

  /** Publish transaction */
  public void publish(final Hash hash, final String transactionData) throws IOException {
    if (connectionManager.getConnection().isOpen()) {
      Channel channel = connectionManager.getOrCreatePublishChannel();
      channel.basicPublish(
          AuthTxServiceConfiguration.exchange,
          AuthTxServiceConfiguration.publishTransactionCreatedRoutingKey,
          null,
          transactionData.getBytes(StandardCharsets.UTF_8));
      // FIXME: handle publish if heartbeat "time is out"
      return;
    }
    LOG.info(
        "Received a transaction when message service is working offline waiting for reconnection...");
    pendingTransactions.put(hash, transactionData);
    scheduleTask();
  }
}

/*
 * Copyright Soruba.
 * https://www.gnu.org/licenses/gpl-3.0.html
 * SPDX-License-Identifier: GNU GPLv3
 */
package org.hyperledger.besu.ethereum.eth.authtxservice;

import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.soruba.TransactionPoolAuthTxObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthTxService {
  private static final Logger LOG = LoggerFactory.getLogger(AuthTxService.class);
  private final TransactionPool transactionPool;
  private final AuthTxServiceConfiguration txPoolConfiguration;
  private ConnectionManager connectionManager;
  private final boolean passiveMode;
  private Thread subscribeThread;
  private PublishService publishService;

  public AuthTxService(
      final AuthTxServiceConfiguration txPoolConfiguration, final TransactionPool transactionPool) {
    this.txPoolConfiguration = txPoolConfiguration;
    this.transactionPool = transactionPool;
    this.passiveMode = txPoolConfiguration.isEnabledAsPassiveMode();
    this.publishService = null;
  }

  public CompletableFuture<?> start() {
    final CompletableFuture<?> resultFuture = new CompletableFuture<>();
    if (passiveMode) {
      LOG.info("Starting AuthTxService as passive mode");
      this.transactionPool.setAuthPublishService(this);
    } else {
      LOG.info("Starting AuthTxService as active mode");

      this.connectionManager = new ConnectionManager(this.txPoolConfiguration);
      this.publishService = new PublishService(this.connectionManager);

      subscribeThread =
          new Thread(
              () -> {
                try {
                  this.declareRabbitConfiguration();
                  this.subscribeMessages();
                  this.transactionPool.setAuthPublishService(this);
                  resultFuture.complete(null);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                } catch (TimeoutException e) {
                  throw new RuntimeException(e);
                }
              });
      subscribeThread.start();
    }
    return resultFuture;
  }

  /** Stop service */
  public CompletableFuture<?> stop() {
    final CompletableFuture<?> resultFuture = new CompletableFuture<>();

    if (passiveMode) {
      resultFuture.complete(null);
      return resultFuture;
    }

    LOG.info("Stopping AuthTxService");
    connectionManager.stop();
    subscribeThread.interrupt();
    resultFuture.complete(null);

    return resultFuture;
  }

  public boolean isPassiveMode() {
    return passiveMode;
  }

  /** Publish message to be consumed by interceptor service */
  public void publishMessage(final Transaction transaction, final String rawTransaction) {
    String transactionData = "";
    try {
      transactionData =
          TransactionPoolAuthTxObjectMapper.encodeAuthTxObject(transaction, rawTransaction);
      this.publishService.publish(transaction.getHash(), transactionData);
    } catch (IOException e) {
      // FIXME: Need to handle failed payload encoding
      throw new RuntimeException(e);
    }
  }

  /** Subscribe to authorized and rejected topics */
  private void subscribeMessages() throws IOException, TimeoutException {
    Channel channel = connectionManager.getOrCreateSubscribeChannel();
    Consumer consumerAuthorized =
        new DefaultConsumer(channel) {
          @Override
          public void handleCancel(final String consumerTag) {
            LOG.info("AuthTxService - Consumer {} has been cancelled unexpectedly", consumerTag);
          }

          @Override
          public void handleDelivery(
              final String consumerTag,
              final Envelope envelope,
              final AMQP.BasicProperties properties,
              final byte[] body)
              throws IOException {
            super.handleDelivery(consumerTag, envelope, properties, body);
            String message = new String(body, StandardCharsets.UTF_8);
            LOG.info("Authorized message received {}", message);
            addLocalTransaction(body, false);
          }
        };

    channel.basicConsume(
        AuthTxServiceConfiguration.subscribeTransactionAuthorizedQueue, true, consumerAuthorized);

    Consumer consumerRejected =
        new DefaultConsumer(channel) {
          @Override
          public void handleCancel(final String consumerTag) {
            LOG.info("AuthTxService - Consumer {} has been cancelled unexpectedly", consumerTag);
          }

          @Override
          public void handleDelivery(
              final String consumerTag,
              final Envelope envelope,
              final AMQP.BasicProperties properties,
              final byte[] body)
              throws IOException {
            super.handleDelivery(consumerTag, envelope, properties, body);
            String message = new String(body, StandardCharsets.UTF_8);
            LOG.info("Rejected message received {}", message);
            addLocalTransaction(body, true);
          }
        };

    channel.basicConsume(
        AuthTxServiceConfiguration.subscribeTransactionRejectedQueue, true, consumerRejected);
  }

  /** Parse message convert bytes payload to string */
  private String parseMessageBytes(final byte[] message) {
    String decodedMsg =
        StringEscapeUtils.unescapeEcmaScript(new String(message, StandardCharsets.UTF_8));
    return decodedMsg.replaceAll("^\"", "");
  }

  /** Insert transaction in TransactionPool */
  private void addLocalTransaction(final byte[] message, final boolean rejectedReasonEnabled) {
    Optional<Transaction> newTransaction;
    try {
      newTransaction =
          TransactionPoolAuthTxObjectMapper.decodeAuthTxObject(
              parseMessageBytes(message), rejectedReasonEnabled);
      if (rejectedReasonEnabled && newTransaction.isEmpty()) {
        LOG.info(
            "Dropping transaction hash {} due not get reject reason code",
            newTransaction.hashCode());
        return;
      }
      transactionPool.addLocalTransactionViaAuth(newTransaction.get());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /** Declare a Topic Exchange. */
  public void declareExchange() throws IOException, TimeoutException {
    Channel channel = connectionManager.createChannel();
    channel.exchangeDeclare(AuthTxServiceConfiguration.exchange, BuiltinExchangeType.TOPIC, true);
    channel.close();
  }

  /** Declare Queues. */
  public void declareQueues() throws IOException, TimeoutException {
    Channel channel = connectionManager.createChannel();

    channel.queueDeclare(
        AuthTxServiceConfiguration.subscribeTransactionAuthorizedQueue, true, false, false, null);
    channel.queueDeclare(
        AuthTxServiceConfiguration.subscribeTransactionRejectedQueue, true, false, false, null);

    channel.close();
  }

  /** Declare Bindings - register interests using routing key patterns. */
  public void declareBindings() throws IOException, TimeoutException {
    Channel channel = connectionManager.createChannel();

    channel.queueBind(
        AuthTxServiceConfiguration.subscribeTransactionAuthorizedQueue,
        AuthTxServiceConfiguration.exchange,
        AuthTxServiceConfiguration.subscribeTransactionAuthorizedRoutingKey);
    channel.queueBind(
        AuthTxServiceConfiguration.subscribeTransactionRejectedQueue,
        AuthTxServiceConfiguration.exchange,
        AuthTxServiceConfiguration.subscribeTransactionRejectedRoutingKey);

    channel.close();
  }

  /** Declare all rabbitmq configuration */
  public void declareRabbitConfiguration() throws IOException, TimeoutException {
    this.declareExchange();
    this.declareQueues();
    this.declareBindings();
  }

  /** Checks if transaction is from known addresses from genesisFile */
  public boolean isTransactionAuthorizedByProtocol(final Transaction transaction) {
    Optional<?> to = transaction.getTo();
    ArrayList<String> addresses = new ArrayList<>();
    addresses.add(transaction.getSender().toString());
    if (to.isPresent()) addresses.add(to.get().toString());
    // FIXME: add a list of allowed addresses in genesis file
    // List<String> allowed = this.txPoolConfiguration.getGenesisAddresses();
    List<String> allowed =
        List.of(
            "0x0000000000000000000000000000000000001010",
            "0x0000000000000000000000000000000000001020");
    for (final String allowedAddress : allowed) {
      if (addresses.contains(allowedAddress)) {
        return true;
      }
    }
    return false;
  }
}

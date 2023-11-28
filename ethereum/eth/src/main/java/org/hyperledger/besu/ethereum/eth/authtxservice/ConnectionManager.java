/*
 * Copyright PublicMint.
 * https://www.gnu.org/licenses/gpl-3.0.html
 * SPDX-License-Identifier: GNU GPLv3
 */
package org.hyperledger.besu.ethereum.eth.authtxservice;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class ConnectionManager {
  private ConnectionFactory connectionFactory;
  private Connection connection;
  private final String endpoint;
  private Channel subscribeChannel;
  private Channel publishChannel;
  private Channel publishBatchChannel;
  private final String connectionName;

  public ConnectionManager(final AuthTxServiceConfiguration config) {
    this.connectionName = config.getEnode();
    this.endpoint = config.getAmqpEndpoint();
    this.connection = this.createConnection();
  }

  private Connection createConnection() {
    if (connection == null) {
      URI uri = URI.create(endpoint);
      try {
        connectionFactory = new ConnectionFactory();
        connectionFactory.setRequestedHeartbeat(30);

        connectionFactory.setUri(uri);
        connection = connectionFactory.newConnection(connectionName);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (TimeoutException e) {
        throw new RuntimeException(e);
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      } catch (KeyManagementException e) {
        throw new RuntimeException(e);
      }
    }

    return connection;
  }

  public Channel createChannel() throws IOException {
    return createConnection().createChannel();
  }

  private Channel createSubscribeChannel() throws IOException {
    subscribeChannel = this.createChannel();
    return subscribeChannel;
  }

  private Channel createPublishChannel() throws IOException {
    publishChannel = this.createChannel();
    return publishChannel;
  }

  private Channel createPublishBatchChannel() throws IOException {
    publishBatchChannel = this.createChannel();
    return publishBatchChannel;
  }

  public Channel getOrCreateSubscribeChannel() throws IOException {
    if (subscribeChannel == null) {
      return this.createSubscribeChannel();
    }
    return subscribeChannel;
  }

  public Channel getOrCreatePublishChannel() throws IOException {
    if (publishChannel == null) {
      return this.createPublishChannel();
    }
    return publishChannel;
  }

  public Channel getOrCreatePublishBatchChannel() throws IOException {
    if (publishBatchChannel == null) {
      return this.createPublishBatchChannel();
    }
    return publishBatchChannel;
  }

  public void closePublishBatchChannel() throws IOException, TimeoutException {
    if (connection.isOpen() && publishBatchChannel != null) {
      this.publishBatchChannel.close();
    }
  }

  public Connection getConnection() {
    return connection;
  }

  public void stop() {
    try {
      this.publishChannel.close();
      this.subscribeChannel.close();
      this.connection.close();
      this.closePublishBatchChannel();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}

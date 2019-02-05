/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon;

import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcHttpService;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketService;
import tech.pegasys.pantheon.ethereum.p2p.NetworkRunner;
import tech.pegasys.pantheon.metrics.prometheus.MetricsService;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Runner implements AutoCloseable {

  private static final Logger LOG = LogManager.getLogger();

  private final Vertx vertx;

  private final ExecutorService exec = Executors.newCachedThreadPool();

  private final NetworkRunner networkRunner;
  private final Optional<JsonRpcHttpService> jsonRpc;
  private final Optional<WebSocketService> websocketRpc;
  private final Optional<MetricsService> metrics;

  private final PantheonController<?> pantheonController;
  private final Path dataDir;

  Runner(
      final Vertx vertx,
      final NetworkRunner networkRunner,
      final Optional<JsonRpcHttpService> jsonRpc,
      final Optional<WebSocketService> websocketRpc,
      final Optional<MetricsService> metrics,
      final PantheonController<?> pantheonController,
      final Path dataDir) {
    this.vertx = vertx;
    this.networkRunner = networkRunner;
    this.jsonRpc = jsonRpc;
    this.websocketRpc = websocketRpc;
    this.metrics = metrics;
    this.pantheonController = pantheonController;
    this.dataDir = dataDir;
  }

  public void execute() {
    try {
      LOG.info("Starting Ethereum main loop ... ");
      networkRunner.start();
      if (networkRunner.getNetwork().isP2pEnabled()) {
        pantheonController.getSynchronizer().start();
      }
      jsonRpc.ifPresent(service -> service.start().join());
      websocketRpc.ifPresent(service -> service.start().join());
      metrics.ifPresent(service -> service.start().join());
      LOG.info("Ethereum main loop is up.");
      writePantheonPortsToFile();
      networkRunner.awaitStop();
    } catch (final InterruptedException e) {
      LOG.debug("Interrupted, exiting", e);
      Thread.currentThread().interrupt();
    } catch (final Exception ex) {
      LOG.error("Exception in main loop:", ex);
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public void close() throws Exception {
    if (networkRunner.getNetwork().isP2pEnabled()) {
      pantheonController.getSynchronizer().stop();
    }
    networkRunner.stop();
    networkRunner.awaitStop();

    exec.shutdown();
    try {
      jsonRpc.ifPresent(service -> service.stop().join());
      websocketRpc.ifPresent(service -> service.stop().join());
      metrics.ifPresent(service -> service.stop().join());
    } finally {
      try {
        exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
      } finally {
        try {
          vertx.close();
        } finally {
          pantheonController.close();
        }
      }
    }
  }

  private void writePantheonPortsToFile() {
    final Properties properties = new Properties();

    if (networkRunner.getNetwork().isListening()) {
      properties.setProperty("discovery", String.valueOf(getP2pUdpPort()));
      properties.setProperty("p2p", String.valueOf(getP2pTcpPort()));
    }

    if (getJsonRpcPort().isPresent()) {
      properties.setProperty("json-rpc", String.valueOf(getJsonRpcPort().get()));
    }
    if (getWebsocketPort().isPresent()) {
      properties.setProperty("ws-rpc", String.valueOf(getWebsocketPort().get()));
    }
    if (getMetricsPort().isPresent()) {
      properties.setProperty("metrics", String.valueOf(getMetricsPort().get()));
    }

    final File portsFile = new File(dataDir.toFile(), "pantheon.ports");
    portsFile.deleteOnExit();

    try (final FileOutputStream fileOutputStream = new FileOutputStream(portsFile)) {
      properties.store(
          fileOutputStream,
          "This file contains the ports used by the running instance of Pantheon. This file will be deleted after the node is shutdown.");
    } catch (final Exception e) {
      LOG.warn("Error writing ports file", e);
    }
  }

  public Optional<Integer> getJsonRpcPort() {
    return jsonRpc.map(service -> service.socketAddress().getPort());
  }

  public Optional<Integer> getWebsocketPort() {
    return websocketRpc.map(service -> service.socketAddress().getPort());
  }

  public Optional<Integer> getMetricsPort() {
    if (metrics.isPresent()) {
      return metrics.get().getPort();
    } else {
      return Optional.empty();
    }
  }

  public int getP2pUdpPort() {
    return networkRunner.getNetwork().getDiscoverySocketAddress().getPort();
  }

  public int getP2pTcpPort() {
    return networkRunner.getNetwork().getLocalPeerInfo().getPort();
  }
}

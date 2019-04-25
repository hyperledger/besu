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
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.metrics.prometheus.MetricsService;
import tech.pegasys.pantheon.util.enode.EnodeURL;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Runner implements AutoCloseable {

  private static final Logger LOG = LogManager.getLogger();

  private final Vertx vertx;

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

  public void start() {
    try {
      LOG.info("Starting Ethereum main loop ... ");
      networkRunner.start();
      if (networkRunner.getNetwork().isP2pEnabled()) {
        pantheonController.getSynchronizer().start();
      }
      vertx.setPeriodic(
          TimeUnit.MINUTES.toMillis(1),
          time ->
              pantheonController
                  .getTransactionPool()
                  .getPendingTransactions()
                  .evictOldTransactions());
      jsonRpc.ifPresent(service -> waitForServiceToStart("jsonRpc", service.start()));
      websocketRpc.ifPresent(service -> waitForServiceToStop("websocketRpc", service.start()));
      metrics.ifPresent(service -> waitForServiceToStart("metrics", service.start()));
      LOG.info("Ethereum main loop is up.");
      writePantheonPortsToFile();
    } catch (final Exception ex) {
      LOG.error("Startup failed", ex);
      throw new IllegalStateException(ex);
    }
  }

  public void awaitStop() {
    try {
      networkRunner.awaitStop();
    } catch (final InterruptedException e) {
      LOG.debug("Interrupted, exiting", e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() throws Exception {
    try {
      if (networkRunner.getNetwork().isP2pEnabled()) {
        pantheonController.getSynchronizer().stop();
      }

      networkRunner.stop();
      networkRunner.awaitStop();

      jsonRpc.ifPresent(service -> waitForServiceToStop("jsonRpc", service.stop()));
      websocketRpc.ifPresent(service -> waitForServiceToStop("websocketRpc", service.stop()));
      metrics.ifPresent(service -> waitForServiceToStop("metrics", service.stop()));
    } finally {
      try {
        vertx.close();
      } finally {
        pantheonController.close();
      }
    }
  }

  private void waitForServiceToStop(
      final String serviceName, final CompletableFuture<?> stopFuture) {
    try {
      stopFuture.get(30, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
      LOG.debug("Interrupted while waiting for service to complete", e);
      Thread.currentThread().interrupt();
    } catch (final ExecutionException e) {
      LOG.error("Service " + serviceName + " failed to shutdown", e);
    } catch (final TimeoutException e) {
      LOG.error("Service {} did not shut down cleanly", serviceName);
    }
  }

  private void waitForServiceToStart(
      final String serviceName, final CompletableFuture<?> startFuture) {
    while (!startFuture.isDone()) {
      try {
        startFuture.get(60, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for service to start", e);
      } catch (final ExecutionException e) {
        LOG.error("Service " + serviceName + " failed to start", e);
        throw new IllegalStateException(e);
      } catch (final TimeoutException e) {
        LOG.warn("Service {} is taking an unusually long time to start", serviceName);
      }
    }
  }

  private void writePantheonPortsToFile() {
    final Properties properties = new Properties();
    final P2PNetwork network = networkRunner.getNetwork();
    if (networkRunner.getNetwork().isP2pEnabled()) {
      networkRunner
          .getNetwork()
          .getLocalEnode()
          .ifPresent(
              enode -> {
                if (network.isDiscoveryEnabled()) {
                  properties.setProperty(
                      "discovery", String.valueOf(enode.getEffectiveDiscoveryPort()));
                }
                properties.setProperty("p2p", String.valueOf(enode.getListeningPort()));
              });
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

  @VisibleForTesting
  Optional<EnodeURL> getLocalEnode() {
    return networkRunner.getNetwork().getLocalEnode();
  }
}

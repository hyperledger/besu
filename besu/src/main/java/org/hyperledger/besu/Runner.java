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
package org.hyperledger.besu;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLHttpService;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcHttpService;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketService;
import org.hyperledger.besu.ethereum.api.query.AutoTransactionLogBloomCachingService;
import org.hyperledger.besu.ethereum.api.query.TransactionLogBloomCacher;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.stratum.StratumServer;
import org.hyperledger.besu.metrics.prometheus.MetricsService;
import org.hyperledger.besu.nat.NatService;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
  private final CountDownLatch vertxShutdownLatch = new CountDownLatch(1);
  private final CountDownLatch shutdown = new CountDownLatch(1);

  private final NetworkRunner networkRunner;
  private final NatService natService;
  private final Optional<JsonRpcHttpService> jsonRpc;
  private final Optional<GraphQLHttpService> graphQLHttp;
  private final Optional<WebSocketService> websocketRpc;
  private final Optional<MetricsService> metrics;

  private final BesuController<?> besuController;
  private final Path dataDir;
  private final Optional<StratumServer> stratumServer;
  private final Optional<AutoTransactionLogBloomCachingService>
      autoTransactionLogBloomCachingService;

  Runner(
      final Vertx vertx,
      final NetworkRunner networkRunner,
      final NatService natService,
      final Optional<JsonRpcHttpService> jsonRpc,
      final Optional<GraphQLHttpService> graphQLHttp,
      final Optional<WebSocketService> websocketRpc,
      final Optional<StratumServer> stratumServer,
      final Optional<MetricsService> metrics,
      final BesuController<?> besuController,
      final Path dataDir,
      final Optional<TransactionLogBloomCacher> transactionLogBloomCacher,
      final Blockchain blockchain) {
    this.vertx = vertx;
    this.networkRunner = networkRunner;
    this.natService = natService;
    this.graphQLHttp = graphQLHttp;
    this.jsonRpc = jsonRpc;
    this.websocketRpc = websocketRpc;
    this.metrics = metrics;
    this.besuController = besuController;
    this.dataDir = dataDir;
    this.stratumServer = stratumServer;
    this.autoTransactionLogBloomCachingService =
        transactionLogBloomCacher.map(
            cacher -> new AutoTransactionLogBloomCachingService(blockchain, cacher));
  }

  public void start() {
    try {
      LOG.info("Starting Ethereum main loop ... ");
      natService.start();
      networkRunner.start();
      if (networkRunner.getNetwork().isP2pEnabled()) {
        besuController.getSynchronizer().start();
      }
      besuController.getMiningCoordinator().start();
      stratumServer.ifPresent(server -> waitForServiceToStart("stratum", server.start()));
      vertx.setPeriodic(
          TimeUnit.MINUTES.toMillis(1),
          time ->
              besuController.getTransactionPool().getPendingTransactions().evictOldTransactions());
      jsonRpc.ifPresent(service -> waitForServiceToStart("jsonRpc", service.start()));
      graphQLHttp.ifPresent(service -> waitForServiceToStart("graphQLHttp", service.start()));
      websocketRpc.ifPresent(service -> waitForServiceToStart("websocketRpc", service.start()));
      metrics.ifPresent(service -> waitForServiceToStart("metrics", service.start()));
      LOG.info("Ethereum main loop is up.");
      writeBesuPortsToFile();
      writeBesuNetworksToFile();
      autoTransactionLogBloomCachingService.ifPresent(AutoTransactionLogBloomCachingService::start);
    } catch (final Exception ex) {
      LOG.error("Startup failed", ex);
      throw new IllegalStateException(ex);
    }
  }

  public void stop() {
    jsonRpc.ifPresent(service -> waitForServiceToStop("jsonRpc", service.stop()));
    graphQLHttp.ifPresent(service -> waitForServiceToStop("graphQLHttp", service.stop()));
    websocketRpc.ifPresent(service -> waitForServiceToStop("websocketRpc", service.stop()));
    metrics.ifPresent(service -> waitForServiceToStop("metrics", service.stop()));

    besuController.getMiningCoordinator().stop();
    waitForServiceToStop("Mining Coordinator", besuController.getMiningCoordinator()::awaitStop);
    stratumServer.ifPresent(server -> waitForServiceToStop("Stratum", server::stop));
    if (networkRunner.getNetwork().isP2pEnabled()) {
      besuController.getSynchronizer().stop();
      waitForServiceToStop("Synchronizer", besuController.getSynchronizer()::awaitStop);
    }

    networkRunner.stop();
    waitForServiceToStop("Network", networkRunner::awaitStop);
    autoTransactionLogBloomCachingService.ifPresent(AutoTransactionLogBloomCachingService::stop);
    natService.stop();
    besuController.close();
    vertx.close((res) -> vertxShutdownLatch.countDown());
    waitForServiceToStop("Vertx", vertxShutdownLatch::await);
    shutdown.countDown();
  }

  public void awaitStop() {
    try {
      shutdown.await();
    } catch (final InterruptedException e) {
      LOG.debug("Interrupted, exiting", e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    stop();
    awaitStop();
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

  private void waitForServiceToStop(final String serviceName, final SynchronousShutdown shutdown) {
    try {
      shutdown.await();
    } catch (final InterruptedException e) {
      LOG.debug("Interrupted while waiting for service " + serviceName + " to stop", e);
      Thread.currentThread().interrupt();
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

  private void writeBesuPortsToFile() {
    final Properties properties = new Properties();
    if (networkRunner.getNetwork().isP2pEnabled()) {
      networkRunner
          .getNetwork()
          .getLocalEnode()
          .ifPresent(
              enode -> {
                if (enode.getDiscoveryPort().isPresent()) {
                  properties.setProperty(
                      "discovery", String.valueOf(enode.getDiscoveryPort().getAsInt()));
                }
                if (enode.getListeningPort().isPresent()) {
                  properties.setProperty(
                      "p2p", String.valueOf(enode.getListeningPort().getAsInt()));
                }
              });
    }

    if (getJsonRpcPort().isPresent()) {
      properties.setProperty("json-rpc", String.valueOf(getJsonRpcPort().get()));
    }
    if (getGraphQLHttpPort().isPresent()) {
      properties.setProperty("graphql-http", String.valueOf(getGraphQLHttpPort().get()));
    }
    if (getWebsocketPort().isPresent()) {
      properties.setProperty("ws-rpc", String.valueOf(getWebsocketPort().get()));
    }
    if (getMetricsPort().isPresent()) {
      properties.setProperty("metrics", String.valueOf(getMetricsPort().get()));
    }
    // create besu.ports file
    createBesuFile(
        properties, "ports", "This file contains the ports used by the running instance of Besu");
  }

  private void writeBesuNetworksToFile() {
    final Properties properties = new Properties();
    if (networkRunner.getNetwork().isP2pEnabled()) {
      networkRunner
          .getNetwork()
          .getLocalEnode()
          .ifPresent(
              enode -> {
                final String globalIp = natService.queryExternalIPAddress(enode.getIpAsString());
                properties.setProperty("global-ip", globalIp);
                final String localIp = natService.queryLocalIPAddress(enode.getIpAsString());
                properties.setProperty("local-ip", localIp);
              });
    }
    // create besu.networks file
    createBesuFile(
        properties,
        "networks",
        "This file contains the IP Addresses (global and local) used by the running instance of Besu");
  }

  public Optional<Integer> getJsonRpcPort() {
    return jsonRpc.map(service -> service.socketAddress().getPort());
  }

  public Optional<Integer> getGraphQLHttpPort() {
    return graphQLHttp.map(service -> service.socketAddress().getPort());
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

  @FunctionalInterface
  private interface SynchronousShutdown {
    void await() throws InterruptedException;
  }

  private void createBesuFile(
      final Properties properties, final String fileName, final String fileHeader) {
    final File file = new File(dataDir.toFile(), String.format("besu.%s", fileName));
    file.deleteOnExit();

    try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
      properties.store(
          fileOutputStream,
          String.format("%s. This file will be deleted after the node is shutdown.", fileHeader));
    } catch (final Exception e) {
      LOG.warn(String.format("Error writing %s file", fileName), e);
    }
  }
}

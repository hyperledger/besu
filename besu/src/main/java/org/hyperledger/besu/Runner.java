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
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcService;
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcService;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketService;
import org.hyperledger.besu.ethereum.api.query.cache.AutoTransactionLogBloomCachingService;
import org.hyperledger.besu.ethereum.api.query.cache.TransactionLogBloomCacher;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolEvictionService;
import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner;
import org.hyperledger.besu.ethereum.stratum.StratumServer;
import org.hyperledger.besu.ethstats.EthStatsService;
import org.hyperledger.besu.metrics.MetricsService;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Runner implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(Runner.class);

  private final Vertx vertx;
  private final CountDownLatch vertxShutdownLatch = new CountDownLatch(1);
  private final CountDownLatch shutdown = new CountDownLatch(1);

  private final NatService natService;
  private final NetworkRunner networkRunner;
  private final Optional<EthStatsService> ethStatsService;
  private final Optional<GraphQLHttpService> graphQLHttp;
  private final Optional<JsonRpcHttpService> jsonRpc;
  private final Optional<JsonRpcService> engineJsonRpc;
  private final Optional<MetricsService> metrics;
  private final Optional<JsonRpcIpcService> ipcJsonRpc;
  private final Optional<Path> pidPath;
  private final Optional<WebSocketService> webSocketRpc;
  private final TransactionPoolEvictionService transactionPoolEvictionService;

  private final BesuController besuController;
  private final Path dataDir;
  private final Optional<StratumServer> stratumServer;
  private final Optional<AutoTransactionLogBloomCachingService>
      autoTransactionLogBloomCachingService;

  Runner(
      final Vertx vertx,
      final NetworkRunner networkRunner,
      final NatService natService,
      final Optional<JsonRpcHttpService> jsonRpc,
      final Optional<JsonRpcService> engineJsonRpc,
      final Optional<GraphQLHttpService> graphQLHttp,
      final Optional<WebSocketService> webSocketRpc,
      final Optional<JsonRpcIpcService> ipcJsonRpc,
      final Optional<StratumServer> stratumServer,
      final Optional<MetricsService> metrics,
      final Optional<EthStatsService> ethStatsService,
      final BesuController besuController,
      final Path dataDir,
      final Optional<Path> pidPath,
      final Optional<TransactionLogBloomCacher> transactionLogBloomCacher,
      final Blockchain blockchain) {
    this.vertx = vertx;
    this.networkRunner = networkRunner;
    this.natService = natService;
    this.graphQLHttp = graphQLHttp;
    this.pidPath = pidPath;
    this.jsonRpc = jsonRpc;
    this.engineJsonRpc = engineJsonRpc;
    this.webSocketRpc = webSocketRpc;
    this.ipcJsonRpc = ipcJsonRpc;
    this.metrics = metrics;
    this.ethStatsService = ethStatsService;
    this.besuController = besuController;
    this.dataDir = dataDir;
    this.stratumServer = stratumServer;
    this.autoTransactionLogBloomCachingService =
        transactionLogBloomCacher.map(
            cacher -> new AutoTransactionLogBloomCachingService(blockchain, cacher));
    this.transactionPoolEvictionService =
        new TransactionPoolEvictionService(vertx, besuController.getTransactionPool());
  }

  public void startExternalServices() {
    LOG.info("Starting external services ... ");
    metrics.ifPresent(service -> waitForServiceToStart("metrics", service.start()));

    jsonRpc.ifPresent(service -> waitForServiceToStart("jsonRpc", service.start()));
    engineJsonRpc.ifPresent(service -> waitForServiceToStart("engineJsonRpc", service.start()));
    graphQLHttp.ifPresent(service -> waitForServiceToStart("graphQLHttp", service.start()));
    webSocketRpc.ifPresent(service -> waitForServiceToStart("websocketRpc", service.start()));
    ipcJsonRpc.ifPresent(
        service ->
            waitForServiceToStart(
                "ipcJsonRpc", service.start().toCompletionStage().toCompletableFuture()));
    stratumServer.ifPresent(server -> waitForServiceToStart("stratum", server.start()));
    autoTransactionLogBloomCachingService.ifPresent(AutoTransactionLogBloomCachingService::start);
    ethStatsService.ifPresent(EthStatsService::start);
  }

  public void startEthereumMainLoop() {
    try {
      LOG.info("Starting Ethereum main loop ... ");
      natService.start();
      networkRunner.start();
      if (networkRunner.getNetwork().isP2pEnabled()) {
        besuController.getSynchronizer().start();
      }
      besuController.getMiningCoordinator().start();
      transactionPoolEvictionService.start();

      LOG.info("Ethereum main loop is up.");
      // we write these values to disk to be able to access them during the acceptance tests
      writeBesuPortsToFile();
      writeBesuNetworksToFile();
      writePidFile();
    } catch (final Exception ex) {
      LOG.error("unable to start main loop", ex);
      throw new IllegalStateException("Startup failed", ex);
    }
  }

  public void stop() {
    transactionPoolEvictionService.stop();
    jsonRpc.ifPresent(service -> waitForServiceToStop("jsonRpc", service.stop()));
    engineJsonRpc.ifPresent(service -> waitForServiceToStop("engineJsonRpc", service.stop()));
    graphQLHttp.ifPresent(service -> waitForServiceToStop("graphQLHttp", service.stop()));
    webSocketRpc.ifPresent(service -> waitForServiceToStop("websocketRpc", service.stop()));
    ipcJsonRpc.ifPresent(
        service ->
            waitForServiceToStop(
                "ipcJsonRpc", service.stop().toCompletionStage().toCompletableFuture()));
    metrics.ifPresent(service -> waitForServiceToStop("metrics", service.stop()));
    ethStatsService.ifPresent(EthStatsService::stop);
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
    do {
      try {
        startFuture.get(60, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for service to start", e);
      } catch (final ExecutionException e) {
        throw new IllegalStateException("Service " + serviceName + " failed to start", e);
      } catch (final TimeoutException e) {
        LOG.warn("Service {} is taking an unusually long time to start", serviceName);
      }
    } while (!startFuture.isDone());
  }

  private void writeBesuPortsToFile() {
    final Properties properties = new Properties();
    if (networkRunner.getNetwork().isP2pEnabled()) {
      networkRunner
          .getNetwork()
          .getLocalEnode()
          .ifPresent(
              enode -> {
                enode
                    .getDiscoveryPort()
                    .ifPresent(
                        discoveryPort ->
                            properties.setProperty("discovery", String.valueOf(discoveryPort)));
                enode
                    .getListeningPort()
                    .ifPresent(
                        listeningPort ->
                            properties.setProperty("p2p", String.valueOf(listeningPort)));
              });
    }

    Optional<Integer> port = getJsonRpcPort();
    if (port.isPresent()) {
      properties.setProperty("json-rpc", String.valueOf(port.get()));
    }
    port = getGraphQLHttpPort();
    if (port.isPresent()) {
      properties.setProperty("graphql-http", String.valueOf(port.get()));
    }
    port = getWebSocketPort();
    if (port.isPresent()) {
      properties.setProperty("ws-rpc", String.valueOf(port.get()));
    }
    port = getMetricsPort();
    if (port.isPresent()) {
      properties.setProperty("metrics", String.valueOf(port.get()));
    }
    port = getEngineJsonRpcPort();
    if (port.isPresent()) {
      properties.setProperty("engine-json-rpc", String.valueOf(port.get()));
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

  private void writePidFile() {
    pidPath.ifPresent(
        path -> {
          String pid = "";
          try {
            pid = Long.toString(ProcessHandle.current().pid());
          } catch (Throwable t) {
            LOG.error("Error retrieving PID", t);
          }
          try {
            Files.write(
                path,
                pid.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            path.toFile().deleteOnExit();
          } catch (IOException e) {
            LOG.error("Error writing PID file", e);
          }
        });
  }

  public Optional<Integer> getJsonRpcPort() {
    return jsonRpc.map(service -> service.socketAddress().getPort());
  }

  public Optional<Integer> getEngineJsonRpcPort() {
    return engineJsonRpc.map(service -> service.socketAddress().getPort());
  }

  public Optional<Integer> getGraphQLHttpPort() {
    return graphQLHttp.map(service -> service.socketAddress().getPort());
  }

  public Optional<Integer> getWebSocketPort() {
    return webSocketRpc.map(service -> service.socketAddress().getPort());
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

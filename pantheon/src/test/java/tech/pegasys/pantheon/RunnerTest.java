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

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.cli.EthNetworkConfig.DEV_NETWORK_ID;
import static tech.pegasys.pantheon.cli.NetworkName.DEV;
import static tech.pegasys.pantheon.controller.KeyPairUtil.loadKeyPair;

import tech.pegasys.pantheon.cli.EthNetworkConfig;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.controller.MainnetPantheonController;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.BlockSyncTestUtils;
import tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.eth.EthereumWireProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.RocksDbStorageProvider;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link Runner}. */
public final class RunnerTest {

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void fullSyncFromGenesis() throws Exception {
    syncFromGenesis(SyncMode.FULL);
  }

  @Test
  public void fastSyncFromGenesis() throws Exception {
    syncFromGenesis(SyncMode.FAST);
  }

  private void syncFromGenesis(final SyncMode mode) throws Exception {
    final Path dataDirAhead = temp.newFolder().toPath();
    final Path dbAhead = dataDirAhead.resolve("database");
    final int blockCount = 500;
    final KeyPair aheadDbNodeKeys = loadKeyPair(dbAhead);
    final SynchronizerConfiguration syncConfigAhead =
        SynchronizerConfiguration.builder().syncMode(SyncMode.FULL).build();
    final MetricsSystem noOpMetricsSystem = new NoOpMetricsSystem();
    final int networkId = 2929;

    // Setup state with block data
    try (final PantheonController<Void> controller =
        MainnetPantheonController.init(
            createKeyValueStorageProvider(dbAhead),
            GenesisConfigFile.mainnet(),
            MainnetProtocolSchedule.create(),
            syncConfigAhead,
            EthereumWireProtocolConfiguration.defaultConfig(),
            new MiningParametersTestBuilder().enabled(false).build(),
            networkId,
            aheadDbNodeKeys,
            PrivacyParameters.DEFAULT,
            dataDirAhead,
            noOpMetricsSystem,
            TestClock.fixed(),
            PendingTransactions.MAX_PENDING_TRANSACTIONS)) {
      setupState(blockCount, controller.getProtocolSchedule(), controller.getProtocolContext());
    }

    // Setup Runner with blocks
    final PantheonController<Void> controllerAhead =
        MainnetPantheonController.init(
            createKeyValueStorageProvider(dbAhead),
            GenesisConfigFile.mainnet(),
            MainnetProtocolSchedule.create(),
            syncConfigAhead,
            EthereumWireProtocolConfiguration.defaultConfig(),
            new MiningParametersTestBuilder().enabled(false).build(),
            networkId,
            aheadDbNodeKeys,
            PrivacyParameters.DEFAULT,
            dataDirAhead,
            noOpMetricsSystem,
            TestClock.fixed(),
            PendingTransactions.MAX_PENDING_TRANSACTIONS);
    final String listenHost = InetAddress.getLoopbackAddress().getHostAddress();
    final JsonRpcConfiguration aheadJsonRpcConfiguration = jsonRpcConfiguration();
    final WebSocketConfiguration aheadWebSocketConfiguration = wsRpcConfiguration();
    final MetricsConfiguration aheadMetricsConfiguration = metricsConfiguration();
    final RunnerBuilder runnerBuilder =
        new RunnerBuilder()
            .vertx(Vertx.vertx())
            .discovery(true)
            .discoveryHost(listenHost)
            .discoveryPort(0)
            .maxPeers(3)
            .metricsSystem(noOpMetricsSystem)
            .bannedNodeIds(emptySet())
            .staticNodes(emptySet());

    Runner runnerBehind = null;
    final Runner runnerAhead =
        runnerBuilder
            .pantheonController(controllerAhead)
            .ethNetworkConfig(EthNetworkConfig.getNetworkConfig(DEV))
            .jsonRpcConfiguration(aheadJsonRpcConfiguration)
            .webSocketConfiguration(aheadWebSocketConfiguration)
            .metricsConfiguration(aheadMetricsConfiguration)
            .dataDir(dbAhead)
            .build();
    try {

      runnerAhead.start();

      final SynchronizerConfiguration syncConfigBehind =
          SynchronizerConfiguration.builder()
              .syncMode(mode)
              .fastSyncPivotDistance(5)
              .fastSyncMinimumPeerCount(1)
              .fastSyncMaximumPeerWaitTime(Duration.ofSeconds(1))
              .build();
      final Path dataDirBehind = temp.newFolder().toPath();
      final JsonRpcConfiguration behindJsonRpcConfiguration = jsonRpcConfiguration();
      final WebSocketConfiguration behindWebSocketConfiguration = wsRpcConfiguration();
      final MetricsConfiguration behindMetricsConfiguration = metricsConfiguration();

      // Setup runner with no block data
      final PantheonController<Void> controllerBehind =
          MainnetPantheonController.init(
              new InMemoryStorageProvider(),
              GenesisConfigFile.mainnet(),
              MainnetProtocolSchedule.create(),
              syncConfigBehind,
              EthereumWireProtocolConfiguration.defaultConfig(),
              new MiningParametersTestBuilder().enabled(false).build(),
              networkId,
              KeyPair.generate(),
              PrivacyParameters.DEFAULT,
              dataDirBehind,
              noOpMetricsSystem,
              TestClock.fixed(),
              PendingTransactions.MAX_PENDING_TRANSACTIONS);
      final Peer advertisedPeer = runnerAhead.getAdvertisedPeer().get();
      final EthNetworkConfig behindEthNetworkConfiguration =
          new EthNetworkConfig(
              EthNetworkConfig.jsonConfig(DEV),
              DEV_NETWORK_ID,
              Collections.singletonList(URI.create(advertisedPeer.getEnodeURLString())));
      runnerBehind =
          runnerBuilder
              .pantheonController(controllerBehind)
              .ethNetworkConfig(behindEthNetworkConfiguration)
              .jsonRpcConfiguration(behindJsonRpcConfiguration)
              .webSocketConfiguration(behindWebSocketConfiguration)
              .metricsConfiguration(behindMetricsConfiguration)
              .dataDir(temp.newFolder().toPath())
              .metricsSystem(noOpMetricsSystem)
              .build();

      runnerBehind.start();

      final int behindJsonRpcPort = runnerBehind.getJsonRpcPort().get();
      final Call.Factory client = new OkHttpClient();
      Awaitility.await()
          .ignoreExceptions()
          .atMost(5L, TimeUnit.MINUTES)
          .untilAsserted(
              () -> {
                final String baseUrl = String.format("http://%s:%s", listenHost, behindJsonRpcPort);
                try (final Response resp =
                    client
                        .newCall(
                            new Request.Builder()
                                .post(
                                    RequestBody.create(
                                        MediaType.parse("application/json; charset=utf-8"),
                                        "{\"jsonrpc\":\"2.0\",\"id\":"
                                            + Json.encode(7)
                                            + ",\"method\":\"eth_blockNumber\"}"))
                                .url(baseUrl)
                                .build())
                        .execute()) {

                  assertThat(resp.code()).isEqualTo(200);
                  final Response syncingResp =
                      client
                          .newCall(
                              new Request.Builder()
                                  .post(
                                      RequestBody.create(
                                          MediaType.parse("application/json; charset=utf-8"),
                                          "{\"jsonrpc\":\"2.0\",\"id\":"
                                              + Json.encode(7)
                                              + ",\"method\":\"eth_syncing\"}"))
                                  .url(baseUrl)
                                  .build())
                          .execute();
                  assertThat(syncingResp.code()).isEqualTo(200);

                  final int currentBlock =
                      UInt256.fromHexString(
                              new JsonObject(resp.body().string()).getString("result"))
                          .toInt();
                  System.out.println("******current block  " + currentBlock);
                  if (currentBlock < blockCount) {
                    // if not yet at blockCount, we should get a sync result from eth_syncing
                    final int syncResultCurrentBlock =
                        UInt256.fromHexString(
                                new JsonObject(syncingResp.body().string())
                                    .getJsonObject("result")
                                    .getString("currentBlock"))
                            .toInt();
                    assertThat(syncResultCurrentBlock).isLessThan(blockCount);
                  }
                  assertThat(currentBlock).isEqualTo(blockCount);
                  resp.close();

                  // when we have synced to blockCount, eth_syncing should return false
                  final boolean syncResult =
                      new JsonObject(syncingResp.body().string()).getBoolean("result");
                  assertThat(syncResult).isFalse();
                  syncingResp.close();
                }
              });

      final Future<Void> future = Future.future();
      final HttpClient httpClient = Vertx.vertx().createHttpClient();
      httpClient.websocket(
          runnerBehind.getWebsocketPort().get(),
          WebSocketConfiguration.DEFAULT_WEBSOCKET_HOST,
          "/",
          ws -> {
            ws.write(
                Buffer.buffer(
                    "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}"));
            ws.handler(
                buffer -> {
                  final boolean matches =
                      buffer.toString().equals("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"0x0\"}");
                  if (matches) {
                    future.complete();
                  } else {
                    future.fail("Unexpected result");
                  }
                });
          });
      Awaitility.await()
          .catchUncaughtExceptions()
          .atMost(5L, TimeUnit.MINUTES)
          .until(future::isComplete);
    } finally {
      if (runnerBehind != null) {
        runnerBehind.close();
        runnerBehind.awaitStop();
      }
      runnerAhead.close();
      runnerAhead.awaitStop();
    }
  }

  private StorageProvider createKeyValueStorageProvider(final Path dbAhead) throws IOException {
    return RocksDbStorageProvider.create(
        new RocksDbConfiguration.Builder().databaseDir(dbAhead).build(), new NoOpMetricsSystem());
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(true);
    configuration.setHostsWhitelist(Collections.singletonList("*"));
    return configuration;
  }

  private WebSocketConfiguration wsRpcConfiguration() {
    final WebSocketConfiguration configuration = WebSocketConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(true);
    configuration.setHostsWhitelist(Collections.singletonList("*"));
    return configuration;
  }

  private MetricsConfiguration metricsConfiguration() {
    final MetricsConfiguration configuration = MetricsConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(false);
    return configuration;
  }

  private static void setupState(
      final int count,
      final ProtocolSchedule<Void> protocolSchedule,
      final ProtocolContext<Void> protocolContext) {
    final List<Block> blocks = BlockSyncTestUtils.firstBlocks(count + 1);

    for (int i = 1; i < count + 1; ++i) {
      final Block block = blocks.get(i);
      final ProtocolSpec<Void> protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
      final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
      final boolean result =
          blockImporter.importBlock(protocolContext, block, HeaderValidationMode.FULL);
      if (!result) {
        throw new IllegalStateException("Unable to import block " + block.getHeader().getNumber());
      }
    }
  }
}

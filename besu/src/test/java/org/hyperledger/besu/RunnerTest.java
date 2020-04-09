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

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.config.EthNetworkConfig.DEV_NETWORK_ID;
import static org.hyperledger.besu.cli.config.NetworkName.DEV;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.GasLimitCalculator;
import org.hyperledger.besu.controller.MainnetBesuControllerBuilder;
import org.hyperledger.besu.crypto.BouncyCastleNodeKey;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.BlockSyncTestUtils;
import org.hyperledger.besu.ethereum.core.InMemoryStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParametersTestBuilder;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tuweni.units.bigints.UInt256;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link Runner}. */
public final class RunnerTest {

  private static final int MAX_OPEN_FILES = 1024;
  private static final long CACHE_CAPACITY = 8388608;
  private static final int MAX_BACKGROUND_COMPACTIONS = 4;
  private static final int BACKGROUND_THREAD_COUNT = 4;

  private Vertx vertx;

  @Before
  public void initVertx() {
    vertx = Vertx.vertx();
  }

  @After
  public void stopVertx() {
    vertx.close();
  }

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void getFixedNodes() {
    final EnodeURL staticNode =
        EnodeURL.fromString(
            "enode://8f4b88336cc40ef2516d8b27df812e007fb2384a61e93635f1899051311344f3dcdbb49a4fe49a79f66d2f589a9f282e8cc4f1d7381e8ef7e4fcc6b0db578c77@127.0.0.1:30301");
    final EnodeURL bootnode =
        EnodeURL.fromString(
            "enode://8f4b88336cc40ef2516d8b27df812e007fb2384a61e93635f1899051311344f3dcdbb49a4fe49a79f66d2f589a9f282e8cc4f1d7381e8ef7e4fcc6b0db578c77@127.0.0.1:30302");
    final List<EnodeURL> bootnodes = new ArrayList<>();
    bootnodes.add(bootnode);
    final Collection<EnodeURL> staticNodes = new ArrayList<>();
    staticNodes.add(staticNode);
    final Collection<EnodeURL> fixedNodes = RunnerBuilder.getFixedNodes(bootnodes, staticNodes);
    assertThat(fixedNodes).containsExactlyInAnyOrder(staticNode, bootnode);
    // bootnodes should be unchanged
    assertThat(bootnodes).containsExactly(bootnode);
  }

  @Test
  public void fullSyncFromGenesis() throws Exception {
    syncFromGenesis(SyncMode.FULL, GenesisConfigFile.mainnet());
  }

  @Test
  public void fastSyncFromGenesis() throws Exception {
    syncFromGenesis(SyncMode.FAST, getFastSyncGenesis());
  }

  private void syncFromGenesis(final SyncMode mode, final GenesisConfigFile genesisConfig)
      throws Exception {
    final Path dataDirAhead = temp.newFolder().toPath();
    final Path dbAhead = dataDirAhead.resolve("database");
    final int blockCount = 500;
    final NodeKey aheadDbNodeKey = new BouncyCastleNodeKey(KeyPairUtil.loadKeyPair(dbAhead));
    final SynchronizerConfiguration syncConfigAhead =
        SynchronizerConfiguration.builder().syncMode(SyncMode.FULL).build();
    final ObservableMetricsSystem noOpMetricsSystem = new NoOpMetricsSystem();
    final BigInteger networkId = BigInteger.valueOf(2929);

    // Setup state with block data
    try (final BesuController<Void> controller =
        new MainnetBesuControllerBuilder()
            .genesisConfigFile(genesisConfig)
            .synchronizerConfiguration(syncConfigAhead)
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .dataDirectory(dataDirAhead)
            .networkId(networkId)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKey(aheadDbNodeKey)
            .metricsSystem(noOpMetricsSystem)
            .privacyParameters(PrivacyParameters.DEFAULT)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .storageProvider(createKeyValueStorageProvider(dataDirAhead, dbAhead))
            .targetGasLimit(GasLimitCalculator.DEFAULT)
            .build()) {
      setupState(blockCount, controller.getProtocolSchedule(), controller.getProtocolContext());
    }

    // Setup Runner with blocks
    final BesuController<Void> controllerAhead =
        new MainnetBesuControllerBuilder()
            .genesisConfigFile(genesisConfig)
            .synchronizerConfiguration(syncConfigAhead)
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .dataDirectory(dataDirAhead)
            .networkId(networkId)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKey(aheadDbNodeKey)
            .metricsSystem(noOpMetricsSystem)
            .privacyParameters(PrivacyParameters.DEFAULT)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .storageProvider(createKeyValueStorageProvider(dataDirAhead, dbAhead))
            .targetGasLimit(GasLimitCalculator.DEFAULT)
            .build();
    final String listenHost = InetAddress.getLoopbackAddress().getHostAddress();
    final JsonRpcConfiguration aheadJsonRpcConfiguration = jsonRpcConfiguration();
    final GraphQLConfiguration aheadGraphQLConfiguration = graphQLConfiguration();
    final WebSocketConfiguration aheadWebSocketConfiguration = wsRpcConfiguration();
    final MetricsConfiguration aheadMetricsConfiguration = metricsConfiguration();
    final RunnerBuilder runnerBuilder =
        new RunnerBuilder()
            .vertx(vertx)
            .discovery(true)
            .p2pAdvertisedHost(listenHost)
            .p2pListenPort(0)
            .maxPeers(3)
            .metricsSystem(noOpMetricsSystem)
            .staticNodes(emptySet());

    Runner runnerBehind = null;
    final Runner runnerAhead =
        runnerBuilder
            .besuController(controllerAhead)
            .ethNetworkConfig(EthNetworkConfig.getNetworkConfig(DEV))
            .jsonRpcConfiguration(aheadJsonRpcConfiguration)
            .graphQLConfiguration(aheadGraphQLConfiguration)
            .webSocketConfiguration(aheadWebSocketConfiguration)
            .metricsConfiguration(aheadMetricsConfiguration)
            .dataDir(dbAhead)
            .besuPluginContext(new BesuPluginContextImpl())
            .build();
    try {

      runnerAhead.start();

      final SynchronizerConfiguration syncConfigBehind =
          SynchronizerConfiguration.builder()
              .syncMode(mode)
              .fastSyncPivotDistance(5)
              .fastSyncMinimumPeerCount(1)
              .build();
      final Path dataDirBehind = temp.newFolder().toPath();
      final JsonRpcConfiguration behindJsonRpcConfiguration = jsonRpcConfiguration();
      final GraphQLConfiguration behindGraphQLConfiguration = graphQLConfiguration();
      final WebSocketConfiguration behindWebSocketConfiguration = wsRpcConfiguration();
      final MetricsConfiguration behindMetricsConfiguration = metricsConfiguration();

      // Setup runner with no block data
      final BesuController<Void> controllerBehind =
          new MainnetBesuControllerBuilder()
              .genesisConfigFile(genesisConfig)
              .synchronizerConfiguration(syncConfigBehind)
              .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
              .dataDirectory(dataDirBehind)
              .networkId(networkId)
              .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
              .nodeKey(BouncyCastleNodeKey.generate())
              .storageProvider(new InMemoryStorageProvider())
              .metricsSystem(noOpMetricsSystem)
              .privacyParameters(PrivacyParameters.DEFAULT)
              .clock(TestClock.fixed())
              .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
              .targetGasLimit(GasLimitCalculator.DEFAULT)
              .build();
      final EnodeURL enode = runnerAhead.getLocalEnode().get();
      final EthNetworkConfig behindEthNetworkConfiguration =
          new EthNetworkConfig(
              EthNetworkConfig.jsonConfig(DEV), DEV_NETWORK_ID, Collections.singletonList(enode));
      runnerBehind =
          runnerBuilder
              .besuController(controllerBehind)
              .ethNetworkConfig(behindEthNetworkConfiguration)
              .jsonRpcConfiguration(behindJsonRpcConfiguration)
              .graphQLConfiguration(behindGraphQLConfiguration)
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
                          .intValue();
                  if (currentBlock < blockCount) {
                    // if not yet at blockCount, we should get a sync result from eth_syncing
                    final int syncResultCurrentBlock =
                        UInt256.fromHexString(
                                new JsonObject(syncingResp.body().string())
                                    .getJsonObject("result")
                                    .getString("currentBlock"))
                            .intValue();
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
      final HttpClient httpClient = vertx.createHttpClient();
      httpClient.websocket(
          runnerBehind.getWebsocketPort().get(),
          WebSocketConfiguration.DEFAULT_WEBSOCKET_HOST,
          "/",
          ws -> {
            ws.writeTextMessage(
                "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}");
            ws.textMessageHandler(
                payload -> {
                  final boolean matches =
                      payload.equals("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"0x0\"}");
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

  private GenesisConfigFile getFastSyncGenesis() {
    final ObjectNode jsonNode = GenesisConfigFile.mainnetJsonNode();
    final Optional<ObjectNode> configNode = JsonUtil.getObjectNode(jsonNode, "config");
    configNode.ifPresent(
        (node) -> {
          // Clear DAO block so that inability to validate DAO block won't interfere with fast sync
          node.remove("daoForkBlock");
          node.put("daoForkSupport", false);
        });
    return GenesisConfigFile.fromConfig(jsonNode);
  }

  private StorageProvider createKeyValueStorageProvider(final Path dataDir, final Path dbDir) {
    return new KeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () ->
                    new RocksDBFactoryConfiguration(
                        MAX_OPEN_FILES,
                        MAX_BACKGROUND_COMPACTIONS,
                        BACKGROUND_THREAD_COUNT,
                        CACHE_CAPACITY),
                Arrays.asList(KeyValueSegmentIdentifier.values()),
                RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS))
        .withCommonConfiguration(new BesuConfigurationImpl(dataDir, dbDir))
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(true);
    configuration.setHostsWhitelist(Collections.singletonList("*"));
    return configuration;
  }

  private GraphQLConfiguration graphQLConfiguration() {
    final GraphQLConfiguration configuration = GraphQLConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(false);
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
    return MetricsConfiguration.builder().enabled(false).port(0).build();
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

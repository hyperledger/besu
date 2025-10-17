/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.cli;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.config.NetworkName.EPHEMERY;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_ENABLE_READ_CACHE_FOR_SNAPSHOTS;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_IS_HIGH_SPEC;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.config.MergeConfiguration;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.MainnetBesuControllerBuilder;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.ImmutableInProcessRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.services.TransactionValidatorServiceImpl;
import org.hyperledger.besu.testutil.TestClock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Vertx;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* for running tests, comment @Disabled annotation and run each test on a singe-run
The setup method access the DB concurrently. DB lock doesn't allow for a concurrent access. */

/** Tests for {@link BesuCommand}. */
@ExtendWith(MockitoExtension.class)
@Disabled("needs to run each test on a single-run")
public class EphemeryTest extends CommandTestAbstract {
  private static final Logger LOG = LoggerFactory.getLogger(EphemeryTest.class);

  private TestBesuCommand besuCommand;
  private Runner runner;
  private final Vertx vertx = Vertx.vertx();
  private final ObservableMetricsSystem noOpMetricsSystem = new NoOpMetricsSystem();
  final SynchronizerConfiguration syncConfig =
      SynchronizerConfiguration.builder()
          .syncMode(SyncMode.SNAP)
          .isPeerTaskSystemEnabled(false)
          .build();

  Field cycleIdField;
  BigInteger initialCycleId;

  String ephemeryDataPathPrefix = "Ephemery-data-chain";
  public static final List<EnodeURL> EPHEMERY_BOOT_NODES =
      Collections.unmodifiableList(
          Stream.of(
                  "enode://50a54ecbd2175497640bcf46a25bbe9bb4fae51d7cc2a29ef4947a7ee17496cf39a699b7fe6b703ed0feb9dbaae7e44fc3827fcb7435ca9ac6de4daa4d983b3d@137.74.203.240:30303",
                  "enode://0f2c301a9a3f9fa2ccfa362b79552c052905d8c2982f707f46cd29ece5a9e1c14ecd06f4ac951b228f059a43c6284a1a14fce709e8976cac93b50345218bf2e9@135.181.140.168:30343")
              .map(EnodeURLImpl::fromString)
              .collect(toList()));
  private Path dataDir;
  private Path db;
  private Path pidPath;
  private NodeKey dbNodeKey;
  private final MiningConfiguration miningParameters = MiningConfiguration.newDefault();
  private final DataStorageConfiguration dataStorageConfiguration =
      DataStorageConfiguration.DEFAULT_FOREST_CONFIG;

  @AfterEach
  public void stop() {
    if (vertx != null) {
      vertx.close();
    }
  }

  @BeforeEach
  public void setUp() {

    besuCommand = parseCommand("--network", "ephemery");
    try {

      assertThat(besuCommand.dataPath.toString()).contains(ephemeryDataPathPrefix);

      initDataPaths();

      storageProvider =
          createKeyValueStorageProvider(dataDir, db, dataStorageConfiguration, miningParameters);

      mockController = setController();
      runner = setRunner();
      runner.startExternalServices();
      runner.startEthereumMainLoop();

      when(besuCommand.buildController()).thenReturn(mockController);
      when(besuCommand.buildRunner()).thenReturn(runner);

      cycleIdField = BesuCommand.class.getDeclaredField("ephemeryNextCycleId");
      cycleIdField.setAccessible(true);
      initialCycleId = (BigInteger) cycleIdField.get(besuCommand);
      besuCommand.clearAllocatedPorts();
      MergeConfiguration.setMergeEnabled(false);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  // this test might need some time to pass
  public void testPeersShouldStartOverAfterRestart() throws Exception {
    Awaitility.await()
        .atMost(90, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(runner.getP2PNetwork().isP2pEnabled()).isTrue();
              List<PeerConnection> discoveredPeers =
                  runner.getP2PNetwork().getPeers().stream().toList();
              long peerCount = discoveredPeers.size();

              LOG.info("cycle1 peer count: {}", peerCount);
              for (int i = 0; i < peerCount; i++) {
                LOG.info("cycle1 peer data : {}", discoveredPeers.get(i).getPeer());
              }
              assertThat(peerCount).isGreaterThanOrEqualTo(1);
            });

    // restart Ephemery
    runner.stopEphemery(besuCommand);
    runner.startEphemery(besuCommand);

    mockController = setController();
    runner = setRunner();
    runner.startExternalServices();
    runner.startEthereumMainLoop();
    when(besuCommand.buildController()).thenReturn(mockController);
    when(besuCommand.buildRunner()).thenReturn(runner);

    Awaitility.await()
        .atMost(180, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(runner.getP2PNetwork().isP2pEnabled()).isTrue();
              List<PeerConnection> discoveredPeers =
                  runner.getP2PNetwork().getPeers().stream().toList();
              long peerCount2 = discoveredPeers.size();

              LOG.info("cycle2 peer count : {}", peerCount2);
              for (int i = 0; i < peerCount2; i++) {
                LOG.info("cycle2 peer data : {}", discoveredPeers.get(i).getPeer());
              }
              assertThat(peerCount2).isGreaterThanOrEqualTo(1);
            });
  }

  @Test
  public void testStopEphemery() throws Exception {
    runner.stopEphemery(besuCommand);
    assertThat(besuCommand.dataPath.toString()).doesNotContain(ephemeryDataPathPrefix);
    assertThat(initialCycleId)
        .isEqualTo(besuCommand.getGenesisConfigOptions().getChainId().get().add(BigInteger.ONE));
    assertThat(runner.getP2PNetwork().isStopped()).isTrue();
  }

  @Test
  public void testStopServices() {
    runner.stopServices();
    assertThat(runner.getP2PNetwork().isStopped()).isTrue();
  }

  @Test
  public void testRestartEphemery() throws Exception {
    runner.stopEphemery(besuCommand);
    runner.startEphemery(besuCommand);
    assertThat(besuCommand.dataPath.toString()).contains(ephemeryDataPathPrefix);
    assertThat(initialCycleId)
        .isEqualTo(besuCommand.getGenesisConfigOptions().getChainId().get().add(BigInteger.ONE));
    assertThat(runner.getP2PNetwork().isP2pEnabled()).isTrue();
  }

  @Test
  public void testInitialProcessWithEphemeryNetwork() throws Exception {
    runner.stopEphemery(besuCommand);
    besuCommand.initialProcess();

    assertThat(besuCommand.dataPath.toString()).contains(ephemeryDataPathPrefix);
    assertThat(initialCycleId)
        .isEqualTo(besuCommand.getGenesisConfigOptions().getChainId().get().add(BigInteger.ONE));
  }

  @Test
  public void testScheduleEphemeryRestartIsTriggered() {

    long lastGenesisTimestamp = besuCommand.getGenesisConfigSupplier().get().getTimestamp();
    when(besuCommand.buildRunner()).thenReturn(mockRunner);
    ArgumentCaptor<Long> timestampCaptor = ArgumentCaptor.forClass(Long.class);

    verify(mockRunner).scheduleEphemeryRestart(any(), timestampCaptor.capture());
    assertThat(lastGenesisTimestamp).isEqualTo(timestampCaptor.getValue());
  }

  @Test
  public void testPeersCountShouldBeMoreThanZeroWhileRunning() {
    getPeers(90, 1, 1);
  }

  @Test
  public void testPeersCountShouldNotBeMoreThanZeroWhenStoppedRunning()
      throws IOException, InterruptedException {
    runner.stopEphemery(besuCommand);
    assertThrows(
        ConditionTimeoutException.class,
        () -> {
          getPeers(30, 1, 1);
        });
  }

  @Test
  public void testPeersCountShouldBeMoreThanZeroBeforeEphemeryStopButNotAfter() throws Exception {
    getPeers(90, 1, 1);
    runner.stopEphemery(besuCommand);
    assertThrows(
        ConditionTimeoutException.class,
        () -> {
          getPeers(30, 1, 2);
        });
  }

  @Test
  void testClearAllocatedPorts() throws NoSuchFieldException, IllegalAccessException, IOException {
    Field portsField = BesuCommand.class.getDeclaredField("allocatedPorts");
    portsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<Integer> allocatedPorts = (Set<Integer>) portsField.get(besuCommand);

    // Add some ports
    allocatedPorts.add(8545);
    allocatedPorts.add(30303);
    assertThat(allocatedPorts).hasSizeGreaterThanOrEqualTo(2);
    besuCommand.clearAllocatedPorts();

    assertThat(allocatedPorts).isEmpty();
  }

  @Test
  void testSetDataPathToParent() throws IOException {
    Path childPath = besuCommand.dataPath.resolve("parent").resolve("child");
    Files.createDirectories(childPath);
    besuCommand.dataPath = childPath;

    besuCommand.setDataPathToParent();

    assertThat(besuCommand.dataPath).isEqualTo(childPath.getParent());
  }

  private void getPeers(final long timeout, final long leastExpectedPeers, final int cycle) {
    Awaitility.await()
        .atMost(timeout, TimeUnit.SECONDS)
        .pollInterval(1, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              assertThat(runner.getP2PNetwork().isP2pEnabled()).isTrue();
              List<PeerConnection> discoveredPeers =
                  runner.getP2PNetwork().getPeers().stream().toList();
              long peerCount = discoveredPeers.size();

              LOG.info("cycle {} peer count: {}", cycle, peerCount);
              for (int i = 0; i < peerCount; i++) {
                LOG.info("cycle {} peer data : {}", cycle, discoveredPeers.get(i).getPeer());
              }
              assertThat(peerCount).isGreaterThanOrEqualTo((leastExpectedPeers));
            });
  }

  private BesuController setController() {
    try {
      mockController =
          getController(
              getFastSyncGenesis(EPHEMERY),
              syncConfig,
              dataDir,
              dbNodeKey,
              storageProvider,
              noOpMetricsSystem,
              miningParameters,
              EPHEMERY.getNetworkId());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return mockController;
  }

  private Runner setRunner() {
    final RunnerBuilder runnerBuilder =
        new RunnerBuilder()
            .vertx(vertx)
            .discoveryEnabled(true)
            .p2pAdvertisedHost(besuCommand.p2PDiscoveryConfig.p2pHost())
            .p2pListenPort(besuCommand.p2PDiscoveryConfig.p2pPort())
            .metricsSystem(noOpMetricsSystem)
            .permissioningService(new PermissioningServiceImpl())
            .staticNodes(EPHEMERY_BOOT_NODES)
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .rpcEndpointService(new RpcEndpointServiceImpl())
            .apiConfiguration(ImmutableApiConfiguration.builder().build())
            .transactionValidatorService(new TransactionValidatorServiceImpl());

    runner =
        runnerBuilder
            .besuController(mockController)
            .ethNetworkConfig(EthNetworkConfig.getNetworkConfig(EPHEMERY))
            .jsonRpcConfiguration(jsonRpcConfiguration())
            .graphQLConfiguration(graphQLConfiguration())
            .webSocketConfiguration(wsRpcConfiguration())
            .jsonRpcIpcConfiguration(new JsonRpcIpcConfiguration())
            .inProcessRpcConfiguration(ImmutableInProcessRpcConfiguration.builder().build())
            .metricsConfiguration(metricsConfiguration())
            .dataDir(dataDir)
            .pidPath(pidPath)
            .besuPluginContext(new BesuPluginContextImpl())
            .rpcEndpointService(new RpcEndpointServiceImpl())
            .build();
    return runner;
  }

  private BesuController getController(
      final GenesisConfig genesisConfig,
      final SynchronizerConfiguration syncConfig,
      final Path dataDir,
      final NodeKey nodeKey,
      final StorageProvider storageProvider,
      final ObservableMetricsSystem metricsSystem,
      final MiningConfiguration miningConfiguration,
      final BigInteger networkId) {
    return new MainnetBesuControllerBuilder()
        .genesisConfig(genesisConfig)
        .synchronizerConfiguration(syncConfig)
        .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
        .dataDirectory(dataDir)
        .networkId(networkId)
        .miningParameters(miningConfiguration)
        .nodeKey(nodeKey)
        .storageProvider(storageProvider)
        .metricsSystem(metricsSystem)
        .clock(TestClock.fixed())
        .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
        .dataStorageConfiguration(DataStorageConfiguration.DEFAULT_FOREST_CONFIG)
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .networkConfiguration(NetworkingConfiguration.create())
        .randomPeerPriority(Boolean.FALSE)
        .besuComponent(mock(BesuComponent.class))
        .maxPeers(25)
        .maxRemotelyInitiatedPeers(15)
        .apiConfiguration(ImmutableApiConfiguration.builder().build())
        .build();
  }

  private void initDataPaths() {
    dataDir = besuCommand.dataDir();
    db = dataDir.resolve("database");

    pidPath = dataDir.resolve("pid");
    dbNodeKey = NodeKeyUtils.createFrom(KeyPairUtil.loadKeyPair(dataDir));
  }

  private GenesisConfig getFastSyncGenesis(final NetworkName networkName) throws IOException {
    final ObjectNode jsonNode =
        (ObjectNode)
            new ObjectMapper()
                .readTree(GenesisConfig.class.getResource(networkName.getGenesisFile()));
    final Optional<ObjectNode> configNode = JsonUtil.getObjectNode(jsonNode, "config");
    configNode.ifPresent(
        (node) -> {
          // Clear DAO block so that inability to validate DAO block won't interfere with fast sync
          node.remove("daoForkBlock");
          // remove merge terminal difficulty for fast sync in the absence of a CL mock
          node.remove("terminalTotalDifficulty");
        });
    return GenesisConfig.fromConfig(jsonNode);
  }

  private StorageProvider createKeyValueStorageProvider(
      final Path dataDir,
      final Path dbDir,
      final DataStorageConfiguration dataStorageConfiguration,
      final MiningConfiguration miningConfiguration) {
    final var besuConfiguration = new BesuConfigurationImpl();
    besuConfiguration
        .init(dataDir, dbDir, dataStorageConfiguration)
        .withMiningParameters(miningConfiguration);
    return new KeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValueStorageFactory(
                () ->
                    new RocksDBFactoryConfiguration(
                        DEFAULT_MAX_OPEN_FILES,
                        DEFAULT_BACKGROUND_THREAD_COUNT,
                        DEFAULT_CACHE_CAPACITY,
                        DEFAULT_IS_HIGH_SPEC,
                        DEFAULT_ENABLE_READ_CACHE_FOR_SNAPSHOTS,
                        false,
                        Optional.empty(),
                        Optional.empty()),
                Arrays.asList(KeyValueSegmentIdentifier.values()),
                RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS))
        .withCommonConfiguration(besuConfiguration)
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(true);
    configuration.setHostsAllowlist(Collections.singletonList("*"));
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
    configuration.setHostsAllowlist(Collections.singletonList("*"));
    return configuration;
  }

  private MetricsConfiguration metricsConfiguration() {
    return MetricsConfiguration.builder().enabled(false).port(0).build();
  }
}

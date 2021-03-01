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
package org.hyperledger.besu.cli;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.options.unstable.EthProtocolOptions;
import org.hyperledger.besu.cli.options.unstable.LauncherOptions;
import org.hyperledger.besu.cli.options.unstable.MetricsCLIOptions;
import org.hyperledger.besu.cli.options.unstable.NetworkingOptions;
import org.hyperledger.besu.cli.options.unstable.SynchronizerOptions;
import org.hyperledger.besu.cli.options.unstable.TransactionPoolOptions;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.controller.NoopPluginServiceFactory;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.PrivacyKeyValueStorageFactory;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.RunLast;

@RunWith(MockitoJUnitRunner.class)
public abstract class CommandTestAbstract {

  private static final Logger TEST_LOGGER = LogManager.getLogger();

  protected final ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
  private final PrintStream outPrintStream = new PrintStream(commandOutput);

  protected final ByteArrayOutputStream commandErrorOutput = new ByteArrayOutputStream();
  private final PrintStream errPrintStream = new PrintStream(commandErrorOutput);
  private final HashMap<String, String> environment = new HashMap<>();

  private final List<TestBesuCommand> besuCommands = new ArrayList<>();
  private KeyPair keyPair;

  @Mock protected RunnerBuilder mockRunnerBuilder;
  @Mock protected Runner mockRunner;

  @Mock protected BesuController.Builder mockControllerBuilderFactory;

  @Mock protected BesuControllerBuilder mockControllerBuilder;
  @Mock protected EthProtocolManager mockEthProtocolManager;
  @Mock protected ProtocolSchedule mockProtocolSchedule;
  @Mock protected ProtocolContext mockProtocolContext;
  @Mock protected BlockBroadcaster mockBlockBroadcaster;
  @Mock protected BesuController mockController;
  @Mock protected RlpBlockExporter rlpBlockExporter;
  @Mock protected JsonBlockImporter jsonBlockImporter;
  @Mock protected RlpBlockImporter rlpBlockImporter;
  @Mock protected StorageServiceImpl storageService;
  @Mock protected SecurityModuleServiceImpl securityModuleService;
  @Mock protected BesuConfiguration commonPluginConfiguration;
  @Mock protected KeyValueStorageFactory rocksDBStorageFactory;
  @Mock protected PrivacyKeyValueStorageFactory rocksDBSPrivacyStorageFactory;
  @Mock protected PicoCLIOptions cliOptions;
  @Mock protected NodeKey nodeKey;
  @Mock protected BesuPluginContextImpl mockBesuPluginContext;

  @SuppressWarnings("PrivateStaticFinalLoggers") // @Mocks are inited by JUnit
  @Mock
  protected Logger mockLogger;

  @Captor protected ArgumentCaptor<Collection<Bytes>> bytesCollectionCollector;
  @Captor protected ArgumentCaptor<Path> pathArgumentCaptor;
  @Captor protected ArgumentCaptor<String> stringArgumentCaptor;
  @Captor protected ArgumentCaptor<Integer> intArgumentCaptor;
  @Captor protected ArgumentCaptor<Float> floatCaptor;
  @Captor protected ArgumentCaptor<EthNetworkConfig> ethNetworkConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<SynchronizerConfiguration> syncConfigurationCaptor;
  @Captor protected ArgumentCaptor<JsonRpcConfiguration> jsonRpcConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<GraphQLConfiguration> graphQLConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<WebSocketConfiguration> wsRpcConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<MetricsConfiguration> metricsConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<StorageProvider> storageProviderArgumentCaptor;
  @Captor protected ArgumentCaptor<EthProtocolConfiguration> ethProtocolConfigurationArgumentCaptor;

  @Captor
  protected ArgumentCaptor<PermissioningConfiguration> permissioningConfigurationArgumentCaptor;

  @Captor protected ArgumentCaptor<TransactionPoolConfiguration> transactionPoolConfigCaptor;

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void initMocks() throws Exception {
    // doReturn used because of generic BesuController
    doReturn(mockControllerBuilder)
        .when(mockControllerBuilderFactory)
        .fromEthNetworkConfig(any(), any());
    when(mockControllerBuilder.synchronizerConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.ethProtocolConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.transactionPoolConfiguration(any()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.dataDirectory(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.miningParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.nodeKey(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.metricsSystem(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.privacyParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.clock(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.isRevertReasonEnabled(false)).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.storageProvider(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.isPruningEnabled(anyBoolean())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.pruningConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.genesisConfigOverrides(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.gasLimitCalculator(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.requiredBlocks(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.reorgLoggingThreshold(anyLong())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.dataStorageConfiguration(any())).thenReturn(mockControllerBuilder);

    // doReturn used because of generic BesuController
    doReturn(mockController).when(mockControllerBuilder).build();
    lenient().when(mockController.getProtocolManager()).thenReturn(mockEthProtocolManager);
    lenient().when(mockController.getProtocolSchedule()).thenReturn(mockProtocolSchedule);
    lenient().when(mockController.getProtocolContext()).thenReturn(mockProtocolContext);
    lenient()
        .when(mockController.getAdditionalPluginServices())
        .thenReturn(new NoopPluginServiceFactory());
    lenient().when(mockController.getNodeKey()).thenReturn(nodeKey);

    when(mockEthProtocolManager.getBlockBroadcaster()).thenReturn(mockBlockBroadcaster);

    when(mockRunnerBuilder.vertx(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.besuController(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discovery(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.ethNetworkConfig(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.networkingConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pAdvertisedHost(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pListenPort(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pListenInterface(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.maxPeers(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.limitRemoteWireConnectionsEnabled(anyBoolean()))
        .thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.fractionRemoteConnectionsAllowed(anyFloat()))
        .thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.randomPeerPriority(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.natMethod(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.natManagerServiceName(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.natMethodFallbackEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.jsonRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.graphQLConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.webSocketConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.apiConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.dataDir(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bannedNodeIds(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsSystem(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.staticNodes(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.identityString(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.besuPluginContext(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.autoLogBloomCaching(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.pidPath(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.ethstatsUrl(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.ethstatsContact(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.storageProvider(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.forkIdSupplier(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.build()).thenReturn(mockRunner);

    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

    final Bytes32 keyPairPrvKey =
        Bytes32.fromHexString("0xf7a58d5e755d51fa2f6206e91dd574597c73248aaf946ec1964b8c6268d6207b");
    keyPair = signatureAlgorithm.createKeyPair(signatureAlgorithm.createPrivateKey(keyPairPrvKey));

    lenient().when(nodeKey.getPublicKey()).thenReturn(keyPair.getPublicKey());

    lenient()
        .when(storageService.getByName(eq("rocksdb")))
        .thenReturn(Optional.of(rocksDBStorageFactory));
    lenient()
        .when(storageService.getByName(eq("rocksdb-privacy")))
        .thenReturn(Optional.of(rocksDBSPrivacyStorageFactory));
    lenient()
        .when(rocksDBSPrivacyStorageFactory.create(any(), any(), any()))
        .thenReturn(new InMemoryKeyValueStorage());

    lenient()
        .when(mockBesuPluginContext.getService(PicoCLIOptions.class))
        .thenReturn(Optional.of(cliOptions));
    lenient()
        .when(mockBesuPluginContext.getService(StorageService.class))
        .thenReturn(Optional.of(storageService));
  }

  // Display outputs for debug purpose
  @After
  public void displayOutput() throws IOException {
    TEST_LOGGER.info("Standard output {}", commandOutput.toString());
    TEST_LOGGER.info("Standard error {}", commandErrorOutput.toString());

    outPrintStream.close();
    commandOutput.close();

    errPrintStream.close();
    commandErrorOutput.close();
    besuCommands.forEach(TestBesuCommand::close);
  }

  protected NodeKey getNodeKey() {
    return nodeKey;
  }

  protected void setEnvironmentVariable(final String name, final String value) {
    environment.put(name, value);
  }

  protected TestBesuCommand parseCommand(final String... args) {
    return parseCommand(System.in, args);
  }

  private JsonBlockImporter jsonBlockImporterFactory(final BesuController controller) {
    return jsonBlockImporter;
  }

  protected TestBesuCommand parseCommand(final InputStream in, final String... args) {
    // turn off ansi usage globally in picocli
    System.setProperty("picocli.ansi", "false");

    final TestBesuCommand besuCommand =
        new TestBesuCommand(
            mockLogger,
            nodeKey,
            keyPair,
            () -> rlpBlockImporter,
            this::jsonBlockImporterFactory,
            (blockchain) -> rlpBlockExporter,
            mockRunnerBuilder,
            mockControllerBuilderFactory,
            mockBesuPluginContext,
            environment,
            storageService,
            securityModuleService);
    besuCommands.add(besuCommand);

    besuCommand.setBesuConfiguration(commonPluginConfiguration);

    // parse using Ansi.OFF to be able to assert on non formatted output results
    besuCommand.parse(
        new RunLast().useOut(outPrintStream).useAnsi(Ansi.OFF),
        besuCommand.exceptionHandler().useErr(errPrintStream).useAnsi(Ansi.OFF),
        in,
        args);
    return besuCommand;
  }

  @CommandLine.Command
  public static class TestBesuCommand extends BesuCommand {

    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    private Vertx vertx;
    private final NodeKey mockNodeKey;
    private final KeyPair keyPair;

    TestBesuCommand(
        final Logger mockLogger,
        final NodeKey mockNodeKey,
        final KeyPair keyPair,
        final Supplier<RlpBlockImporter> mockBlockImporter,
        final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
        final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
        final RunnerBuilder mockRunnerBuilder,
        final BesuController.Builder controllerBuilderFactory,
        final BesuPluginContextImpl besuPluginContext,
        final Map<String, String> environment,
        final StorageServiceImpl storageService,
        final SecurityModuleServiceImpl securityModuleService) {
      super(
          mockLogger,
          mockBlockImporter,
          jsonBlockImporterFactory,
          rlpBlockExporterFactory,
          mockRunnerBuilder,
          controllerBuilderFactory,
          besuPluginContext,
          environment,
          storageService,
          securityModuleService);
      this.mockNodeKey = mockNodeKey;
      this.keyPair = keyPair;
    }

    @Override
    protected void validateP2PInterface(final String p2pInterface) {
      // For testing, don't actually query for networking interfaces to validate this option
    }

    @Override
    protected Vertx createVertx(final VertxOptions vertxOptions) {
      vertx = super.createVertx(vertxOptions);
      return vertx;
    }

    @Override
    NodeKey buildNodeKey() {
      // for testing.
      return mockNodeKey;
    }

    @Override
    KeyPair loadKeyPair() {
      // for testing.
      return keyPair;
    }

    public CommandSpec getSpec() {
      return spec;
    }

    public NetworkingOptions getNetworkingOptions() {
      return unstableNetworkingOptions;
    }

    public SynchronizerOptions getSynchronizerOptions() {
      return unstableSynchronizerOptions;
    }

    public EthProtocolOptions getEthProtocolOptions() {
      return unstableEthProtocolOptions;
    }

    public TransactionPoolOptions getTransactionPoolOptions() {
      return unstableTransactionPoolOptions;
    }

    public MetricsCLIOptions getMetricsCLIOptions() {
      return unstableMetricsCLIOptions;
    }

    public LauncherOptions getLauncherOptions() {
      return unstableLauncherOptions;
    }

    public void close() {
      if (vertx != null) {
        final AtomicBoolean closed = new AtomicBoolean(false);
        vertx.close(event -> closed.set(true));
        Awaitility.waitAtMost(30, TimeUnit.SECONDS).until(closed::get);
      }
    }
  }
}

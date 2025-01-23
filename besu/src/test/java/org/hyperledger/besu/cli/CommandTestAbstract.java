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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPENDENCY_WARNING_MSG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.options.EthProtocolOptions;
import org.hyperledger.besu.cli.options.EthstatsOptions;
import org.hyperledger.besu.cli.options.MiningOptions;
import org.hyperledger.besu.cli.options.NetworkingOptions;
import org.hyperledger.besu.cli.options.P2PDiscoveryOptions;
import org.hyperledger.besu.cli.options.SynchronizerOptions;
import org.hyperledger.besu.cli.options.TransactionPoolOptions;
import org.hyperledger.besu.cli.options.storage.DataStorageOptions;
import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.controller.NoopPluginServiceFactory;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.PrivacyKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.BlockchainServiceImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.PrivacyPluginServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.TransactionPoolValidatorServiceImpl;
import org.hyperledger.besu.services.TransactionSelectionServiceImpl;
import org.hyperledger.besu.services.TransactionSimulationServiceImpl;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.RunLast;

@ExtendWith(MockitoExtension.class)
public abstract class CommandTestAbstract {
  private static final Logger TEST_LOGGER = LoggerFactory.getLogger(CommandTestAbstract.class);

  protected static final int POA_BLOCK_PERIOD_SECONDS = 5;
  protected static final int POA_EMPTY_BLOCK_PERIOD_SECONDS = 50;
  protected static final JsonObject VALID_GENESIS_QBFT_POST_LONDON =
      (new JsonObject())
          .put(
              "config",
              new JsonObject()
                  .put("londonBlock", 0)
                  .put("qbft", new JsonObject().put("blockperiodseconds", POA_BLOCK_PERIOD_SECONDS))
                  .put(
                      "qbft",
                      new JsonObject()
                          .put("xemptyblockperiodseconds", POA_EMPTY_BLOCK_PERIOD_SECONDS)));
  protected static final JsonObject VALID_GENESIS_IBFT2_POST_LONDON =
      (new JsonObject())
          .put(
              "config",
              new JsonObject()
                  .put("londonBlock", 0)
                  .put(
                      "ibft2",
                      new JsonObject().put("blockperiodseconds", POA_BLOCK_PERIOD_SECONDS)));

  protected static final JsonObject VALID_GENESIS_CLIQUE_POST_LONDON =
      (new JsonObject())
          .put(
              "config",
              new JsonObject()
                  .put("londonBlock", 0)
                  .put(
                      "clique",
                      new JsonObject().put("blockperiodseconds", POA_BLOCK_PERIOD_SECONDS)));

  protected static final JsonObject GENESIS_WITH_ZERO_BASE_FEE_MARKET =
      new JsonObject().put("config", new JsonObject().put("zeroBaseFee", true));

  protected final PrintStream originalOut = System.out;
  protected final PrintStream originalErr = System.err;
  protected final ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
  protected final ByteArrayOutputStream commandErrorOutput = new ByteArrayOutputStream();
  private final HashMap<String, String> environment = new HashMap<>();

  private final List<TestBesuCommand> besuCommands = new ArrayList<>();
  private KeyPair keyPair;

  protected static final RpcEndpointServiceImpl rpcEndpointServiceImpl =
      new RpcEndpointServiceImpl();

  @Mock(lenient = true)
  protected RunnerBuilder mockRunnerBuilder;

  @Mock protected Runner mockRunner;

  @Mock(lenient = true)
  protected BesuController.Builder mockControllerBuilderFactory;

  @Mock(lenient = true)
  protected BesuControllerBuilder mockControllerBuilder;

  @Mock(lenient = true)
  protected EthProtocolManager mockEthProtocolManager;

  @Mock protected ProtocolSchedule mockProtocolSchedule;

  @Mock(lenient = true)
  protected ProtocolContext mockProtocolContext;

  @Mock protected BlockBroadcaster mockBlockBroadcaster;

  @Mock(lenient = true)
  protected BesuController mockController;

  @Mock(lenient = true)
  protected BesuComponent mockBesuComponent;

  @Mock protected RlpBlockExporter rlpBlockExporter;
  @Mock protected JsonBlockImporter jsonBlockImporter;
  @Mock protected RlpBlockImporter rlpBlockImporter;
  @Mock protected StorageServiceImpl storageService;
  @Mock protected TransactionSelectionServiceImpl txSelectionService;
  @Mock protected SecurityModuleServiceImpl securityModuleService;
  @Mock protected SecurityModule securityModule;
  @Spy protected BesuConfigurationImpl commonPluginConfiguration = new BesuConfigurationImpl();
  @Mock protected KeyValueStorageFactory rocksDBStorageFactory;
  @Mock protected PrivacyKeyValueStorageFactory rocksDBSPrivacyStorageFactory;
  @Mock protected PicoCLIOptions cliOptions;
  @Mock protected NodeKey nodeKey;
  @Mock protected BesuPluginContextImpl mockBesuPluginContext;
  @Mock protected MutableBlockchain mockMutableBlockchain;
  @Mock protected WorldStateArchive mockWorldStateArchive;
  @Mock protected TransactionPool mockTransactionPool;
  @Mock protected PrivacyPluginServiceImpl privacyPluginService;
  @Mock protected StorageProvider storageProvider;

  @SuppressWarnings("PrivateStaticFinalLoggers") // @Mocks are inited by JUnit
  @Mock
  protected Logger mockLogger;

  @Captor protected ArgumentCaptor<Collection<Bytes>> bytesCollectionCollector;
  @Captor protected ArgumentCaptor<Path> pathArgumentCaptor;
  @Captor protected ArgumentCaptor<String> stringArgumentCaptor;
  @Captor protected ArgumentCaptor<Integer> intArgumentCaptor;
  @Captor protected ArgumentCaptor<Long> longArgumentCaptor;
  @Captor protected ArgumentCaptor<EthNetworkConfig> ethNetworkConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<SynchronizerConfiguration> syncConfigurationCaptor;
  @Captor protected ArgumentCaptor<JsonRpcConfiguration> jsonRpcConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<GraphQLConfiguration> graphQLConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<WebSocketConfiguration> wsRpcConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<MetricsConfiguration> metricsConfigArgumentCaptor;
  @Captor protected ArgumentCaptor<StorageProvider> storageProviderArgumentCaptor;
  @Captor protected ArgumentCaptor<EthProtocolConfiguration> ethProtocolConfigurationArgumentCaptor;
  @Captor protected ArgumentCaptor<DataStorageConfiguration> dataStorageConfigurationArgumentCaptor;

  @Captor
  protected ArgumentCaptor<Optional<PermissioningConfiguration>>
      permissioningConfigurationArgumentCaptor;

  @Captor protected ArgumentCaptor<TransactionPoolConfiguration> transactionPoolConfigCaptor;
  @Captor protected ArgumentCaptor<ApiConfiguration> apiConfigurationCaptor;

  @Captor protected ArgumentCaptor<EthstatsOptions> ethstatsOptionsArgumentCaptor;
  @Captor protected ArgumentCaptor<List<SubnetInfo>> allowedSubnetsArgumentCaptor;

  @BeforeEach
  public void initMocks() throws Exception {
    when(mockControllerBuilderFactory.fromEthNetworkConfig(any(), any()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.synchronizerConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.ethProtocolConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.transactionPoolConfiguration(any()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.dataDirectory(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.miningParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.nodeKey(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.metricsSystem(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.messagePermissioningProviders(any()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.privacyParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.clock(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.isRevertReasonEnabled(false)).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.isParallelTxProcessingEnabled(false))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.storageProvider(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.gasLimitCalculator(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.requiredBlocks(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.reorgLoggingThreshold(anyLong())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.dataStorageConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.evmConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.networkConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.randomPeerPriority(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.maxPeers(anyInt())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.chainPruningConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.maxPeers(anyInt())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.maxRemotelyInitiatedPeers(anyInt()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.besuComponent(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.cacheLastBlocks(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.genesisStateHashCacheEnabled(any()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.apiConfiguration(any())).thenReturn(mockControllerBuilder);

    when(mockControllerBuilder.build()).thenReturn(mockController);
    lenient().when(mockController.getProtocolManager()).thenReturn(mockEthProtocolManager);
    lenient().when(mockController.getProtocolSchedule()).thenReturn(mockProtocolSchedule);
    lenient().when(mockController.getProtocolContext()).thenReturn(mockProtocolContext);
    lenient()
        .when(mockController.getAdditionalPluginServices())
        .thenReturn(new NoopPluginServiceFactory());
    lenient().when(mockController.getNodeKey()).thenReturn(nodeKey);

    when(mockEthProtocolManager.getBlockBroadcaster()).thenReturn(mockBlockBroadcaster);

    when(mockProtocolContext.getBlockchain()).thenReturn(mockMutableBlockchain);
    lenient().when(mockProtocolContext.getWorldStateArchive()).thenReturn(mockWorldStateArchive);
    when(mockController.getTransactionPool()).thenReturn(mockTransactionPool);
    when(mockController.getStorageProvider()).thenReturn(storageProvider);

    when(mockRunnerBuilder.vertx(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.besuController(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discoveryEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.ethNetworkConfig(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.networkingConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pAdvertisedHost(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pListenPort(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pListenInterface(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.permissioningConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.natMethod(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.natManagerServiceName(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.natMethodFallbackEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.jsonRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.engineJsonRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.graphQLConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.webSocketConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.jsonRpcIpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.inProcessRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.apiConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.dataDir(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bannedNodeIds(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsSystem(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.permissioningService(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.staticNodes(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.identityString(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.besuPluginContext(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.autoLogBloomCaching(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.pidPath(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.ethstatsOptions(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.storageProvider(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.rpcEndpointService(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.legacyForkId(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.apiConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.enodeDnsConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.allowedSubnets(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.poaDiscoveryRetryBootnodes(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.build()).thenReturn(mockRunner);
    when(mockBesuComponent.getMetricsSystem()).thenReturn(new NoOpMetricsSystem());

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
        .when(securityModuleService.getByName(eq("localfile")))
        .thenReturn(Optional.of(() -> securityModule));
    lenient()
        .when(rocksDBSPrivacyStorageFactory.create(any(SegmentIdentifier.class), any(), any()))
        .thenReturn(new InMemoryKeyValueStorage());

    lenient()
        .when(mockBesuPluginContext.getService(PicoCLIOptions.class))
        .thenReturn(Optional.of(cliOptions));
    lenient()
        .when(mockBesuPluginContext.getService(StorageService.class))
        .thenReturn(Optional.of(storageService));
    lenient()
        .when(mockBesuPluginContext.getService(TransactionSelectionService.class))
        .thenReturn(Optional.of(txSelectionService));
  }

  @BeforeEach
  public void setUpStreams() {
    // reset the global opentelemetry singleton
    GlobalOpenTelemetry.resetForTest();
    commandOutput.reset();
    commandErrorOutput.reset();
    System.setOut(new PrintStream(commandOutput));
    System.setErr(new PrintStream(commandErrorOutput));
  }

  // Display outputs for debug purpose
  @AfterEach
  public void displayOutput() throws IOException {
    TEST_LOGGER.info("Standard output {}", commandOutput.toString(UTF_8));
    TEST_LOGGER.info("Standard error {}", commandErrorOutput.toString(UTF_8));

    System.setOut(originalOut);
    System.setErr(originalErr);
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

  protected TestBesuCommand parseCommand(final InputStream in, final String... args) {
    return parseCommand(TestType.NO_PORT_CHECK, in, args);
  }

  protected TestBesuCommand parseCommandWithRequiredOption(final String... args) {
    return parseCommand(TestType.REQUIRED_OPTION, System.in, args);
  }

  protected TestBesuCommand parseCommandWithPortCheck(final String... args) {
    return parseCommand(TestType.PORT_CHECK, System.in, args);
  }

  private JsonBlockImporter jsonBlockImporterFactory(final BesuController controller) {
    return jsonBlockImporter;
  }

  protected TestBesuCommand parseCommand(
      final TestType testType, final InputStream in, final String... args) {
    // turn off ansi usage globally in picocli
    System.setProperty("picocli.ansi", "false");
    // reset GlobalOpenTelemetry
    GlobalOpenTelemetry.resetForTest();

    final TestBesuCommand besuCommand = getTestBesuCommand(testType);
    besuCommands.add(besuCommand);

    final File defaultKeyFile =
        KeyPairUtil.getDefaultKeyFile(DefaultCommandValues.getDefaultBesuDataPath(besuCommand));
    try {
      Files.writeString(defaultKeyFile.toPath(), keyPair.getPrivateKey().toString());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    besuCommand.setBesuConfiguration(commonPluginConfiguration);

    // parse using Ansi.OFF to be able to assert on non formatted output results
    besuCommand.parse(
        new RunLast(),
        besuCommand.parameterExceptionHandler(),
        besuCommand.executionExceptionHandler(),
        in,
        mockBesuComponent,
        args);
    return besuCommand;
  }

  private TestBesuCommand getTestBesuCommand(final TestType testType) {
    switch (testType) {
      case REQUIRED_OPTION:
        return new TestBesuCommandWithRequiredOption(
            () -> rlpBlockImporter,
            this::jsonBlockImporterFactory,
            (blockchain) -> rlpBlockExporter,
            mockRunnerBuilder,
            mockControllerBuilderFactory,
            mockBesuPluginContext,
            environment,
            storageService,
            securityModuleService,
            privacyPluginService,
            mockLogger);
      case PORT_CHECK:
        return new TestBesuCommand(
            () -> rlpBlockImporter,
            this::jsonBlockImporterFactory,
            (blockchain) -> rlpBlockExporter,
            mockRunnerBuilder,
            mockControllerBuilderFactory,
            mockBesuPluginContext,
            environment,
            storageService,
            securityModuleService,
            privacyPluginService,
            mockLogger);
      default:
        return new TestBesuCommandWithoutPortCheck(
            () -> rlpBlockImporter,
            this::jsonBlockImporterFactory,
            (blockchain) -> rlpBlockExporter,
            mockRunnerBuilder,
            mockControllerBuilderFactory,
            mockBesuPluginContext,
            environment,
            storageService,
            securityModuleService,
            privacyPluginService,
            mockLogger);
    }
  }

  protected Path createTempFile(final String filename, final byte[] contents) throws IOException {
    final Path file = Files.createTempFile(filename, "");
    Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }

  protected Path createFakeGenesisFile(final JsonObject jsonGenesis) throws IOException {
    return createTempFile("genesisFile", encodeJsonGenesis(jsonGenesis).getBytes(UTF_8));
  }

  protected String encodeJsonGenesis(final JsonObject jsonGenesis) {
    return jsonGenesis.encodePrettily();
  }

  protected Path createTempFile(final String filename, final String contents) throws IOException {
    return createTempFile(filename, contents.getBytes(UTF_8));
  }

  @CommandLine.Command
  public static class TestBesuCommand extends BesuCommand {

    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    private Vertx vertx;

    TestBesuCommand(
        final Supplier<RlpBlockImporter> mockBlockImporter,
        final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
        final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
        final RunnerBuilder mockRunnerBuilder,
        final BesuController.Builder controllerBuilderFactory,
        final BesuPluginContextImpl besuPluginContext,
        final Map<String, String> environment,
        final StorageServiceImpl storageService,
        final SecurityModuleServiceImpl securityModuleService,
        final PrivacyPluginServiceImpl privacyPluginService,
        final Logger commandLogger) {
      super(
          mockBlockImporter,
          jsonBlockImporterFactory,
          rlpBlockExporterFactory,
          mockRunnerBuilder,
          controllerBuilderFactory,
          besuPluginContext,
          environment,
          storageService,
          securityModuleService,
          new PermissioningServiceImpl(),
          privacyPluginService,
          rpcEndpointServiceImpl,
          new TransactionSelectionServiceImpl(),
          new TransactionPoolValidatorServiceImpl(),
          new TransactionSimulationServiceImpl(),
          new BlockchainServiceImpl(),
          commandLogger);
    }

    @Override
    protected P2PDiscoveryOptions.NetworkInterfaceChecker getNetworkInterfaceChecker() {
      // For testing, don't actually query for networking interfaces to validate this option
      return (networkInterface) -> true;
    }

    @Override
    protected Vertx createVertx(final VertxOptions vertxOptions) {
      vertx = super.createVertx(vertxOptions);
      return vertx;
    }

    @Override
    public GenesisConfigOptions getGenesisConfigOptions() {
      return super.getGenesisConfigOptions();
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

    public MiningOptions getMiningOptions() {
      return miningOptions;
    }

    public TransactionPoolOptions getTransactionPoolOptions() {
      return transactionPoolOptions;
    }

    public DataStorageOptions getDataStorageOptions() {
      return dataStorageOptions;
    }

    public void close() {
      if (vertx != null) {
        final AtomicBoolean closed = new AtomicBoolean(false);
        vertx.close(event -> closed.set(true));
        Awaitility.waitAtMost(30, TimeUnit.SECONDS).until(closed::get);
      }
    }
  }

  @CommandLine.Command
  public static class TestBesuCommandWithRequiredOption extends TestBesuCommand {

    @NotEmpty
    @CommandLine.Option(
        names = {"--accept-terms-and-conditions"},
        description = "You must explicitly accept terms and conditions",
        arity = "1",
        required = true)
    private final Boolean acceptTermsAndConditions = false;

    TestBesuCommandWithRequiredOption(
        final Supplier<RlpBlockImporter> mockBlockImporter,
        final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
        final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
        final RunnerBuilder mockRunnerBuilder,
        final BesuController.Builder controllerBuilderFactory,
        final BesuPluginContextImpl besuPluginContext,
        final Map<String, String> environment,
        final StorageServiceImpl storageService,
        final SecurityModuleServiceImpl securityModuleService,
        final PrivacyPluginServiceImpl privacyPluginService,
        final Logger commandLogger) {
      super(
          mockBlockImporter,
          jsonBlockImporterFactory,
          rlpBlockExporterFactory,
          mockRunnerBuilder,
          controllerBuilderFactory,
          besuPluginContext,
          environment,
          storageService,
          securityModuleService,
          privacyPluginService,
          commandLogger);
    }

    public Boolean getAcceptTermsAndConditions() {
      return acceptTermsAndConditions;
    }
  }

  @CommandLine.Command
  public static class TestBesuCommandWithoutPortCheck extends TestBesuCommand {

    TestBesuCommandWithoutPortCheck(
        final Supplier<RlpBlockImporter> mockBlockImporter,
        final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
        final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
        final RunnerBuilder mockRunnerBuilder,
        final BesuController.Builder controllerBuilderFactory,
        final BesuPluginContextImpl besuPluginContext,
        final Map<String, String> environment,
        final StorageServiceImpl storageService,
        final SecurityModuleServiceImpl securityModuleService,
        final PrivacyPluginServiceImpl privacyPluginService,
        final Logger commandLogger) {
      super(
          mockBlockImporter,
          jsonBlockImporterFactory,
          rlpBlockExporterFactory,
          mockRunnerBuilder,
          controllerBuilderFactory,
          besuPluginContext,
          environment,
          storageService,
          securityModuleService,
          privacyPluginService,
          commandLogger);
    }

    @Override
    protected void checkIfRequiredPortsAreAvailable() {
      // For testing, don't check for port conflicts
    }
  }

  private enum TestType {
    REQUIRED_OPTION,
    PORT_CHECK,
    NO_PORT_CHECK
  }

  protected static String escapeTomlString(final String s) {
    return StringEscapeUtils.escapeJava(s);
  }

  /**
   * Check logger calls
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param dependentOptions the string representing the list of dependent options names
   * @param mainOption the main option name
   */
  protected void verifyOptionsConstraintLoggerCall(
      final String mainOption, final String... dependentOptions) {
    verify(mockLogger, atLeast(1))
        .warn(
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getAllValues().get(0)).isEqualTo(DEPENDENCY_WARNING_MSG);

    for (final String option : dependentOptions) {
      assertThat(stringArgumentCaptor.getAllValues().get(1)).contains(option);
    }

    assertThat(stringArgumentCaptor.getAllValues().get(2)).isEqualTo(mainOption);
  }

  /**
   * Check logger calls
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param stringToLog the string that is logged
   */
  void verifyMultiOptionsConstraintLoggerCall(final String stringToLog) {
    verify(mockLogger, atLeast(1)).warn(stringToLog);
  }
}

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
package tech.pegasys.pantheon.cli;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.Runner;
import tech.pegasys.pantheon.RunnerBuilder;
import tech.pegasys.pantheon.chainexport.RlpBlockExporter;
import tech.pegasys.pantheon.chainimport.JsonBlockImporter;
import tech.pegasys.pantheon.chainimport.RlpBlockImporter;
import tech.pegasys.pantheon.cli.config.EthNetworkConfig;
import tech.pegasys.pantheon.cli.options.EthProtocolOptions;
import tech.pegasys.pantheon.cli.options.MetricsCLIOptions;
import tech.pegasys.pantheon.cli.options.NetworkingOptions;
import tech.pegasys.pantheon.cli.options.SynchronizerOptions;
import tech.pegasys.pantheon.cli.options.TransactionPoolOptions;
import tech.pegasys.pantheon.cli.subcommands.PublicKeySubCommand.KeyLoader;
import tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.JsonBlockImporterFactory;
import tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.RlpBlockExporterFactory;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.controller.PantheonControllerBuilder;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.BlockBroadcaster;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.ethereum.graphql.GraphQLConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.plugin.services.PantheonConfiguration;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageFactory;
import tech.pegasys.pantheon.services.PantheonPluginContextImpl;
import tech.pegasys.pantheon.services.StorageServiceImpl;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

  private final Logger TEST_LOGGER = LogManager.getLogger();

  protected final ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
  private final PrintStream outPrintStream = new PrintStream(commandOutput);

  protected final ByteArrayOutputStream commandErrorOutput = new ByteArrayOutputStream();
  private final PrintStream errPrintStream = new PrintStream(commandErrorOutput);
  private final HashMap<String, String> environment = new HashMap<>();

  @Mock protected RunnerBuilder mockRunnerBuilder;
  @Mock protected Runner mockRunner;

  @Mock protected PantheonController.Builder mockControllerBuilderFactory;

  @Mock protected PantheonControllerBuilder<Void> mockControllerBuilder;
  @Mock protected EthProtocolManager mockEthProtocolManager;
  @Mock protected ProtocolSchedule<Object> mockProtocolSchedule;
  @Mock protected ProtocolContext<Object> mockProtocolContext;
  @Mock protected BlockBroadcaster mockBlockBroadcaster;
  @Mock protected PantheonController<Object> mockController;
  @Mock protected RlpBlockExporter rlpBlockExporter;
  @Mock protected JsonBlockImporter<?> jsonBlockImporter;
  @Mock protected RlpBlockImporter rlpBlockImporter;
  @Mock protected StorageServiceImpl storageService;
  @Mock protected PantheonConfiguration commonPluginConfiguration;
  @Mock protected KeyValueStorageFactory rocksDBStorageFactory;
  @Mock protected KeyValueStorageFactory rocksDBSPrivacyStorageFactory;

  @Mock protected Logger mockLogger;
  @Mock protected PantheonPluginContextImpl mockPantheonPluginContext;

  @Captor protected ArgumentCaptor<Collection<BytesValue>> bytesValueCollectionCollector;
  @Captor protected ArgumentCaptor<Path> pathArgumentCaptor;
  @Captor protected ArgumentCaptor<File> fileArgumentCaptor;
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

  @Captor
  protected ArgumentCaptor<PermissioningConfiguration> permissioningConfigurationArgumentCaptor;

  @Captor protected ArgumentCaptor<TransactionPoolConfiguration> transactionPoolConfigCaptor;

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Before
  @After
  public void resetSystemProps() {
    System.setProperty("pantheon.docker", "false");
  }

  @Before
  public void initMocks() throws Exception {

    // doReturn used because of generic PantheonController
    doReturn(mockControllerBuilder).when(mockControllerBuilderFactory).fromEthNetworkConfig(any());

    when(mockControllerBuilder.synchronizerConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.ethProtocolConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.transactionPoolConfiguration(any()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.dataDirectory(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.miningParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.nodePrivateKeyFile(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.metricsSystem(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.privacyParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.clock(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.isRevertReasonEnabled(false)).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.storageProvider(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.isPruningEnabled(anyBoolean())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.pruningConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.genesisConfigOverrides(any())).thenReturn(mockControllerBuilder);

    // doReturn used because of generic PantheonController
    doReturn(mockController).when(mockControllerBuilder).build();
    lenient().when(mockController.getProtocolManager()).thenReturn(mockEthProtocolManager);
    lenient().when(mockController.getProtocolSchedule()).thenReturn(mockProtocolSchedule);
    lenient().when(mockController.getProtocolContext()).thenReturn(mockProtocolContext);

    when(mockEthProtocolManager.getBlockBroadcaster()).thenReturn(mockBlockBroadcaster);

    when(mockRunnerBuilder.vertx(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.pantheonController(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discovery(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.ethNetworkConfig(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.networkingConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pAdvertisedHost(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pListenPort(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.maxPeers(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.limitRemoteWireConnectionsEnabled(anyBoolean()))
        .thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.fractionRemoteConnectionsAllowed(anyFloat()))
        .thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.natMethod(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.jsonRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.graphQLConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.webSocketConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.dataDir(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bannedNodeIds(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsSystem(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.staticNodes(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.build()).thenReturn(mockRunner);

    when(storageService.getByName("rocksdb")).thenReturn(rocksDBStorageFactory);
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
  }

  protected void setEnvironemntVariable(final String name, final String value) {
    environment.put(name, value);
  }

  protected TestPantheonCommand parseCommand(final String... args) {
    return parseCommand(System.in, args);
  }

  protected TestPantheonCommand parseCommand(final KeyLoader keyLoader, final String... args) {
    return parseCommand(keyLoader, System.in, args);
  }

  protected TestPantheonCommand parseCommand(final InputStream in, final String... args) {
    return parseCommand(f -> KeyPair.generate(), in, args);
  }

  @SuppressWarnings("unchecked")
  private <T> JsonBlockImporter<T> jsonBlockImporterFactory(
      final PantheonController<T> controller) {
    return (JsonBlockImporter<T>) jsonBlockImporter;
  }

  private TestPantheonCommand parseCommand(
      final KeyLoader keyLoader, final InputStream in, final String... args) {
    // turn off ansi usage globally in picocli
    System.setProperty("picocli.ansi", "false");

    final TestPantheonCommand pantheonCommand =
        new TestPantheonCommand(
            mockLogger,
            rlpBlockImporter,
            this::jsonBlockImporterFactory,
            (blockchain) -> rlpBlockExporter,
            mockRunnerBuilder,
            mockControllerBuilderFactory,
            keyLoader,
            mockPantheonPluginContext,
            environment,
            storageService);

    pantheonCommand.setPantheonConfiguration(commonPluginConfiguration);

    // parse using Ansi.OFF to be able to assert on non formatted output results
    pantheonCommand.parse(
        new RunLast().useOut(outPrintStream).useAnsi(Ansi.OFF),
        pantheonCommand.exceptionHandler().useErr(errPrintStream).useAnsi(Ansi.OFF),
        in,
        args);
    return pantheonCommand;
  }

  @CommandLine.Command
  public static class TestPantheonCommand extends PantheonCommand {

    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    private final KeyLoader keyLoader;

    @Override
    protected KeyLoader getKeyLoader() {
      return keyLoader;
    }

    TestPantheonCommand(
        final Logger mockLogger,
        final RlpBlockImporter mockBlockImporter,
        final JsonBlockImporterFactory jsonBlockImporterFactory,
        final RlpBlockExporterFactory rlpBlockExporterFactory,
        final RunnerBuilder mockRunnerBuilder,
        final PantheonController.Builder controllerBuilderFactory,
        final KeyLoader keyLoader,
        final PantheonPluginContextImpl pantheonPluginContext,
        final Map<String, String> environment,
        final StorageServiceImpl storageService) {
      super(
          mockLogger,
          mockBlockImporter,
          jsonBlockImporterFactory,
          rlpBlockExporterFactory,
          mockRunnerBuilder,
          controllerBuilderFactory,
          pantheonPluginContext,
          environment,
          storageService);
      this.keyLoader = keyLoader;
    }

    public CommandSpec getSpec() {
      return spec;
    }

    public NetworkingOptions getNetworkingOptions() {
      return networkingOptions;
    }

    public SynchronizerOptions getSynchronizerOptions() {
      return synchronizerOptions;
    }

    public EthProtocolOptions getEthProtocolOptions() {
      return ethProtocolOptions;
    }

    public TransactionPoolOptions getTransactionPoolOptions() {
      return transactionPoolOptions;
    }

    public MetricsCLIOptions getMetricsCLIOptions() {
      return metricsCLIOptions;
    }
  }
}

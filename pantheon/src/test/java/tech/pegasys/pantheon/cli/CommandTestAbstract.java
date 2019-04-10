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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.Runner;
import tech.pegasys.pantheon.RunnerBuilder;
import tech.pegasys.pantheon.cli.PublicKeySubCommand.KeyLoader;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.eth.EthereumWireProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.services.kvstore.RocksDbConfiguration;
import tech.pegasys.pantheon.util.BlockImporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;

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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.RunLast;

@RunWith(MockitoJUnitRunner.class)
public abstract class CommandTestAbstract {

  private final Logger TEST_LOGGER = LogManager.getLogger();

  protected final ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
  private final PrintStream outPrintStream = new PrintStream(commandOutput);

  protected final ByteArrayOutputStream commandErrorOutput = new ByteArrayOutputStream();
  private final PrintStream errPrintStream = new PrintStream(commandErrorOutput);

  @Mock RunnerBuilder mockRunnerBuilder;
  @Mock Runner mockRunner;
  @Mock PantheonControllerBuilder mockControllerBuilder;
  @Mock SynchronizerConfiguration.Builder mockSyncConfBuilder;
  @Mock EthereumWireProtocolConfiguration.Builder mockEthereumWireProtocolConfigurationBuilder;
  @Mock SynchronizerConfiguration mockSyncConf;
  @Mock RocksDbConfiguration.Builder mockRocksDbConfBuilder;
  @Mock RocksDbConfiguration mockRocksDbConf;
  @Mock PantheonController<?> mockController;
  @Mock BlockImporter mockBlockImporter;
  @Mock Logger mockLogger;

  @Captor ArgumentCaptor<Collection<String>> stringListArgumentCaptor;
  @Captor ArgumentCaptor<Path> pathArgumentCaptor;
  @Captor ArgumentCaptor<File> fileArgumentCaptor;
  @Captor ArgumentCaptor<String> stringArgumentCaptor;
  @Captor ArgumentCaptor<Integer> intArgumentCaptor;
  @Captor ArgumentCaptor<EthNetworkConfig> ethNetworkConfigArgumentCaptor;
  @Captor ArgumentCaptor<JsonRpcConfiguration> jsonRpcConfigArgumentCaptor;
  @Captor ArgumentCaptor<WebSocketConfiguration> wsRpcConfigArgumentCaptor;
  @Captor ArgumentCaptor<MetricsConfiguration> metricsConfigArgumentCaptor;

  @Captor ArgumentCaptor<PermissioningConfiguration> permissioningConfigurationArgumentCaptor;

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Before
  @After
  public void resetSystemProps() {
    System.setProperty("pantheon.docker", "false");
  }

  @Before
  public void initMocks() throws Exception {
    // doReturn used because of generic PantheonController
    Mockito.doReturn(mockController).when(mockControllerBuilder).build();
    when(mockControllerBuilder.synchronizerConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.ethereumWireProtocolConfiguration(any()))
        .thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.rocksDbConfiguration(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.homePath(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.ethNetworkConfig(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.miningParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.devMode(anyBoolean())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.maxPendingTransactions(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.nodePrivateKeyFile(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.metricsSystem(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.privacyParameters(any())).thenReturn(mockControllerBuilder);

    when(mockSyncConfBuilder.syncMode(any())).thenReturn(mockSyncConfBuilder);
    when(mockSyncConfBuilder.maxTrailingPeers(anyInt())).thenReturn(mockSyncConfBuilder);
    when(mockSyncConfBuilder.fastSyncMinimumPeerCount(anyInt())).thenReturn(mockSyncConfBuilder);
    when(mockSyncConfBuilder.fastSyncMaximumPeerWaitTime(any(Duration.class)))
        .thenReturn(mockSyncConfBuilder);
    when(mockSyncConfBuilder.build()).thenReturn(mockSyncConf);

    when(mockEthereumWireProtocolConfigurationBuilder.build())
        .thenReturn(EthereumWireProtocolConfiguration.defaultConfig());

    when(mockRocksDbConfBuilder.databaseDir(any())).thenReturn(mockRocksDbConfBuilder);
    when(mockRocksDbConfBuilder.build()).thenReturn(mockRocksDbConf);

    when(mockRunnerBuilder.vertx(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.pantheonController(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discovery(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.ethNetworkConfig(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discoveryHost(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discoveryPort(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.maxPeers(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.jsonRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.webSocketConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.dataDir(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bannedNodeIds(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsSystem(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.staticNodes(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.build()).thenReturn(mockRunner);
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

  protected CommandLine.Model.CommandSpec parseCommand(final String... args) {
    return parseCommand(System.in, args);
  }

  protected CommandLine.Model.CommandSpec parseCommand(
      final KeyLoader keyLoader, final String... args) {
    return parseCommand(keyLoader, System.in, args);
  }

  protected CommandLine.Model.CommandSpec parseCommand(final InputStream in, final String... args) {
    return parseCommand(f -> KeyPair.generate(), in, args);
  }

  private CommandLine.Model.CommandSpec parseCommand(
      final KeyLoader keyLoader, final InputStream in, final String... args) {
    // turn off ansi usage globally in picocli
    System.setProperty("picocli.ansi", "false");

    final TestPantheonCommand pantheonCommand =
        new TestPantheonCommand(
            mockLogger,
            mockBlockImporter,
            mockRunnerBuilder,
            mockControllerBuilder,
            mockSyncConfBuilder,
            mockEthereumWireProtocolConfigurationBuilder,
            mockRocksDbConfBuilder,
            keyLoader);

    // parse using Ansi.OFF to be able to assert on non formatted output results
    pantheonCommand.parse(
        new RunLast().useOut(outPrintStream).useAnsi(Ansi.OFF),
        pantheonCommand.exceptionHandler().useErr(errPrintStream).useAnsi(Ansi.OFF),
        in,
        args);
    return pantheonCommand.spec;
  }

  @CommandLine.Command
  static class TestPantheonCommand extends PantheonCommand {
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    private final KeyLoader keyLoader;

    @Override
    protected KeyLoader getKeyLoader() {
      return keyLoader;
    }

    TestPantheonCommand(
        final Logger mockLogger,
        final BlockImporter mockBlockImporter,
        final RunnerBuilder mockRunnerBuilder,
        final PantheonControllerBuilder mockControllerBuilder,
        final SynchronizerConfiguration.Builder mockSyncConfBuilder,
        final EthereumWireProtocolConfiguration.Builder mockEthereumConfigurationMockBuilder,
        final RocksDbConfiguration.Builder mockRocksDbConfBuilder,
        final KeyLoader keyLoader) {
      super(
          mockLogger,
          mockBlockImporter,
          mockRunnerBuilder,
          mockControllerBuilder,
          mockSyncConfBuilder,
          mockEthereumConfigurationMockBuilder,
          mockRocksDbConfBuilder);
      this.keyLoader = keyLoader;
    }
  }
}

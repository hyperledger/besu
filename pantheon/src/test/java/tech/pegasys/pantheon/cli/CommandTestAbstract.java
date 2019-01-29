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
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.util.BlockImporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

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
import picocli.CommandLine.DefaultExceptionHandler;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.RunLast;

@RunWith(MockitoJUnitRunner.class)
public abstract class CommandTestAbstract {

  private final Logger TEST_LOGGER = LogManager.getLogger();

  final ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
  private final PrintStream outPrintStream = new PrintStream(commandOutput);

  final ByteArrayOutputStream commandErrorOutput = new ByteArrayOutputStream();
  private final PrintStream errPrintStream = new PrintStream(commandErrorOutput);

  @Mock RunnerBuilder mockRunnerBuilder;
  @Mock Runner mockRunner;
  @Mock PantheonControllerBuilder mockControllerBuilder;
  @Mock SynchronizerConfiguration.Builder mockSyncConfBuilder;
  @Mock SynchronizerConfiguration mockSyncConf;
  @Mock PantheonController<?> mockController;
  @Mock BlockImporter mockBlockImporter;
  @Mock Logger mockLogger;

  @Captor ArgumentCaptor<Collection<String>> stringListArgumentCaptor;
  @Captor ArgumentCaptor<Path> pathArgumentCaptor;
  @Captor ArgumentCaptor<File> fileArgumentCaptor;
  @Captor ArgumentCaptor<String> stringArgumentCaptor;
  @Captor ArgumentCaptor<Integer> intArgumentCaptor;
  @Captor ArgumentCaptor<JsonRpcConfiguration> jsonRpcConfigArgumentCaptor;
  @Captor ArgumentCaptor<WebSocketConfiguration> wsRpcConfigArgumentCaptor;
  @Captor ArgumentCaptor<MetricsConfiguration> metricsConfigArgumentCaptor;
  @Captor ArgumentCaptor<PermissioningConfiguration> permissioningConfigurationArgumentCaptor;
  @Captor ArgumentCaptor<Collection<URI>> uriListArgumentCaptor;

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
    when(mockControllerBuilder.homePath(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.ethNetworkConfig(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.syncWithOttoman(anyBoolean())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.miningParameters(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.devMode(anyBoolean())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.nodePrivateKeyFile(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.metricsSystem(any())).thenReturn(mockControllerBuilder);
    when(mockControllerBuilder.privacyParameters(any())).thenReturn(mockControllerBuilder);

    when(mockSyncConfBuilder.build()).thenReturn(mockSyncConf);

    when(mockRunnerBuilder.vertx(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.pantheonController(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discovery(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bootstrapPeers(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discoveryHost(anyString())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.discoveryPort(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.maxPeers(anyInt())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.p2pEnabled(anyBoolean())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.jsonRpcConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.webSocketConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.permissioningConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.dataDir(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.bannedNodeIds(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsSystem(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.metricsConfiguration(any())).thenReturn(mockRunnerBuilder);
    when(mockRunnerBuilder.build()).thenReturn(mockRunner);
  }

  // Display outputs for debug purpose
  @After
  public void displayOutput() {
    TEST_LOGGER.info("Standard output {}", commandOutput.toString());
    TEST_LOGGER.info("Standard error {}", commandErrorOutput.toString());
  }

  CommandLine.Model.CommandSpec parseCommand(final String... args) {

    final TestPantheonCommand pantheonCommand =
        new TestPantheonCommand(
            mockLogger,
            mockBlockImporter,
            mockRunnerBuilder,
            mockControllerBuilder,
            mockSyncConfBuilder);

    // parse using Ansi.OFF to be able to assert on non formatted output results
    pantheonCommand.parse(
        new RunLast().useOut(outPrintStream).useAnsi(Ansi.OFF),
        new DefaultExceptionHandler<List<Object>>().useErr(errPrintStream).useAnsi(Ansi.OFF),
        args);
    return pantheonCommand.spec;
  }

  @CommandLine.Command
  static class TestPantheonCommand extends PantheonCommand {
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;

    TestPantheonCommand(
        final Logger mockLogger,
        final BlockImporter mockBlockImporter,
        final RunnerBuilder mockRunnerBuilder,
        final PantheonControllerBuilder mockControllerBuilder,
        final SynchronizerConfiguration.Builder mockSyncConfBuilder) {
      super(
          mockLogger,
          mockBlockImporter,
          mockRunnerBuilder,
          mockControllerBuilder,
          mockSyncConfBuilder);
    }
  }
}

package net.consensys.pantheon.cli;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.Runner;
import net.consensys.pantheon.RunnerBuilder;
import net.consensys.pantheon.controller.PantheonController;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import net.consensys.pantheon.util.BlockImporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine.DefaultExceptionHandler;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.RunLast;

@RunWith(MockitoJUnitRunner.class)
public abstract class CommandTestAbstract {

  private final Logger LOGGER = LogManager.getLogger();

  final ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
  private final PrintStream outPrintStream = new PrintStream(commandOutput);

  final ByteArrayOutputStream commandErrorOutput = new ByteArrayOutputStream();
  private final PrintStream errPrintStream = new PrintStream(commandErrorOutput);

  @Mock RunnerBuilder mockRunnerBuilder;
  @Mock Runner mockRunner;
  @Mock PantheonControllerBuilder mockControllerBuilder;
  @Mock SynchronizerConfiguration.Builder mockSyncConfBuilder;
  @Mock SynchronizerConfiguration mockSyncConf;
  @Mock PantheonController<?, ?> mockController;
  @Mock BlockImporter mockBlockImporter;

  @Captor ArgumentCaptor<Collection<String>> stringListArgumentCaptor;
  @Captor ArgumentCaptor<Path> pathArgumentCaptor;
  @Captor ArgumentCaptor<File> fileArgumentCaptor;
  @Captor ArgumentCaptor<String> stringArgumentCaptor;
  @Captor ArgumentCaptor<Integer> intArgumentCaptor;
  @Captor ArgumentCaptor<JsonRpcConfiguration> jsonRpcConfigArgumentCaptor;
  @Captor ArgumentCaptor<WebSocketConfiguration> wsRpcConfigArgumentCaptor;

  @Before
  public void initMocks() throws Exception {
    // doReturn used because of generic PantheonController
    Mockito.doReturn(mockController)
        .when(mockControllerBuilder)
        .build(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt());

    when(mockSyncConfBuilder.build()).thenReturn(mockSyncConf);
  }

  // Display outputs for debug purpose
  @After
  public void displayOutput() {
    LOGGER.info("Standard output {}", commandOutput.toString());
    LOGGER.info("Standard error {}", commandErrorOutput.toString());
  }

  void parseCommand(final String... args) {

    final PantheonCommand pantheonCommand =
        new PantheonCommand(
            mockBlockImporter, null, mockRunnerBuilder, mockControllerBuilder, mockSyncConfBuilder);

    // parse using Ansi.OFF to be able to assert on non formatted output results
    pantheonCommand.parse(
        new RunLast().useOut(outPrintStream).useAnsi(Ansi.OFF),
        new DefaultExceptionHandler<List<Object>>().useErr(errPrintStream).useAnsi(Ansi.OFF),
        args);
  }
}

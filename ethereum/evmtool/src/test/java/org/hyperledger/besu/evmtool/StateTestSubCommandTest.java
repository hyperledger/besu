package org.hyperledger.besu.evmtool;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.evmtool.exception.UnsupportedForkException;

import org.junit.Test;
import picocli.CommandLine;

public class StateTestSubCommandTest {

  @Test
  public void shouldDetectUnsupportedFork() {
    final StateTestSubCommand stateTestSubCommand = new StateTestSubCommand(new EvmToolCommand());
    final CommandLine cmd = new CommandLine(stateTestSubCommand);
    cmd.parseArgs(
        StateTestSubCommandTest.class.getResource("unsupported-fork-state-test.json").getPath());
    assertThatThrownBy(stateTestSubCommand::run)
        .hasMessageContaining("Fork 'UnknownFork' not supported")
        .isInstanceOf(UnsupportedForkException.class);
  }

  @Test
  public void shouldWorkWithValidStateTest() {
    final StateTestSubCommand stateTestSubCommand = new StateTestSubCommand(new EvmToolCommand());
    final CommandLine cmd = new CommandLine(stateTestSubCommand);
    cmd.parseArgs(StateTestSubCommandTest.class.getResource("valid-state-test.json").getPath());
    stateTestSubCommand.run();
  }
}

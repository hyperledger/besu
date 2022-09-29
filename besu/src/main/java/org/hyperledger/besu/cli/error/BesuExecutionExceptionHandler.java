package org.hyperledger.besu.cli.error;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public class BesuExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {
  @Override
  public int handleExecutionException(
      final Exception ex,
      final CommandLine commandLine,
      final CommandLine.ParseResult parseResult) {
    final CommandSpec spec = commandLine.getCommandSpec();
    commandLine.getOut().println(ex);
    return commandLine.getExitCodeExceptionMapper() != null
        ? commandLine.getExitCodeExceptionMapper().getExitCode(ex)
        : spec.exitCodeOnExecutionException();
  }
}

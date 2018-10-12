package tech.pegasys.pantheon;

import static picocli.CommandLine.defaultExceptionHandler;

import tech.pegasys.pantheon.cli.PantheonCommand;
import tech.pegasys.pantheon.cli.PantheonControllerBuilder;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration.Builder;
import tech.pegasys.pantheon.util.BlockImporter;
import tech.pegasys.pantheon.util.BlockchainImporter;

import picocli.CommandLine.RunLast;

public final class Pantheon {
  private static final int SUCCESS_EXIT_CODE = 0;
  private static final int ERROR_EXIT_CODE = 1;

  public static void main(final String... args) {

    final PantheonCommand pantheonCommand =
        new PantheonCommand(
            new BlockImporter(),
            new BlockchainImporter(),
            new RunnerBuilder(),
            new PantheonControllerBuilder(),
            new Builder());

    pantheonCommand.parse(
        new RunLast().andExit(SUCCESS_EXIT_CODE),
        defaultExceptionHandler().andExit(ERROR_EXIT_CODE),
        args);
  }
}

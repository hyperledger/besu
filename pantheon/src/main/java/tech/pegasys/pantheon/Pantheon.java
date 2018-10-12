package net.consensys.pantheon;

import static picocli.CommandLine.defaultExceptionHandler;

import net.consensys.pantheon.cli.PantheonCommand;
import net.consensys.pantheon.cli.PantheonControllerBuilder;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration.Builder;
import net.consensys.pantheon.util.BlockImporter;
import net.consensys.pantheon.util.BlockchainImporter;

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

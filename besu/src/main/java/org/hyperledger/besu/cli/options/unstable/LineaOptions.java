package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.ethereum.linea.LineaParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import picocli.CommandLine;

public class LineaOptions implements CLIOptions<LineaParameters> {
  private static final String TRANSACTION_MAX_CALLDATA_SIZE = "--Xtransaction-max-calldata-size";
  private static final String BLOCK_MAX_CALLDATA_SIZE = "--Xblock-max-calldata-size";
  ;

  public static LineaOptions create() {
    return new LineaOptions();
  }

  @CommandLine.Option(
      hidden = true,
      names = {TRANSACTION_MAX_CALLDATA_SIZE},
      paramLabel = "<INTEGER>",
      description =
          "If specified, overrides the max size in bytes allowed in the transaction calldata field, specified by the current hard fork")
  private Integer transactionMaxCalldataSize;

  @CommandLine.Option(
      hidden = true,
      names = {BLOCK_MAX_CALLDATA_SIZE},
      paramLabel = "<INTEGER>",
      description =
          "If specified, overrides the max size in bytes of the sum of all transaction calldata fields contained in a block, specified by the current hard fork")
  private Integer blockMaxCalldataSize;

  public Optional<Integer> getTransactionMaxCalldataSize() {
    return Optional.ofNullable(transactionMaxCalldataSize);
  }

  public Optional<Integer> getBlockMaxCalldataSize() {
    return Optional.ofNullable(blockMaxCalldataSize);
  }

  @Override
  public LineaParameters toDomainObject() {
    return new LineaParameters(transactionMaxCalldataSize, blockMaxCalldataSize);
  }

  @Override
  public List<String> getCLIOptions() {
    final List<String> cliOptions = new ArrayList<>(2);
    getTransactionMaxCalldataSize()
        .ifPresent(size -> cliOptions.add(TRANSACTION_MAX_CALLDATA_SIZE + "=" + size));
    getBlockMaxCalldataSize()
        .ifPresent(size -> cliOptions.add(BLOCK_MAX_CALLDATA_SIZE + "=" + size));
    return cliOptions;
  }
}

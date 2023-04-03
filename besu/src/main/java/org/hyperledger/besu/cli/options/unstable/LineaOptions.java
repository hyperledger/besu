package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.ethereum.linea.LineaParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import picocli.CommandLine;

public class LineaOptions implements CLIOptions<LineaParameters> {
  private static final String TRANSACTION_MAX_CALLDATA_SIZE = "--Xtransaction-max-calldata-size";

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

  public Optional<Integer> getTransactionMaxCalldataSize() {
    return Optional.ofNullable(transactionMaxCalldataSize);
  }

  @Override
  public LineaParameters toDomainObject() {
    return new LineaParameters(transactionMaxCalldataSize);
  }

  @Override
  public List<String> getCLIOptions() {
    final List<String> cliOptions = new ArrayList<>(1);
    getTransactionMaxCalldataSize()
        .ifPresent(size -> cliOptions.add(TRANSACTION_MAX_CALLDATA_SIZE + "=" + size));
    return cliOptions;
  }
}

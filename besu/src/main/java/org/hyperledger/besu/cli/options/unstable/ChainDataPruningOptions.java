package org.hyperledger.besu.cli.options.unstable;

import picocli.CommandLine;

public class ChainDataPruningOptions {

  public static final long DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED = 1024;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xchain-data-pruning-enabled"},
      description =
          "Enable the chain pruner to actively prune old chain data (default: ${DEFAULT-VALUE})")
  private final Boolean chainDataPruningEnabled = Boolean.FALSE;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xchain-data-pruning-blocks-retained"},
      description =
          "The number of recent blocks for which to keep the chain data. Must be >= 1024 (default: ${DEFAULT-VALUE})")
  private final Long chainDataPruningBlocksRetained =
      DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED;

  public static ChainDataPruningOptions create() {
    return new ChainDataPruningOptions();
  }

  public Boolean getChainDataPruningEnabled() {
    return chainDataPruningEnabled;
  }

  public Long getChainDataPruningBlocksRetained() {
    return chainDataPruningBlocksRetained;
  }
}

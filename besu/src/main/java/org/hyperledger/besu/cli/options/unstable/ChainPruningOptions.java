/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.cli.options.unstable;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.ethereum.chain.ChainPrunerConfiguration;
import org.hyperledger.besu.util.number.PositiveNumber;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

/** The Chain pruning CLI options. */
public class ChainPruningOptions implements CLIOptions<ChainPrunerConfiguration> {
  private static final String CHAIN_PRUNING_ENABLED_FLAG = "--Xchain-pruning-enabled";
  private static final String CHAIN_PRUNING_BLOCKS_RETAINED_FLAG =
      "--Xchain-pruning-blocks-retained";
  private static final String CHAIN_PRUNING_FREQUENCY_FLAG = "--Xchain-pruning-frequency";
  /** The constant DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED. */
  public static final long DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED = 512;
  // TODO find a way to avoid pruning blocks after or equal pivot block during initial sync
  /** The constant DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY. */
  public static final int DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY = 256;

  @CommandLine.Option(
      hidden = true,
      names = {CHAIN_PRUNING_ENABLED_FLAG},
      description =
          "Enable the chain pruner to actively prune old chain data (default: ${DEFAULT-VALUE})")
  private final Boolean chainDataPruningEnabled = Boolean.FALSE;

  @CommandLine.Option(
      hidden = true,
      names = {CHAIN_PRUNING_BLOCKS_RETAINED_FLAG},
      description =
          "The number of recent blocks for which to keep the chain data. Must be >= "
              + DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED
              + " (default: ${DEFAULT-VALUE})")
  private final Long chainDataPruningBlocksRetained =
      DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED;

  @CommandLine.Option(
      hidden = true,
      names = {CHAIN_PRUNING_FREQUENCY_FLAG},
      description =
          "The number of blocks added to the chain between two pruning operations. Must be non-negative (default: ${DEFAULT-VALUE})")
  private final PositiveNumber chainDataPruningBlocksFrequency =
      PositiveNumber.fromInt(DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY);

  /**
   * Create chain pruning options.
   *
   * @return the chain pruning options
   */
  public static ChainPruningOptions create() {
    return new ChainPruningOptions();
  }

  /**
   * Gets chain data pruning enabled.
   *
   * @return the chain data pruning enabled
   */
  public Boolean getChainDataPruningEnabled() {
    return chainDataPruningEnabled;
  }

  /**
   * Gets chain data pruning blocks retained.
   *
   * @return the chain data pruning blocks retained
   */
  public Long getChainDataPruningBlocksRetained() {
    return chainDataPruningBlocksRetained;
  }

  @Override
  public ChainPrunerConfiguration toDomainObject() {
    return new ChainPrunerConfiguration(
        chainDataPruningEnabled,
        chainDataPruningBlocksRetained,
        chainDataPruningBlocksFrequency.getValue());
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        CHAIN_PRUNING_ENABLED_FLAG,
        chainDataPruningEnabled.toString(),
        CHAIN_PRUNING_BLOCKS_RETAINED_FLAG,
        chainDataPruningBlocksRetained.toString(),
        CHAIN_PRUNING_FREQUENCY_FLAG,
        chainDataPruningBlocksFrequency.toString());
  }
}

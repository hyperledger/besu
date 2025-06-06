/*
 * Copyright contributors to Hyperledger Besu.
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
 */
package org.hyperledger.besu.cli.options;

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
  private static final String CHAIN_PRUNING_BLOCKS_RETAINED_LIMIT_FLAG =
      "--Xchain-pruning-blocks-retained-limit";
  private static final String CHAIN_PRUNING_FREQUENCY_FLAG = "--Xchain-pruning-frequency";
  private static final String PRE_MERGE_PRUNING_QUANTITY_FLAG = "--Xpre-merge-pruning-quantity";

  /**
   * The "CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED_LIMIT" field sets the minimum limit for the
   * "CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED" value. For most networks, the default value of this
   * limit is the safest. Reducing this value requires careful consideration and understanding of
   * the potential implications. Lowering this limit may have unintended side effects.
   */
  public static final long CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED_LIMIT = 7200;

  /** The constant DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY. */
  public static final int DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY = 256;

  /** The constant DEFAULT_PRE_MERGE_PRUNING_QUANTITY. */
  public static final int DEFAULT_PRE_MERGE_PRUNING_QUANTITY = 100;

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
          "The number of recent blocks for which to keep the chain data. Should be >= "
              + CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED_LIMIT
              + " (default: ${DEFAULT-VALUE}). Unused if --Xhistory-expiry-prune is enabled")
  private final Long chainDataPruningBlocksRetained = CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED_LIMIT;

  @CommandLine.Option(
      hidden = true,
      names = {CHAIN_PRUNING_BLOCKS_RETAINED_LIMIT_FLAG},
      description =
          "Allows setting the limit below which no more blocks can be pruned. This prevents setting a value lower than this for "
              + CHAIN_PRUNING_BLOCKS_RETAINED_FLAG
              + ". This flag should be used with caution as reducing the limit may have unintended side effects."
              + " (default: ${DEFAULT-VALUE}). Unused if --Xhistory-expiry-prune is enabled")
  private final Long chainDataPruningBlocksRetainedLimit =
      CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED_LIMIT;

  @CommandLine.Option(
      hidden = true,
      names = {CHAIN_PRUNING_FREQUENCY_FLAG},
      description =
          "The number of blocks added to the chain between two pruning operations. Must be non-negative (default: ${DEFAULT-VALUE})")
  private final PositiveNumber chainDataPruningBlocksFrequency =
      PositiveNumber.fromInt(DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY);

  @CommandLine.Option(
      hidden = true,
      names = {PRE_MERGE_PRUNING_QUANTITY_FLAG},
      description =
          "The number of pre-merge blocks to prune per pruning operation. Must be non-negative (default: ${DEFAULT-VALUE})")
  private final PositiveNumber preMergePruningBlocksQuantity =
      PositiveNumber.fromInt(DEFAULT_PRE_MERGE_PRUNING_QUANTITY);

  /** Default Constructor. */
  ChainPruningOptions() {}

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

  /**
   * Get the configured number of retained blocks for chain pruning.
   *
   * @return the number of retained blocks
   */
  public Long getChainDataPruningBlocksRetainedLimit() {
    return chainDataPruningBlocksRetainedLimit;
  }

  @Override
  public ChainPrunerConfiguration toDomainObject() {
    return new ChainPrunerConfiguration(
        chainDataPruningEnabled,
        chainDataPruningBlocksRetained,
        chainDataPruningBlocksRetainedLimit,
        chainDataPruningBlocksFrequency.getValue(),
        preMergePruningBlocksQuantity.getValue());
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        CHAIN_PRUNING_ENABLED_FLAG,
        chainDataPruningEnabled.toString(),
        CHAIN_PRUNING_BLOCKS_RETAINED_FLAG,
        chainDataPruningBlocksRetained.toString(),
        CHAIN_PRUNING_BLOCKS_RETAINED_LIMIT_FLAG,
        chainDataPruningBlocksRetainedLimit.toString(),
        CHAIN_PRUNING_FREQUENCY_FLAG,
        chainDataPruningBlocksFrequency.toString(),
        PRE_MERGE_PRUNING_QUANTITY_FLAG,
        preMergePruningBlocksQuantity.toString());
  }
}

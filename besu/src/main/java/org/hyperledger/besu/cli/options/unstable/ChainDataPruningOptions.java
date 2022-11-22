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

import picocli.CommandLine;

public class ChainDataPruningOptions {

  public static final long DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED = 50400;
  public static final long DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY = 256;

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
          "The number of recent blocks for which to keep the chain data. Must be >= "
              + DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED
              + " (default: ${DEFAULT-VALUE})")
  private final Long chainDataPruningBlocksRetained =
      DEFAULT_CHAIN_DATA_PRUNING_MIN_BLOCKS_RETAINED;

  @CommandLine.Option(
      hidden = true,
      names = {"--Xchain-data-pruning-frequency"},
      description =
          "The number of blocks added to the chain between two pruning operations. Must be non-negative (default: ${DEFAULT-VALUE})")
  private final Long chainDataPruningBlocksFrequency = DEFAULT_CHAIN_DATA_PRUNING_FREQUENCY;

  public static ChainDataPruningOptions create() {
    return new ChainDataPruningOptions();
  }

  public Boolean getChainDataPruningEnabled() {
    return chainDataPruningEnabled;
  }

  public Long getChainDataPruningBlocksRetained() {
    return chainDataPruningBlocksRetained;
  }

  public Long getChainDataPruningBlocksFrequency() {
    return chainDataPruningBlocksFrequency;
  }
}

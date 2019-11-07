/*
 * Copyright ConsenSys AG.
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

import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class PrunerOptions implements CLIOptions<PrunerConfiguration> {
  private static final String BLOCKS_RETAINED_FLAG = "--Xpruning-blocks-retained";
  private static final String BLOCK_CONFIRMATIONS_FLAG = "--Xpruning-block-confirmations";

  @CommandLine.Option(
      names = {BLOCKS_RETAINED_FLAG},
      hidden = true,
      defaultValue = "1024",
      paramLabel = "<INTEGER>",
      description =
          "Minimum number of recent blocks for which to keep entire world state (default: ${DEFAULT-VALUE})",
      arity = "1")
  private long pruningBlocksRetained = PrunerConfiguration.DEFAULT_PRUNING_BLOCKS_RETAINED;

  @CommandLine.Option(
      names = {BLOCK_CONFIRMATIONS_FLAG},
      defaultValue = "10",
      hidden = true,
      paramLabel = "<INTEGER>",
      description =
          "Minimum number of confirmations on a block before marking begins (default: ${DEFAULT-VALUE})",
      arity = "1")
  private long pruningBlockConfirmations = PrunerConfiguration.DEFAULT_PRUNING_BLOCK_CONFIRMATIONS;

  public static PrunerOptions create() {
    return new PrunerOptions();
  }

  @Override
  public PrunerConfiguration toDomainObject() {
    return new PrunerConfiguration(pruningBlockConfirmations, pruningBlocksRetained);
  }

  public static PrunerOptions fromDomainObject(final PrunerConfiguration prunerConfiguration) {
    final PrunerOptions prunerOptions = new PrunerOptions();
    prunerOptions.pruningBlockConfirmations = prunerConfiguration.getBlockConfirmations();
    prunerOptions.pruningBlocksRetained = prunerConfiguration.getBlocksRetained();
    return prunerOptions;
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        BLOCKS_RETAINED_FLAG,
        String.valueOf(pruningBlocksRetained),
        BLOCK_CONFIRMATIONS_FLAG,
        String.valueOf(pruningBlockConfirmations));
  }
}

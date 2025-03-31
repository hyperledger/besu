/*
 * Copyright contributors to Besu.
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

import org.hyperledger.besu.ethereum.chain.PreMergeBlockPruningConfiguration;

import java.util.List;

import picocli.CommandLine;

/** The pre-merge block pruning CLI options */
public class PreMergeBlockPruningOptions implements CLIOptions<PreMergeBlockPruningConfiguration> {
  private static final String ENABLE_PRE_MERGE_BLOCK_PRUNING_FLAG =
      "--enable-pre-merge-block-pruning";
  private static final boolean ENABLE_PRE_MERGE_BLOCK_PRUNING_DEFAULT = false;
  private static final String PRE_MERGE_BLOCK_PRUNER_RANGE_FLAG = "--pre-merge-block-pruner-range";
  private static final int PRE_MERGE_BLOCK_PRUNER_RANGE_DEFAULT = 100;
  private static final String PRE_MERGE_BLOCK_PRUNER_SLEEP_TIME_FLAG =
      "--pre-merge-block-pruner-sleep-time";
  private static final long PRE_MERGE_BLOCK_PRUNER_SLEEP_TIME_DEFAULT = 1000;

  @CommandLine.Option(
      names = {ENABLE_PRE_MERGE_BLOCK_PRUNING_FLAG},
      description =
          "Enable the online pre-merge block pruner to actively prune pre-merge block data (default: ${DEFAULT-VALUE})")
  private final Boolean preMergeBlockPruningEnabled = ENABLE_PRE_MERGE_BLOCK_PRUNING_DEFAULT;

  @CommandLine.Option(
      names = {PRE_MERGE_BLOCK_PRUNER_RANGE_FLAG},
      description =
          "The number of pre-merge blocks to prune at a time. (default: ${DEFAULT-VALUE})")
  private final Integer preMergeBlockPrunerRange = PRE_MERGE_BLOCK_PRUNER_RANGE_DEFAULT;

  @CommandLine.Option(
      names = {PRE_MERGE_BLOCK_PRUNER_SLEEP_TIME_FLAG},
      description =
          "The time (in milliseconds) between pre-merge block pruner ranges being removed. (default: ${DEFAULT-VALUE})")
  private final Long preMergeBlockPrunerSleepTime = PRE_MERGE_BLOCK_PRUNER_SLEEP_TIME_DEFAULT;

  /** Default constructor */
  private PreMergeBlockPruningOptions() {}

  /**
   * Creates a PreMergeBlockPruningOptions instance
   *
   * @return a PreMergeBlockPruningOptions instance
   */
  public static PreMergeBlockPruningOptions create() {
    return new PreMergeBlockPruningOptions();
  }

  @Override
  public PreMergeBlockPruningConfiguration toDomainObject() {
    return new PreMergeBlockPruningConfiguration(
        preMergeBlockPruningEnabled, preMergeBlockPrunerRange, preMergeBlockPrunerSleepTime);
  }

  @Override
  public List<String> getCLIOptions() {
    return List.of(
        ENABLE_PRE_MERGE_BLOCK_PRUNING_FLAG, preMergeBlockPruningEnabled.toString(),
        PRE_MERGE_BLOCK_PRUNER_RANGE_FLAG, preMergeBlockPrunerRange.toString(),
        PRE_MERGE_BLOCK_PRUNER_SLEEP_TIME_FLAG, preMergeBlockPrunerSleepTime.toString());
  }
}

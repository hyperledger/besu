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
 *
 */

package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_TRIE_LOG_PRUNING_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_TRIE_LOG_PRUNING_LIMIT;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Data storage CLI options. */
public class DataStorageOptions implements CLIOptions<DataStorageConfiguration> {

  private static final String DATA_STORAGE_FORMAT = "--data-storage-format";

  private static final String BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD =
      "--bonsai-historical-block-limit";



  // Use Bonsai DB
  @Option(
      names = {DATA_STORAGE_FORMAT},
      description =
          "Format to store trie data in.  Either FOREST or BONSAI (default: ${DEFAULT-VALUE}).",
      arity = "1")
  private DataStorageFormat dataStorageFormat = DataStorageFormat.FOREST;

  @Option(
      names = {BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD, "--bonsai-maximum-back-layers-to-load"},
      paramLabel = "<LONG>",
      description =
          "Limit of historical layers that can be loaded with BONSAI (default: ${DEFAULT-VALUE}).",
      arity = "1")
  private Long bonsaiMaxLayersToLoad = DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD;

  @CommandLine.ArgGroup(validate = false)
  private final DataStorageOptions.Unstable unstableOptions = new Unstable();

  static class Unstable {
    private static final String BONSAI_LIMIT_TRIE_LOGS_ENABLED = "--Xbonsai-limit-trie-logs-enabled";
    private static final String BONSAI_TRIE_LOGS_RETENTION_THRESHOLD =
        "--Xbonsai-trie-logs-retention-threshold";
    private static final String BONSAI_TRIE_LOG_PRUNING_LIMIT = "--Xbonsai-trie-logs-pruning-limit";
    @CommandLine.Option(
        hidden = true,
        names = {BONSAI_LIMIT_TRIE_LOGS_ENABLED},
        description = "Enable trie log pruning. (default: ${DEFAULT-VALUE})")
    private boolean bonsaiTrieLogPruningEnabled = DEFAULT_BONSAI_TRIE_LOG_PRUNING_ENABLED;

    @CommandLine.Option(
        hidden = true,
        names = {BONSAI_TRIE_LOGS_RETENTION_THRESHOLD},
        description =
            "The number of blocks for which to retain trie logs. (default: ${DEFAULT-VALUE})")
    private long bonsaiTrieLogRetentionThreshold = DEFAULT_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;

    @CommandLine.Option(
        hidden = true,
        names = {BONSAI_TRIE_LOG_PRUNING_LIMIT},
        description =
            "The max number of blocks to load and prune trie logs for at startup. (default: ${DEFAULT-VALUE})")
    private int bonsaiTrieLogPruningLimit = DEFAULT_BONSAI_TRIE_LOG_PRUNING_LIMIT;
  }
  /**
   * Create data storage options.
   *
   * @return the data storage options
   */
  public static DataStorageOptions create() {
    return new DataStorageOptions();
  }

  /**
   * Validates the data storage options
   *
   * @param commandLine the full commandLine to check all the options specified by the user
   */
  public void validate(final CommandLine commandLine) {
    if (unstableOptions.bonsaiTrieLogPruningEnabled) {
      if (unstableOptions.bonsaiTrieLogRetentionThreshold
          < MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD) {
        throw new CommandLine.ParameterException(
            commandLine,
            String.format(
                "--Xbonsai-trie-log-retention-threshold minimum value is %d",
                MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD));
      }
      if (unstableOptions.bonsaiTrieLogPruningLimit <= 0) {
        throw new CommandLine.ParameterException(
            commandLine,
            String.format(
                "--Xbonsai-trie-log-pruning-limit=%d must be greater than 0",
                unstableOptions.bonsaiTrieLogPruningLimit));
      }
    }
  }

  static DataStorageOptions fromConfig(final DataStorageConfiguration domainObject) {
    final DataStorageOptions dataStorageOptions = DataStorageOptions.create();
    dataStorageOptions.dataStorageFormat = domainObject.getDataStorageFormat();
    dataStorageOptions.bonsaiMaxLayersToLoad = domainObject.getBonsaiMaxLayersToLoad();
    dataStorageOptions.unstableOptions.bonsaiTrieLogPruningEnabled =
        domainObject.getUnstable().getBonsaiTrieLogPruningEnabled();
    dataStorageOptions.unstableOptions.bonsaiTrieLogRetentionThreshold =
        domainObject.getUnstable().getBonsaiTrieLogRetentionThreshold();
    dataStorageOptions.unstableOptions.bonsaiTrieLogPruningLimit =
        domainObject.getUnstable().getBonsaiTrieLogPruningLimit();

    return dataStorageOptions;
  }

  @Override
  public DataStorageConfiguration toDomainObject() {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(dataStorageFormat)
        .bonsaiMaxLayersToLoad(bonsaiMaxLayersToLoad)
        .unstable(
            ImmutableDataStorageConfiguration.Unstable.builder()
                .bonsaiTrieLogPruningEnabled(unstableOptions.bonsaiTrieLogPruningEnabled)
                .bonsaiTrieLogRetentionThreshold(unstableOptions.bonsaiTrieLogRetentionThreshold)
                .bonsaiTrieLogPruningLimit(unstableOptions.bonsaiTrieLogPruningLimit)
                .build())
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new DataStorageOptions());
  }

  /**
   * Normalize data storage format string.
   *
   * @return the normalized string
   */
  public String normalizeDataStorageFormat() {
    return StringUtils.capitalize(dataStorageFormat.toString().toLowerCase());
  }
}

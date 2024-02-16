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
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_CODE_USING_CODE_HASH_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_LIMIT_TRIE_LOGS_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_TRIE_LOG_PRUNING_WINDOW_SIZE;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.MINIMUM_BONSAI_TRIE_LOG_RETENTION_LIMIT;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Data storage CLI options. */
public class DataStorageOptions implements CLIOptions<DataStorageConfiguration> {

  private static final String DATA_STORAGE_FORMAT = "--data-storage-format";

  /** The maximum number of historical layers to load. */
  public static final String BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD =
      "--bonsai-historical-block-limit";

  // Use Bonsai DB
  @Option(
      names = {DATA_STORAGE_FORMAT},
      description =
          "Format to store trie data in.  Either FOREST or BONSAI (default: ${DEFAULT-VALUE}).",
      arity = "1")
  private DataStorageFormat dataStorageFormat = DataStorageFormat.BONSAI;

  @Option(
      names = {BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD, "--bonsai-maximum-back-layers-to-load"},
      paramLabel = "<LONG>",
      description =
          "Limit of historical layers that can be loaded with BONSAI (default: ${DEFAULT-VALUE}). When using "
              + Unstable.BONSAI_LIMIT_TRIE_LOGS_ENABLED
              + " it will also be used as the number of layers of trie logs to retain.",
      arity = "1")
  private Long bonsaiMaxLayersToLoad = DEFAULT_BONSAI_MAX_LAYERS_TO_LOAD;

  @CommandLine.ArgGroup(validate = false)
  private final DataStorageOptions.Unstable unstableOptions = new Unstable();

  /** The unstable options for data storage. */
  public static class Unstable {
    private static final String BONSAI_LIMIT_TRIE_LOGS_ENABLED =
        "--Xbonsai-limit-trie-logs-enabled";

    /** The bonsai trie logs pruning window size. */
    public static final String BONSAI_TRIE_LOG_PRUNING_WINDOW_SIZE =
        "--Xbonsai-trie-logs-pruning-window-size";

    @CommandLine.Option(
        hidden = true,
        names = {BONSAI_LIMIT_TRIE_LOGS_ENABLED, "--Xbonsai-trie-log-pruning-enabled"},
        description =
            "Limit the number of trie logs that are retained. (default: ${DEFAULT-VALUE})")
    private boolean bonsaiLimitTrieLogsEnabled = DEFAULT_BONSAI_LIMIT_TRIE_LOGS_ENABLED;

    @CommandLine.Option(
        hidden = true,
        names = {BONSAI_TRIE_LOG_PRUNING_WINDOW_SIZE},
        description =
            "The max number of blocks to load and prune trie logs for at startup. (default: ${DEFAULT-VALUE})")
    private int bonsaiTrieLogPruningWindowSize = DEFAULT_BONSAI_TRIE_LOG_PRUNING_WINDOW_SIZE;

    @CommandLine.Option(
        hidden = true,
        names = {"--Xbonsai-code-using-code-hash-enabled"},
        arity = "1",
        description =
            "Enables code storage using code hash instead of by account hash. (default: ${DEFAULT-VALUE})")
    private boolean bonsaiCodeUsingCodeHashEnabled = DEFAULT_BONSAI_CODE_USING_CODE_HASH_ENABLED;
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
    if (unstableOptions.bonsaiLimitTrieLogsEnabled) {
      if (bonsaiMaxLayersToLoad < MINIMUM_BONSAI_TRIE_LOG_RETENTION_LIMIT) {
        throw new CommandLine.ParameterException(
            commandLine,
            String.format(
                BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD + " minimum value is %d",
                MINIMUM_BONSAI_TRIE_LOG_RETENTION_LIMIT));
      }
      if (unstableOptions.bonsaiTrieLogPruningWindowSize <= 0) {
        throw new CommandLine.ParameterException(
            commandLine,
            String.format(
                Unstable.BONSAI_TRIE_LOG_PRUNING_WINDOW_SIZE + "=%d must be greater than 0",
                unstableOptions.bonsaiTrieLogPruningWindowSize));
      }
      if (unstableOptions.bonsaiTrieLogPruningWindowSize <= bonsaiMaxLayersToLoad) {
        throw new CommandLine.ParameterException(
            commandLine,
            String.format(
                Unstable.BONSAI_TRIE_LOG_PRUNING_WINDOW_SIZE
                    + "=%d must be greater than "
                    + BONSAI_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD
                    + "=%d",
                unstableOptions.bonsaiTrieLogPruningWindowSize,
                bonsaiMaxLayersToLoad));
      }
    }
  }

  public static DataStorageOptions fromConfig(final DataStorageConfiguration domainObject) {
    final DataStorageOptions dataStorageOptions = DataStorageOptions.create();
    dataStorageOptions.dataStorageFormat = domainObject.getDataStorageFormat();
    dataStorageOptions.bonsaiMaxLayersToLoad = domainObject.getBonsaiMaxLayersToLoad();
    dataStorageOptions.unstableOptions.bonsaiLimitTrieLogsEnabled =
        domainObject.getUnstable().getBonsaiLimitTrieLogsEnabled();
    dataStorageOptions.unstableOptions.bonsaiTrieLogPruningWindowSize =
        domainObject.getUnstable().getBonsaiTrieLogPruningWindowSize();
    dataStorageOptions.unstableOptions.bonsaiCodeUsingCodeHashEnabled =
        domainObject.getUnstable().getBonsaiCodeStoredByCodeHashEnabled();

    return dataStorageOptions;
  }

  @Override
  public DataStorageConfiguration toDomainObject() {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(dataStorageFormat)
        .bonsaiMaxLayersToLoad(bonsaiMaxLayersToLoad)
        .unstable(
            ImmutableDataStorageConfiguration.Unstable.builder()
                .bonsaiLimitTrieLogsEnabled(unstableOptions.bonsaiLimitTrieLogsEnabled)
                .bonsaiTrieLogPruningWindowSize(unstableOptions.bonsaiTrieLogPruningWindowSize)
                .bonsaiCodeStoredByCodeHashEnabled(unstableOptions.bonsaiCodeUsingCodeHashEnabled)
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

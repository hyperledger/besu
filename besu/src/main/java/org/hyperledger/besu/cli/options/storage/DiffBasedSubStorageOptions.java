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
package org.hyperledger.besu.cli.options.storage;

import static org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration.DEFAULT_LIMIT_TRIE_LOGS_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration.DEFAULT_MAX_LAYERS_TO_LOAD;
import static org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration.DEFAULT_TRIE_LOG_PRUNING_WINDOW_SIZE;
import static org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration.DiffBasedUnstable.DEFAULT_CODE_USING_CODE_HASH_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration.DiffBasedUnstable.DEFAULT_FULL_FLAT_DB_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration.MINIMUM_TRIE_LOG_RETENTION_LIMIT;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.worldstate.DiffBasedSubStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDiffBasedSubStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Data storage CLI options. */
public class DiffBasedSubStorageOptions implements CLIOptions<DiffBasedSubStorageConfiguration> {

  /** The maximum number of historical layers to load. */
  public static final String MAX_LAYERS_TO_LOAD = "--bonsai-historical-block-limit";

  @Option(
      names = {MAX_LAYERS_TO_LOAD, "--bonsai-maximum-back-layers-to-load"},
      paramLabel = "<LONG>",
      description =
          "Limit of historical layers that can be loaded with BONSAI (default: ${DEFAULT-VALUE}). When using "
              + LIMIT_TRIE_LOGS_ENABLED
              + " it will also be used as the number of layers of trie logs to retain.",
      arity = "1")
  private Long maxLayersToLoad = DEFAULT_MAX_LAYERS_TO_LOAD;

  /** The bonsai limit trie logs enabled option name */
  public static final String LIMIT_TRIE_LOGS_ENABLED = "--bonsai-limit-trie-logs-enabled";

  /** The bonsai trie logs pruning window size. */
  public static final String TRIE_LOG_PRUNING_WINDOW_SIZE =
      "--bonsai-trie-logs-pruning-window-size";

  // TODO --Xbonsai-limit-trie-logs-enabled and --Xbonsai-trie-log-pruning-enabled are deprecated,
  // remove in a future release
  @SuppressWarnings("ExperimentalCliOptionMustBeCorrectlyDisplayed")
  @Option(
      names = {
        LIMIT_TRIE_LOGS_ENABLED,
        "--Xbonsai-limit-trie-logs-enabled", // deprecated
        "--Xbonsai-trie-log-pruning-enabled" // deprecated
      },
      fallbackValue = "true",
      description = "Limit the number of trie logs that are retained. (default: ${DEFAULT-VALUE})")
  private Boolean limitTrieLogsEnabled = DEFAULT_LIMIT_TRIE_LOGS_ENABLED;

  // TODO --Xbonsai-trie-logs-pruning-window-size is deprecated, remove in a future release
  @SuppressWarnings("ExperimentalCliOptionMustBeCorrectlyDisplayed")
  @Option(
      names = {
        TRIE_LOG_PRUNING_WINDOW_SIZE,
        "--Xbonsai-trie-logs-pruning-window-size" // deprecated
      },
      description =
          "The max number of blocks to load and prune trie logs for at startup. (default: ${DEFAULT-VALUE})")
  private Integer trieLogPruningWindowSize = DEFAULT_TRIE_LOG_PRUNING_WINDOW_SIZE;

  @CommandLine.ArgGroup(validate = false)
  private final DiffBasedSubStorageOptions.Unstable unstableOptions = new Unstable();

  /** Default Constructor. */
  DiffBasedSubStorageOptions() {}

  /** The unstable options for data storage. */
  public static class Unstable {

    // TODO: --Xsnapsync-synchronizer-flat-db-healing-enabled is deprecated, remove it in a future
    // release
    @Option(
        hidden = true,
        names = {
          "--Xbonsai-full-flat-db-enabled",
          "--Xsnapsync-synchronizer-flat-db-healing-enabled"
        },
        arity = "1",
        description = "Enables bonsai full flat database strategy. (default: ${DEFAULT-VALUE})")
    private Boolean fullFlatDbEnabled = DEFAULT_FULL_FLAT_DB_ENABLED;

    @Option(
        hidden = true,
        names = {"--Xbonsai-code-using-code-hash-enabled"},
        arity = "1",
        description =
            "Enables code storage using code hash instead of by account hash. (default: ${DEFAULT-VALUE})")
    private boolean codeUsingCodeHashEnabled = DEFAULT_CODE_USING_CODE_HASH_ENABLED;

    @Option(
        hidden = true,
        names = {"--Xbonsai-parallel-tx-processing-enabled"},
        arity = "1",
        description =
            "Enables parallelization of transactions to optimize processing speed by concurrently loading and executing necessary data in advance. (default: ${DEFAULT-VALUE})")
    private Boolean isParallelTxProcessingEnabled = false;

    /** Default Constructor. */
    Unstable() {}
  }

  /**
   * Create data storage options.
   *
   * @return the data storage options
   */
  public static DiffBasedSubStorageOptions create() {
    return new DiffBasedSubStorageOptions();
  }

  /**
   * Validates the data storage options
   *
   * @param commandLine the full commandLine to check all the options specified by the user
   * @param dataStorageFormat the selected data storage format which determines the validation rules
   *     to apply.
   */
  public void validate(final CommandLine commandLine, final DataStorageFormat dataStorageFormat) {
    if (DataStorageFormat.BONSAI == dataStorageFormat) {
      if (limitTrieLogsEnabled) {
        if (maxLayersToLoad < MINIMUM_TRIE_LOG_RETENTION_LIMIT) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  MAX_LAYERS_TO_LOAD + " minimum value is %d", MINIMUM_TRIE_LOG_RETENTION_LIMIT));
        }
        if (trieLogPruningWindowSize <= 0) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  TRIE_LOG_PRUNING_WINDOW_SIZE + "=%d must be greater than 0",
                  trieLogPruningWindowSize));
        }
        if (trieLogPruningWindowSize <= maxLayersToLoad) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  TRIE_LOG_PRUNING_WINDOW_SIZE
                      + "=%d must be greater than "
                      + MAX_LAYERS_TO_LOAD
                      + "=%d",
                  trieLogPruningWindowSize,
                  maxLayersToLoad));
        }
      }
    } else {
      if (unstableOptions.isParallelTxProcessingEnabled) {
        throw new CommandLine.ParameterException(
            commandLine,
            "Transaction parallelization is not supported unless operating in a 'diffbased' mode, such as Bonsai.");
      }
    }
  }

  /**
   * Converts to options from the configuration
   *
   * @param domainObject to be reversed
   * @return the options that correspond to the configuration
   */
  public static DiffBasedSubStorageOptions fromConfig(
      final DiffBasedSubStorageConfiguration domainObject) {
    final DiffBasedSubStorageOptions dataStorageOptions = DiffBasedSubStorageOptions.create();
    dataStorageOptions.maxLayersToLoad = domainObject.getMaxLayersToLoad();
    dataStorageOptions.limitTrieLogsEnabled = domainObject.getLimitTrieLogsEnabled();
    dataStorageOptions.trieLogPruningWindowSize = domainObject.getTrieLogPruningWindowSize();
    dataStorageOptions.unstableOptions.fullFlatDbEnabled =
        domainObject.getUnstable().getFullFlatDbEnabled();
    dataStorageOptions.unstableOptions.codeUsingCodeHashEnabled =
        domainObject.getUnstable().getCodeStoredByCodeHashEnabled();
    dataStorageOptions.unstableOptions.isParallelTxProcessingEnabled =
        domainObject.getUnstable().isParallelTxProcessingEnabled();

    return dataStorageOptions;
  }

  @Override
  public final DiffBasedSubStorageConfiguration toDomainObject() {
    return ImmutableDiffBasedSubStorageConfiguration.builder()
        .maxLayersToLoad(maxLayersToLoad)
        .limitTrieLogsEnabled(limitTrieLogsEnabled)
        .trieLogPruningWindowSize(trieLogPruningWindowSize)
        .unstable(
            ImmutableDiffBasedSubStorageConfiguration.DiffBasedUnstable.builder()
                .fullFlatDbEnabled(unstableOptions.fullFlatDbEnabled)
                .codeStoredByCodeHashEnabled(unstableOptions.codeUsingCodeHashEnabled)
                .isParallelTxProcessingEnabled(unstableOptions.isParallelTxProcessingEnabled)
                .build())
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new DiffBasedSubStorageOptions());
  }
}

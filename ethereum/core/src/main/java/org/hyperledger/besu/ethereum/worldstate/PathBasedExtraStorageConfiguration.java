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

import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.DEFAULT_LIMIT_TRIE_LOGS_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.DEFAULT_MAX_LAYERS_TO_LOAD;
import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.DEFAULT_PARALLEL_TX_PROCESSING;
import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.DEFAULT_TRIE_LOG_PRUNING_BATCH_SIZE;
import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.DEFAULT_TRIE_LOG_RETENTION_LIMIT;
import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.MINIMUM_TRIE_LOG_RETENTION_LIMIT;
import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.PathBasedUnstable.DEFAULT_CODE_USING_CODE_HASH_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration.PathBasedUnstable.DEFAULT_FULL_FLAT_DB_ENABLED;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.worldstate.ImmutablePathBasedExtraStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;

import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Data storage CLI options. */
public class PathBasedExtraStorageOptions
    implements CLIOptions<PathBasedExtraStorageConfiguration> {

  /** The maximum number of historical layers to load. */
  public static final String MAX_LAYERS_TO_LOAD = "--bonsai-historical-block-limit";

  @Option(
      names = {MAX_LAYERS_TO_LOAD},
      paramLabel = "<LONG>",
      description =
          "Limit of historical layers that can be loaded with BONSAI (default: ${DEFAULT-VALUE}). When using "
              + LIMIT_TRIE_LOGS_ENABLED
              + " it will also be used as the number of layers of trie logs to retain.",
      arity = "1")
  private Long maxLayersToLoad = DEFAULT_MAX_LAYERS_TO_LOAD;

  /** The bonsai limit trie logs enabled option name */
  public static final String LIMIT_TRIE_LOGS_ENABLED = "--bonsai-limit-trie-logs-enabled";

  /** The bonsai trie logs retention limit option name */
  public static final String TRIE_LOG_RETENTION_LIMIT = "--bonsai-trie-logs-retention-limit";

  /** The bonsai trie logs pruning batch size. */
  public static final String TRIE_LOG_PRUNING_BATCH_SIZE =
      "--bonsai-trie-logs-pruning-batch-size";

  /** The bonsai parallel tx processing enabled option name. */
  public static final String PARALLEL_TX_PROCESSING_ENABLED =
      "--bonsai-parallel-tx-processing-enabled";

  @Option(
      names = {LIMIT_TRIE_LOGS_ENABLED},
      fallbackValue = "true",
      description = "Limit the number of trie logs that are retained. (default: ${DEFAULT-VALUE})")
  private Boolean limitTrieLogsEnabled = DEFAULT_LIMIT_TRIE_LOGS_ENABLED;

  @Option(
      names = {TRIE_LOG_RETENTION_LIMIT},
      description =
          "The number of blocks to retain trie logs for. (default: ${DEFAULT-VALUE}). If not set, uses --bonsai-historical-block-limit as default.")
  private Long trieLogRetentionLimit = null;

  @Option(
      names = {
        TRIE_LOG_PRUNING_BATCH_SIZE,
      },
      description =
          "The max number of blocks to load and prune trie logs for at startup. (default: ${DEFAULT-VALUE})")
  private Integer trieLogPruningBatchSize = DEFAULT_TRIE_LOG_PRUNING_BATCH_SIZE;

  @Option(
      names = {PARALLEL_TX_PROCESSING_ENABLED},
      arity = "1",
      description =
          "Enables parallelization of transactions to optimize processing speed by concurrently loading and executing necessary data in advance. Will be ignored if --data-storage-format is not bonsai (default: ${DEFAULT-VALUE})")
  private Boolean isParallelTxProcessingEnabled = DEFAULT_PARALLEL_TX_PROCESSING;

  @CommandLine.ArgGroup(validate = false)
  private final PathBasedExtraStorageOptions.Unstable unstableOptions = new Unstable();

  /** Default Constructor. */
  PathBasedExtraStorageOptions() {}

  /** The unstable options for data storage. */
  public static class Unstable {

    @Option(
        hidden = true,
        names = {"--Xbonsai-full-flat-db-enabled"},
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

    /** Default Constructor. */
    Unstable() {}
  }

  /**
   * Create data storage options.
   *
   * @return the data storage options
   */
  public static PathBasedExtraStorageOptions create() {
    return new PathBasedExtraStorageOptions();
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
        final long effectiveRetentionLimit =
            trieLogRetentionLimit != null ? trieLogRetentionLimit : maxLayersToLoad;
        if (effectiveRetentionLimit < MINIMUM_TRIE_LOG_RETENTION_LIMIT) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  TRIE_LOG_RETENTION_LIMIT + " minimum value is %d",
                  MINIMUM_TRIE_LOG_RETENTION_LIMIT));
        }
        if (trieLogPruningBatchSize <= 0) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  TRIE_LOG_PRUNING_BATCH_SIZE + "=%d must be greater than 0",
                  trieLogPruningBatchSize));
        }
        if (trieLogPruningBatchSize <= effectiveRetentionLimit) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  TRIE_LOG_PRUNING_BATCH_SIZE
                      + "=%d must be greater than retention limit=%d",
                  trieLogPruningBatchSize,
                  effectiveRetentionLimit));
        }
      }
    }
  }

  /**
   * Converts to options from the configuration
   *
   * @param domainObject to be reversed
   * @return the options that correspond to the configuration
   */
  public static PathBasedExtraStorageOptions fromConfig(
      final PathBasedExtraStorageConfiguration domainObject) {
    final PathBasedExtraStorageOptions dataStorageOptions = PathBasedExtraStorageOptions.create();
    dataStorageOptions.maxLayersToLoad = domainObject.getMaxLayersToLoad();
    dataStorageOptions.limitTrieLogsEnabled = domainObject.getLimitTrieLogsEnabled();
    // Only set trieLogRetentionLimit if it differs from maxLayersToLoad (i.e., was explicitly set)
    final long retentionLimit = domainObject.getTrieLogRetentionLimit();
    if (retentionLimit != domainObject.getMaxLayersToLoad()) {
      dataStorageOptions.trieLogRetentionLimit = retentionLimit;
    } else {
      dataStorageOptions.trieLogRetentionLimit = null; // Use default (maxLayersToLoad)
    }
    dataStorageOptions.trieLogPruningBatchSize = domainObject.getTrieLogPruningBatchSize();
    dataStorageOptions.unstableOptions.fullFlatDbEnabled =
        domainObject.getUnstable().getFullFlatDbEnabled();
    dataStorageOptions.unstableOptions.codeUsingCodeHashEnabled =
        domainObject.getUnstable().getCodeStoredByCodeHashEnabled();
    dataStorageOptions.isParallelTxProcessingEnabled =
        domainObject.getParallelTxProcessingEnabled();

    return dataStorageOptions;
  }

  @Override
  public final PathBasedExtraStorageConfiguration toDomainObject() {
    // If retention limit is not explicitly set, use historical block limit as default
    // (for backward compatibility), otherwise use the configured default (14,400)
    final long effectiveRetentionLimit =
        trieLogRetentionLimit != null ? trieLogRetentionLimit : maxLayersToLoad;
    return ImmutablePathBasedExtraStorageConfiguration.builder()
        .maxLayersToLoad(maxLayersToLoad)
        .limitTrieLogsEnabled(limitTrieLogsEnabled)
        .trieLogRetentionLimit(effectiveRetentionLimit)
        .trieLogPruningBatchSize(trieLogPruningBatchSize)
        .parallelTxProcessingEnabled(isParallelTxProcessingEnabled)
        .unstable(
            ImmutablePathBasedExtraStorageConfiguration.PathBasedUnstable.builder()
                .fullFlatDbEnabled(unstableOptions.fullFlatDbEnabled)
                .codeStoredByCodeHashEnabled(unstableOptions.codeUsingCodeHashEnabled)
                .build())
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new PathBasedExtraStorageOptions());
  }
}

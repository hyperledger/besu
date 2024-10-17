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
package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.DEFAULT_DIFFBASED_LIMIT_TRIE_LOGS_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.DEFAULT_DIFFBASED_MAX_LAYERS_TO_LOAD;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.DEFAULT_DIFFBASED_TRIE_LOG_PRUNING_WINDOW_SIZE;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.DEFAULT_RECEIPT_COMPACTION_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.MINIMUM_DIFFBASED_TRIE_LOG_RETENTION_LIMIT;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_BONSAI_FULL_FLAT_DB_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_DIFFBASED_CODE_USING_CODE_HASH_ENABLED;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.DEFAULT_VERKLE_STEM_FLAT_DB_ENABLED;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Data storage CLI options. */
public class DataStorageOptions implements CLIOptions<DataStorageConfiguration> {

  private static final String DATA_STORAGE_FORMAT = "--data-storage-format";

  /** The maximum number of historical layers to load. */
  public static final String DIFFBASED_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD =
      "--bonsai-historical-block-limit";

  // Use Bonsai DB
  @Option(
      names = {DATA_STORAGE_FORMAT},
      description =
          "Format to store trie data in.  Either FOREST or BONSAI (default: ${DEFAULT-VALUE}).",
      arity = "1")
  private DataStorageFormat dataStorageFormat = DataStorageFormat.BONSAI;

  @Option(
      names = {DIFFBASED_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD, "--bonsai-maximum-back-layers-to-load"},
      paramLabel = "<LONG>",
      description =
          "Limit of historical layers that can be loaded with BONSAI (default: ${DEFAULT-VALUE}). When using "
              + DIFFBASED_LIMIT_TRIE_LOGS_ENABLED
              + " it will also be used as the number of layers of trie logs to retain.",
      arity = "1")
  private Long diffBasedMaxLayersToLoad = DEFAULT_DIFFBASED_MAX_LAYERS_TO_LOAD;

  /** The bonsai limit trie logs enabled option name */
  public static final String DIFFBASED_LIMIT_TRIE_LOGS_ENABLED = "--bonsai-limit-trie-logs-enabled";

  /** The bonsai trie logs pruning window size. */
  public static final String DIFFBASED_TRIE_LOG_PRUNING_WINDOW_SIZE =
      "--bonsai-trie-logs-pruning-window-size";

  // TODO --Xbonsai-limit-trie-logs-enabled and --Xbonsai-trie-log-pruning-enabled are deprecated,
  // remove in a future release
  @SuppressWarnings("ExperimentalCliOptionMustBeCorrectlyDisplayed")
  @CommandLine.Option(
      names = {
        DIFFBASED_LIMIT_TRIE_LOGS_ENABLED,
        "--Xbonsai-limit-trie-logs-enabled", // deprecated
        "--Xbonsai-trie-log-pruning-enabled" // deprecated
      },
      fallbackValue = "true",
      description = "Limit the number of trie logs that are retained. (default: ${DEFAULT-VALUE})")
  private Boolean diffBasedLimitTrieLogsEnabled = DEFAULT_DIFFBASED_LIMIT_TRIE_LOGS_ENABLED;

  // TODO --Xbonsai-trie-logs-pruning-window-size is deprecated, remove in a future release
  @SuppressWarnings("ExperimentalCliOptionMustBeCorrectlyDisplayed")
  @CommandLine.Option(
      names = {
        DIFFBASED_TRIE_LOG_PRUNING_WINDOW_SIZE,
        "--Xbonsai-trie-logs-pruning-window-size" // deprecated
      },
      description =
          "The max number of blocks to load and prune trie logs for at startup. (default: ${DEFAULT-VALUE})")
  private Integer diffBasedTrieLogPruningWindowSize =
      DEFAULT_DIFFBASED_TRIE_LOG_PRUNING_WINDOW_SIZE;

  @Option(
      names = "--receipt-compaction-enabled",
      description = "Enables compact storing of receipts (default: ${DEFAULT-VALUE})",
      fallbackValue = "true")
  private Boolean receiptCompactionEnabled = DEFAULT_RECEIPT_COMPACTION_ENABLED;

  @CommandLine.ArgGroup(validate = false)
  private final DataStorageOptions.Unstable unstableOptions = new Unstable();

  /** Default Constructor. */
  DataStorageOptions() {}

  /** The unstable options for data storage. */
  public static class Unstable {

    // TODO: --Xsnapsync-synchronizer-flat-db-healing-enabled is deprecated, remove it in a future
    // release
    @CommandLine.Option(
        hidden = true,
        names = {
          "--Xbonsai-full-flat-db-enabled",
          "--Xsnapsync-synchronizer-flat-db-healing-enabled"
        },
        arity = "1",
        description = "Enables bonsai full flat database strategy. (default: ${DEFAULT-VALUE})")
    private Boolean bonsaiFullFlatDbEnabled = DEFAULT_BONSAI_FULL_FLAT_DB_ENABLED;

    @CommandLine.Option(
        hidden = true,
        names = {"--Xbonsai-code-using-code-hash-enabled"},
        arity = "1",
        description =
            "Enables code storage using code hash instead of by account hash. (default: ${DEFAULT-VALUE})")
    private boolean bonsaiCodeUsingCodeHashEnabled = DEFAULT_DIFFBASED_CODE_USING_CODE_HASH_ENABLED;

    @CommandLine.Option(
        hidden = true,
        names = {"--Xbonsai-parallel-tx-processing-enabled"},
        arity = "1",
        description =
            "Enables parallelization of transactions to optimize processing speed by concurrently loading and executing necessary data in advance. (default: ${DEFAULT-VALUE})")
    private Boolean isParallelTxProcessingEnabled = false;

    @CommandLine.Option(
        hidden = true,
        names = {
          "--Xverkle-stem-flat-db-enabled",
        },
        arity = "1",
        description = "Enables stem flat database strategy for verkle. (default: ${DEFAULT-VALUE})")
    private Boolean verkleStemFlatDbEnabled = DEFAULT_VERKLE_STEM_FLAT_DB_ENABLED;

    /** Default Constructor. */
    Unstable() {}
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
    if (DataStorageFormat.BONSAI == dataStorageFormat
        || DataStorageFormat.VERKLE == dataStorageFormat) {
      if (diffBasedLimitTrieLogsEnabled) {
        if (diffBasedMaxLayersToLoad < MINIMUM_DIFFBASED_TRIE_LOG_RETENTION_LIMIT) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  DIFFBASED_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD + " minimum value is %d",
                  MINIMUM_DIFFBASED_TRIE_LOG_RETENTION_LIMIT));
        }
        if (diffBasedTrieLogPruningWindowSize <= 0) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  DIFFBASED_TRIE_LOG_PRUNING_WINDOW_SIZE + "=%d must be greater than 0",
                  diffBasedTrieLogPruningWindowSize));
        }
        if (diffBasedTrieLogPruningWindowSize <= diffBasedMaxLayersToLoad) {
          throw new CommandLine.ParameterException(
              commandLine,
              String.format(
                  DIFFBASED_TRIE_LOG_PRUNING_WINDOW_SIZE
                      + "=%d must be greater than "
                      + DIFFBASED_STORAGE_FORMAT_MAX_LAYERS_TO_LOAD
                      + "=%d",
                  diffBasedTrieLogPruningWindowSize,
                  diffBasedMaxLayersToLoad));
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
  public static DataStorageOptions fromConfig(final DataStorageConfiguration domainObject) {
    final DataStorageOptions dataStorageOptions = DataStorageOptions.create();
    dataStorageOptions.dataStorageFormat = domainObject.getDataStorageFormat();
    dataStorageOptions.diffBasedMaxLayersToLoad = domainObject.getDiffbasedMaxLayersToLoad();
    dataStorageOptions.receiptCompactionEnabled = domainObject.getReceiptCompactionEnabled();
    dataStorageOptions.diffBasedLimitTrieLogsEnabled =
        domainObject.getDiffbasedLimitTrieLogsEnabled();
    dataStorageOptions.diffBasedTrieLogPruningWindowSize =
        domainObject.getDiffbasedTrieLogPruningWindowSize();
    dataStorageOptions.unstableOptions.bonsaiFullFlatDbEnabled =
        domainObject.getUnstable().getBonsaiFullFlatDbEnabled();
    dataStorageOptions.unstableOptions.bonsaiCodeUsingCodeHashEnabled =
        domainObject.getUnstable().getDiffbasedCodeStoredByCodeHashEnabled();
    dataStorageOptions.unstableOptions.isParallelTxProcessingEnabled =
        domainObject.getUnstable().isParallelTxProcessingEnabled();
    dataStorageOptions.unstableOptions.verkleStemFlatDbEnabled =
        domainObject.getUnstable().getVerkleStemFlatDbEnabled();

    return dataStorageOptions;
  }

  @Override
  public DataStorageConfiguration toDomainObject() {
    return ImmutableDataStorageConfiguration.builder()
        .dataStorageFormat(dataStorageFormat)
        .diffbasedMaxLayersToLoad(diffBasedMaxLayersToLoad)
        .receiptCompactionEnabled(receiptCompactionEnabled)
        .diffbasedLimitTrieLogsEnabled(diffBasedLimitTrieLogsEnabled)
        .diffbasedTrieLogPruningWindowSize(diffBasedTrieLogPruningWindowSize)
        .unstable(
            ImmutableDataStorageConfiguration.Unstable.builder()
                .bonsaiFullFlatDbEnabled(unstableOptions.bonsaiFullFlatDbEnabled)
                .diffbasedCodeStoredByCodeHashEnabled(
                    unstableOptions.bonsaiCodeUsingCodeHashEnabled)
                .isParallelTxProcessingEnabled(unstableOptions.isParallelTxProcessingEnabled)
                .verkleStemFlatDbEnabled(unstableOptions.verkleStemFlatDbEnabled)
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
    return StringUtils.capitalize(dataStorageFormat.toString().toLowerCase(Locale.ROOT));
  }
}

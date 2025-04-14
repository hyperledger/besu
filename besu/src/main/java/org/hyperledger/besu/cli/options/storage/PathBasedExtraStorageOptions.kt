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
package org.hyperledger.besu.cli.options.storage

import org.hyperledger.besu.cli.options.CLIOptions
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.worldstate.ImmutablePathBasedExtraStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.PathBasedExtraStorageConfiguration
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat
import picocli.CommandLine

/** The Data storage CLI options.  */
class PathBasedExtraStorageOptions

/** Default Constructor.  */
internal constructor() : CLIOptions<PathBasedExtraStorageConfiguration?> {
    @CommandLine.Option(
        names = [MAX_LAYERS_TO_LOAD, "--bonsai-maximum-back-layers-to-load"],
        paramLabel = "<LONG>",
        description = [("Limit of historical layers that can be loaded with BONSAI (default: \${DEFAULT-VALUE}). When using "
                + LIMIT_TRIE_LOGS_ENABLED
                + " it will also be used as the number of layers of trie logs to retain.")],
        arity = "1"
    )
    private var maxLayersToLoad = PathBasedExtraStorageConfiguration.DEFAULT_MAX_LAYERS_TO_LOAD

    // TODO --Xbonsai-limit-trie-logs-enabled and --Xbonsai-trie-log-pruning-enabled are deprecated,
    // remove in a future release
    @CommandLine.Option(
        names = [LIMIT_TRIE_LOGS_ENABLED, "--Xbonsai-limit-trie-logs-enabled" // deprecated
            , "--Xbonsai-trie-log-pruning-enabled" // deprecated
        ],
        fallbackValue = "true",
        description = ["Limit the number of trie logs that are retained. (default: \${DEFAULT-VALUE})"]
    )
    private var limitTrieLogsEnabled = PathBasedExtraStorageConfiguration.DEFAULT_LIMIT_TRIE_LOGS_ENABLED

    // TODO --Xbonsai-trie-logs-pruning-window-size is deprecated, remove in a future release
    @CommandLine.Option(
        names = [TRIE_LOG_PRUNING_WINDOW_SIZE, "--Xbonsai-trie-logs-pruning-window-size" // deprecated
        ],
        description = ["The max number of blocks to load and prune trie logs for at startup. (default: \${DEFAULT-VALUE})"]
    )
    private var trieLogPruningWindowSize = PathBasedExtraStorageConfiguration.DEFAULT_TRIE_LOG_PRUNING_WINDOW_SIZE

    @CommandLine.ArgGroup(validate = false)
    private var unstableOptions = Unstable()

    /** The unstable options for data storage.  */
    class Unstable
    /** Default Constructor.  */
    internal constructor() {
        // TODO: --Xsnapsync-synchronizer-flat-db-healing-enabled is deprecated, remove it in a future
        // release
        @CommandLine.Option(
            hidden = true, names = ["--Xbonsai-full-flat-db-enabled", "--Xsnapsync-synchronizer-flat-db-healing-enabled"
            ], arity = "1", description = ["Enables bonsai full flat database strategy. (default: \${DEFAULT-VALUE})"]
        )
        var fullFlatDbEnabled: Boolean =
            PathBasedExtraStorageConfiguration.PathBasedUnstable.DEFAULT_FULL_FLAT_DB_ENABLED

        @CommandLine.Option(
            hidden = true,
            names = ["--Xbonsai-code-using-code-hash-enabled"],
            arity = "1",
            description = ["Enables code storage using code hash instead of by account hash. (default: \${DEFAULT-VALUE})"]
        )
        var codeUsingCodeHashEnabled: Boolean =
            PathBasedExtraStorageConfiguration.PathBasedUnstable.DEFAULT_CODE_USING_CODE_HASH_ENABLED

        @CommandLine.Option(
            hidden = true,
            names = ["--Xbonsai-parallel-tx-processing-enabled"],
            arity = "1",
            description = ["Enables parallelization of transactions to optimize processing speed by concurrently loading and executing necessary data in advance. (default: \${DEFAULT-VALUE})"]
        )
        var isParallelTxProcessingEnabled: Boolean = false
    }

    /**
     * Validates the data storage options
     *
     * @param commandLine the full commandLine to check all the options specified by the user
     * @param dataStorageFormat the selected data storage format which determines the validation rules
     * to apply.
     */
    fun validate(commandLine: CommandLine, dataStorageFormat: DataStorageFormat) {
        if (DataStorageFormat.BONSAI == dataStorageFormat) {
            if (limitTrieLogsEnabled) {
                if (maxLayersToLoad < PathBasedExtraStorageConfiguration.MINIMUM_TRIE_LOG_RETENTION_LIMIT) {
                    throw CommandLine.ParameterException(
                        commandLine,
                        String.format(
                            MAX_LAYERS_TO_LOAD + " minimum value is %d",
                            PathBasedExtraStorageConfiguration.MINIMUM_TRIE_LOG_RETENTION_LIMIT
                        )
                    )
                }
                if (trieLogPruningWindowSize <= 0) {
                    throw CommandLine.ParameterException(
                        commandLine,
                        String.format(
                            TRIE_LOG_PRUNING_WINDOW_SIZE + "=%d must be greater than 0",
                            trieLogPruningWindowSize
                        )
                    )
                }
                if (trieLogPruningWindowSize <= maxLayersToLoad) {
                    throw CommandLine.ParameterException(
                        commandLine,
                        String.format(
                            (TRIE_LOG_PRUNING_WINDOW_SIZE
                                    + "=%d must be greater than "
                                    + MAX_LAYERS_TO_LOAD
                                    + "=%d"),
                            trieLogPruningWindowSize,
                            maxLayersToLoad
                        )
                    )
                }
            }
        } else {
            if (unstableOptions.isParallelTxProcessingEnabled) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "Transaction parallelization is not supported unless operating in a 'pathbased' mode, such as Bonsai."
                )
            }
        }
    }

    override fun toDomainObject(): PathBasedExtraStorageConfiguration {
        return ImmutablePathBasedExtraStorageConfiguration.builder()
            .maxLayersToLoad(maxLayersToLoad)
            .limitTrieLogsEnabled(limitTrieLogsEnabled)
            .trieLogPruningWindowSize(trieLogPruningWindowSize)
            .unstable(
                ImmutablePathBasedExtraStorageConfiguration.PathBasedUnstable.builder()
                    .fullFlatDbEnabled(unstableOptions.fullFlatDbEnabled)
                    .codeStoredByCodeHashEnabled(unstableOptions.codeUsingCodeHashEnabled)
                    .isParallelTxProcessingEnabled(unstableOptions.isParallelTxProcessingEnabled)
                    .build()
            )
            .build()
    }

    override fun getCLIOptions(): List<String> {
        return CommandLineUtils.getCLIOptions(this, PathBasedExtraStorageOptions())
    }

    companion object {
        /** The maximum number of historical layers to load.  */
        const val MAX_LAYERS_TO_LOAD: String = "--bonsai-historical-block-limit"

        /** The bonsai limit trie logs enabled option name  */
        const val LIMIT_TRIE_LOGS_ENABLED: String = "--bonsai-limit-trie-logs-enabled"

        /** The bonsai trie logs pruning window size.  */
        const val TRIE_LOG_PRUNING_WINDOW_SIZE: String = "--bonsai-trie-logs-pruning-window-size"

        /**
         * Create data storage options.
         *
         * @return the data storage options
         */
        fun create(): PathBasedExtraStorageOptions {
            return PathBasedExtraStorageOptions()
        }

        /**
         * Converts to options from the configuration
         *
         * @param domainObject to be reversed
         * @return the options that correspond to the configuration
         */
        fun fromConfig(
            domainObject: PathBasedExtraStorageConfiguration
        ): PathBasedExtraStorageOptions {
            val dataStorageOptions = create()
            dataStorageOptions.maxLayersToLoad = domainObject.maxLayersToLoad
            dataStorageOptions.limitTrieLogsEnabled = domainObject.limitTrieLogsEnabled
            dataStorageOptions.trieLogPruningWindowSize = domainObject.trieLogPruningWindowSize
            dataStorageOptions.unstableOptions.fullFlatDbEnabled =
                domainObject.unstable.fullFlatDbEnabled
            dataStorageOptions.unstableOptions.codeUsingCodeHashEnabled =
                domainObject.unstable.codeStoredByCodeHashEnabled
            dataStorageOptions.unstableOptions.isParallelTxProcessingEnabled =
                domainObject.unstable.isParallelTxProcessingEnabled

            return dataStorageOptions
        }
    }
}

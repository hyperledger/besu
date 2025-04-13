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

import org.apache.commons.lang3.StringUtils
import org.hyperledger.besu.cli.options.CLIOptions
import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.ImmutableDataStorageConfiguration
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat
import picocli.CommandLine

/** The Data storage CLI options.  */
class DataStorageOptions
/** Default Constructor.  */
internal constructor() : CLIOptions<DataStorageConfiguration?> {
    // Use Bonsai DB
    @CommandLine.Option(
        names = [DATA_STORAGE_FORMAT],
        description = ["Format to store trie data in.  Either FOREST or BONSAI (default: \${DEFAULT-VALUE})."],
        arity = "1"
    )
    private var dataStorageFormat = DataStorageFormat.BONSAI

    @CommandLine.Option(
        names = ["--receipt-compaction-enabled"],
        description = ["Enables compact storing of receipts (default: \${DEFAULT-VALUE})"],
        fallbackValue = "true"
    )
    private var receiptCompactionEnabled = DataStorageConfiguration.DEFAULT_RECEIPT_COMPACTION_ENABLED

    /**
     * Options specific to path-based storage modes. Holds the necessary parameters to configure
     * path-based storage, such as the Bonsai mode or Verkle in the future.
     */
    @CommandLine.Mixin
    private var pathBasedExtraStorageOptions: PathBasedExtraStorageOptions = PathBasedExtraStorageOptions.create()

    /**
     * Validates the data storage options
     *
     * @param commandLine the full commandLine to check all the options specified by the user
     */
    fun validate(commandLine: CommandLine?) {
        pathBasedExtraStorageOptions.validate(commandLine, dataStorageFormat)
    }

    override fun toDomainObject(): DataStorageConfiguration {
        val builder =
            ImmutableDataStorageConfiguration.builder()
                .dataStorageFormat(dataStorageFormat)
                .receiptCompactionEnabled(receiptCompactionEnabled)
                .pathBasedExtraStorageConfiguration(pathBasedExtraStorageOptions.toDomainObject())
        return builder.build()
    }

    override fun getCLIOptions(): List<String> {
        val cliOptions = CommandLineUtils.getCLIOptions(this, DataStorageOptions())
        cliOptions.addAll(pathBasedExtraStorageOptions.cliOptions)
        return cliOptions
    }

    /**
     * Normalize data storage format string.
     *
     * @return the normalized string
     */
    fun normalizeDataStorageFormat(): String {
        return StringUtils.capitalize(dataStorageFormat.toString().lowercase())
    }

    companion object {
        private const val DATA_STORAGE_FORMAT = "--data-storage-format"

        /**
         * Create data storage options.
         *
         * @return the data storage options
         */
        @JvmStatic
        fun create(): DataStorageOptions {
            return DataStorageOptions()
        }

        /**
         * Converts to options from the configuration
         *
         * @param domainObject to be reversed
         * @return the options that correspond to the configuration
         */
        @JvmStatic
        fun fromConfig(domainObject: DataStorageConfiguration): DataStorageOptions {
            val dataStorageOptions = create()
            dataStorageOptions.dataStorageFormat = domainObject.dataStorageFormat
            dataStorageOptions.receiptCompactionEnabled = domainObject.receiptCompactionEnabled
            dataStorageOptions.pathBasedExtraStorageOptions =
                PathBasedExtraStorageOptions.fromConfig(
                    domainObject.pathBasedExtraStorageConfiguration
                )
            return dataStorageOptions
        }
    }
}

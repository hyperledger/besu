/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.options

import org.hyperledger.besu.evm.internal.EvmConfiguration
import org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode
import picocli.CommandLine

/** The Evm CLI options.  */
class EvmOptions
/** Default constructor.  */
internal constructor() : CLIOptions<EvmConfiguration?> {
    @CommandLine.Option(
        names = [JUMPDEST_CACHE_WEIGHT],
        description = [("size in kilobytes to allow the cache "
                + "of valid jump destinations to grow to before evicting the least recently used entry")],
        fallbackValue = "32000",
        hidden = true,
        arity = "1"
    )
    private var jumpDestCacheWeightKilobytes = 32000L // 10k contracts, (25k max contract size / 8 bit) + 32byte hash

    @CommandLine.Option(
        names = [WORLDSTATE_UPDATE_MODE],
        description = ["How to handle worldstate updates within a transaction"],
        fallbackValue = "STACKED",
        hidden = true,
        arity = "1"
    )
    private var worldstateUpdateMode = WorldUpdaterMode
        .STACKED // Stacked Updater.  Years of battle tested correctness.

    override fun toDomainObject(): EvmConfiguration {
        return EvmConfiguration(jumpDestCacheWeightKilobytes, worldstateUpdateMode)
    }

    override fun getCLIOptions(): List<String> {
        return java.util.List.of(JUMPDEST_CACHE_WEIGHT, WORLDSTATE_UPDATE_MODE)
    }

    companion object {
        /** The constant JUMPDEST_CACHE_WEIGHT.  */
        const val JUMPDEST_CACHE_WEIGHT: String = "--Xevm-jumpdest-cache-weight-kb"

        /** The constant WORLDSTATE_UPDATE_MODE.  */
        const val WORLDSTATE_UPDATE_MODE: String = "--Xevm-worldstate-update-mode"

        /**
         * Create evm options.
         *
         * @return the evm options
         */
        @JvmStatic
        fun create(): EvmOptions {
            return EvmOptions()
        }
    }
}

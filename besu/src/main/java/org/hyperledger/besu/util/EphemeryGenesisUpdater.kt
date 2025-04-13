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
package org.hyperledger.besu.util

import org.hyperledger.besu.cli.config.NetworkName
import org.hyperledger.besu.config.GenesisConfig
import java.io.IOException
import java.math.BigInteger
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The Generate Ephemery Genesis Updater. Checks for update based on the set period and update the
 * Ephemery genesis in memory
 */
object EphemeryGenesisUpdater {
    private const val PERIOD_IN_DAYS = 28
    private const val PERIOD_IN_SECONDS = (PERIOD_IN_DAYS * 24 * 60 * 60).toLong()

    /**
     * Updates the Ephemery genesis configuration based on the predefined period.
     *
     * @param overrides a map of configuration overrides
     * @return the updated GenesisConfigFile
     * @throws RuntimeException if an error occurs during the update process
     */
    @Throws(RuntimeException::class)
    @JvmStatic
    fun updateGenesis(overrides: MutableMap<String?, String?>): GenesisConfig {
        var genesisConfig: GenesisConfig
        try {
            if (NetworkName.EPHEMERY.genesisFile == null) {
                throw IOException("Genesis file or config options are null")
            }
            genesisConfig = GenesisConfig.fromResource(NetworkName.EPHEMERY.genesisFile)
            val genesisTimestamp = genesisConfig.timestamp
            val genesisChainId = genesisConfig.configOptions.chainId
            val currentTimestamp = Instant.now().epochSecond
            val periodsSinceGenesis =
                (ChronoUnit.DAYS.between(Instant.ofEpochSecond(genesisTimestamp), Instant.now())
                        / PERIOD_IN_DAYS)

            val updatedTimestamp = genesisTimestamp + (periodsSinceGenesis * PERIOD_IN_SECONDS)
            val updatedChainId =
                genesisChainId
                    .orElseThrow { IllegalStateException("ChainId not present") }
                    .add(BigInteger.valueOf(periodsSinceGenesis))
            // has a period elapsed since original ephemery genesis time? if so, override chainId and
            // timestamp
            if (currentTimestamp > (genesisTimestamp + PERIOD_IN_SECONDS)) {
                overrides["chainId"] = updatedChainId.toString()
                overrides["timestamp"] = updatedTimestamp.toString()
                genesisConfig = genesisConfig.withOverrides(overrides)
            }
            return genesisConfig.withOverrides(overrides)
        } catch (e: IOException) {
            throw RuntimeException("Error updating ephemery genesis: " + e.message, e)
        }
    }
}

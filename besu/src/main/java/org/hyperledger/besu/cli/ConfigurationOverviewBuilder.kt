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
package org.hyperledger.besu.cli

import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration
import org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode
import org.hyperledger.besu.services.BesuPluginContextImpl
import org.hyperledger.besu.util.BesuVersionUtils
import org.hyperledger.besu.util.log.FramedLogMessage
import org.hyperledger.besu.util.platform.PlatformDetector
import org.slf4j.Logger
import oshi.PlatformEnum
import oshi.SystemInfo
import java.math.BigInteger
import java.util.*

/** The Configuration overview builder.  */
class ConfigurationOverviewBuilder
/**
 * Create a new ConfigurationOverviewBuilder.
 *
 * @param logger the logger
 */(private val logger: Logger) {
    private var network: String? = null
    private var networkId: BigInteger? = null
    private var profile: String? = null
    private var hasCustomGenesis = false
    private var customGenesisFileName: String? = null
    private var dataStorage: String? = null
    private var syncMode: String? = null
    private var syncMinPeers: Int? = null
    private var rpcPort: Int? = null
    private var rpcHttpApis: Collection<String>? = null
    private var enginePort: Int? = null
    private var engineApis: Collection<String>? = null
    private var engineJwtFilePath: String? = null
    private var isHighSpec = false
    private var isLimitTrieLogsEnabled = false
    private var trieLogRetentionLimit: Long = 0
    private var trieLogsPruningWindowSize: Int? = null
    private var isSnapServerEnabled = false
    private var txPoolImplementation: TransactionPoolConfiguration.Implementation? = null
    private var worldStateUpdateMode: WorldUpdaterMode? = null
    private var environment: Map<String, String>? = null
    private var besuPluginContext: BesuPluginContextImpl? = null

    /**
     * Sets network.
     *
     * @param network the network
     * @return the network
     */
    fun setNetwork(network: String?): ConfigurationOverviewBuilder {
        this.network = network
        return this
    }

    /**
     * Sets whether a networkId has been specified
     *
     * @param networkId the specified networkId
     * @return the builder
     */
    fun setNetworkId(networkId: BigInteger?): ConfigurationOverviewBuilder {
        this.networkId = networkId
        return this
    }

    /**
     * Sets profile.
     *
     * @param profile the profile
     * @return the profile
     */
    fun setProfile(profile: String?): ConfigurationOverviewBuilder {
        this.profile = profile
        return this
    }

    /**
     * Sets whether a custom genesis has been specified.
     *
     * @param hasCustomGenesis a boolean representing whether a custom genesis file was specified
     * @return the builder
     */
    fun setHasCustomGenesis(hasCustomGenesis: Boolean): ConfigurationOverviewBuilder {
        this.hasCustomGenesis = hasCustomGenesis
        return this
    }

    /**
     * Sets location of custom genesis file specified.
     *
     * @param customGenesisFileName the filename of the custom genesis file, only set if specified
     * @return the builder
     */
    fun setCustomGenesis(customGenesisFileName: String?): ConfigurationOverviewBuilder {
        this.customGenesisFileName = customGenesisFileName
        return this
    }

    /**
     * Sets data storage.
     *
     * @param dataStorage the data storage
     * @return the builder
     */
    fun setDataStorage(dataStorage: String?): ConfigurationOverviewBuilder {
        this.dataStorage = dataStorage
        return this
    }

    /**
     * Sets sync mode.
     *
     * @param syncMode the sync mode
     * @return the builder
     */
    fun setSyncMode(syncMode: String?): ConfigurationOverviewBuilder {
        this.syncMode = syncMode
        return this
    }

    /**
     * Sets sync min peers.
     *
     * @param syncMinPeers number of min peers for sync
     * @return the builder
     */
    fun setSyncMinPeers(syncMinPeers: Int): ConfigurationOverviewBuilder {
        this.syncMinPeers = syncMinPeers
        return this
    }

    /**
     * Sets rpc port.
     *
     * @param rpcPort the rpc port
     * @return the builder
     */
    fun setRpcPort(rpcPort: Int?): ConfigurationOverviewBuilder {
        this.rpcPort = rpcPort
        return this
    }

    /**
     * Sets rpc http apis.
     *
     * @param rpcHttpApis the rpc http apis
     * @return the builder
     */
    fun setRpcHttpApis(rpcHttpApis: Collection<String>?): ConfigurationOverviewBuilder {
        this.rpcHttpApis = rpcHttpApis
        return this
    }

    /**
     * Sets engine port.
     *
     * @param enginePort the engine port
     * @return the builder
     */
    fun setEnginePort(enginePort: Int?): ConfigurationOverviewBuilder {
        this.enginePort = enginePort
        return this
    }

    /**
     * Sets engine apis.
     *
     * @param engineApis the engine apis
     * @return the builder
     */
    fun setEngineApis(engineApis: Collection<String>?): ConfigurationOverviewBuilder {
        this.engineApis = engineApis
        return this
    }

    /**
     * Sets high spec enabled.
     *
     * @return the builder
     */
    fun setHighSpecEnabled(): ConfigurationOverviewBuilder {
        isHighSpec = true
        return this
    }

    /**
     * Sets limit trie logs enabled
     *
     * @return the builder
     */
    fun setLimitTrieLogsEnabled(): ConfigurationOverviewBuilder {
        isLimitTrieLogsEnabled = true
        return this
    }

    /**
     * Sets trie log retention limit
     *
     * @param limit the number of blocks to retain trie logs for
     * @return the builder
     */
    fun setTrieLogRetentionLimit(limit: Long): ConfigurationOverviewBuilder {
        trieLogRetentionLimit = limit
        return this
    }

    /**
     * Sets snap server enabled/disabled
     *
     * @param snapServerEnabled bool to indicate if snap server is enabled
     * @return the builder
     */
    fun setSnapServerEnabled(snapServerEnabled: Boolean): ConfigurationOverviewBuilder {
        isSnapServerEnabled = snapServerEnabled
        return this
    }

    /**
     * Sets trie logs pruning window size
     *
     * @param size the max number of blocks to load and prune trie logs for at startup
     * @return the builder
     */
    fun setTrieLogsPruningWindowSize(size: Int): ConfigurationOverviewBuilder {
        trieLogsPruningWindowSize = size
        return this
    }

    /**
     * Sets the txpool implementation in use.
     *
     * @param implementation the txpool implementation
     * @return the builder
     */
    fun setTxPoolImplementation(
        implementation: TransactionPoolConfiguration.Implementation?
    ): ConfigurationOverviewBuilder {
        txPoolImplementation = implementation
        return this
    }

    /**
     * Sets the world state updater mode
     *
     * @param worldStateUpdateMode the world state updater mode
     * @return the builder
     */
    fun setWorldStateUpdateMode(
        worldStateUpdateMode: WorldUpdaterMode?
    ): ConfigurationOverviewBuilder {
        this.worldStateUpdateMode = worldStateUpdateMode
        return this
    }

    /**
     * Sets the engine jwt file path.
     *
     * @param engineJwtFilePath the engine apis
     * @return the builder
     */
    fun setEngineJwtFile(engineJwtFilePath: String?): ConfigurationOverviewBuilder {
        this.engineJwtFilePath = engineJwtFilePath
        return this
    }

    /**
     * Sets the environment variables.
     *
     * @param environment the environment variables
     * @return the builder
     */
    fun setEnvironment(environment: Map<String, String>?): ConfigurationOverviewBuilder {
        this.environment = environment
        return this
    }

    /**
     * Build configuration overview.
     *
     * @return the string representing configuration overview
     */
    fun build(): String {
        val lines: MutableList<String> = ArrayList()
        lines.add("Besu version " + BesuVersionUtils.version())
        lines.add("")
        lines.add("Configuration:")

        // Don't include the default network if a genesis file has been supplied
        if (network != null && !hasCustomGenesis) {
            lines.add("Network: $network")
        }

        if (hasCustomGenesis) {
            lines.add("Network: Custom genesis file")
            lines.add(
                (if (customGenesisFileName == null) "Custom genesis file is null" else customGenesisFileName)!!
            )
        }

        if (networkId != null) {
            lines.add("Network Id: $networkId")
        }

        if (profile != null) {
            lines.add("Profile: $profile")
        }

        if (dataStorage != null) {
            lines.add("Data storage: $dataStorage")
        }

        if (syncMode != null) {
            lines.add(
                "Sync mode: " + syncMode + (if (syncMode.equals("FAST", ignoreCase = true)) " (Deprecated)" else "")
            )
        }

        if (syncMinPeers != null) {
            lines.add("Sync min peers: $syncMinPeers")
        }

        if (rpcHttpApis != null) {
            lines.add("RPC HTTP APIs: " + java.lang.String.join(",", rpcHttpApis))
        }
        if (rpcPort != null) {
            lines.add("RPC HTTP port: $rpcPort")
        }

        if (engineApis != null) {
            lines.add("Engine APIs: " + java.lang.String.join(",", engineApis))
        }
        if (enginePort != null) {
            lines.add("Engine port: $enginePort")
        }
        if (engineJwtFilePath != null) {
            lines.add("Engine JWT: $engineJwtFilePath")
        }

        lines.add("Using $txPoolImplementation transaction pool implementation")

        if (isHighSpec) {
            lines.add("Experimental high spec configuration enabled")
        }

        lines.add("Using $worldStateUpdateMode worldstate update mode")

        if (isSnapServerEnabled) {
            lines.add("Experimental Snap Sync server enabled")
        }

        if (isLimitTrieLogsEnabled) {
            val trieLogPruningString = StringBuilder()
            trieLogPruningString
                .append("Limit trie logs enabled: retention: ")
                .append(trieLogRetentionLimit)
            if (trieLogsPruningWindowSize != null) {
                trieLogPruningString.append("; prune window: ").append(trieLogsPruningWindowSize)
            }
            lines.add(trieLogPruningString.toString())
        }

        lines.add("")
        lines.add("Host:")

        lines.add("Java: " + PlatformDetector.getVM())
        lines.add("Maximum heap size: " + normalizeSize(Runtime.getRuntime().maxMemory()))
        lines.add("OS: " + PlatformDetector.getOS())

        if (SystemInfo.getCurrentPlatform() == PlatformEnum.LINUX) {
            val glibcVersion = PlatformDetector.getGlibc()
            if (glibcVersion != null) {
                lines.add("glibc: $glibcVersion")
            }

            detectJemalloc(lines)
        }

        val hardwareInfo = SystemInfo().hardware

        lines.add("Total memory: " + normalizeSize(hardwareInfo.memory.total))
        lines.add("CPU cores: " + hardwareInfo.processor.logicalProcessorCount)

        lines.add("")

        if (besuPluginContext != null) {
            lines.addAll(besuPluginContext!!.pluginsSummaryLog)
        }

        return FramedLogMessage.generate(lines)
    }

    private fun detectJemalloc(lines: MutableList<String>) {
        Optional.ofNullable(if (Objects.isNull(environment)) null else environment!!["BESU_USING_JEMALLOC"])
            .ifPresentOrElse(
                { jemallocEnabled: String ->
                    try {
                        if (jemallocEnabled.toBoolean()) {
                            val version = PlatformDetector.getJemalloc()
                            lines.add("jemalloc: $version")
                        } else {
                            logger.warn(
                                "besu_using_jemalloc is present but is not set to true, jemalloc library not loaded"
                            )
                        }
                    } catch (throwable: Throwable) {
                        logger.warn(
                            "besu_using_jemalloc is present but we failed to load jemalloc library to get the version"
                        )
                    }
                },
                {
                    // in case the user is using jemalloc without BESU_USING_JEMALLOC env var
                    try {
                        val version = PlatformDetector.getJemalloc()
                        lines.add("jemalloc: $version")
                    } catch (throwable: Throwable) {
                        logger.info(
                            "jemalloc library not found, memory usage may be reduced by installing it"
                        )
                    }
                })
    }

    private fun normalizeSize(size: Long): String {
        return String.format("%.02f", (size).toDouble() / 1024 / 1024 / 1024) + " GB"
    }

    /**
     * set the plugin context
     *
     * @param besuPluginContext the plugin context
     */
    fun setPluginContext(besuPluginContext: BesuPluginContextImpl?) {
        this.besuPluginContext = besuPluginContext
    }
}

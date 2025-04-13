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
package org.hyperledger.besu.controller

import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.cli.config.EthNetworkConfig
import org.hyperledger.besu.config.GenesisConfig
import org.hyperledger.besu.config.GenesisConfigOptions
import org.hyperledger.besu.config.PowAlgorithm
import org.hyperledger.besu.config.QbftConfigOptions
import org.hyperledger.besu.cryptoservices.NodeKey
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.core.PrivacyParameters
import org.hyperledger.besu.ethereum.core.Synchronizer
import org.hyperledger.besu.ethereum.eth.manager.EthPeers
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager
import org.hyperledger.besu.ethereum.eth.sync.SyncMode
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration
import org.hyperledger.besu.ethereum.storage.StorageProvider
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.util.function.Consumer

/** The Besu controller.  */
class BesuController
/**
 * Instantiates a new Besu controller.
 *
 * @param protocolSchedule the protocol schedule
 * @param protocolContext the protocol context
 * @param ethProtocolManager the eth protocol manager
 * @param genesisConfigOptions the genesis config options
 * @param subProtocolConfiguration the sub protocol configuration
 * @param synchronizer the synchronizer
 * @param syncState the sync state
 * @param transactionPool the transaction pool
 * @param miningCoordinator the mining coordinator
 * @param privacyParameters the privacy parameters
 * @param miningConfiguration the mining parameters
 * @param additionalJsonRpcMethodsFactory the additional json rpc methods factory
 * @param nodeKey the node key
 * @param closeables the closeables
 * @param additionalPluginServices the additional plugin services
 * @param ethPeers the eth peers
 * @param storageProvider the storage provider
 * @param dataStorageConfiguration the data storage configuration
 * @param transactionSimulator the transaction simulator
 */ internal constructor(
  /**
     * Gets protocol schedule.
     *
     * @return the protocol schedule
     */
  @JvmField val protocolSchedule: ProtocolSchedule,
  /**
     * Gets protocol context.
     *
     * @return the protocol context
     */
  @JvmField val protocolContext: ProtocolContext,
  /**
     * Gets protocol manager.
     *
     * @return the protocol manager
     */
    val protocolManager: EthProtocolManager,
  /**
     * Gets genesis config options.
     *
     * @return the genesis config options
     */
  @JvmField val genesisConfigOptions: GenesisConfigOptions,
  /**
     * Gets sub protocol configuration.
     *
     * @return the sub protocol configuration
     */
  @JvmField val subProtocolConfiguration: SubProtocolConfiguration,
  /**
     * Gets synchronizer.
     *
     * @return the synchronizer
     */
  @JvmField val synchronizer: Synchronizer,
  /**
     * Gets sync state.
     *
     * @return the sync state
     */
  @JvmField val syncState: SyncState,
  /**
     * Gets transaction pool.
     *
     * @return the transaction pool
     */
  @JvmField val transactionPool: TransactionPool,
  /**
     * Gets mining coordinator.
     *
     * @return the mining coordinator
     */
  @JvmField val miningCoordinator: MiningCoordinator,
  /**
     * Gets privacy parameters.
     *
     * @return the privacy parameters
     */
  @JvmField val privacyParameters: PrivacyParameters,
  /**
     * Gets mining parameters.
     *
     * @return the mining parameters
     */
    val miningParameters: MiningConfiguration,
  private val additionalJsonRpcMethodsFactory: JsonRpcMethods,
  /**
     * Gets node key.
     *
     * @return the node key
     */
  @JvmField val nodeKey: NodeKey,
  private val closeables: List<Closeable>,
  /**
     * Gets additional plugin services.
     *
     * @return the additional plugin services
     */
  @JvmField val additionalPluginServices: PluginServiceFactory,
  /**
     * get the collection of eth peers
     *
     * @return the EthPeers collection
     */
  @JvmField val ethPeers: EthPeers,
  /**
     * Get the storage provider
     *
     * @return the storage provider
     */
  @JvmField val storageProvider: StorageProvider,
  /**
     * Gets data storage configuration.
     *
     * @return the data storage configuration
     */
  @JvmField val dataStorageConfiguration: DataStorageConfiguration,
  /**
     * Gets the transaction simulator
     *
     * @return the transaction simulator
     */
  @JvmField val transactionSimulator: TransactionSimulator
) : Closeable {
    override fun close() {
        closeables.forEach(Consumer { closeable: Closeable -> this.tryClose(closeable) })
    }

    private fun tryClose(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            LOG.error("Unable to close resource.", e)
        }
    }

    /**
     * Gets additional json rpc methods.
     *
     * @param enabledRpcApis the enabled rpc apis
     * @return the additional json rpc methods
     */
    fun getAdditionalJsonRpcMethods(
        enabledRpcApis: Collection<String?>?
    ): Map<String, JsonRpcMethod> {
        return additionalJsonRpcMethodsFactory.create(enabledRpcApis)
    }

    /** The type Builder.  */
    class Builder
    /** Instantiates a new Builder.  */
    {
        /**
         * From eth network config besu controller builder.
         *
         * @param ethNetworkConfig the eth network config
         * @param syncMode The sync mode
         * @return the besu controller builder
         */
        fun fromEthNetworkConfig(
            ethNetworkConfig: EthNetworkConfig, syncMode: SyncMode
        ): BesuControllerBuilder {
            return fromGenesisFile(ethNetworkConfig.genesisConfig, syncMode)
                .networkId(ethNetworkConfig.networkId)
        }

        /**
         * From genesis config besu controller builder.
         *
         * @param genesisConfig the genesis config file
         * @param syncMode the sync mode
         * @return the besu controller builder
         */
        fun fromGenesisFile(
            genesisConfig: GenesisConfig, syncMode: SyncMode
        ): BesuControllerBuilder {
            var builder: BesuControllerBuilder
            val configOptions = genesisConfig.configOptions

            if (configOptions.isConsensusMigration) {
                return createConsensusScheduleBesuControllerBuilder(genesisConfig)
            }

            if (configOptions.powAlgorithm != PowAlgorithm.UNSUPPORTED) {
                builder = MainnetBesuControllerBuilder()
            } else if (configOptions.isIbft2) {
                builder = IbftBesuControllerBuilder()
            } else check(!configOptions.isIbftLegacy) { "IBFT1 (legacy) is no longer supported. Consider using IBFT2 or QBFT." }
            builder = if (configOptions.isQbft) {
                QbftBesuControllerBuilder()
            } else if (configOptions.isClique) {
                CliqueBesuControllerBuilder()
            } else {
                throw IllegalArgumentException("Unknown consensus mechanism defined")
            }

            // wrap with TransitionBesuControllerBuilder if we have a terminal total difficulty:
            return if (configOptions.terminalTotalDifficulty.isPresent) {
                // Enable start with vanilla MergeBesuControllerBuilder for PoS checkpoint block
                if (syncMode == SyncMode.CHECKPOINT && isCheckpointPoSBlock(
                        configOptions
                    )
                ) {
                    MergeBesuControllerBuilder().genesisConfig(genesisConfig)
                } else {
                    // TODO this should be changed to vanilla MergeBesuControllerBuilder and the Transition*
                    // series of classes removed after we successfully transition to PoS
                    // https://github.com/hyperledger/besu/issues/2897
                    TransitionBesuControllerBuilder(builder, MergeBesuControllerBuilder())
                        .genesisConfig(genesisConfig)
                }
            } else builder.genesisConfig(genesisConfig)
        }

        private fun createConsensusScheduleBesuControllerBuilder(
            genesisConfig: GenesisConfig
        ): BesuControllerBuilder {
            val besuControllerBuilderSchedule: MutableMap<Long, BesuControllerBuilder> = HashMap()
            val configOptions = genesisConfig.configOptions
            val originalControllerBuilder = if (configOptions.isIbft2) {
                IbftBesuControllerBuilder()
            } else if (configOptions.isIbftLegacy) {
                IbftLegacyBesuControllerBuilder()
            } else {
                throw IllegalStateException(
                    "Invalid genesis migration config. Migration is supported from IBFT (legacy) or IBFT2 to QBFT)"
                )
            }
            besuControllerBuilderSchedule[0L] = originalControllerBuilder

            val qbftConfigOptions = configOptions.qbftConfigOptions
            val qbftBlock = readQbftStartBlockConfig(qbftConfigOptions)
            besuControllerBuilderSchedule[qbftBlock] = QbftBesuControllerBuilder()

            return ConsensusScheduleBesuControllerBuilder(besuControllerBuilderSchedule)
                .genesisConfig(genesisConfig)
        }

        private fun readQbftStartBlockConfig(qbftConfigOptions: QbftConfigOptions): Long {
            val startBlock =
                qbftConfigOptions
                    .startBlock
                    .orElseThrow { IllegalStateException("Missing QBFT startBlock config in genesis file") }

            check(startBlock > 0) { "Invalid QBFT startBlock config in genesis file" }

            return startBlock
        }

        private fun isCheckpointPoSBlock(configOptions: GenesisConfigOptions): Boolean {
            val terminalTotalDifficulty = configOptions.terminalTotalDifficulty.get()

            return configOptions.checkpointOptions.isValid
                    && (UInt256.fromHexString(configOptions.checkpointOptions.totalDifficulty.get())
                .greaterThan(terminalTotalDifficulty))
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(BesuController::class.java)

        /** The constant DATABASE_PATH.  */
        const val DATABASE_PATH: String = "database"

        /** The constant CACHE_PATH.  */
        const val CACHE_PATH: String = "caches"
    }
}

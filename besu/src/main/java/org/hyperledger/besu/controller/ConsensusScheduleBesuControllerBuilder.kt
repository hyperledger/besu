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
package org.hyperledger.besu.controller

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import org.hyperledger.besu.config.GenesisConfig
import org.hyperledger.besu.consensus.common.*
import org.hyperledger.besu.cryptoservices.NodeKey
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.ConsensusContext
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.chain.MutableBlockchain
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.core.PrivacyParameters
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration
import org.hyperledger.besu.ethereum.eth.manager.*
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration
import org.hyperledger.besu.ethereum.forkid.ForkIdManager
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration
import org.hyperledger.besu.ethereum.storage.StorageProvider
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.hyperledger.besu.evm.internal.EvmConfiguration
import org.hyperledger.besu.metrics.ObservableMetricsSystem
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider
import java.math.BigInteger
import java.nio.file.Path
import java.time.Clock
import java.util.*
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors

/**
 * This is a placeholder class for the QBFT migration logic. For now, all it does is to delegate any
 * BesuControllerBuilder to the first controller in the list.
 */
class ConsensusScheduleBesuControllerBuilder @VisibleForTesting constructor(
    besuControllerBuilderSchedule: Map<Long, BesuControllerBuilder>,
    combinedProtocolScheduleFactory: BiFunction<NavigableSet<ForkSpec<ProtocolSchedule>>, Optional<BigInteger>, ProtocolSchedule>
) : BesuControllerBuilder() {
    private val besuControllerBuilderSchedule: MutableMap<Long, BesuControllerBuilder> = HashMap()
    private val combinedProtocolScheduleFactory: BiFunction<NavigableSet<ForkSpec<ProtocolSchedule>>, Optional<BigInteger>, ProtocolSchedule>


    /**
     * Instantiates a new Consensus schedule Besu controller builder.
     *
     * @param besuControllerBuilderSchedule the besu controller builder schedule
     */
    constructor(besuControllerBuilderSchedule: Map<Long, BesuControllerBuilder>) : this(
        besuControllerBuilderSchedule,
        BiFunction<NavigableSet<ForkSpec<ProtocolSchedule>>, Optional<BigInteger>, ProtocolSchedule> { protocolScheduleSpecs: NavigableSet<ForkSpec<ProtocolSchedule>>, chainId: Optional<BigInteger> ->
            CombinedProtocolScheduleFactory().create(
                protocolScheduleSpecs,
                chainId
            )
        })

    /**
     * Instantiates a new Consensus schedule besu controller builder. Visible for testing.
     *
     * @param besuControllerBuilderSchedule the besu controller builder schedule
     * @param combinedProtocolScheduleFactory the combined protocol schedule factory
     */
    init {
        Preconditions.checkNotNull(
            besuControllerBuilderSchedule, "BesuControllerBuilder schedule can't be null"
        )
        Preconditions.checkArgument(
            !besuControllerBuilderSchedule.isEmpty(), "BesuControllerBuilder schedule can't be empty"
        )
        this.besuControllerBuilderSchedule.putAll(besuControllerBuilderSchedule)
        this.combinedProtocolScheduleFactory = combinedProtocolScheduleFactory
    }

    override fun prepForBuild() {
        besuControllerBuilderSchedule.values.forEach(Consumer { obj: BesuControllerBuilder -> obj.prepForBuild() })
        super.prepForBuild()
    }

    override fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator {
        val miningCoordinatorForkSpecs =
            besuControllerBuilderSchedule.entries.stream()
                .map { e: Map.Entry<Long, BesuControllerBuilder> ->
                    ForkSpec(
                        e.key,
                        e.value
                            .createMiningCoordinator(
                                protocolSchedule,
                                protocolContext,
                                transactionPool,
                                miningConfiguration,
                                syncState,
                                ethProtocolManager
                            )
                    )
                }
                .collect(Collectors.toList())
        val miningCoordinatorSchedule =
            ForksSchedule(miningCoordinatorForkSpecs)

        return MigratingMiningCoordinator(
            miningCoordinatorSchedule, protocolContext!!.blockchain
        )
    }

    override fun createProtocolSchedule(): ProtocolSchedule {
        val protocolScheduleSpecs: NavigableSet<ForkSpec<ProtocolSchedule>> =
            besuControllerBuilderSchedule.entries.stream()
                .map { e: Map.Entry<Long, BesuControllerBuilder> -> ForkSpec(e.key, e.value.createProtocolSchedule()) }
                .collect(
                    Collectors.toCollection<ForkSpec<ProtocolSchedule>, TreeSet<ForkSpec<ProtocolSchedule>>>(
                        Supplier { TreeSet(ForkSpec.COMPARATOR) })
                )
        val chainId = genesisConfigOptions!!.chainId
        return combinedProtocolScheduleFactory.apply(protocolScheduleSpecs, chainId)
    }

    override fun createProtocolContext(
        blockchain: MutableBlockchain?,
        worldStateArchive: WorldStateArchive?,
        consensusContext: ConsensusContext?,
        serviceManager: ServiceManager?
    ): ProtocolContext {
        return MigratingProtocolContext(
            blockchain,
            worldStateArchive,
            consensusContext!!.`as`(MigratingConsensusContext::class.java),
            badBlockManager,
            serviceManager
        )
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): ConsensusContext {
        val consensusContextSpecs =
            besuControllerBuilderSchedule.entries.stream()
                .map { e: Map.Entry<Long, BesuControllerBuilder> ->
                    ForkSpec(
                        e.key,
                        e.value
                            .createConsensusContext(
                                blockchain, worldStateArchive, protocolSchedule
                            )
                    )
                }
                .toList()
        val consensusContextsSchedule =
            ForksSchedule(consensusContextSpecs)
        return MigratingConsensusContext(consensusContextsSchedule)
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder ->
                b.createAdditionalPluginServices(
                    blockchain,
                    protocolContext
                )
            })
        return NoopPluginServiceFactory()
    }

    override fun createAdditionalJsonRpcMethodFactory(
        protocolContext: ProtocolContext?,
        protocolSchedule: ProtocolSchedule?,
        miningConfiguration: MiningConfiguration?
    ): JsonRpcMethods {
        besuControllerBuilderSchedule
            .values
            .forEach(
                Consumer { b: BesuControllerBuilder ->
                    b.createAdditionalJsonRpcMethodFactory(
                        protocolContext, protocolSchedule, miningConfiguration
                    )
                })
        return super.createAdditionalJsonRpcMethodFactory(
            protocolContext, protocolSchedule, miningConfiguration
        )
    }

    override fun createSubProtocolConfiguration(
        ethProtocolManager: EthProtocolManager?,
        maybeSnapProtocolManager: Optional<SnapProtocolManager>
    ): SubProtocolConfiguration {
        return besuControllerBuilderSchedule[besuControllerBuilderSchedule.keys.stream().skip(1).findFirst()
            .orElseThrow()]!!
            .createSubProtocolConfiguration(ethProtocolManager, maybeSnapProtocolManager)
    }

    override fun validateContext(context: ProtocolContext?) {
        besuControllerBuilderSchedule[BlockHeader.GENESIS_BLOCK_NUMBER]!!.validateContext(context)
    }

    override fun createEthProtocolManager(
        protocolContext: ProtocolContext,
        synchronizerConfiguration: SynchronizerConfiguration?,
        transactionPool: TransactionPool?,
        ethereumWireProtocolConfiguration: EthProtocolConfiguration,
        ethPeers: EthPeers?,
        ethContext: EthContext?,
        ethMessages: EthMessages?,
        scheduler: EthScheduler?,
        peerValidators: List<PeerValidator>?,
        mergePeerFilter: Optional<MergePeerFilter>,
        forkIdManager: ForkIdManager?
    ): EthProtocolManager {
        besuControllerBuilderSchedule
            .values
            .forEach(
                Consumer { b: BesuControllerBuilder ->
                    b.createEthProtocolManager(
                        protocolContext,
                        synchronizerConfiguration,
                        transactionPool,
                        ethereumWireProtocolConfiguration,
                        ethPeers,
                        ethContext,
                        ethMessages,
                        scheduler,
                        peerValidators,
                        mergePeerFilter,
                        forkIdManager
                    )
                })
        return super.createEthProtocolManager(
            protocolContext,
            synchronizerConfiguration,
            transactionPool,
            ethereumWireProtocolConfiguration,
            ethPeers,
            ethContext,
            ethMessages,
            scheduler,
            peerValidators,
            mergePeerFilter,
            forkIdManager
        )
    }

    override fun storageProvider(storageProvider: StorageProvider?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.storageProvider(
                storageProvider
            )
        })
        return super.storageProvider(storageProvider)
    }

    override fun genesisConfig(genesisConfig: GenesisConfig): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.genesisConfig(
                genesisConfig
            )
        })
        return super.genesisConfig(genesisConfig)
    }

    override fun synchronizerConfiguration(
        synchronizerConfig: SynchronizerConfiguration?
    ): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder -> b.synchronizerConfiguration(synchronizerConfig) })
        return super.synchronizerConfiguration(synchronizerConfig)
    }

    override fun ethProtocolConfiguration(
        ethProtocolConfiguration: EthProtocolConfiguration?
    ): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder -> b.ethProtocolConfiguration(ethProtocolConfiguration) })
        return super.ethProtocolConfiguration(ethProtocolConfiguration)
    }

    override fun networkId(networkId: BigInteger?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder -> b.networkId(networkId) })
        return super.networkId(networkId)
    }

    override fun miningParameters(miningConfiguration: MiningConfiguration?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.miningParameters(
                miningConfiguration
            )
        })
        return super.miningParameters(miningConfiguration)
    }

    override fun messagePermissioningProviders(
        messagePermissioningProviders: List<NodeMessagePermissioningProvider>
    ): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder ->
                b.messagePermissioningProviders(
                    messagePermissioningProviders
                )
            })
        return super.messagePermissioningProviders(messagePermissioningProviders)
    }

    override fun nodeKey(nodeKey: NodeKey?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder -> b.nodeKey(nodeKey) })
        return super.nodeKey(nodeKey)
    }

    override fun metricsSystem(metricsSystem: ObservableMetricsSystem?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.metricsSystem(
                metricsSystem
            )
        })
        return super.metricsSystem(metricsSystem)
    }

    override fun privacyParameters(privacyParameters: PrivacyParameters?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.privacyParameters(
                privacyParameters
            )
        })
        return super.privacyParameters(privacyParameters)
    }

    override fun dataDirectory(dataDirectory: Path?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.dataDirectory(
                dataDirectory
            )
        })
        return super.dataDirectory(dataDirectory)
    }

    override fun clock(clock: Clock?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder -> b.clock(clock) })
        return super.clock(clock)
    }

    override fun transactionPoolConfiguration(
        transactionPoolConfiguration: TransactionPoolConfiguration?
    ): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder -> b.transactionPoolConfiguration(transactionPoolConfiguration) })
        return super.transactionPoolConfiguration(transactionPoolConfiguration)
    }

    override fun isRevertReasonEnabled(isRevertReasonEnabled: Boolean): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder -> b.isRevertReasonEnabled(isRevertReasonEnabled) })
        return super.isRevertReasonEnabled(isRevertReasonEnabled)
    }

    override fun isParallelTxProcessingEnabled(
        isParallelTxProcessingEnabled: Boolean
    ): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder ->
                b.isParallelTxProcessingEnabled(
                    isParallelTxProcessingEnabled
                )
            })
        return super.isParallelTxProcessingEnabled(isParallelTxProcessingEnabled)
    }

    override fun requiredBlocks(requiredBlocks: Map<Long, Hash>): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.requiredBlocks(
                requiredBlocks
            )
        })
        return super.requiredBlocks(requiredBlocks)
    }

    override fun reorgLoggingThreshold(reorgLoggingThreshold: Long): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder -> b.reorgLoggingThreshold(reorgLoggingThreshold) })
        return super.reorgLoggingThreshold(reorgLoggingThreshold)
    }

    override fun dataStorageConfiguration(
        dataStorageConfiguration: DataStorageConfiguration
    ): BesuControllerBuilder? {
        besuControllerBuilderSchedule
            .values
            .forEach(Consumer { b: BesuControllerBuilder -> b.dataStorageConfiguration(dataStorageConfiguration) })
        return super.dataStorageConfiguration(dataStorageConfiguration)
    }

    override fun evmConfiguration(evmConfiguration: EvmConfiguration?): BesuControllerBuilder? {
        besuControllerBuilderSchedule.values.forEach(Consumer { b: BesuControllerBuilder ->
            b.evmConfiguration(
                evmConfiguration
            )
        })
        return super.evmConfiguration(evmConfiguration)
    }

    /**
     * Gets besu controller builder schedule. Visible for testing.
     *
     * @return the Besu controller builder schedule
     */
    @VisibleForTesting
    fun getBesuControllerBuilderSchedule(): Map<Long, BesuControllerBuilder> {
        return besuControllerBuilderSchedule
    }
}

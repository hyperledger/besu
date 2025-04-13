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

import org.hyperledger.besu.config.GenesisConfig
import org.hyperledger.besu.consensus.merge.MergeContext
import org.hyperledger.besu.consensus.merge.TransitionBackwardSyncContext
import org.hyperledger.besu.consensus.merge.TransitionContext
import org.hyperledger.besu.consensus.merge.TransitionProtocolSchedule
import org.hyperledger.besu.consensus.merge.blockcreation.TransitionCoordinator
import org.hyperledger.besu.cryptoservices.NodeKey
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.ConsensusContext
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.chain.MutableBlockchain
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration
import org.hyperledger.besu.ethereum.eth.manager.*
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator
import org.hyperledger.besu.ethereum.eth.sync.DefaultSynchronizer
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardSyncContext
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration
import org.hyperledger.besu.ethereum.forkid.ForkIdManager
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.storage.StorageProvider
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator
import org.hyperledger.besu.evm.internal.EvmConfiguration
import org.hyperledger.besu.metrics.ObservableMetricsSystem
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.nio.file.Path
import java.time.Clock
import java.util.*
import java.util.function.Consumer

/** The Transition besu controller builder.  */
class TransitionBesuControllerBuilder
/**
 * Instantiates a new Transition besu controller builder.
 *
 * @param preMergeBesuControllerBuilder the pre merge besu controller builder
 * @param mergeBesuControllerBuilder the merge besu controller builder
 */(
    private val preMergeBesuControllerBuilder: BesuControllerBuilder,
    private val mergeBesuControllerBuilder: MergeBesuControllerBuilder
) : BesuControllerBuilder() {
    private var transitionProtocolSchedule: TransitionProtocolSchedule? = null

    override fun prepForBuild() {
        preMergeBesuControllerBuilder.prepForBuild()
        mergeBesuControllerBuilder.prepForBuild()
    }

    override fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator {
        // cast to transition schedule for explicit access to pre and post objects:

        val transitionProtocolSchedule =
            protocolSchedule as TransitionProtocolSchedule?

        // PoA consensus mines by default, get consensus-specific mining parameters for
        // TransitionCoordinator:
        val transitionMiningConfiguration =
            preMergeBesuControllerBuilder.getMiningParameterOverrides(miningConfiguration!!)

        // construct a transition backward sync context
        val transitionBackwardsSyncContext: BackwardSyncContext =
            TransitionBackwardSyncContext(
                protocolContext,
                transitionProtocolSchedule,
                syncConfig,
                metricsSystem,
                ethProtocolManager!!.ethContext(),
                syncState,
                storageProvider
            )

        val composedCoordinator =
            TransitionCoordinator(
                preMergeBesuControllerBuilder.createMiningCoordinator(
                    transitionProtocolSchedule!!.preMergeSchedule,
                    protocolContext,
                    transactionPool,
                    ImmutableMiningConfiguration.builder()
                        .from(miningConfiguration)
                        .mutableInitValues(
                            ImmutableMiningConfiguration.MutableInitValues.builder()
                                .isMiningEnabled(false)
                                .build()
                        )
                        .build(),
                    syncState,
                    ethProtocolManager
                ),
                mergeBesuControllerBuilder.createTransitionMiningCoordinator(
                    transitionProtocolSchedule,
                    protocolContext!!,
                    transactionPool,
                    transitionMiningConfiguration,
                    syncState,
                    transitionBackwardsSyncContext,
                    ethProtocolManager.ethContext().scheduler
                ),
                mergeBesuControllerBuilder.postMergeContext
            )
        initTransitionWatcher(protocolContext, composedCoordinator)
        return composedCoordinator
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
        return mergeBesuControllerBuilder.createEthProtocolManager(
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

    override fun createProtocolSchedule(): ProtocolSchedule {
        transitionProtocolSchedule =
            TransitionProtocolSchedule(
                preMergeBesuControllerBuilder.createProtocolSchedule(),
                mergeBesuControllerBuilder.createProtocolSchedule(),
                mergeBesuControllerBuilder.postMergeContext
            )
        return transitionProtocolSchedule!!
    }

    override fun createProtocolContext(
        blockchain: MutableBlockchain?,
        worldStateArchive: WorldStateArchive?,
        consensusContext: ConsensusContext?,
        serviceManager: ServiceManager?
    ): ProtocolContext {
        val protocolContext =
            super.createProtocolContext(
                blockchain, worldStateArchive, consensusContext, serviceManager
            )
        transitionProtocolSchedule!!.setProtocolContext(protocolContext)
        return protocolContext
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): ConsensusContext? {
        return TransitionContext(
            preMergeBesuControllerBuilder.createConsensusContext(
                blockchain, worldStateArchive, protocolSchedule
            ),
            mergeBesuControllerBuilder.createConsensusContext(
                blockchain, worldStateArchive, protocolSchedule
            )
        )
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        return NoopPluginServiceFactory()
    }

    override fun createSynchronizer(
        protocolSchedule: ProtocolSchedule?,
        worldStateStorageCoordinator: WorldStateStorageCoordinator?,
        protocolContext: ProtocolContext,
        ethContext: EthContext?,
        peerTaskExecutor: PeerTaskExecutor?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager,
        pivotBlockSelector: PivotBlockSelector?
    ): DefaultSynchronizer {
        val sync =
            super.createSynchronizer(
                protocolSchedule,
                worldStateStorageCoordinator,
                protocolContext,
                ethContext,
                peerTaskExecutor,
                syncState,
                ethProtocolManager,
                pivotBlockSelector
            )

        if (genesisConfigOptions!!.terminalTotalDifficulty.isPresent) {
            LOG.info(
                "TTD present, creating DefaultSynchronizer that stops propagating after finalization"
            )
            protocolContext
                .getConsensusContext(MergeContext::class.java)
                .addNewUnverifiedForkchoiceListener(sync)
        }

        return sync
    }

    private fun initTransitionWatcher(
        protocolContext: ProtocolContext, composedCoordinator: TransitionCoordinator
    ) {
        val postMergeContext = mergeBesuControllerBuilder.postMergeContext
        postMergeContext.observeNewIsPostMergeState { isPoS: Boolean, priorState: Optional<Boolean?>?, difficultyStoppedAt: Optional<Difficulty?>? ->
            if (isPoS) {
                // if we transitioned to post-merge, stop and disable any mining
                composedCoordinator.preMergeObject.disable()
                composedCoordinator.preMergeObject.stop()
                // set the blockchoiceRule to never reorg, rely on forkchoiceUpdated instead
                protocolContext
                    .blockchain.blockChoiceRule =
                    Comparator { newBlockHeader: BlockHeader?, currentBlockHeader: BlockHeader? -> -1 }
            } else if (composedCoordinator.isMiningBeforeMerge) {
                // if our merge state is set to mine pre-merge and we are mining, start mining
                composedCoordinator.preMergeObject.enable()
                composedCoordinator.preMergeObject.start()
            }
        }

        // initialize our merge context merge status before we would start either
        val blockchain: Blockchain = protocolContext.blockchain
        blockchain
            .getTotalDifficultyByHash(blockchain.chainHeadHash)
            .ifPresent { totalDifficulty: Difficulty? -> postMergeContext.setIsPostMerge(totalDifficulty) }
    }

    override fun storageProvider(storageProvider: StorageProvider?): BesuControllerBuilder? {
        super.storageProvider(storageProvider)
        return propagateConfig { z: BesuControllerBuilder -> z.storageProvider(storageProvider) }
    }

    override fun build(): BesuController? {
        val controller = super.build()
        mergeBesuControllerBuilder.postMergeContext.setSyncState(controller!!.syncState)
        return controller
    }

    override fun evmConfiguration(evmConfiguration: EvmConfiguration?): BesuControllerBuilder? {
        super.evmConfiguration(evmConfiguration)
        return propagateConfig { z: BesuControllerBuilder -> z.evmConfiguration(evmConfiguration) }
    }

    override fun genesisConfig(genesisConfig: GenesisConfig): BesuControllerBuilder? {
        super.genesisConfig(genesisConfig)
        return propagateConfig { z: BesuControllerBuilder -> z.genesisConfig(genesisConfig) }
    }

    override fun synchronizerConfiguration(
        synchronizerConfig: SynchronizerConfiguration?
    ): BesuControllerBuilder? {
        super.synchronizerConfiguration(synchronizerConfig)
        return propagateConfig { z: BesuControllerBuilder -> z.synchronizerConfiguration(synchronizerConfig) }
    }

    override fun ethProtocolConfiguration(
        ethProtocolConfiguration: EthProtocolConfiguration?
    ): BesuControllerBuilder? {
        super.ethProtocolConfiguration(ethProtocolConfiguration)
        return propagateConfig { z: BesuControllerBuilder -> z.ethProtocolConfiguration(ethProtocolConfiguration) }
    }

    override fun networkId(networkId: BigInteger?): BesuControllerBuilder? {
        super.networkId(networkId)
        return propagateConfig { z: BesuControllerBuilder -> z.networkId(networkId) }
    }

    override fun miningParameters(miningConfiguration: MiningConfiguration?): BesuControllerBuilder? {
        super.miningParameters(miningConfiguration)
        return propagateConfig { z: BesuControllerBuilder -> z.miningParameters(miningConfiguration) }
    }

    override fun messagePermissioningProviders(
        messagePermissioningProviders: List<NodeMessagePermissioningProvider>
    ): BesuControllerBuilder {
        super.messagePermissioningProviders(messagePermissioningProviders)
        return propagateConfig { z: BesuControllerBuilder ->
            z.messagePermissioningProviders(
                messagePermissioningProviders
            )
        }
    }

    override fun nodeKey(nodeKey: NodeKey?): BesuControllerBuilder? {
        super.nodeKey(nodeKey)
        return propagateConfig { z: BesuControllerBuilder -> z.nodeKey(nodeKey) }
    }

    override fun metricsSystem(metricsSystem: ObservableMetricsSystem?): BesuControllerBuilder? {
        super.metricsSystem(metricsSystem)
        return propagateConfig { z: BesuControllerBuilder -> z.metricsSystem(metricsSystem) }
    }

    override fun privacyParameters(privacyParameters: PrivacyParameters?): BesuControllerBuilder? {
        super.privacyParameters(privacyParameters)
        return propagateConfig { z: BesuControllerBuilder -> z.privacyParameters(privacyParameters) }
    }

    override fun dataDirectory(dataDirectory: Path?): BesuControllerBuilder? {
        super.dataDirectory(dataDirectory)
        return propagateConfig { z: BesuControllerBuilder -> z.dataDirectory(dataDirectory) }
    }

    override fun clock(clock: Clock?): BesuControllerBuilder? {
        super.clock(clock)
        return propagateConfig { z: BesuControllerBuilder -> z.clock(clock) }
    }

    override fun transactionPoolConfiguration(
        transactionPoolConfiguration: TransactionPoolConfiguration?
    ): BesuControllerBuilder? {
        super.transactionPoolConfiguration(transactionPoolConfiguration)
        return propagateConfig { z: BesuControllerBuilder -> z.transactionPoolConfiguration(transactionPoolConfiguration) }
    }

    override fun isRevertReasonEnabled(isRevertReasonEnabled: Boolean): BesuControllerBuilder? {
        super.isRevertReasonEnabled(isRevertReasonEnabled)
        return propagateConfig { z: BesuControllerBuilder -> z.isRevertReasonEnabled(isRevertReasonEnabled) }
    }

    override fun isParallelTxProcessingEnabled(
        isParallelTxProcessingEnabled: Boolean
    ): BesuControllerBuilder? {
        super.isParallelTxProcessingEnabled(isParallelTxProcessingEnabled)
        return propagateConfig { z: BesuControllerBuilder ->
            z.isParallelTxProcessingEnabled(
                isParallelTxProcessingEnabled
            )
        }
    }

    override fun requiredBlocks(requiredBlocks: Map<Long, Hash>): BesuControllerBuilder {
        super.requiredBlocks(requiredBlocks)
        return propagateConfig { z: BesuControllerBuilder -> z.requiredBlocks(requiredBlocks) }
    }

    override fun reorgLoggingThreshold(reorgLoggingThreshold: Long): BesuControllerBuilder? {
        super.reorgLoggingThreshold(reorgLoggingThreshold)
        return propagateConfig { z: BesuControllerBuilder -> z.reorgLoggingThreshold(reorgLoggingThreshold) }
    }

    override fun dataStorageConfiguration(
        dataStorageConfiguration: DataStorageConfiguration
    ): BesuControllerBuilder? {
        super.dataStorageConfiguration(dataStorageConfiguration)
        return propagateConfig { z: BesuControllerBuilder -> z.dataStorageConfiguration(dataStorageConfiguration) }
    }

    private fun propagateConfig(toPropagate: Consumer<BesuControllerBuilder>): BesuControllerBuilder {
        toPropagate.accept(preMergeBesuControllerBuilder)
        toPropagate.accept(mergeBesuControllerBuilder)
        return this
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TransitionBesuControllerBuilder::class.java)
    }
}

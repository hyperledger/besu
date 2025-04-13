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

import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.consensus.merge.MergeContext
import org.hyperledger.besu.consensus.merge.MergeProtocolSchedule
import org.hyperledger.besu.consensus.merge.PostMergeContext
import org.hyperledger.besu.consensus.merge.TransitionBestPeerComparator
import org.hyperledger.besu.consensus.merge.blockcreation.MergeCoordinator
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.core.Difficulty
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration
import org.hyperledger.besu.ethereum.eth.manager.*
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator
import org.hyperledger.besu.ethereum.eth.peervalidation.RequiredBlocksPeerValidator
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardChain
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardSyncContext
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.forkid.ForkIdManager
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/** The Merge besu controller builder.  */
class MergeBesuControllerBuilder
/** Default constructor.  */
    : BesuControllerBuilder() {
    private val syncState = AtomicReference<SyncState?>()

    /**
     * Gets post merge context.
     *
     * @return the post merge context
     */
    @JvmField
    val postMergeContext: PostMergeContext = PostMergeContext()

    override fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator {
        return createTransitionMiningCoordinator(
            protocolSchedule,
            protocolContext!!,
            transactionPool,
            miningConfiguration!!,
            syncState,
            BackwardSyncContext(
                protocolContext,
                protocolSchedule,
                syncConfig,
                metricsSystem,
                ethProtocolManager!!.ethContext(),
                syncState,
                BackwardChain.from(
                    storageProvider, ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)
                )
            ),
            ethProtocolManager.ethContext().scheduler
        )
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
        val mergeContext = protocolContext!!.getConsensusContext(MergeContext::class.java)

        val mergeBestPeerComparator =
            TransitionBestPeerComparator(
                genesisConfigOptions!!.terminalTotalDifficulty.map { value: UInt256? -> Difficulty.of(value) }
                    .orElseThrow()
            )
        ethPeers!!.bestPeerComparator = mergeBestPeerComparator
        mergeContext.observeNewIsPostMergeState(mergeBestPeerComparator)

        var filterToUse = Optional.of(MergePeerFilter())

        if (mergePeerFilter!!.isPresent) {
            filterToUse = mergePeerFilter
        }
        mergeContext.observeNewIsPostMergeState(filterToUse.get())
        mergeContext.addNewUnverifiedForkchoiceListener(filterToUse.get())

        val ethProtocolManager =
            super.createEthProtocolManager(
                protocolContext!!,
                synchronizerConfiguration,
                transactionPool,
                ethereumWireProtocolConfiguration,
                ethPeers,
                ethContext,
                ethMessages,
                scheduler,
                peerValidators,
                filterToUse,
                forkIdManager
            )

        return ethProtocolManager
    }

    /**
     * Create transition mining coordinator.
     *
     * @param protocolSchedule the protocol schedule
     * @param protocolContext the protocol context
     * @param transactionPool the transaction pool
     * @param miningConfiguration the mining parameters
     * @param syncState the sync state
     * @param backwardSyncContext the backward sync context
     * @param ethScheduler the scheduler
     * @return the mining coordinator
     */
    fun createTransitionMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration,
        syncState: SyncState?,
        backwardSyncContext: BackwardSyncContext?,
        ethScheduler: EthScheduler?
    ): MiningCoordinator {
        this.syncState.set(syncState)

        val depositContractAddress =
            genesisConfigOptions!!.depositContractAddress

        return MergeCoordinator(
            protocolContext,
            protocolSchedule,
            ethScheduler,
            transactionPool,
            miningConfiguration,
            backwardSyncContext,
            depositContractAddress
        )
    }

    override fun createProtocolSchedule(): ProtocolSchedule {
        return MergeProtocolSchedule.create(
            genesisConfigOptions,
            privacyParameters,
            isRevertReasonEnabled,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem
        )
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): MergeContext? {
        val terminalBlockNumber = genesisConfigOptions!!.terminalBlockNumber
        val terminalBlockHash = genesisConfigOptions!!.terminalBlockHash
        val isPostMergeAtGenesis =
            genesisConfigOptions!!.terminalTotalDifficulty.isPresent
                    && genesisConfigOptions!!.terminalTotalDifficulty.get().isZero
                    && blockchain!!.genesisBlockHeader.difficulty.isZero

        val mergeContext: MergeContext =
            postMergeContext
                .setSyncState(syncState.get())
                .setTerminalTotalDifficulty(
                    genesisConfigOptions!!
                        .getTerminalTotalDifficulty()
                        .map { value: UInt256? -> Difficulty.of(value) }
                        .orElse(Difficulty.ZERO))
                .setPostMergeAtGenesis(isPostMergeAtGenesis)

        blockchain!!
            .finalized
            .flatMap { blockHeaderHash: Hash? ->
                blockchain.getBlockHeader(
                    blockHeaderHash
                )
            }
            .ifPresent { blockHeader: BlockHeader? ->
                mergeContext.setFinalized(
                    blockHeader
                )
            }

        blockchain
            .safeBlock
            .flatMap { blockHeaderHash: Hash? ->
                blockchain.getBlockHeader(
                    blockHeaderHash
                )
            }
            .ifPresent { blockHeader: BlockHeader? ->
                mergeContext.setSafeBlock(
                    blockHeader
                )
            }

        if (terminalBlockNumber.isPresent && terminalBlockHash.isPresent) {
            val termBlock = blockchain.getBlockHeader(terminalBlockNumber.asLong)
            mergeContext.terminalPoWBlock = termBlock
        }
        blockchain.observeBlockAdded { blockAddedEvent: BlockAddedEvent ->
            blockchain
                .getTotalDifficultyByHash(blockAddedEvent.block.header.hash)
                .ifPresent { totalDifficulty: Difficulty? -> mergeContext.setIsPostMerge(totalDifficulty) }
        }

        return mergeContext
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        return NoopPluginServiceFactory()
    }

    override fun createPeerValidators(
        protocolSchedule: ProtocolSchedule?, peerTaskExecutor: PeerTaskExecutor?
    ): List<PeerValidator> {
        val retval = super.createPeerValidators(protocolSchedule, peerTaskExecutor)
        val powTerminalBlockNumber = genesisConfigOptions!!.terminalBlockNumber
        val powTerminalBlockHash = genesisConfigOptions!!.terminalBlockHash
        if (powTerminalBlockHash.isPresent && powTerminalBlockNumber.isPresent) {
            retval.toMutableList().add(
                RequiredBlocksPeerValidator(
                    protocolSchedule,
                    peerTaskExecutor,
                    syncConfig,
                    metricsSystem,
                    powTerminalBlockNumber.asLong,
                    powTerminalBlockHash.get(),
                    0
                )
            )
        } else {
            LOG.debug("unable to validate peers with terminal difficulty blocks")
        }
        return retval
    }

    override fun build(): BesuController? {
        val controller = super.build()
        postMergeContext.setSyncState(syncState.get())
        return controller
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MergeBesuControllerBuilder::class.java)
    }
}

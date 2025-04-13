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

import org.hyperledger.besu.config.BftConfigOptions
import org.hyperledger.besu.config.BftFork
import org.hyperledger.besu.consensus.common.BftValidatorOverrides
import org.hyperledger.besu.consensus.common.EpochManager
import org.hyperledger.besu.consensus.common.ForksSchedule
import org.hyperledger.besu.consensus.common.bft.*
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreatorFactory
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftMiningCoordinator
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftProposerSelector
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector
import org.hyperledger.besu.consensus.common.bft.network.ValidatorPeers
import org.hyperledger.besu.consensus.common.bft.protocol.BftProtocolManager
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider
import org.hyperledger.besu.consensus.ibft.IbftExtraDataCodec
import org.hyperledger.besu.consensus.ibft.IbftForksSchedulesFactory
import org.hyperledger.besu.consensus.ibft.IbftGossip
import org.hyperledger.besu.consensus.ibft.IbftProtocolScheduleBuilder
import org.hyperledger.besu.consensus.ibft.jsonrpc.IbftJsonRpcMethods
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory
import org.hyperledger.besu.consensus.ibft.protocol.IbftSubProtocol
import org.hyperledger.besu.consensus.ibft.statemachine.IbftBlockHeightManagerFactory
import org.hyperledger.besu.consensus.ibft.statemachine.IbftController
import org.hyperledger.besu.consensus.ibft.statemachine.IbftRoundFactory
import org.hyperledger.besu.consensus.ibft.validation.MessageValidatorFactory
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver
import org.hyperledger.besu.ethereum.core.Block
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.core.Util
import org.hyperledger.besu.ethereum.eth.EthProtocol
import org.hyperledger.besu.ethereum.eth.SnapProtocol
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.hyperledger.besu.plugin.data.SyncStatus
import org.hyperledger.besu.plugin.services.BesuEvents.InitialSyncCompletionListener
import org.hyperledger.besu.util.Subscribers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.stream.Collectors

/** The Ibft besu controller builder.  */
class IbftBesuControllerBuilder
/** Default Constructor  */
    : BesuControllerBuilder() {
    private var bftEventQueue: BftEventQueue? = null
    private var bftConfig: BftConfigOptions? = null
    private var forksSchedule: ForksSchedule<BftConfigOptions>? = null
    private var peers: ValidatorPeers? = null
    private var bftExtraDataCodec: IbftExtraDataCodec? = null
    private var bftBlockInterface: BftBlockInterface? = null

    override fun prepForBuild() {
        bftConfig = genesisConfigOptions!!.bftConfigOptions
        bftEventQueue = BftEventQueue(bftConfig!!.getMessageQueueLimit())
        forksSchedule = IbftForksSchedulesFactory.create(genesisConfigOptions)
        bftExtraDataCodec = IbftExtraDataCodec()
        bftBlockInterface = BftBlockInterface(bftExtraDataCodec)
    }

    override fun createAdditionalJsonRpcMethodFactory(
        protocolContext: ProtocolContext?,
        protocolSchedule: ProtocolSchedule?,
        miningConfiguration: MiningConfiguration?
    ): JsonRpcMethods {
        return IbftJsonRpcMethods(protocolContext, protocolSchedule, miningConfiguration)
    }

    override fun createSubProtocolConfiguration(
        ethProtocolManager: EthProtocolManager?,
        maybeSnapProtocolManager: Optional<SnapProtocolManager>
    ): SubProtocolConfiguration {
        val subProtocolConfiguration =
            SubProtocolConfiguration()
                .withSubProtocol(EthProtocol.get(), ethProtocolManager)
                .withSubProtocol(
                    IbftSubProtocol.get(),
                    BftProtocolManager(
                        bftEventQueue, peers, IbftSubProtocol.IBFV1, IbftSubProtocol.get().name
                    )
                )
        maybeSnapProtocolManager.ifPresent { snapProtocolManager: SnapProtocolManager? ->
            subProtocolConfiguration.withSubProtocol(SnapProtocol.get(), snapProtocolManager)
        }
        return subProtocolConfiguration
    }

    override fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator {
        val blockchain = protocolContext!!.blockchain
        val bftExecutors =
            BftExecutors.create(metricsSystem, BftExecutors.ConsensusType.IBFT)

        val localAddress = Util.publicKeyToAddress(
            nodeKey!!.publicKey
        )
        val bftProtocolSchedule = protocolSchedule as BftProtocolSchedule?
        val blockCreatorFactory: BftBlockCreatorFactory<*> =
            BftBlockCreatorFactory(
                transactionPool,
                protocolContext,
                bftProtocolSchedule,
                forksSchedule,
                miningConfiguration,
                localAddress,
                bftExtraDataCodec,
                ethProtocolManager!!.ethContext().scheduler
            )

        val validatorProvider =
            protocolContext.getConsensusContext(BftContext::class.java).validatorProvider

        val proposerSelector: ProposerSelector =
            BftProposerSelector(blockchain, bftBlockInterface, true, validatorProvider)

        // NOTE: peers should not be used for accessing the network as it does not enforce the
        // "only send once" filter applied by the UniqueMessageMulticaster.
        peers = ValidatorPeers(validatorProvider, IbftSubProtocol.NAME)

        val uniqueMessageMulticaster =
            UniqueMessageMulticaster(peers, bftConfig!!.gossipedHistoryLimit)

        val gossiper = IbftGossip(uniqueMessageMulticaster)

        val finalState =
            BftFinalState(
                validatorProvider,
                nodeKey,
                Util.publicKeyToAddress(nodeKey!!.publicKey),
                proposerSelector,
                uniqueMessageMulticaster,
                RoundTimer(
                    bftEventQueue,
                    Duration.ofSeconds(bftConfig!!.requestTimeoutSeconds.toLong()),
                    bftExecutors
                ),
                BlockTimer(bftEventQueue, forksSchedule, bftExecutors, clock),
                blockCreatorFactory,
                clock
            )

        val messageValidatorFactory =
            MessageValidatorFactory(
                proposerSelector, bftProtocolSchedule, protocolContext, bftExtraDataCodec
            )

        val minedBlockObservers = Subscribers.create<MinedBlockObserver>()
        minedBlockObservers.subscribe(ethProtocolManager)
        minedBlockObservers.subscribe(blockLogger(transactionPool!!, localAddress))

        val futureMessageBuffer =
            FutureMessageBuffer(
                bftConfig!!.futureMessagesMaxDistance.toLong(),
                bftConfig!!.futureMessagesLimit.toLong(),
                blockchain.chainHeadBlockNumber
            )
        val duplicateMessageTracker =
            MessageTracker(bftConfig!!.duplicateMessageLimit)

        val messageFactory = MessageFactory(nodeKey)

        val ibftController: BftEventHandler =
            IbftController(
                blockchain,
                finalState,
                IbftBlockHeightManagerFactory(
                    finalState,
                    IbftRoundFactory(
                        finalState,
                        protocolContext,
                        bftProtocolSchedule,
                        minedBlockObservers,
                        messageValidatorFactory,
                        messageFactory,
                        bftExtraDataCodec
                    ),
                    messageValidatorFactory,
                    messageFactory
                ),
                gossiper,
                duplicateMessageTracker,
                futureMessageBuffer,
                EthSynchronizerUpdater(ethProtocolManager.ethContext().ethPeers)
            )

        val eventMultiplexer = EventMultiplexer(ibftController)
        val bftProcessor = BftProcessor(bftEventQueue, eventMultiplexer)

        val ibftMiningCoordinator: MiningCoordinator =
            BftMiningCoordinator(
                bftExecutors,
                ibftController,
                bftProcessor,
                blockCreatorFactory,
                blockchain,
                bftEventQueue
            )

        // Update the next block period in seconds according to the transition schedule
        protocolContext
            .blockchain
            .observeBlockAdded { o: BlockAddedEvent ->
                miningConfiguration!!.setBlockPeriodSeconds(
                    forksSchedule!!
                        .getFork(o.block.header.number + 1)
                        .value
                        .blockPeriodSeconds
                )
            }

        syncState!!.subscribeSyncStatus { syncStatus: Optional<SyncStatus?>? ->
            if (syncState.syncTarget().isPresent) {
                // We're syncing so stop doing other stuff
                LOG.info("Stopping IBFT mining coordinator while we are syncing")
                ibftMiningCoordinator.stop()
            } else {
                LOG.info("Starting IBFT mining coordinator following sync")
                ibftMiningCoordinator.enable()
                ibftMiningCoordinator.start()
            }
        }

        syncState.subscribeCompletionReached(
            object : InitialSyncCompletionListener {
                override fun onInitialSyncCompleted() {
                    LOG.info("Starting IBFT mining coordinator following initial sync")
                    ibftMiningCoordinator.enable()
                    ibftMiningCoordinator.start()
                }

                override fun onInitialSyncRestart() {
                    // Nothing to do. The mining coordinator won't be started until
                    // sync has completed.
                }
            })

        return ibftMiningCoordinator
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        val validatorProvider =
            protocolContext!!.getConsensusContext(BftContext::class.java).validatorProvider
        return IbftQueryPluginServiceFactory(
            blockchain!!, bftBlockInterface!!, validatorProvider, nodeKey!!
        )
    }

    override fun createProtocolSchedule(): ProtocolSchedule {
        return IbftProtocolScheduleBuilder.create(
            genesisConfigOptions,
            forksSchedule,
            privacyParameters,
            isRevertReasonEnabled,
            bftExtraDataCodec,
            evmConfiguration,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem
        )
    }

    override fun validateContext(context: ProtocolContext?) {
        val genesisBlockHeader = context!!.blockchain.genesisBlock.header

        if (bftBlockInterface!!.validatorsInBlock(genesisBlockHeader).isEmpty()) {
            LOG.warn("Genesis block contains no signers - chain will not progress.")
        }
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): BftContext {
        val ibftConfig = genesisConfigOptions!!.bftConfigOptions
        val epochManager = EpochManager(ibftConfig.epochLength)

        val validatorOverrides =
            convertIbftForks(genesisConfigOptions!!.transitions.ibftForks)

        return BftContext(
            BlockValidatorProvider.forkingValidatorProvider(
                blockchain, epochManager, bftBlockInterface, validatorOverrides
            ),
            epochManager,
            bftBlockInterface
        )
    }

    private fun convertIbftForks(bftForks: List<BftFork>): BftValidatorOverrides {
        val result: MutableMap<Long, List<Address>> = HashMap()

        for (fork in bftForks) {
            fork.validators
                .ifPresent { validators: List<String?> ->
                    result[fork.forkBlock] = validators.stream()
                        .map { str: String? ->
                            Address.fromHexString(
                                str
                            )
                        }
                        .collect(Collectors.toList())
                }
        }

        return BftValidatorOverrides(result)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IbftBesuControllerBuilder::class.java)
        private fun blockLogger(
            transactionPool: TransactionPool, localAddress: Address
        ): MinedBlockObserver {
            return MinedBlockObserver { block: Block ->
                LOG.info(
                    String.format(
                        "%s #%,d / %d tx / %d pending / %,d (%01.1f%%) gas / (%s)",
                        if (block.header.coinbase == localAddress) "Produced" else "Imported",
                        block.header.number,
                        block.body.transactions.size,
                        transactionPool.count(),
                        block.header.gasUsed,
                        (block.header.gasUsed * 100.0) / block.header.gasLimit,
                        block.hash.toHexString()
                    )
                )
            }
        }
    }
}

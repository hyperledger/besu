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

import com.google.common.base.Preconditions
import org.hyperledger.besu.config.BftConfigOptions
import org.hyperledger.besu.config.QbftConfigOptions
import org.hyperledger.besu.config.QbftFork
import org.hyperledger.besu.consensus.common.BftValidatorOverrides
import org.hyperledger.besu.consensus.common.EpochManager
import org.hyperledger.besu.consensus.common.ForksSchedule
import org.hyperledger.besu.consensus.common.bft.*
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftMiningCoordinator
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftProposerSelector
import org.hyperledger.besu.consensus.common.bft.blockcreation.ProposerSelector
import org.hyperledger.besu.consensus.common.bft.network.ValidatorPeers
import org.hyperledger.besu.consensus.common.bft.protocol.BftProtocolManager
import org.hyperledger.besu.consensus.common.bft.statemachine.BftEventHandler
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider
import org.hyperledger.besu.consensus.qbft.FutureMessageSynchronizerHandler
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec
import org.hyperledger.besu.consensus.qbft.QbftForksSchedulesFactory
import org.hyperledger.besu.consensus.qbft.QbftProtocolScheduleBuilder
import org.hyperledger.besu.consensus.qbft.adaptor.*
import org.hyperledger.besu.consensus.qbft.blockcreation.QbftBlockCreatorFactory
import org.hyperledger.besu.consensus.qbft.core.network.QbftGossip
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory
import org.hyperledger.besu.consensus.qbft.core.statemachine.QbftBlockHeightManagerFactory
import org.hyperledger.besu.consensus.qbft.core.statemachine.QbftController
import org.hyperledger.besu.consensus.qbft.core.statemachine.QbftRoundFactory
import org.hyperledger.besu.consensus.qbft.core.types.*
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidatorFactory
import org.hyperledger.besu.consensus.qbft.jsonrpc.QbftJsonRpcMethods
import org.hyperledger.besu.consensus.qbft.protocol.Istanbul100SubProtocol
import org.hyperledger.besu.consensus.qbft.validator.ForkingValidatorProvider
import org.hyperledger.besu.consensus.qbft.validator.TransactionValidatorProvider
import org.hyperledger.besu.consensus.qbft.validator.ValidatorContractController
import org.hyperledger.besu.consensus.qbft.validator.ValidatorModeTransitionLogger
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver
import org.hyperledger.besu.ethereum.core.Block
import org.hyperledger.besu.ethereum.core.BlockHeader
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
import org.hyperledger.besu.util.Subscribers
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.stream.Collectors

/** The Qbft Besu controller builder.  */
class QbftBesuControllerBuilder
/** Default Constructor.  */
    : BesuControllerBuilder() {
    private var bftEventQueue: BftEventQueue? = null
    private var qbftConfig: QbftConfigOptions? = null
    private var qbftForksSchedule: ForksSchedule<QbftConfigOptions>? = null
    private var peers: ValidatorPeers? = null
    private var transactionValidatorProvider: TransactionValidatorProvider? = null
    private var bftConfigOptions: BftConfigOptions? = null
    private var qbftExtraDataCodec: QbftExtraDataCodec? = null
    private var bftBlockInterface: BftBlockInterface? = null

    override fun prepForBuild() {
        qbftConfig = genesisConfigOptions!!.qbftConfigOptions
        bftEventQueue = BftEventQueue(qbftConfig!!.getMessageQueueLimit())
        qbftForksSchedule = QbftForksSchedulesFactory.create(genesisConfigOptions)
        bftConfigOptions = qbftConfig
        qbftExtraDataCodec = QbftExtraDataCodec()
        bftBlockInterface = BftBlockInterface(qbftExtraDataCodec)
    }

    override fun createAdditionalJsonRpcMethodFactory(
        protocolContext: ProtocolContext?,
        protocolSchedule: ProtocolSchedule?,
        miningConfiguration: MiningConfiguration?
    ): JsonRpcMethods {
        return QbftJsonRpcMethods(
            protocolContext,
            protocolSchedule,
            miningConfiguration,
            createReadOnlyValidatorProvider(protocolContext!!.blockchain),
            bftConfigOptions
        )
    }

    private fun createReadOnlyValidatorProvider(blockchain: Blockchain): ValidatorProvider {
        Preconditions.checkNotNull(
            transactionValidatorProvider, "transactionValidatorProvider should have been initialised"
        )
        val startBlock =
            if (qbftConfig!!.startBlock.isPresent) qbftConfig!!.startBlock.asLong else 0
        val epochManager = EpochManager(qbftConfig!!.epochLength, startBlock)
        // Must create our own voteTallyCache as using this would pollute the main voteTallyCache
        val readOnlyBlockValidatorProvider =
            BlockValidatorProvider.nonForkingValidatorProvider(
                blockchain, epochManager, bftBlockInterface
            )
        return ForkingValidatorProvider(
            blockchain,
            qbftForksSchedule,
            readOnlyBlockValidatorProvider,
            transactionValidatorProvider
        )
    }

    override fun createSubProtocolConfiguration(
        ethProtocolManager: EthProtocolManager?,
        maybeSnapProtocolManager: Optional<SnapProtocolManager>
    ): SubProtocolConfiguration {
        val subProtocolConfiguration =
            SubProtocolConfiguration()
                .withSubProtocol(EthProtocol.get(), ethProtocolManager)
                .withSubProtocol(
                    Istanbul100SubProtocol.get(),
                    BftProtocolManager(
                        bftEventQueue,
                        peers,
                        Istanbul100SubProtocol.ISTANBUL_100,
                        Istanbul100SubProtocol.get().name
                    )
                )
        maybeSnapProtocolManager.ifPresent { snapProtocolManager: SnapProtocolManager? ->
            subProtocolConfiguration.withSubProtocol(
                SnapProtocol.get(),
                snapProtocolManager
            )
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
            BftExecutors.create(metricsSystem, BftExecutors.ConsensusType.QBFT)
        val blockEncoder: QbftBlockCodec = QbftBlockCodecAdaptor(qbftExtraDataCodec)

        val localAddress = Util.publicKeyToAddress(
            nodeKey!!.publicKey
        )
        val bftProtocolSchedule = protocolSchedule as BftProtocolSchedule?
        val qbftProtocolSchedule: QbftProtocolSchedule =
            QbftProtocolScheduleAdaptor(bftProtocolSchedule, protocolContext)
        val blockCreatorFactory =
            QbftBlockCreatorFactory(
                transactionPool,
                protocolContext,
                bftProtocolSchedule,
                qbftForksSchedule,
                miningConfiguration,
                localAddress,
                qbftExtraDataCodec,
                ethProtocolManager!!.ethContext().scheduler
            )
        val validatorProvider = if (qbftConfig!!.startBlock.isPresent) {
            protocolContext
                .getConsensusContext(BftContext::class.java, qbftConfig!!.startBlock.asLong)
                .validatorProvider
        } else {
            protocolContext.getConsensusContext(BftContext::class.java).validatorProvider
        }
        val qbftValidatorProvider: QbftValidatorProvider =
            QbftValidatorProviderAdaptor(validatorProvider)

        val qbftBlockInterface: QbftBlockInterface = QbftBlockInterfaceAdaptor(bftBlockInterface)

        val proposerSelector: ProposerSelector =
            BftProposerSelector(blockchain, bftBlockInterface, true, validatorProvider)

        // NOTE: peers should not be used for accessing the network as it does not enforce the
        // "only send once" filter applied by the UniqueMessageMulticaster.
        peers = ValidatorPeers(validatorProvider, Istanbul100SubProtocol.NAME)

        val uniqueMessageMulticaster =
            UniqueMessageMulticaster(peers, qbftConfig!!.gossipedHistoryLimit)

        val gossiper = QbftGossip(uniqueMessageMulticaster, blockEncoder)

        val finalState: QbftFinalState =
            QbftFinalStateImpl(
                validatorProvider,
                nodeKey,
                Util.publicKeyToAddress(nodeKey!!.publicKey),
                proposerSelector,
                uniqueMessageMulticaster,
                RoundTimer(
                    bftEventQueue,
                    Duration.ofSeconds(qbftConfig!!.requestTimeoutSeconds.toLong()),
                    bftExecutors
                ),
                BlockTimer(bftEventQueue, qbftForksSchedule, bftExecutors, clock),
                QbftBlockCreatorFactoryAdaptor(blockCreatorFactory, qbftExtraDataCodec),
                clock
            )

        val messageValidatorFactory =
            MessageValidatorFactory(
                proposerSelector, qbftProtocolSchedule, qbftValidatorProvider, qbftBlockInterface
            )

        val minedBlockObservers = Subscribers.create<QbftMinedBlockObserver>()
        minedBlockObservers.subscribe(
            QbftMinedBlockObserver { qbftBlock: QbftBlock? ->
                ethProtocolManager.blockMined(
                    BlockUtil.toBesuBlock(
                        qbftBlock
                    )
                )
            })
        minedBlockObservers.subscribe(
            QbftMinedBlockObserver { qbftBlock: QbftBlock? ->
                blockLogger(transactionPool!!, localAddress)
                    .blockMined(BlockUtil.toBesuBlock(qbftBlock))
            })

        val synchronizerUpdater =
            EthSynchronizerUpdater(ethProtocolManager.ethContext().ethPeers)
        val futureMessageBuffer =
            FutureMessageBuffer(
                qbftConfig!!.futureMessagesMaxDistance.toLong(),
                qbftConfig!!.futureMessagesLimit.toLong(),
                blockchain.chainHeadBlockNumber,
                FutureMessageSynchronizerHandler(synchronizerUpdater)
            )
        val duplicateMessageTracker =
            MessageTracker(qbftConfig!!.duplicateMessageLimit)

        val messageFactory = MessageFactory(nodeKey, blockEncoder)

        val qbftRoundFactory =
            QbftRoundFactory(
                finalState,
                qbftBlockInterface,
                qbftProtocolSchedule,
                minedBlockObservers,
                messageValidatorFactory,
                messageFactory
            )
        val qbftBlockHeightManagerFactory =
            QbftBlockHeightManagerFactory(
                finalState,
                qbftRoundFactory,
                messageValidatorFactory,
                messageFactory,
                qbftValidatorProvider,
                QbftValidatorModeTransitionLoggerAdaptor(
                    ValidatorModeTransitionLogger(qbftForksSchedule)
                )
            )

        qbftBlockHeightManagerFactory.isEarlyRoundChangeEnabled(isEarlyRoundChangeEnabled)

        val qbftController: QbftEventHandler =
            QbftController(
                QbftBlockchainAdaptor(blockchain),
                finalState,
                qbftBlockHeightManagerFactory,
                gossiper,
                duplicateMessageTracker,
                futureMessageBuffer,
                blockEncoder
            )
        val bftEventHandler: BftEventHandler = BftEventHandlerAdaptor(qbftController)

        val eventMultiplexer = EventMultiplexer(bftEventHandler)
        val bftProcessor = BftProcessor(bftEventQueue, eventMultiplexer)

        val miningCoordinator: MiningCoordinator =
            BftMiningCoordinator(
                bftExecutors,
                bftEventHandler,
                bftProcessor,
                blockCreatorFactory,
                blockchain,
                bftEventQueue,
                syncState
            )

        // Update the next block period in seconds according to the transition schedule
        protocolContext
            .blockchain
            .observeBlockAdded { o: BlockAddedEvent ->
                miningConfiguration!!.setBlockPeriodSeconds(
                    qbftForksSchedule!!
                        .getFork(o.block.header.number + 1)
                        .value
                        .blockPeriodSeconds
                )
                miningConfiguration.setEmptyBlockPeriodSeconds(
                    qbftForksSchedule!!
                        .getFork(o.block.header.number + 1)
                        .value
                        .emptyBlockPeriodSeconds
                )
            }

        return miningCoordinator
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        val validatorProvider =
            protocolContext!!.getConsensusContext(BftContext::class.java).validatorProvider
        return BftQueryPluginServiceFactory(
            blockchain!!, qbftExtraDataCodec!!, validatorProvider, nodeKey!!, "qbft"
        )
    }

    override fun createProtocolSchedule(): ProtocolSchedule {
        return QbftProtocolScheduleBuilder.create(
            genesisConfigOptions,
            qbftForksSchedule,
            privacyParameters,
            isRevertReasonEnabled,
            qbftExtraDataCodec,
            evmConfiguration,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem
        )
    }

    override fun validateContext(context: ProtocolContext?) {
        val genesisBlockHeader = context!!.blockchain.genesisBlock.header

        if (usingValidatorContractModeButSignersExistIn(genesisBlockHeader)) {
            LOG.warn(
                "Using validator contract mode but genesis block contains signers - the genesis block signers will not be used."
            )
        }

        if (usingValidatorBlockHeaderModeButNoSignersIn(genesisBlockHeader)) {
            LOG.warn("Genesis block contains no signers - chain will not progress.")
        }
    }

    private fun usingValidatorContractModeButSignersExistIn(
        genesisBlockHeader: BlockHeader
    ): Boolean {
        return isValidatorContractMode && signersExistIn(genesisBlockHeader)
    }

    private fun usingValidatorBlockHeaderModeButNoSignersIn(
        genesisBlockHeader: BlockHeader
    ): Boolean {
        return !isValidatorContractMode && !signersExistIn(genesisBlockHeader)
    }

    private val isValidatorContractMode: Boolean
        get() = genesisConfigOptions!!.qbftConfigOptions.isValidatorContractMode

    private fun signersExistIn(genesisBlockHeader: BlockHeader): Boolean {
        return !bftBlockInterface!!.validatorsInBlock(genesisBlockHeader).isEmpty()
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): BftContext? {
        val startBlock =
            if (qbftConfig!!.startBlock.isPresent) qbftConfig!!.startBlock.asLong else 0
        val epochManager = EpochManager(qbftConfig!!.epochLength, startBlock)

        val validatorOverrides =
            convertBftForks(genesisConfigOptions!!.transitions.qbftForks)
        val blockValidatorProvider =
            BlockValidatorProvider.forkingValidatorProvider(
                blockchain, epochManager, bftBlockInterface, validatorOverrides
            )

        transactionValidatorProvider =
            TransactionValidatorProvider(
                blockchain, ValidatorContractController(transactionSimulator), qbftForksSchedule
            )

        val validatorProvider: ValidatorProvider =
            ForkingValidatorProvider(
                blockchain, qbftForksSchedule, blockValidatorProvider, transactionValidatorProvider
            )

        return BftContext(validatorProvider, epochManager, bftBlockInterface)
    }

    private fun convertBftForks(bftForks: List<QbftFork>): BftValidatorOverrides {
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
        private val LOG: Logger = LoggerFactory.getLogger(QbftBesuControllerBuilder::class.java)
        private fun blockLogger(
            transactionPool: TransactionPool, localAddress: Address
        ): MinedBlockObserver {
            return MinedBlockObserver { block: Block ->
                LOG.info(
                    String.format(
                        "%s %s #%,d / %d tx / %d pending / %,d (%01.1f%%) gas / (%s)",
                        if (block.header.coinbase == localAddress) "Produced" else "Imported",
                        if (block.body.transactions.isEmpty()) "empty block" else "block",
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

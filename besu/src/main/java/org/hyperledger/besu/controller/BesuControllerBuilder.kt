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
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.components.BesuComponent
import org.hyperledger.besu.config.GenesisConfig
import org.hyperledger.besu.config.GenesisConfigOptions
import org.hyperledger.besu.consensus.merge.MergeContext
import org.hyperledger.besu.consensus.merge.UnverifiedForkchoiceSupplier
import org.hyperledger.besu.consensus.qbft.BFTPivotSelectorFromPeers
import org.hyperledger.besu.cryptoservices.NodeKey
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.ConsensusContext
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.api.ApiConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.chain.*
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.eth.EthProtocol
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration
import org.hyperledger.besu.ethereum.eth.SnapProtocol
import org.hyperledger.besu.ethereum.eth.manager.*
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutor
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskRequestSender
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager
import org.hyperledger.besu.ethereum.eth.peervalidation.*
import org.hyperledger.besu.ethereum.eth.sync.DefaultSynchronizer
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector
import org.hyperledger.besu.ethereum.eth.sync.SyncMode
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotSelectorFromPeers
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotSelectorFromSafeBlock
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.ImmutableCheckpoint
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolFactory
import org.hyperledger.besu.ethereum.forkid.ForkIdManager
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration
import org.hyperledger.besu.ethereum.storage.StorageProvider
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator
import org.hyperledger.besu.ethereum.trie.forest.ForestWorldStateArchive
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiWorldStateProvider
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.BonsaiCachedMerkleTrieLoader
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogPruner
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive.WorldStateHealer
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator
import org.hyperledger.besu.evm.internal.EvmConfiguration
import org.hyperledger.besu.metrics.ObservableMetricsSystem
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat
import org.hyperledger.besu.services.BesuPluginContextImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.math.BigInteger
import java.nio.file.Path
import java.time.Clock
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/** The Besu controller builder that builds Besu Controller.  */
abstract class BesuControllerBuilder
/** Instantiates a new Besu controller builder.  */
protected constructor() : MiningParameterOverrides {
    /** The genesis file  */
    protected var genesisConfig: GenesisConfig? = null

    /** The genesis config options;  */
    @JvmField
    protected var genesisConfigOptions: GenesisConfigOptions? = null

    /** The is genesis state hash from data.  */
    protected var genesisStateHashCacheEnabled: Boolean = false

    /** The Sync config.  */
    @JvmField
    protected var syncConfig: SynchronizerConfiguration? = null

    /** The Ethereum wire protocol configuration.  */
    protected var ethereumWireProtocolConfiguration: EthProtocolConfiguration? = null

    /** The Transaction pool configuration.  */
    protected var transactionPoolConfiguration: TransactionPoolConfiguration? = null

    /** The Network id.  */
    protected var networkId: BigInteger? = null

    /** The Mining parameters.  */
    @JvmField
    protected var miningConfiguration: MiningConfiguration? = null

    /** The Metrics system.  */
    @JvmField
    protected var metricsSystem: ObservableMetricsSystem? = null

    /** The Privacy parameters.  */
    @JvmField
    protected var privacyParameters: PrivacyParameters? = null

    /** The Data directory.  */
    protected var dataDirectory: Path? = null

    /** The Clock.  */
    @JvmField
    protected var clock: Clock? = null

    /** The Node key.  */
    @JvmField
    protected var nodeKey: NodeKey? = null

    /** The Is revert reason enabled.  */
    @JvmField
    protected var isRevertReasonEnabled: Boolean = false

    /** The Storage provider.  */
    @JvmField
    protected var storageProvider: StorageProvider? = null

    /** The Required blocks.  */
    protected var requiredBlocks: Map<Long, Hash> = emptyMap()

    /** The Reorg logging threshold.  */
    protected var reorgLoggingThreshold: Long = 0

    /** The Data storage configuration.  */
    protected var dataStorageConfiguration: DataStorageConfiguration = DataStorageConfiguration.DEFAULT_CONFIG

    /** The Message permissioning providers.  */
    protected var messagePermissioningProviders: List<NodeMessagePermissioningProvider> = emptyList()

    /** The Evm configuration.  */
    @JvmField
    protected var evmConfiguration: EvmConfiguration? = null

    /** The Max peers.  */
    protected var maxPeers: Int = 0

    /** Manages a cache of bad blocks globally  */
    @JvmField
    protected val badBlockManager: BadBlockManager = BadBlockManager()

    private var maxRemotelyInitiatedPeers = 0

    /** The Chain pruner configuration.  */
    protected var chainPrunerConfiguration: ChainPrunerConfiguration = ChainPrunerConfiguration.DEFAULT

    private var networkingConfiguration: NetworkingConfiguration? = null
    private var randomPeerPriority: Boolean? = null

    /** the Dagger configured context that can provide dependencies  */
    protected var besuComponent: Optional<BesuComponent> = Optional.empty()

    private var numberOfBlocksToCache = 0

    /** whether parallel transaction processing is enabled or not  */
    @JvmField
    protected var isParallelTxProcessingEnabled: Boolean = false

    /** The API configuration  */
    protected var apiConfiguration: ApiConfiguration? = null

    /** The transaction simulator  */
    @JvmField
    protected var transactionSimulator: TransactionSimulator? = null

    /** When enabled, round changes on f+1 RC messages from higher rounds  */
    @JvmField
    protected var isEarlyRoundChangeEnabled: Boolean = false

    /**
     * Provide a BesuComponent which can be used to get other dependencies
     *
     * @param besuComponent application context that can be used to get other dependencies
     * @return the besu controller builder
     */
    fun besuComponent(besuComponent: BesuComponent?): BesuControllerBuilder {
        this.besuComponent = Optional.ofNullable(besuComponent)
        return this
    }

    /**
     * Storage provider besu controller builder.
     *
     * @param storageProvider the storage provider
     * @return the besu controller builder
     */
    open fun storageProvider(storageProvider: StorageProvider?): BesuControllerBuilder? {
        this.storageProvider = storageProvider
        return this
    }

    /**
     * Genesis config file besu controller builder.
     *
     * @param genesisConfig the genesis config
     * @return the besu controller builder
     */
    open fun genesisConfig(genesisConfig: GenesisConfig): BesuControllerBuilder? {
        this.genesisConfig = genesisConfig
        this.genesisConfigOptions = genesisConfig.configOptions
        return this
    }

    /**
     * Genesis state hash from data besu controller builder.
     *
     * @param genesisStateHashCacheEnabled the is genesis state hash from data
     * @return the besu controller builder
     */
    fun genesisStateHashCacheEnabled(
        genesisStateHashCacheEnabled: Boolean
    ): BesuControllerBuilder {
        this.genesisStateHashCacheEnabled = genesisStateHashCacheEnabled
        return this
    }

    /**
     * Synchronizer configuration besu controller builder.
     *
     * @param synchronizerConfig the synchronizer config
     * @return the besu controller builder
     */
    open fun synchronizerConfiguration(
        synchronizerConfig: SynchronizerConfiguration?
    ): BesuControllerBuilder? {
        this.syncConfig = synchronizerConfig
        return this
    }

    /**
     * API configuration besu controller builder.
     *
     * @param apiConfiguration the API config
     * @return the besu controller builder
     */
    fun apiConfiguration(apiConfiguration: ApiConfiguration?): BesuControllerBuilder {
        this.apiConfiguration = apiConfiguration
        return this
    }

    /**
     * Eth protocol configuration besu controller builder.
     *
     * @param ethProtocolConfiguration the eth protocol configuration
     * @return the besu controller builder
     */
    open fun ethProtocolConfiguration(
        ethProtocolConfiguration: EthProtocolConfiguration?
    ): BesuControllerBuilder? {
        this.ethereumWireProtocolConfiguration = ethProtocolConfiguration
        return this
    }

    /**
     * Network id besu controller builder.
     *
     * @param networkId the network id
     * @return the besu controller builder
     */
    open fun networkId(networkId: BigInteger?): BesuControllerBuilder? {
        this.networkId = networkId
        return this
    }

    /**
     * Mining parameters besu controller builder.
     *
     * @param miningConfiguration the mining parameters
     * @return the besu controller builder
     */
    open fun miningParameters(miningConfiguration: MiningConfiguration?): BesuControllerBuilder? {
        this.miningConfiguration = miningConfiguration
        return this
    }

    /**
     * Message permissioning providers besu controller builder.
     *
     * @param messagePermissioningProviders the message permissioning providers
     * @return the besu controller builder
     */
    open fun messagePermissioningProviders(
        messagePermissioningProviders: List<NodeMessagePermissioningProvider>
    ): BesuControllerBuilder? {
        this.messagePermissioningProviders = messagePermissioningProviders
        return this
    }

    /**
     * Node key besu controller builder.
     *
     * @param nodeKey the node key
     * @return the besu controller builder
     */
    open fun nodeKey(nodeKey: NodeKey?): BesuControllerBuilder? {
        this.nodeKey = nodeKey
        return this
    }

    /**
     * Metrics system besu controller builder.
     *
     * @param metricsSystem the metrics system
     * @return the besu controller builder
     */
    open fun metricsSystem(metricsSystem: ObservableMetricsSystem?): BesuControllerBuilder? {
        this.metricsSystem = metricsSystem
        return this
    }

    /**
     * Privacy parameters besu controller builder.
     *
     * @param privacyParameters the privacy parameters
     * @return the besu controller builder
     */
    open fun privacyParameters(privacyParameters: PrivacyParameters?): BesuControllerBuilder? {
        this.privacyParameters = privacyParameters
        return this
    }

    /**
     * Data directory besu controller builder.
     *
     * @param dataDirectory the data directory
     * @return the besu controller builder
     */
    open fun dataDirectory(dataDirectory: Path?): BesuControllerBuilder? {
        this.dataDirectory = dataDirectory
        return this
    }

    /**
     * Clock besu controller builder.
     *
     * @param clock the clock
     * @return the besu controller builder
     */
    open fun clock(clock: Clock?): BesuControllerBuilder? {
        this.clock = clock
        return this
    }

    /**
     * Transaction pool configuration besu controller builder.
     *
     * @param transactionPoolConfiguration the transaction pool configuration
     * @return the besu controller builder
     */
    open fun transactionPoolConfiguration(
        transactionPoolConfiguration: TransactionPoolConfiguration?
    ): BesuControllerBuilder? {
        this.transactionPoolConfiguration = transactionPoolConfiguration
        return this
    }

    /**
     * Is revert reason enabled besu controller builder.
     *
     * @param isRevertReasonEnabled the is revert reason enabled
     * @return the besu controller builder
     */
    open fun isRevertReasonEnabled(isRevertReasonEnabled: Boolean): BesuControllerBuilder? {
        this.isRevertReasonEnabled = isRevertReasonEnabled
        return this
    }

    /**
     * Required blocks besu controller builder.
     *
     * @param requiredBlocks the required blocks
     * @return the besu controller builder
     */
    open fun requiredBlocks(requiredBlocks: Map<Long, Hash>): BesuControllerBuilder? {
        this.requiredBlocks = requiredBlocks
        return this
    }

    /**
     * Reorg logging threshold besu controller builder.
     *
     * @param reorgLoggingThreshold the reorg logging threshold
     * @return the besu controller builder
     */
    open fun reorgLoggingThreshold(reorgLoggingThreshold: Long): BesuControllerBuilder? {
        this.reorgLoggingThreshold = reorgLoggingThreshold
        return this
    }

    /**
     * Data storage configuration besu controller builder.
     *
     * @param dataStorageConfiguration the data storage configuration
     * @return the besu controller builder
     */
    open fun dataStorageConfiguration(
        dataStorageConfiguration: DataStorageConfiguration
    ): BesuControllerBuilder? {
        this.dataStorageConfiguration = dataStorageConfiguration
        return this
    }

    /**
     * Evm configuration besu controller builder.
     *
     * @param evmConfiguration the evm configuration
     * @return the besu controller builder
     */
    open fun evmConfiguration(evmConfiguration: EvmConfiguration?): BesuControllerBuilder? {
        this.evmConfiguration = evmConfiguration
        return this
    }

    /**
     * Max peers besu controller builder.
     *
     * @param maxPeers the max peers
     * @return the besu controller builder
     */
    fun maxPeers(maxPeers: Int): BesuControllerBuilder {
        this.maxPeers = maxPeers
        return this
    }

    /**
     * Maximum number of remotely initiated peer connections
     *
     * @param maxRemotelyInitiatedPeers maximum number of remotely initiated peer connections
     * @return the besu controller builder
     */
    fun maxRemotelyInitiatedPeers(maxRemotelyInitiatedPeers: Int): BesuControllerBuilder {
        this.maxRemotelyInitiatedPeers = maxRemotelyInitiatedPeers
        return this
    }

    /**
     * Chain pruning configuration besu controller builder.
     *
     * @param chainPrunerConfiguration the chain pruner configuration
     * @return the besu controller builder
     */
    fun chainPruningConfiguration(
        chainPrunerConfiguration: ChainPrunerConfiguration
    ): BesuControllerBuilder {
        this.chainPrunerConfiguration = chainPrunerConfiguration
        return this
    }

    /**
     * Sets the number of blocks to cache.
     *
     * @param numberOfBlocksToCache the number of blocks to cache
     * @return the besu controller builder
     */
    fun cacheLastBlocks(numberOfBlocksToCache: Int): BesuControllerBuilder {
        this.numberOfBlocksToCache = numberOfBlocksToCache
        return this
    }

    /**
     * sets the networkConfiguration in the builder
     *
     * @param networkingConfiguration the networking config
     * @return the besu controller builder
     */
    fun networkConfiguration(
        networkingConfiguration: NetworkingConfiguration?
    ): BesuControllerBuilder {
        this.networkingConfiguration = networkingConfiguration
        return this
    }

    /**
     * sets the randomPeerPriority flag in the builder
     *
     * @param randomPeerPriority the random peer priority flag
     * @return the besu controller builder
     */
    fun randomPeerPriority(randomPeerPriority: Boolean?): BesuControllerBuilder {
        this.randomPeerPriority = randomPeerPriority
        return this
    }

    /**
     * Sets whether parallel transaction processing is enabled. When parallel transaction processing
     * is enabled, transactions within a block can be processed in parallel and potentially improving
     * performance
     *
     * @param isParallelTxProcessingEnabled true to enable parallel transaction
     * @return the besu controller
     */
    open fun isParallelTxProcessingEnabled(
        isParallelTxProcessingEnabled: Boolean
    ): BesuControllerBuilder? {
        this.isParallelTxProcessingEnabled = isParallelTxProcessingEnabled
        return this
    }

    /**
     * check if early round change is enabled when f+1 RC messages from higher rounds are received
     *
     * @param isEarlyRoundChangeEnabled whether to enable early round change
     * @return the besu controller
     */
    fun isEarlyRoundChangeEnabled(isEarlyRoundChangeEnabled: Boolean): BesuControllerBuilder {
        this.isEarlyRoundChangeEnabled = isEarlyRoundChangeEnabled
        return this
    }

    /**
     * Build besu controller.
     *
     * @return the besu controller
     */
    open fun build(): BesuController? {
        Preconditions.checkNotNull(genesisConfig, "Missing genesis config file")
        Preconditions.checkNotNull(genesisConfigOptions, "Missing genesis config options")
        Preconditions.checkNotNull(syncConfig, "Missing sync config")
        Preconditions.checkNotNull(ethereumWireProtocolConfiguration, "Missing ethereum protocol configuration")
        Preconditions.checkNotNull(networkId, "Missing network ID")
        Preconditions.checkNotNull(miningConfiguration, "Missing mining parameters")
        Preconditions.checkNotNull(metricsSystem, "Missing metrics system")
        Preconditions.checkNotNull(privacyParameters, "Missing privacy parameters")
        Preconditions.checkNotNull(dataDirectory, "Missing data directory") // Why do we need this?
        Preconditions.checkNotNull(clock, "Missing clock")
        Preconditions.checkNotNull(transactionPoolConfiguration, "Missing transaction pool configuration")
        Preconditions.checkNotNull(nodeKey, "Missing node key")
        Preconditions.checkNotNull(storageProvider, "Must supply a storage provider")
        Preconditions.checkNotNull(evmConfiguration, "Missing evm config")
        Preconditions.checkNotNull(networkingConfiguration, "Missing network configuration")
        Preconditions.checkNotNull(apiConfiguration, "Missing API configuration")
        Preconditions.checkNotNull(dataStorageConfiguration, "Missing data storage configuration")
        Preconditions.checkNotNull(besuComponent, "Must supply a BesuComponent")
        prepForBuild()

        val protocolSchedule = createProtocolSchedule()

        val variablesStorage = storageProvider!!.createVariablesStorage()

        val worldStateStorageCoordinator =
            storageProvider!!.createWorldStateStorageCoordinator(dataStorageConfiguration)

        val blockchainStorage =
            storageProvider!!.createBlockchainStorage(
                protocolSchedule, variablesStorage, dataStorageConfiguration
            )

        val maybeStoredGenesisBlockHash = blockchainStorage.getBlockHash(0L)

        val genesisState =
            getGenesisState(
                maybeStoredGenesisBlockHash.flatMap { blockHash: Hash? ->
                    blockchainStorage.getBlockHeader(
                        blockHash
                    )
                },
                protocolSchedule
            )

        val blockchain =
            DefaultBlockchain.createMutable(
                genesisState.block,
                blockchainStorage,
                metricsSystem,
                reorgLoggingThreshold,
                dataDirectory.toString(),
                numberOfBlocksToCache
            )
        val bonsaiCachedMerkleTrieLoader =
            besuComponent
                .map { obj: BesuComponent -> obj.cachedMerkleTrieLoader }
                .orElseGet { BonsaiCachedMerkleTrieLoader(metricsSystem) }

        val worldStateHealerSupplier = AtomicReference<WorldStateHealer>()

        val worldStateArchive =
            createWorldStateArchive(
                worldStateStorageCoordinator,
                blockchain,
                bonsaiCachedMerkleTrieLoader
            ) { worldStateHealerSupplier.get() }

        if (maybeStoredGenesisBlockHash.isEmpty) {
            genesisState.writeStateTo(worldStateArchive.worldState)
        }

        transactionSimulator =
            TransactionSimulator(
                blockchain,
                worldStateArchive,
                protocolSchedule,
                miningConfiguration,
                apiConfiguration!!.gasCap
            )

        val consensusContext =
            createConsensusContext(blockchain, worldStateArchive, protocolSchedule)

        val protocolContext =
            createProtocolContext(
                blockchain,
                worldStateArchive,
                consensusContext,
                besuComponent
                    .map { obj: BesuComponent -> obj.besuPluginContext }
                    .orElse(BesuPluginContextImpl()))
        validateContext(protocolContext)

        protocolSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(
            protocolContext.worldStateArchive
        )

        val maxMessageSize = ethereumWireProtocolConfiguration!!.maxMessageSize
        val currentProtocolSpecSupplier =
            Supplier { protocolSchedule.getByBlockHeader(blockchain.chainHeadHeader) }
        val forkIdManager =
            ForkIdManager(
                blockchain,
                genesisConfigOptions!!.forkBlockNumbers,
                genesisConfigOptions!!.forkBlockTimestamps
            )
        val ethPeers =
            EthPeers(
                currentProtocolSpecSupplier,
                clock,
                metricsSystem,
                maxMessageSize,
                messagePermissioningProviders,
                nodeKey!!.publicKey.encodedBytes,
                maxPeers,
                maxRemotelyInitiatedPeers,
                randomPeerPriority,
                syncConfig!!.syncMode,
                forkIdManager
            )

        val ethMessages = EthMessages()
        val snapMessages = EthMessages()

        val scheduler =
            EthScheduler(
                syncConfig!!.downloaderParallelism,
                syncConfig!!.transactionsParallelism,
                syncConfig!!.computationParallelism,
                metricsSystem
            )

        var checkpoint: Optional<Checkpoint> = Optional.empty()
        if (genesisConfigOptions!!.checkpointOptions.isValid) {
            checkpoint =
                Optional.of(
                    ImmutableCheckpoint.builder()
                        .blockHash(
                            Hash.fromHexString(
                                genesisConfigOptions!!.checkpointOptions.hash.get()
                            )
                        )
                        .blockNumber(genesisConfigOptions!!.checkpointOptions.number.asLong)
                        .totalDifficulty(
                            Difficulty.fromHexString(
                                genesisConfigOptions!!.checkpointOptions.totalDifficulty.get()
                            )
                        )
                        .build()
                )
        }

        val peerTaskExecutor =
            PeerTaskExecutor(ethPeers, PeerTaskRequestSender(), metricsSystem)
        val ethContext =
            EthContext(ethPeers, ethMessages, snapMessages, scheduler, peerTaskExecutor)
        val fullSyncDisabled = !SyncMode.isFullSync(syncConfig!!.syncMode)
        val syncState = SyncState(blockchain, ethPeers, fullSyncDisabled, checkpoint)

        if (chainPrunerConfiguration.chainPruningEnabled) {
            val chainDataPruner = createChainPruner(blockchainStorage)
            blockchain.observeBlockAdded(chainDataPruner)
            LOG.info(
                ("Chain data pruning enabled with recent blocks retained to be: "
                        + chainPrunerConfiguration.chainPruningBlocksRetained
                        + " and frequency to be: "
                        + chainPrunerConfiguration.chainPruningBlocksFrequency)
            )
        }

        val transactionPool =
            TransactionPoolFactory.createTransactionPool(
                protocolSchedule,
                protocolContext,
                ethContext,
                clock,
                metricsSystem,
                syncState,
                transactionPoolConfiguration,
                besuComponent.map { obj: BesuComponent -> obj.blobCache }.orElse(BlobCache()),
                miningConfiguration,
                syncConfig!!.isPeerTaskSystemEnabled
            )

        val peerValidators =
            createPeerValidators(protocolSchedule, peerTaskExecutor)

        val ethProtocolManager =
            createEthProtocolManager(
                protocolContext,
                syncConfig,
                transactionPool,
                ethereumWireProtocolConfiguration!!,
                ethPeers,
                ethContext,
                ethMessages,
                scheduler,
                peerValidators,
                Optional.empty(),
                forkIdManager
            )

        val pivotBlockSelector =
            createPivotSelector(
                protocolSchedule, protocolContext, ethContext, syncState, metricsSystem, blockchain
            )

        val synchronizer =
            createSynchronizer(
                protocolSchedule,
                worldStateStorageCoordinator,
                protocolContext,
                ethContext,
                peerTaskExecutor,
                syncState,
                ethProtocolManager,
                pivotBlockSelector
            )

        worldStateHealerSupplier.set(WorldStateHealer { maybeAccountToRepair: Optional<Address?>?, location: Bytes? ->
            synchronizer.healWorldState(
                maybeAccountToRepair,
                location
            )
        })

        ethPeers.setTrailingPeerRequirementsSupplier { synchronizer.calculateTrailingPeerRequirements() }

        if (syncConfig!!.syncMode == SyncMode.SNAP
            || syncConfig!!.syncMode == SyncMode.CHECKPOINT
        ) {
            synchronizer.subscribeInSync { b: Boolean -> ethPeers.snapServerPeersNeeded(!b) }
            ethPeers.snapServerPeersNeeded(true)
        } else {
            ethPeers.snapServerPeersNeeded(false)
        }

        val maybeSnapProtocolManager =
            createSnapProtocolManager(
                protocolContext, worldStateStorageCoordinator, ethPeers, snapMessages, synchronizer
            )

        val miningCoordinator =
            createMiningCoordinator(
                protocolSchedule,
                protocolContext,
                transactionPool,
                miningConfiguration,
                syncState,
                ethProtocolManager
            )

        val additionalPluginServices =
            createAdditionalPluginServices(blockchain, protocolContext)

        val subProtocolConfiguration =
            createSubProtocolConfiguration(ethProtocolManager, maybeSnapProtocolManager)

        val additionalJsonRpcMethodFactory =
            createAdditionalJsonRpcMethodFactory(
                protocolContext, protocolSchedule, miningConfiguration
            )

        if (DataStorageFormat.BONSAI == dataStorageConfiguration.dataStorageFormat) {
            val subStorageConfiguration =
                dataStorageConfiguration.pathBasedExtraStorageConfiguration
            if (subStorageConfiguration.limitTrieLogsEnabled) {
                val trieLogManager =
                    (worldStateArchive as BonsaiWorldStateProvider).trieLogManager
                val worldStateKeyValueStorage =
                    worldStateStorageCoordinator.getStrategy(
                        BonsaiWorldStateKeyValueStorage::class.java
                    )
                val trieLogPruner =
                    createTrieLogPruner(worldStateKeyValueStorage, blockchain, scheduler)
                trieLogManager.subscribe(trieLogPruner)
            }
        }

        val closeables: MutableList<Closeable?> = ArrayList()
        closeables.add(protocolContext.worldStateArchive)
        closeables.add(storageProvider)
        if (privacyParameters!!.privateStorageProvider != null) {
            closeables.add(privacyParameters!!.privateStorageProvider)
        }

        return BesuController(
            protocolSchedule,
            protocolContext,
            ethProtocolManager,
            genesisConfigOptions!!,
            subProtocolConfiguration,
            synchronizer,
            syncState,
            transactionPool,
            miningCoordinator,
            privacyParameters!!,
            miningConfiguration!!,
            additionalJsonRpcMethodFactory,
            nodeKey!!,
            closeables,
            additionalPluginServices,
            ethPeers,
            storageProvider!!,
            dataStorageConfiguration,
            transactionSimulator!!
        )
    }

    private fun getGenesisState(
        maybeGenesisBlockHeader: Optional<BlockHeader>,
        protocolSchedule: ProtocolSchedule
    ): GenesisState {
        val maybeGenesisStateRoot =
            if (genesisStateHashCacheEnabled)
                maybeGenesisBlockHeader.map { obj: BlockHeader -> obj.stateRoot }
            else
                Optional.empty()

        return maybeGenesisStateRoot
            .map { genesisStateRoot: Hash? ->
                GenesisState.fromStorage(
                    genesisStateRoot,
                    genesisConfig,
                    protocolSchedule
                )
            }
            .orElseGet { GenesisState.fromConfig(dataStorageConfiguration, genesisConfig, protocolSchedule) }
    }

    private fun createTrieLogPruner(
        worldStateStorage: WorldStateKeyValueStorage,
        blockchain: Blockchain,
        scheduler: EthScheduler
    ): TrieLogPruner {
        val isProofOfStake = genesisConfigOptions!!.terminalTotalDifficulty.isPresent
        val subStorageConfiguration =
            dataStorageConfiguration.pathBasedExtraStorageConfiguration
        val trieLogPruner =
            TrieLogPruner(
                worldStateStorage as BonsaiWorldStateKeyValueStorage,
                blockchain,
                { command: Runnable? -> scheduler.executeServiceTask(command) },
                subStorageConfiguration.maxLayersToLoad,
                subStorageConfiguration.trieLogPruningWindowSize,
                isProofOfStake,
                metricsSystem
            )
        trieLogPruner.initialize()

        return trieLogPruner
    }

    /**
     * Create synchronizer synchronizer.
     *
     * @param protocolSchedule the protocol schedule
     * @param worldStateStorageCoordinator the world state storage
     * @param protocolContext the protocol context
     * @param ethContext the eth context
     * @param peerTaskExecutor the PeerTaskExecutor
     * @param syncState the sync state
     * @param ethProtocolManager the eth protocol manager
     * @param pivotBlockSelector the pivot block selector
     * @return the synchronizer
     */
    protected open fun createSynchronizer(
        protocolSchedule: ProtocolSchedule?,
        worldStateStorageCoordinator: WorldStateStorageCoordinator?,
        protocolContext: ProtocolContext,
        ethContext: EthContext?,
        peerTaskExecutor: PeerTaskExecutor?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager,
        pivotBlockSelector: PivotBlockSelector?
    ): DefaultSynchronizer {
        return DefaultSynchronizer(
            syncConfig,
            protocolSchedule,
            protocolContext,
            worldStateStorageCoordinator,
            ethProtocolManager.blockBroadcaster,
            ethContext,
            peerTaskExecutor,
            syncState,
            dataDirectory,
            storageProvider,
            clock,
            metricsSystem,
            getFullSyncTerminationCondition(protocolContext.blockchain),
            pivotBlockSelector
        )
    }

    private fun createPivotSelector(
        protocolSchedule: ProtocolSchedule,
        protocolContext: ProtocolContext,
        ethContext: EthContext,
        syncState: SyncState,
        metricsSystem: MetricsSystem?,
        blockchain: Blockchain
    ): PivotBlockSelector {
        if (genesisConfigOptions!!.isQbft || genesisConfigOptions!!.isIbft2) {
            LOG.info(
                "{} is configured, creating initial sync for BFT",
                genesisConfigOptions!!.consensusEngine.uppercase()
            )
            return BFTPivotSelectorFromPeers(
                ethContext,
                syncConfig,
                syncState,
                protocolContext,
                nodeKey,
                blockchain.chainHeadHeader
            )
        } else if (genesisConfigOptions!!.terminalTotalDifficulty.isPresent) {
            LOG.info("TTD difficulty is present, creating initial sync for PoS")

            val mergeContext = protocolContext.getConsensusContext(MergeContext::class.java)
            val unverifiedForkchoiceSupplier =
                UnverifiedForkchoiceSupplier()
            val subscriptionId =
                mergeContext.addNewUnverifiedForkchoiceListener(unverifiedForkchoiceSupplier)

            val unsubscribeForkchoiceListener =
                Runnable {
                    mergeContext.removeNewUnverifiedForkchoiceListener(subscriptionId)
                    LOG.info("Initial sync done, unsubscribe forkchoice supplier")
                }

            return PivotSelectorFromSafeBlock(
                protocolContext,
                protocolSchedule,
                ethContext,
                metricsSystem,
                genesisConfigOptions,
                syncConfig,
                unverifiedForkchoiceSupplier,
                unsubscribeForkchoiceListener
            )
        } else {
            LOG.info("TTD difficulty is not present, creating initial sync phase for PoW")
            return PivotSelectorFromPeers(ethContext, syncConfig, syncState)
        }
    }

    /**
     * Gets full sync termination condition.
     *
     * @param blockchain the blockchain
     * @return the full sync termination condition
     */
    protected fun getFullSyncTerminationCondition(blockchain: Blockchain?): SyncTerminationCondition {
        return genesisConfigOptions!!
            .getTerminalTotalDifficulty()
            .map { difficulty: UInt256? ->
                SyncTerminationCondition.difficulty(
                    difficulty,
                    blockchain
                )
            }
            .orElse(SyncTerminationCondition.never())
    }

    /** Prep for build.  */
    open fun prepForBuild() {}

    /**
     * Create additional json rpc method factory json rpc methods.
     *
     * @param protocolContext the protocol context
     * @param protocolSchedule the protocol schedule
     * @param miningConfiguration the mining parameters
     * @return the json rpc methods
     */
    open fun createAdditionalJsonRpcMethodFactory(
        protocolContext: ProtocolContext?,
        protocolSchedule: ProtocolSchedule?,
        miningConfiguration: MiningConfiguration?
    ): JsonRpcMethods {
        return JsonRpcMethods { apis: Collection<String?>? -> emptyMap() }
    }

    /**
     * Create sub protocol configuration sub protocol configuration.
     *
     * @param ethProtocolManager the eth protocol manager
     * @param maybeSnapProtocolManager the maybe snap protocol manager
     * @return the sub protocol configuration
     */
    open fun createSubProtocolConfiguration(
        ethProtocolManager: EthProtocolManager?,
        maybeSnapProtocolManager: Optional<SnapProtocolManager>
    ): SubProtocolConfiguration {
        val subProtocolConfiguration =
            SubProtocolConfiguration().withSubProtocol(EthProtocol.get(), ethProtocolManager)
        maybeSnapProtocolManager.ifPresent { snapProtocolManager: SnapProtocolManager? ->
            subProtocolConfiguration.withSubProtocol(
                SnapProtocol.get(),
                snapProtocolManager
            )
        }
        return subProtocolConfiguration
    }

    /**
     * Create mining coordinator.
     *
     * @param protocolSchedule the protocol schedule
     * @param protocolContext the protocol context
     * @param transactionPool the transaction pool
     * @param miningConfiguration the mining parameters
     * @param syncState the sync state
     * @param ethProtocolManager the eth protocol manager
     * @return the mining coordinator
     */
    abstract fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator

    /**
     * Create protocol schedule protocol schedule.
     *
     * @return the protocol schedule
     */
    abstract fun createProtocolSchedule(): ProtocolSchedule

    /**
     * Validate context.
     *
     * @param context the context
     */
    open fun validateContext(context: ProtocolContext?) {}

    /**
     * Create consensus context consensus context.
     *
     * @param blockchain the blockchain
     * @param worldStateArchive the world state archive
     * @param protocolSchedule the protocol schedule
     * @return the consensus context
     */
    abstract fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): ConsensusContext?

    /**
     * Create eth protocol manager eth protocol manager.
     *
     * @param protocolContext the protocol context
     * @param synchronizerConfiguration the synchronizer configuration
     * @param transactionPool the transaction pool
     * @param ethereumWireProtocolConfiguration the ethereum wire protocol configuration
     * @param ethPeers the eth peers
     * @param ethContext the eth context
     * @param ethMessages the eth messages
     * @param scheduler the scheduler
     * @param peerValidators the peer validators
     * @param mergePeerFilter the merge peer filter
     * @param forkIdManager the fork id manager
     * @return the eth protocol manager
     */
    open fun createEthProtocolManager(
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
        return EthProtocolManager(
            protocolContext.blockchain,
            networkId,
            protocolContext.worldStateArchive,
            transactionPool,
            ethereumWireProtocolConfiguration,
            ethPeers,
            ethMessages,
            ethContext,
            peerValidators,
            mergePeerFilter,
            synchronizerConfiguration,
            scheduler,
            forkIdManager
        )
    }

    /**
     * Create protocol context protocol context.
     *
     * @param blockchain the blockchain
     * @param worldStateArchive the world state archive
     * @param consensusContext the consensus context
     * @param serviceManager plugin service manager
     * @return the protocol context
     */
    protected open fun createProtocolContext(
        blockchain: MutableBlockchain?,
        worldStateArchive: WorldStateArchive?,
        consensusContext: ConsensusContext?,
        serviceManager: ServiceManager?
    ): ProtocolContext {
        return ProtocolContext.Builder()
            .withBlockchain(blockchain)
            .withWorldStateArchive(worldStateArchive)
            .withConsensusContext(consensusContext)
            .withBadBlockManager(badBlockManager)
            .withServiceManager(serviceManager)
            .build()
    }

    private fun createSnapProtocolManager(
        protocolContext: ProtocolContext,
        worldStateStorageCoordinator: WorldStateStorageCoordinator,
        ethPeers: EthPeers,
        snapMessages: EthMessages,
        synchronizer: Synchronizer
    ): Optional<SnapProtocolManager> {
        return Optional.of(
            SnapProtocolManager(
                worldStateStorageCoordinator,
                syncConfig!!.snapSyncConfiguration,
                ethPeers,
                snapMessages,
                protocolContext,
                synchronizer
            )
        )
    }

    fun createWorldStateArchive(
        worldStateStorageCoordinator: WorldStateStorageCoordinator,
        blockchain: Blockchain?,
        bonsaiCachedMerkleTrieLoader: BonsaiCachedMerkleTrieLoader?,
        worldStateHealerSupplier: Supplier<WorldStateHealer>?
    ): WorldStateArchive {
        return when (dataStorageConfiguration.dataStorageFormat) {
            DataStorageFormat.BONSAI -> {
                val worldStateKeyValueStorage =
                    worldStateStorageCoordinator.getStrategy(
                        BonsaiWorldStateKeyValueStorage::class.java
                    )

                BonsaiWorldStateProvider(
                    worldStateKeyValueStorage,
                    blockchain,
                    Optional.of(
                        dataStorageConfiguration
                            .pathBasedExtraStorageConfiguration
                            .maxLayersToLoad
                    ),
                    bonsaiCachedMerkleTrieLoader,
                    besuComponent.map { obj: BesuComponent -> obj.besuPluginContext }.orElse(null),
                    evmConfiguration,
                    worldStateHealerSupplier
                )
            }

            DataStorageFormat.FOREST -> {
                val preimageStorage =
                    storageProvider!!.createWorldStatePreimageStorage()
                ForestWorldStateArchive(
                    worldStateStorageCoordinator, preimageStorage, evmConfiguration
                )
            }

            else -> throw IllegalStateException(
                "Unexpected value: " + dataStorageConfiguration.dataStorageFormat
            )
        }
    }

    private fun createChainPruner(blockchainStorage: BlockchainStorage): ChainDataPruner {
        return ChainDataPruner(
            blockchainStorage,
            ChainDataPrunerStorage(
                storageProvider!!.getStorageBySegmentIdentifier(
                    KeyValueSegmentIdentifier.CHAIN_PRUNER_STATE
                )
            ),
            chainPrunerConfiguration.chainPruningBlocksRetained,
            chainPrunerConfiguration.chainPruningBlocksFrequency,
            MonitoredExecutors.newBoundedThreadPool(
                ChainDataPruner::class.java.simpleName,
                1,
                1,
                ChainDataPruner.MAX_PRUNING_THREAD_QUEUE_SIZE,
                metricsSystem
            )
        )
    }

    /**
     * Create peer validators list.
     *
     * @param protocolSchedule the protocol schedule
     * @param peerTaskExecutor the peer task executor
     * @return the list
     */
    protected open fun createPeerValidators(
        protocolSchedule: ProtocolSchedule?, peerTaskExecutor: PeerTaskExecutor?
    ): List<PeerValidator> {
        val validators: MutableList<PeerValidator> = ArrayList()

        val daoBlock = genesisConfigOptions!!.daoForkBlock
        if (daoBlock.isPresent) {
            // Setup dao validator
            validators.add(
                DaoForkPeerValidator(
                    protocolSchedule, peerTaskExecutor, syncConfig, metricsSystem, daoBlock.asLong
                )
            )
        }

        val classicBlock = genesisConfigOptions!!.classicForkBlock
        // setup classic validator
        if (classicBlock.isPresent) {
            validators.add(
                ClassicForkPeerValidator(
                    protocolSchedule,
                    peerTaskExecutor,
                    syncConfig,
                    metricsSystem,
                    classicBlock.asLong
                )
            )
        }

        for ((key, value) in requiredBlocks) {
            validators.add(
                RequiredBlocksPeerValidator(
                    protocolSchedule,
                    peerTaskExecutor,
                    syncConfig,
                    metricsSystem,
                    key,
                    value
                )
            )
        }

        val checkpointConfigOptions =
            genesisConfigOptions!!.checkpointOptions
        if (syncConfig!!.syncMode == SyncMode.CHECKPOINT && checkpointConfigOptions.isValid) {
            validators.add(
                CheckpointBlocksPeerValidator(
                    protocolSchedule,
                    peerTaskExecutor,
                    syncConfig,
                    metricsSystem,
                    checkpointConfigOptions.number.orElseThrow(),
                    checkpointConfigOptions.hash.map { str: String? ->
                        Hash.fromHexString(
                            str
                        )
                    }.orElseThrow()
                )
            )
        }
        return validators
    }

    /**
     * Create additional plugin services plugin service factory.
     *
     * @param blockchain the blockchain
     * @param protocolContext the protocol context
     * @return the plugin service factory
     */
    abstract fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(BesuControllerBuilder::class.java)
    }
}

/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import graphql.GraphQL
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import org.apache.commons.net.util.SubnetUtils.SubnetInfo
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.cli.config.EthNetworkConfig
import org.hyperledger.besu.cli.config.EthNetworkConfig.Companion.getNetworkConfig
import org.hyperledger.besu.cli.config.NetworkName
import org.hyperledger.besu.cli.options.EthstatsOptions
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.api.ApiConfiguration
import org.hyperledger.besu.ethereum.api.graphql.*
import org.hyperledger.besu.ethereum.api.jsonrpc.EngineJsonRpcService
import org.hyperledger.besu.ethereum.api.jsonrpc.InProcessRpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcHttpService
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationService
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.DefaultAuthenticationService
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.EngineAuthService
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.AuthenticatedJsonRpcProcessor
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.BaseJsonRpcProcessor
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService
import org.hyperledger.besu.ethereum.api.jsonrpc.health.LivenessCheck
import org.hyperledger.besu.ethereum.api.jsonrpc.health.ReadinessCheck
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManagerBuilder
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.ipc.JsonRpcIpcService
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethodsFactory
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketMessageHandler
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketService
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.PrivateWebSocketMethodsFactory
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketMethodsFactory
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscriptionService
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.logs.LogsSubscriptionService
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.pending.PendingTransactionDroppedSubscriptionService
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.pending.PendingTransactionSubscriptionService
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.syncing.SyncingSubscriptionService
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.FlexiblePrivacyPrecompiledContract
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration
import org.hyperledger.besu.ethereum.p2p.network.*
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration
import org.hyperledger.besu.ethereum.p2p.peers.Peer
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionSubnet
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsDenylist
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController
import org.hyperledger.besu.ethereum.permissioning.NodePermissioningControllerFactory
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration
import org.hyperledger.besu.ethereum.permissioning.account.AccountPermissioningController
import org.hyperledger.besu.ethereum.permissioning.account.AccountPermissioningControllerFactory
import org.hyperledger.besu.ethereum.permissioning.node.InsufficientPeersPermissioningProvider
import org.hyperledger.besu.ethereum.permissioning.node.NodePermissioningController
import org.hyperledger.besu.ethereum.permissioning.node.PeerPermissionsAdapter
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionObserver
import org.hyperledger.besu.ethereum.storage.StorageProvider
import org.hyperledger.besu.ethereum.stratum.StratumServer
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator
import org.hyperledger.besu.ethstats.EthStatsService
import org.hyperledger.besu.ethstats.util.EthStatsConnectOptions
import org.hyperledger.besu.metrics.MetricsService
import org.hyperledger.besu.metrics.ObservableMetricsSystem
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration
import org.hyperledger.besu.nat.NatMethod
import org.hyperledger.besu.nat.NatService
import org.hyperledger.besu.nat.core.NatManager
import org.hyperledger.besu.nat.docker.DockerDetector
import org.hyperledger.besu.nat.docker.DockerNatManager
import org.hyperledger.besu.nat.upnp.UpnpNatManager
import org.hyperledger.besu.plugin.BesuPlugin
import org.hyperledger.besu.plugin.data.EnodeURL
import org.hyperledger.besu.services.BesuPluginContextImpl
import org.hyperledger.besu.services.PermissioningServiceImpl
import org.hyperledger.besu.services.RpcEndpointServiceImpl
import org.hyperledger.besu.util.BesuVersionUtils
import org.hyperledger.besu.util.NetworkUtility
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream

/** The builder for Runner class.  */
class RunnerBuilder
/** Instantiates a new Runner builder.  */
{
    private var vertx: Vertx? = null
    private var besuController: BesuController? = null

    private var networkingConfiguration: NetworkingConfiguration = NetworkingConfiguration.create()
    private val bannedNodeIds: MutableCollection<Bytes> = ArrayList()
    private var p2pEnabled = true
    private var discoveryEnabled = false
    private var p2pAdvertisedHost: String? = null
    private var p2pListenInterface = NetworkUtility.INADDR_ANY
    private var p2pListenPort = 0
    private var natMethod = NatMethod.AUTO
    private var natMethodFallbackEnabled = false
    private var ethNetworkConfig: EthNetworkConfig? = null
    private var ethstatsOptions: EthstatsOptions? = null
    private var jsonRpcConfiguration: JsonRpcConfiguration? = null
    private var engineJsonRpcConfiguration = Optional.empty<JsonRpcConfiguration>()
    private var graphQLConfiguration: GraphQLConfiguration? = null
    private var webSocketConfiguration: WebSocketConfiguration? = null
    private var inProcessRpcConfiguration: InProcessRpcConfiguration? = null
    private var apiConfiguration: ApiConfiguration? = null
    private var dataDir: Path? = null
    private var pidPath = Optional.empty<Path>()
    private var metricsConfiguration: MetricsConfiguration? = null
    private var metricsSystem: ObservableMetricsSystem? = null
    private var permissioningService: PermissioningServiceImpl? = null
    private var permissioningConfiguration = Optional.empty<PermissioningConfiguration>()
    private var staticNodes: Collection<EnodeURL> = emptyList()
    private var identityString = Optional.empty<String>()
    private var besuPluginContext: BesuPluginContextImpl? = null
    private var autoLogBloomCaching = true
    private var storageProvider: StorageProvider? = null
    private var rpcEndpointServiceImpl: RpcEndpointServiceImpl? = null
    private var jsonRpcIpcConfiguration: JsonRpcIpcConfiguration? = null
    private var enodeDnsConfiguration: Optional<EnodeDnsConfiguration>? = null
    private var allowedSubnets: List<SubnetInfo> = ArrayList()
    private var poaDiscoveryRetryBootnodes = true

    /**
     * Add Vertx.
     *
     * @param vertx the vertx instance
     * @return runner builder
     */
    fun vertx(vertx: Vertx?): RunnerBuilder {
        this.vertx = vertx
        return this
    }

    /**
     * Add Besu controller.
     *
     * @param besuController the besu controller
     * @return the runner builder
     */
    fun besuController(besuController: BesuController?): RunnerBuilder {
        this.besuController = besuController
        return this
    }

    /**
     * P2p enabled.
     *
     * @param p2pEnabled the p 2 p enabled
     * @return the runner builder
     */
    fun p2pEnabled(p2pEnabled: Boolean): RunnerBuilder {
        this.p2pEnabled = p2pEnabled
        return this
    }

    /**
     * Enable Discovery.
     *
     * @param discoveryEnabled the discoveryEnabled
     * @return the runner builder
     */
    fun discoveryEnabled(discoveryEnabled: Boolean): RunnerBuilder {
        this.discoveryEnabled = discoveryEnabled
        return this
    }

    /**
     * Add Eth network config.
     *
     * @param ethNetworkConfig the eth network config
     * @return the runner builder
     */
    fun ethNetworkConfig(ethNetworkConfig: EthNetworkConfig?): RunnerBuilder {
        this.ethNetworkConfig = ethNetworkConfig
        return this
    }

    /**
     * Add Networking configuration.
     *
     * @param networkingConfiguration the networking configuration
     * @return the runner builder
     */
    fun networkingConfiguration(
        networkingConfiguration: NetworkingConfiguration
    ): RunnerBuilder {
        this.networkingConfiguration = networkingConfiguration
        return this
    }

    /**
     * Add P2p advertised host.
     *
     * @param p2pAdvertisedHost the P2P advertised host
     * @return the runner builder
     */
    fun p2pAdvertisedHost(p2pAdvertisedHost: String?): RunnerBuilder {
        this.p2pAdvertisedHost = p2pAdvertisedHost
        return this
    }

    /**
     * Add P2P Listener interface ip/host name.
     *
     * @param ip the ip
     * @return the runner builder
     */
    fun p2pListenInterface(ip: String): RunnerBuilder {
        Preconditions.checkArgument(!Objects.isNull(ip), "Invalid null value supplied for p2pListenInterface")
        this.p2pListenInterface = ip
        return this
    }

    /**
     * Add P2P listen port.
     *
     * @param p2pListenPort the p 2 p listen port
     * @return the runner builder
     */
    fun p2pListenPort(p2pListenPort: Int): RunnerBuilder {
        this.p2pListenPort = p2pListenPort
        return this
    }

    /**
     * Add Nat method.
     *
     * @param natMethod the nat method
     * @return the runner builder
     */
    fun natMethod(natMethod: NatMethod): RunnerBuilder {
        this.natMethod = natMethod
        return this
    }

    /**
     * Enable Nat method fallback.
     *
     * @param natMethodFallbackEnabled the nat method fallback enabled
     * @return the runner builder
     */
    fun natMethodFallbackEnabled(natMethodFallbackEnabled: Boolean): RunnerBuilder {
        this.natMethodFallbackEnabled = natMethodFallbackEnabled
        return this
    }

    /**
     * Add EthStatsOptions
     *
     * @param ethstatsOptions the ethstats options
     * @return Runner builder instance
     */
    fun ethstatsOptions(ethstatsOptions: EthstatsOptions?): RunnerBuilder {
        this.ethstatsOptions = ethstatsOptions
        return this
    }

    /**
     * Add Json RPC configuration.
     *
     * @param jsonRpcConfiguration the json rpc configuration
     * @return the runner builder
     */
    fun jsonRpcConfiguration(jsonRpcConfiguration: JsonRpcConfiguration?): RunnerBuilder {
        this.jsonRpcConfiguration = jsonRpcConfiguration
        return this
    }

    /**
     * Add Engine json RPC configuration.
     *
     * @param engineJsonRpcConfiguration the engine json rpc configuration
     * @return the runner builder
     */
    fun engineJsonRpcConfiguration(
        engineJsonRpcConfiguration: JsonRpcConfiguration?
    ): RunnerBuilder {
        this.engineJsonRpcConfiguration = Optional.ofNullable(engineJsonRpcConfiguration)
        return this
    }

    /**
     * Add GraphQl configuration.
     *
     * @param graphQLConfiguration the graph ql configuration
     * @return the runner builder
     */
    fun graphQLConfiguration(graphQLConfiguration: GraphQLConfiguration?): RunnerBuilder {
        this.graphQLConfiguration = graphQLConfiguration
        return this
    }

    /**
     * Add Web socket configuration.
     *
     * @param webSocketConfiguration the web socket configuration
     * @return the runner builder
     */
    fun webSocketConfiguration(webSocketConfiguration: WebSocketConfiguration?): RunnerBuilder {
        this.webSocketConfiguration = webSocketConfiguration
        return this
    }

    /**
     * Add In-Process RPC configuration.
     *
     * @param inProcessRpcConfiguration the in-process RPC configuration
     * @return the runner builder
     */
    fun inProcessRpcConfiguration(
        inProcessRpcConfiguration: InProcessRpcConfiguration?
    ): RunnerBuilder {
        this.inProcessRpcConfiguration = inProcessRpcConfiguration
        return this
    }

    /**
     * Add Api configuration.
     *
     * @param apiConfiguration the api configuration
     * @return the runner builder
     */
    fun apiConfiguration(apiConfiguration: ApiConfiguration?): RunnerBuilder {
        this.apiConfiguration = apiConfiguration
        return this
    }

    /**
     * Add Permissioning configuration.
     *
     * @param permissioningConfiguration the permissioning configuration
     * @return the runner builder
     */
    fun permissioningConfiguration(
        permissioningConfiguration: Optional<PermissioningConfiguration>
    ): RunnerBuilder {
        this.permissioningConfiguration = permissioningConfiguration
        return this
    }

    /**
     * Add pid path.
     *
     * @param pidPath the pid path
     * @return the runner builder
     */
    fun pidPath(pidPath: Path?): RunnerBuilder {
        this.pidPath = Optional.ofNullable(pidPath)
        return this
    }

    /**
     * Add Data dir.
     *
     * @param dataDir the data dir
     * @return the runner builder
     */
    fun dataDir(dataDir: Path?): RunnerBuilder {
        this.dataDir = dataDir
        return this
    }

    /**
     * Add list of Banned node id.
     *
     * @param bannedNodeIds the banned node ids
     * @return the runner builder
     */
    fun bannedNodeIds(bannedNodeIds: Collection<Bytes>): RunnerBuilder {
        this.bannedNodeIds.addAll(bannedNodeIds)
        return this
    }

    /**
     * Add Metrics configuration.
     *
     * @param metricsConfiguration the metrics configuration
     * @return the runner builder
     */
    fun metricsConfiguration(metricsConfiguration: MetricsConfiguration?): RunnerBuilder {
        this.metricsConfiguration = metricsConfiguration
        return this
    }

    /**
     * Add Metrics system.
     *
     * @param metricsSystem the metrics system
     * @return the runner builder
     */
    fun metricsSystem(metricsSystem: ObservableMetricsSystem?): RunnerBuilder {
        this.metricsSystem = metricsSystem
        return this
    }

    /**
     * Add Permissioning service.
     *
     * @param permissioningService the permissioning service
     * @return the runner builder
     */
    fun permissioningService(permissioningService: PermissioningServiceImpl?): RunnerBuilder {
        this.permissioningService = permissioningService
        return this
    }

    /**
     * Add Static nodes collection.
     *
     * @param staticNodes the static nodes
     * @return the runner builder
     */
    fun staticNodes(staticNodes: Collection<EnodeURL>): RunnerBuilder {
        this.staticNodes = staticNodes
        return this
    }

    /**
     * Add Node identity string.
     *
     * @param identityString the identity string
     * @return the runner builder
     */
    fun identityString(identityString: Optional<String>): RunnerBuilder {
        this.identityString = identityString
        return this
    }

    /**
     * Add Besu plugin context.
     *
     * @param besuPluginContext the besu plugin context
     * @return the runner builder
     */
    fun besuPluginContext(besuPluginContext: BesuPluginContextImpl?): RunnerBuilder {
        this.besuPluginContext = besuPluginContext
        return this
    }

    /**
     * Enable Auto log bloom caching.
     *
     * @param autoLogBloomCaching the auto log bloom caching
     * @return the runner builder
     */
    fun autoLogBloomCaching(autoLogBloomCaching: Boolean): RunnerBuilder {
        this.autoLogBloomCaching = autoLogBloomCaching
        return this
    }

    /**
     * Add Storage provider.
     *
     * @param storageProvider the storage provider
     * @return the runner builder
     */
    fun storageProvider(storageProvider: StorageProvider?): RunnerBuilder {
        this.storageProvider = storageProvider
        return this
    }

    /**
     * Add Rpc endpoint service.
     *
     * @param rpcEndpointService the rpc endpoint service
     * @return the runner builder
     */
    fun rpcEndpointService(rpcEndpointService: RpcEndpointServiceImpl?): RunnerBuilder {
        this.rpcEndpointServiceImpl = rpcEndpointService
        return this
    }

    /**
     * Add Json Rpc Ipc configuration.
     *
     * @param jsonRpcIpcConfiguration the json rpc ipc configuration
     * @return the runner builder
     */
    fun jsonRpcIpcConfiguration(
        jsonRpcIpcConfiguration: JsonRpcIpcConfiguration?
    ): RunnerBuilder {
        this.jsonRpcIpcConfiguration = jsonRpcIpcConfiguration
        return this
    }

    /**
     * Add enode DNS configuration
     *
     * @param enodeDnsConfiguration the DNS configuration for enodes
     * @return the runner builder
     */
    fun enodeDnsConfiguration(enodeDnsConfiguration: EnodeDnsConfiguration?): RunnerBuilder {
        this.enodeDnsConfiguration =
            if (enodeDnsConfiguration != null) Optional.of(enodeDnsConfiguration) else Optional.empty()
        return this
    }

    /**
     * Add subnet configuration
     *
     * @param allowedSubnets the allowedSubnets
     * @return the runner builder
     */
    fun allowedSubnets(allowedSubnets: List<SubnetInfo>): RunnerBuilder {
        this.allowedSubnets = allowedSubnets
        return this
    }

    /**
     * Flag to indicate if peer table refreshes should always query bootnodes
     *
     * @param poaDiscoveryRetryBootnodes whether to always query bootnodes
     * @return the runner builder
     */
    fun poaDiscoveryRetryBootnodes(poaDiscoveryRetryBootnodes: Boolean): RunnerBuilder {
        this.poaDiscoveryRetryBootnodes = poaDiscoveryRetryBootnodes
        return this
    }

    /**
     * Build Runner instance.
     *
     * @return the runner
     */
    fun build(): Runner {
        Preconditions.checkNotNull(besuController)

        val discoveryConfiguration =
            DiscoveryConfiguration.create()
                .setBindHost(p2pListenInterface)
                .setBindPort(p2pListenPort)
                .setAdvertisedHost(p2pAdvertisedHost)
        if (discoveryEnabled) {
            val bootstrap = if (ethNetworkConfig!!.bootNodes == null) {
                getNetworkConfig(NetworkName.MAINNET).bootNodes
            } else {
                ethNetworkConfig!!.bootNodes
            }
            discoveryConfiguration.setBootnodes(bootstrap)
            discoveryConfiguration.setIncludeBootnodesOnPeerRefresh(
                besuController!!.genesisConfigOptions.isPoa && poaDiscoveryRetryBootnodes
            )
            LOG.info("Resolved {} bootnodes.", bootstrap.size)
            LOG.debug("Bootnodes = {}", bootstrap)
            discoveryConfiguration.setDnsDiscoveryURL(ethNetworkConfig!!.dnsDiscoveryUrl)
            discoveryConfiguration.isDiscoveryV5Enabled = networkingConfiguration.discovery.isDiscoveryV5Enabled
            discoveryConfiguration.setFilterOnEnrForkId(
                networkingConfiguration.discovery.isFilterOnEnrForkIdEnabled
            )
        } else {
            discoveryConfiguration.setEnabled(false)
        }

        val nodeKey = besuController!!.nodeKey

        val subProtocolConfiguration =
            besuController!!.subProtocolConfiguration

        val protocolSchedule = besuController!!.protocolSchedule
        val context = besuController!!.protocolContext

        val subProtocols = subProtocolConfiguration.subProtocols
        val protocolManagers = subProtocolConfiguration.protocolManagers
        val supportedCapabilities =
            protocolManagers.stream()
                .flatMap { protocolManager: ProtocolManager -> protocolManager.supportedCapabilities.stream() }
                .collect(Collectors.toSet())

        val rlpxConfiguration =
            RlpxConfiguration.create()
                .setBindHost(p2pListenInterface)
                .setBindPort(p2pListenPort)
                .setSupportedProtocols(subProtocols)
                .setClientId(BesuVersionUtils.nodeName(identityString))
        networkingConfiguration.setRlpx(rlpxConfiguration).setDiscovery(discoveryConfiguration)

        val bannedNodes = PeerPermissionsDenylist.create()
        bannedNodeIds.forEach(Consumer { peerId: Bytes? -> bannedNodes.add(peerId) })

        val peerPermissionSubnet = PeerPermissionSubnet(allowedSubnets)
        val defaultPeerPermissions =
            PeerPermissions.combine(peerPermissionSubnet, bannedNodes)

        val bootnodes = discoveryConfiguration.bootnodes

        val synchronizer = besuController!!.synchronizer

        val transactionSimulator = besuController!!.transactionSimulator

        val localNodeId = nodeKey.publicKey.encodedBytes
        val nodePermissioningController =
            buildNodePermissioningController(
                bootnodes, synchronizer, transactionSimulator, localNodeId, context.blockchain
            )

        val peerPermissions =
            nodePermissioningController
                .map { nodePC: NodePermissioningController? ->
                    PeerPermissionsAdapter(
                        nodePC,
                        bootnodes,
                        context.blockchain
                    )
                }
                .map { nodePerms: PeerPermissionsAdapter? ->
                    PeerPermissions.combine(
                        nodePerms,
                        defaultPeerPermissions
                    )
                }
                .orElse(defaultPeerPermissions)

        val ethPeers = besuController!!.ethPeers

        LOG.info("Detecting NAT service.")
        val fallbackEnabled = natMethod == NatMethod.AUTO || natMethodFallbackEnabled
        val natService = NatService(buildNatManager(natMethod), fallbackEnabled)
        val inactiveNetwork =
            NetworkRunner.NetworkBuilder { caps: List<Capability?>? -> NoopP2PNetwork() }

        val activeNetwork =
            NetworkRunner.NetworkBuilder { caps: List<Capability?>? ->
                DefaultP2PNetwork.builder()
                    .vertx(vertx)
                    .nodeKey(nodeKey)
                    .config(networkingConfiguration)
                    .peerPermissions(peerPermissions)
                    .metricsSystem(metricsSystem)
                    .supportedCapabilities(caps)
                    .natService(natService)
                    .storageProvider(storageProvider)
                    .blockchain(context.blockchain)
                    .blockNumberForks(besuController!!.genesisConfigOptions.forkBlockNumbers)
                    .timestampForks(besuController!!.genesisConfigOptions.forkBlockTimestamps)
                    .allConnectionsSupplier { ethPeers.streamAllConnections() }
                    .allActiveConnectionsSupplier { ethPeers.streamAllActiveConnections() }
                    .maxPeers(ethPeers.maxPeers)
                    .build()
            }

        val networkRunner =
            NetworkRunner.builder()
                .protocolManagers(protocolManagers)
                .subProtocols(subProtocols)
                .network(if (p2pEnabled) activeNetwork else inactiveNetwork)
                .metricsSystem(metricsSystem)
                .ethPeersShouldConnect { peer: Peer?, inbound: Boolean? ->
                    ethPeers.shouldTryToConnect(
                        peer,
                        inbound!!
                    )
                }
                .build()

        ethPeers.setRlpxAgent(networkRunner.rlpxAgent)

        val network = networkRunner.network
        // ForkId in Ethereum Node Record needs updating when we transition to a new protocol spec
        context
            .blockchain
            .observeBlockAdded { blockAddedEvent: BlockAddedEvent ->
                if (protocolSchedule.isOnMilestoneBoundary(blockAddedEvent.block.header)) {
                    network.updateNodeRecord()
                }
            }
        nodePermissioningController.ifPresent { n: NodePermissioningController ->
            n.setInsufficientPeersPermissioningProvider(
                InsufficientPeersPermissioningProvider(network, bootnodes)
            )
        }

        val transactionPool = besuController!!.transactionPool
        val miningCoordinator = besuController!!.miningCoordinator
        val miningConfiguration = besuController!!.miningParameters

        val blockchainQueries =
            BlockchainQueries(
                protocolSchedule,
                context.blockchain,
                context.worldStateArchive,
                Optional.of(dataDir!!.resolve(BesuController.CACHE_PATH)),
                Optional.of(besuController!!.protocolManager.ethContext().scheduler),
                apiConfiguration,
                miningConfiguration
            )

        val privacyParameters = besuController!!.privacyParameters

        val filterManager =
            FilterManagerBuilder()
                .blockchainQueries(blockchainQueries)
                .transactionPool(transactionPool)
                .privacyParameters(privacyParameters)
                .build()
        vertx!!.deployVerticle(filterManager)

        createPrivateTransactionObserver(
            filterManager, privacyParameters, context.blockchain.genesisBlockHeader
        )

        val peerNetwork = networkRunner.network

        var stratumServer: Optional<StratumServer> = Optional.empty()

        if (miningConfiguration.isStratumMiningEnabled) {
            require(miningCoordinator is PoWMiningCoordinator) {
                ("Stratum mining requires the network option(--network) to be set to CLASSIC. Stratum server requires a PoWMiningCoordinator not "
                        + (if (miningCoordinator == null) "null" else miningCoordinator.javaClass.name))
            }
            stratumServer =
                Optional.of(
                    StratumServer(
                        vertx,
                        miningCoordinator,
                        miningConfiguration.stratumPort,
                        miningConfiguration.stratumNetworkInterface,
                        miningConfiguration.unstable.stratumExtranonce,
                        metricsSystem
                    )
                )
            miningCoordinator.addEthHashObserver(stratumServer.get())
            LOG.debug("added ethash observer: {}", stratumServer.get())
        }

        sanitizePeers(network, staticNodes)
            .map { enodeURL: EnodeURL? -> DefaultPeer.fromEnodeURL(enodeURL) }
            .forEach { peer: DefaultPeer? -> peerNetwork.addMaintainedConnectionPeer(peer) }

        val nodeLocalConfigPermissioningController =
            nodePermissioningController.flatMap { obj: NodePermissioningController -> obj.localConfigController() }

        val accountPermissioningController =
            buildAccountPermissioningController(
                permissioningConfiguration, besuController!!, transactionSimulator
            )

        val accountLocalConfigPermissioningController =
            accountPermissioningController.flatMap { obj: AccountPermissioningController -> obj.accountLocalConfigPermissioningController }

        var jsonRpcHttpService: Optional<JsonRpcHttpService> = Optional.empty()

        if (jsonRpcConfiguration!!.isEnabled) {
            val nonEngineMethods =
                jsonRpcMethods(
                    protocolSchedule,
                    context,
                    besuController!!,
                    peerNetwork,
                    blockchainQueries,
                    synchronizer,
                    transactionPool,
                    miningConfiguration,
                    miningCoordinator,
                    metricsSystem,
                    supportedCapabilities,
                    jsonRpcConfiguration!!.rpcApis.stream()
                        .filter { apiGroup: String -> !apiGroup.lowercase().startsWith("engine") }
                        .collect(Collectors.toList()),
                    filterManager,
                    accountLocalConfigPermissioningController,
                    nodeLocalConfigPermissioningController,
                    privacyParameters,
                    jsonRpcConfiguration!!,
                    webSocketConfiguration,
                    metricsConfiguration,
                    graphQLConfiguration,
                    natService,
                    besuPluginContext!!.namedPlugins,
                    dataDir,
                    rpcEndpointServiceImpl!!,
                    transactionSimulator,
                    besuController!!.protocolManager.ethContext().scheduler)

            jsonRpcHttpService =
                Optional.of(
                    JsonRpcHttpService(
                        vertx,
                        dataDir,
                        jsonRpcConfiguration,
                        metricsSystem,
                        natService,
                        nonEngineMethods,
                        HealthService(LivenessCheck()),
                        HealthService(ReadinessCheck(peerNetwork, synchronizer))
                    )
                )
        }

        val subscriptionManager =
            createSubscriptionManager(vertx!!, transactionPool, blockchainQueries)

        var engineJsonRpcService: Optional<EngineJsonRpcService> = Optional.empty()
        if (engineJsonRpcConfiguration.isPresent && engineJsonRpcConfiguration.get().isEnabled) {
            val engineMethods =
                jsonRpcMethods(
                    protocolSchedule,
                    context,
                    besuController!!,
                    peerNetwork,
                    blockchainQueries,
                    synchronizer,
                    transactionPool,
                    miningConfiguration,
                    miningCoordinator,
                    metricsSystem,
                    supportedCapabilities,
                    engineJsonRpcConfiguration.get().rpcApis,
                    filterManager,
                    accountLocalConfigPermissioningController,
                    nodeLocalConfigPermissioningController,
                    privacyParameters,
                    engineJsonRpcConfiguration.get(),
                    webSocketConfiguration,
                    metricsConfiguration,
                    graphQLConfiguration,
                    natService,
                    besuPluginContext!!.namedPlugins,
                    dataDir,
                    rpcEndpointServiceImpl!!,
                    transactionSimulator,
                    besuController!!.protocolManager.ethContext().scheduler
                )

            val authToUse =
                if (engineJsonRpcConfiguration.get().isAuthenticationEnabled)
                    Optional.of<AuthenticationService>(
                        EngineAuthService(
                            vertx,
                            Optional.ofNullable(
                                engineJsonRpcConfiguration.get().authenticationPublicKeyFile
                            ),
                            dataDir
                        )
                    )
                else
                    Optional.empty()

            val engineSocketConfig =
                if (webSocketConfiguration!!.isEnabled)
                    webSocketConfiguration
                else
                    WebSocketConfiguration.createEngineDefault()

            val websocketMethodsFactory =
                WebSocketMethodsFactory(subscriptionManager, engineMethods)

            engineJsonRpcService =
                Optional.of(
                    EngineJsonRpcService(
                        vertx,
                        dataDir,
                        engineJsonRpcConfiguration.orElse(JsonRpcConfiguration.createEngineDefault()),
                        metricsSystem,
                        natService,
                        websocketMethodsFactory.methods(),
                        Optional.ofNullable(engineSocketConfig),
                        besuController!!.protocolManager.ethContext().scheduler,
                        authToUse,
                        HealthService(LivenessCheck()),
                        HealthService(ReadinessCheck(peerNetwork, synchronizer))
                    )
                )
        }

        var graphQLHttpService: Optional<GraphQLHttpService> = Optional.empty()
        if (graphQLConfiguration!!.isEnabled) {
            val fetchers = GraphQLDataFetchers(supportedCapabilities)
            val graphQlContextMap: MutableMap<GraphQLContextType, Any> = ConcurrentHashMap()
            graphQlContextMap.putIfAbsent(GraphQLContextType.BLOCKCHAIN_QUERIES, blockchainQueries)
            graphQlContextMap.putIfAbsent(GraphQLContextType.PROTOCOL_SCHEDULE, protocolSchedule)
            graphQlContextMap.putIfAbsent(GraphQLContextType.TRANSACTION_POOL, transactionPool)
            graphQlContextMap.putIfAbsent(GraphQLContextType.MINING_COORDINATOR, miningCoordinator)
            graphQlContextMap.putIfAbsent(GraphQLContextType.SYNCHRONIZER, synchronizer)
            graphQlContextMap.putIfAbsent(
                GraphQLContextType.CHAIN_ID, protocolSchedule.chainId.map { value: BigInteger? ->
                    UInt256.valueOf(
                        value
                    )
                })
            graphQlContextMap.putIfAbsent(GraphQLContextType.TRANSACTION_SIMULATOR, transactionSimulator)
            val graphQL: GraphQL
            try {
                graphQL = GraphQLProvider.buildGraphQL(fetchers)
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            }

            graphQLHttpService =
                Optional.of(
                    GraphQLHttpService(
                        vertx,
                        dataDir,
                        graphQLConfiguration,
                        graphQL,
                        graphQlContextMap,
                        besuController!!.protocolManager.ethContext().scheduler
                    )
                )
        }

        var webSocketService: Optional<WebSocketService> = Optional.empty()
        if (webSocketConfiguration!!.isEnabled) {
            val nonEngineMethods =
                jsonRpcMethods(
                    protocolSchedule,
                    context,
                    besuController!!,
                    peerNetwork,
                    blockchainQueries,
                    synchronizer,
                    transactionPool,
                    miningConfiguration,
                    miningCoordinator,
                    metricsSystem,
                    supportedCapabilities,
                    webSocketConfiguration!!.rpcApis.stream()
                        .filter { apiGroup: String -> !apiGroup.lowercase().startsWith("engine") }
                        .collect(Collectors.toList()),
                    filterManager,
                    accountLocalConfigPermissioningController,
                    nodeLocalConfigPermissioningController,
                    privacyParameters,
                    jsonRpcConfiguration!!,
                    webSocketConfiguration,
                    metricsConfiguration,
                    graphQLConfiguration,
                    natService,
                    besuPluginContext!!.namedPlugins,
                    dataDir,
                    rpcEndpointServiceImpl!!,
                    transactionSimulator,
                    besuController!!.protocolManager.ethContext().scheduler)

            createLogsSubscriptionService(
                context.blockchain, subscriptionManager, privacyParameters, blockchainQueries
            )

            createNewBlockHeadersSubscriptionService(
                context.blockchain, blockchainQueries, subscriptionManager
            )

            createSyncingSubscriptionService(synchronizer, subscriptionManager)

            webSocketService =
                Optional.of(
                    createWebsocketService(
                        vertx,
                        webSocketConfiguration!!,
                        subscriptionManager,
                        nonEngineMethods,
                        privacyParameters,
                        protocolSchedule,
                        blockchainQueries,
                        DefaultAuthenticationService.create(vertx, webSocketConfiguration),
                        metricsSystem!!
                    )
                )

            createPrivateTransactionObserver(
                subscriptionManager, privacyParameters, context.blockchain.genesisBlockHeader
            )
        }

        val metricsService = createMetricsService(metricsConfiguration!!)
        val ethStatsService: Optional<EthStatsService> = if (this.isEthStatsEnabled) {
            Optional.of(
                EthStatsService(
                    EthStatsConnectOptions.fromParams(
                        ethstatsOptions!!.ethstatsUrl,
                        ethstatsOptions!!.ethstatsContact,
                        ethstatsOptions!!.ethstatsCaCert
                    ),
                    blockchainQueries,
                    besuController!!.protocolManager,
                    transactionPool,
                    miningCoordinator,
                    besuController!!.syncState,
                    vertx,
                    BesuVersionUtils.nodeName(identityString),
                    besuController!!.genesisConfigOptions,
                    network
                )
            )
        } else {
            Optional.empty<EthStatsService>()
        }

        val jsonRpcIpcService: Optional<JsonRpcIpcService>
        if (jsonRpcIpcConfiguration!!.isEnabled) {
            val ipcMethods =
                jsonRpcMethods(
                    protocolSchedule,
                    context,
                    besuController!!,
                    peerNetwork,
                    blockchainQueries,
                    synchronizer,
                    transactionPool,
                    miningConfiguration,
                    miningCoordinator,
                    metricsSystem,
                    supportedCapabilities,
                    jsonRpcIpcConfiguration!!.enabledApis.stream()
                        .filter { apiGroup: String -> !apiGroup.lowercase().startsWith("engine") }
                        .collect(Collectors.toList()),
                    filterManager,
                    accountLocalConfigPermissioningController,
                    nodeLocalConfigPermissioningController,
                    privacyParameters,
                    jsonRpcConfiguration!!,
                    webSocketConfiguration,
                    metricsConfiguration,
                    graphQLConfiguration,
                    natService,
                    besuPluginContext!!.namedPlugins,
                    dataDir,
                    rpcEndpointServiceImpl!!,
                    transactionSimulator,
                    besuController!!.protocolManager.ethContext().scheduler)

            jsonRpcIpcService =
                Optional.of(
                    JsonRpcIpcService(
                        vertx,
                        jsonRpcIpcConfiguration!!.path,
                        JsonRpcExecutor(BaseJsonRpcProcessor(), ipcMethods)
                    )
                )
        } else {
            jsonRpcIpcService = Optional.empty()
        }
        val inProcessRpcMethods: Map<String, JsonRpcMethod>? = if (inProcessRpcConfiguration!!.isEnabled) {
            jsonRpcMethods(
                protocolSchedule,
                context,
                besuController!!,
                peerNetwork,
                blockchainQueries,
                synchronizer,
                transactionPool,
                miningConfiguration,
                miningCoordinator,
                metricsSystem,
                supportedCapabilities,
                inProcessRpcConfiguration!!.inProcessRpcApis,
                filterManager,
                accountLocalConfigPermissioningController,
                nodeLocalConfigPermissioningController,
                privacyParameters,
                jsonRpcConfiguration!!,
                webSocketConfiguration,
                metricsConfiguration,
                graphQLConfiguration,
                natService,
                besuPluginContext!!.namedPlugins,
                dataDir,
                rpcEndpointServiceImpl!!,
                transactionSimulator,
                besuController!!.protocolManager.ethContext().scheduler
            )
        } else {
            java.util.Map.of<String, JsonRpcMethod?>()
        }

        return Runner(
            vertx!!,
            networkRunner,
            natService,
            jsonRpcHttpService,
            engineJsonRpcService,
            graphQLHttpService,
            webSocketService,
            jsonRpcIpcService,
            inProcessRpcMethods,
            stratumServer,
            metricsService,
            ethStatsService,
            besuController!!,
            dataDir!!,
            pidPath,
            if (autoLogBloomCaching) blockchainQueries.transactionLogBloomCacher else Optional.empty(),
            context.blockchain
        )
    }

    private val isEthStatsEnabled: Boolean
        get() = ethstatsOptions != null && !Strings.isNullOrEmpty(ethstatsOptions!!.ethstatsUrl)

    private fun sanitizePeers(
        network: P2PNetwork, enodeURLS: Collection<EnodeURL>
    ): Stream<EnodeURL> {
        if (network.localEnode.isEmpty) {
            return enodeURLS.stream()
        }
        val localEnodeURL = network.localEnode.get()
        return enodeURLS.stream()
            .filter { enodeURL: EnodeURL -> enodeURL.nodeId != localEnodeURL.nodeId }
    }

    private fun buildNodePermissioningController(
        bootnodesAsEnodeURLs: List<EnodeURL>,
        synchronizer: Synchronizer,
        transactionSimulator: TransactionSimulator,
        localNodeId: Bytes,
        blockchain: Blockchain
    ): Optional<NodePermissioningController> {
        val fixedNodes = getFixedNodes(bootnodesAsEnodeURLs, staticNodes)

        if (permissioningConfiguration.isPresent) {
            val configuration = permissioningConfiguration.get()
            val nodePermissioningController =
                NodePermissioningControllerFactory()
                    .create(
                        configuration,
                        synchronizer,
                        fixedNodes,
                        localNodeId,
                        transactionSimulator,
                        metricsSystem,
                        blockchain,
                        permissioningService!!.getConnectionPermissioningProviders()
                    )

            return Optional.of(nodePermissioningController)
        } else if (permissioningService!!.getConnectionPermissioningProviders().size > 0) {
            val nodePermissioningController =
                NodePermissioningControllerFactory()
                    .create(
                        PermissioningConfiguration(Optional.empty(), Optional.empty()),
                        synchronizer,
                        fixedNodes,
                        localNodeId,
                        transactionSimulator,
                        metricsSystem,
                        blockchain,
                        permissioningService!!.getConnectionPermissioningProviders()
                    )

            return Optional.of(nodePermissioningController)
        } else {
            return Optional.empty()
        }
    }

    private fun buildAccountPermissioningController(
        permissioningConfiguration: Optional<PermissioningConfiguration>,
        besuController: BesuController,
        transactionSimulator: TransactionSimulator
    ): Optional<AccountPermissioningController> {
        if (permissioningConfiguration.isPresent
            || permissioningService!!.getTransactionPermissioningProviders().size > 0
        ) {
            val configuration =
                permissioningConfiguration.orElse(
                    PermissioningConfiguration(Optional.empty(), Optional.empty())
                )
            val accountPermissioningController =
                AccountPermissioningControllerFactory.create(
                    configuration,
                    transactionSimulator,
                    metricsSystem,
                    permissioningService!!.getTransactionPermissioningProviders()
                )

            accountPermissioningController.ifPresent { permissioningController: AccountPermissioningController ->
                besuController.protocolSchedule.setPermissionTransactionFilter { transaction: Transaction?, includeLocalCheck: Boolean, includeOnchainCheck: Boolean ->
                    permissioningController.isPermitted(
                        transaction,
                        includeLocalCheck,
                        includeOnchainCheck
                    )
                }
            }

            return accountPermissioningController
        } else {
            return Optional.empty()
        }
    }

    private fun buildNatManager(natMethod: NatMethod): Optional<NatManager> {
        val detectedNatMethod =
            Optional.of(natMethod)
                .filter(Predicate.not(Predicate.isEqual(NatMethod.AUTO)))
                .orElse(NatService.autoDetectNatMethod(DockerDetector()))
        return when (detectedNatMethod) {
            NatMethod.UPNP -> Optional.of(UpnpNatManager())
            NatMethod.DOCKER -> Optional.of(
                DockerNatManager(p2pAdvertisedHost, p2pListenPort, jsonRpcConfiguration!!.port)
            )

            NatMethod.NONE -> Optional.empty()
            else -> Optional.empty()
        }
    }

    private fun jsonRpcMethods(
        protocolSchedule: ProtocolSchedule,
        protocolContext: ProtocolContext,
        besuController: BesuController,
        network: P2PNetwork,
        blockchainQueries: BlockchainQueries,
        synchronizer: Synchronizer,
        transactionPool: TransactionPool,
        miningConfiguration: MiningConfiguration,
        miningCoordinator: MiningCoordinator,
        metricsSystem: ObservableMetricsSystem?,
        supportedCapabilities: Set<Capability>,
        jsonRpcApis: Collection<String?>,
        filterManager: FilterManager,
        accountAllowlistController: Optional<AccountLocalConfigPermissioningController>,
        nodeAllowlistController: Optional<NodeLocalConfigPermissioningController>,
        privacyParameters: PrivacyParameters,
        jsonRpcConfiguration: JsonRpcConfiguration,
        webSocketConfiguration: WebSocketConfiguration?,
        metricsConfiguration: MetricsConfiguration?,
        graphQLConfiguration: GraphQLConfiguration?,
        natService: NatService,
        namedPlugins: Map<String, BesuPlugin?>,
        dataDir: Path?,
        rpcEndpointServiceImpl: RpcEndpointServiceImpl,
        transactionSimulator: TransactionSimulator,
        ethScheduler: EthScheduler
    ): Map<String, JsonRpcMethod>? {
        // sync vertx for engine consensus API, to process requests in FIFO order;
        val consensusEngineServer = Vertx.vertx(VertxOptions().setWorkerPoolSize(1))

        val methods =
            JsonRpcMethodsFactory()
                .methods(
                    BesuVersionUtils.nodeName(identityString),
                    BesuVersionUtils.shortVersion(),
                    BesuVersionUtils.commit(),
                    ethNetworkConfig!!.networkId,
                    besuController.genesisConfigOptions,
                    network,
                    blockchainQueries,
                    synchronizer,
                    protocolSchedule,
                    protocolContext,
                    filterManager,
                    transactionPool,
                    miningConfiguration,
                    miningCoordinator,
                    metricsSystem,
                    supportedCapabilities,
                    accountAllowlistController,
                    nodeAllowlistController,
                    jsonRpcApis,
                    privacyParameters,
                    jsonRpcConfiguration,
                    webSocketConfiguration,
                    metricsConfiguration,
                    graphQLConfiguration,
                    natService,
                    namedPlugins,
                    dataDir,
                    besuController.protocolManager.ethContext().ethPeers,
                    consensusEngineServer,
                    apiConfiguration,
                    enodeDnsConfiguration,
                    transactionSimulator,
                    ethScheduler
                )
        methods.putAll(besuController.getAdditionalJsonRpcMethods(jsonRpcApis))

        val pluginMethods =
            rpcEndpointServiceImpl.getPluginMethods(jsonRpcConfiguration.rpcApis)

        val overriddenMethods =
            methods.keys.stream().filter { key: String -> pluginMethods.containsKey(key) }.collect(Collectors.toList())
        if (overriddenMethods.size > 0) {
            throw RuntimeException("You can not override built in methods $overriddenMethods")
        }

        methods.putAll(pluginMethods)
        return methods
    }

    private fun createSubscriptionManager(
        vertx: Vertx,
        transactionPool: TransactionPool,
        blockchainQueries: BlockchainQueries
    ): SubscriptionManager {
        val subscriptionManager =
            SubscriptionManager(metricsSystem, blockchainQueries.blockchain)
        val pendingTransactions =
            PendingTransactionSubscriptionService(subscriptionManager)
        val pendingTransactionsRemoved =
            PendingTransactionDroppedSubscriptionService(subscriptionManager)
        transactionPool.subscribePendingTransactions(pendingTransactions)
        transactionPool.subscribeDroppedTransactions(pendingTransactionsRemoved)
        vertx.deployVerticle(subscriptionManager)

        return subscriptionManager
    }

    private fun createLogsSubscriptionService(
        blockchain: Blockchain,
        subscriptionManager: SubscriptionManager,
        privacyParameters: PrivacyParameters,
        blockchainQueries: BlockchainQueries
    ) {
        var privacyQueries: Optional<PrivacyQueries> = Optional.empty()
        if (privacyParameters.isEnabled) {
            privacyQueries =
                Optional.of(
                    PrivacyQueries(
                        blockchainQueries, privacyParameters.privateWorldStateReader
                    )
                )
        }

        val logsSubscriptionService =
            LogsSubscriptionService(subscriptionManager, privacyQueries)

        // monitoring public logs
        blockchain.observeLogs(logsSubscriptionService)

        // monitoring private logs
        if (privacyParameters.isEnabled) {
            blockchain.observeBlockAdded { event: BlockAddedEvent? -> logsSubscriptionService.checkPrivateLogs(event) }
        }
    }

    private fun createPrivateTransactionObserver(
        privateTransactionObserver: PrivateTransactionObserver,
        privacyParameters: PrivacyParameters,
        genesisBlockHeader: BlockHeader
    ) {
        // register privateTransactionObserver as observer of events fired by the flexible precompile.
        if (privacyParameters.isFlexiblePrivacyGroupsEnabled
            && privacyParameters.isMultiTenancyEnabled
        ) {
            val flexiblePrivacyPrecompiledContract =
                besuController!!
                    .protocolSchedule
                    .getByBlockHeader(genesisBlockHeader)
                    .precompileContractRegistry[PrivacyParameters.FLEXIBLE_PRIVACY] as FlexiblePrivacyPrecompiledContract

            flexiblePrivacyPrecompiledContract.addPrivateTransactionObserver(privateTransactionObserver)
        }
    }

    private fun createSyncingSubscriptionService(
        synchronizer: Synchronizer, subscriptionManager: SubscriptionManager
    ) {
        SyncingSubscriptionService(subscriptionManager, synchronizer)
    }

    private fun createNewBlockHeadersSubscriptionService(
        blockchain: Blockchain,
        blockchainQueries: BlockchainQueries,
        subscriptionManager: SubscriptionManager
    ) {
        val newBlockHeadersSubscriptionService =
            NewBlockHeadersSubscriptionService(subscriptionManager, blockchainQueries)

        blockchain.observeBlockAdded(newBlockHeadersSubscriptionService)
    }

    private fun createWebsocketService(
        vertx: Vertx?,
        configuration: WebSocketConfiguration,
        subscriptionManager: SubscriptionManager,
        jsonRpcMethods: Map<String, JsonRpcMethod>?,
        privacyParameters: PrivacyParameters,
        protocolSchedule: ProtocolSchedule,
        blockchainQueries: BlockchainQueries,
        authenticationService: Optional<AuthenticationService>,
        metricsSystem: ObservableMetricsSystem
    ): WebSocketService {
        val websocketMethodsFactory =
            WebSocketMethodsFactory(subscriptionManager, jsonRpcMethods)

        if (privacyParameters.isEnabled) {
            val privateWebSocketMethodsFactory =
                PrivateWebSocketMethodsFactory(
                    privacyParameters, subscriptionManager, protocolSchedule, blockchainQueries
                )

            privateWebSocketMethodsFactory.methods()
                .forEach(Consumer { rpcMethods: JsonRpcMethod? -> websocketMethodsFactory.addMethods(rpcMethods) })
        }

        rpcEndpointServiceImpl!!
            .getPluginMethods(configuration.rpcApis)
            .values
            .forEach(Consumer { rpcMethods: JsonRpcMethod? -> websocketMethodsFactory.addMethods(rpcMethods) })
        val jsonRpcProcessor = if (authenticationService.isPresent) {
            AuthenticatedJsonRpcProcessor(
                BaseJsonRpcProcessor(),
                authenticationService.get(),
                configuration.rpcApisNoAuth
            )
        } else {
            BaseJsonRpcProcessor()
        }
        val jsonRpcExecutor =
            JsonRpcExecutor(jsonRpcProcessor, websocketMethodsFactory.methods())
        val websocketMessageHandler =
            WebSocketMessageHandler(
                vertx,
                jsonRpcExecutor,
                besuController!!.protocolManager.ethContext().scheduler,
                webSocketConfiguration!!.timeoutSec
            )

        return WebSocketService(
            vertx, configuration, websocketMessageHandler, authenticationService, metricsSystem
        )
    }

    private fun createMetricsService(configuration: MetricsConfiguration): Optional<MetricsService> {
        return MetricsService.create(configuration, metricsSystem)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RunnerBuilder::class.java)

        /**
         * Gets fixed nodes. Visible for testing.
         *
         * @param someFixedNodes the fixed nodes
         * @param moreFixedNodes nodes added to fixed nodes
         * @return the fixed and more nodes combined
         */
        @VisibleForTesting
        fun getFixedNodes(
            someFixedNodes: Collection<EnodeURL>, moreFixedNodes: Collection<EnodeURL>
        ): Collection<EnodeURL> {
            val fixedNodes: MutableCollection<EnodeURL> = ArrayList(someFixedNodes)
            fixedNodes.addAll(moreFixedNodes)
            return fixedNodes
        }
    }
}

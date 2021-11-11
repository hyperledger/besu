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
package org.hyperledger.besu;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.isNull;
import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;
import static org.hyperledger.besu.controller.BesuController.CACHE_PATH;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.ONCHAIN_PRIVACY;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLDataFetcherContextImpl;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLDataFetchers;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLHttpService;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcHttpService;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.LivenessCheck;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.ReadinessCheck;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManagerBuilder;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketRequestHandler;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketService;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.PrivateWebSocketMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscriptionService;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.logs.LogsSubscriptionService;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.pending.PendingTransactionDroppedSubscriptionService;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.pending.PendingTransactionSubscriptionService;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.syncing.SyncingSubscriptionService;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.precompiles.privacy.OnchainPrivacyPrecompiledContract;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.RlpxConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.p2p.network.DefaultP2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner;
import org.hyperledger.besu.ethereum.p2p.network.NetworkRunner.NetworkBuilder;
import org.hyperledger.besu.ethereum.p2p.network.NoopP2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.peers.DefaultPeer;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissions;
import org.hyperledger.besu.ethereum.p2p.permissions.PeerPermissionsDenylist;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty.TLSConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodePermissioningControllerFactory;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.account.AccountPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.account.AccountPermissioningControllerFactory;
import org.hyperledger.besu.ethereum.permissioning.node.InsufficientPeersPermissioningProvider;
import org.hyperledger.besu.ethereum.permissioning.node.NodePermissioningController;
import org.hyperledger.besu.ethereum.permissioning.node.PeerPermissionsAdapter;
import org.hyperledger.besu.ethereum.privacy.PrivateMarkerTransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionObserver;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.stratum.StratumServer;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethstats.EthStatsService;
import org.hyperledger.besu.ethstats.util.NetstatsUrl;
import org.hyperledger.besu.metrics.MetricsService;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.nat.NatService;
import org.hyperledger.besu.nat.core.NatManager;
import org.hyperledger.besu.nat.docker.DockerDetector;
import org.hyperledger.besu.nat.docker.DockerNatManager;
import org.hyperledger.besu.nat.kubernetes.KubernetesDetector;
import org.hyperledger.besu.nat.kubernetes.KubernetesNatManager;
import org.hyperledger.besu.nat.upnp.UpnpNatManager;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.util.NetworkUtility;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import graphql.GraphQL;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class RunnerBuilder {

  private static final Logger LOG = LogManager.getLogger();

  private Vertx vertx;
  private BesuController besuController;

  private NetworkingConfiguration networkingConfiguration = NetworkingConfiguration.create();
  private final Collection<Bytes> bannedNodeIds = new ArrayList<>();
  private boolean p2pEnabled = true;
  private Optional<TLSConfiguration> p2pTLSConfiguration = Optional.empty();
  private boolean discovery;
  private String p2pAdvertisedHost;
  private String p2pListenInterface = NetworkUtility.INADDR_ANY;
  private int p2pListenPort;
  private NatMethod natMethod = NatMethod.AUTO;
  private String natManagerServiceName;
  private boolean natMethodFallbackEnabled;
  private int maxPeers;
  private boolean limitRemoteWireConnectionsEnabled = false;
  private float fractionRemoteConnectionsAllowed;
  private EthNetworkConfig ethNetworkConfig;

  private String ethstatsUrl;
  private String ethstatsContact;
  private JsonRpcConfiguration jsonRpcConfiguration;
  private GraphQLConfiguration graphQLConfiguration;
  private WebSocketConfiguration webSocketConfiguration;
  private ApiConfiguration apiConfiguration;
  private Path dataDir;
  private Optional<Path> pidPath = Optional.empty();
  private MetricsConfiguration metricsConfiguration;
  private ObservableMetricsSystem metricsSystem;
  private PermissioningServiceImpl permissioningService;
  private Optional<PermissioningConfiguration> permissioningConfiguration = Optional.empty();
  private Collection<EnodeURL> staticNodes = Collections.emptyList();
  private Optional<String> identityString = Optional.empty();
  private BesuPluginContextImpl besuPluginContext;
  private boolean autoLogBloomCaching = true;
  private boolean randomPeerPriority;
  private StorageProvider storageProvider;
  private Supplier<List<Bytes>> forkIdSupplier;
  private RpcEndpointServiceImpl rpcEndpointServiceImpl;

  public RunnerBuilder vertx(final Vertx vertx) {
    this.vertx = vertx;
    return this;
  }

  public RunnerBuilder besuController(final BesuController besuController) {
    this.besuController = besuController;
    return this;
  }

  public RunnerBuilder p2pEnabled(final boolean p2pEnabled) {
    this.p2pEnabled = p2pEnabled;
    return this;
  }

  public RunnerBuilder p2pTLSConfiguration(final TLSConfiguration p2pTLSConfiguration) {
    this.p2pTLSConfiguration = Optional.of(p2pTLSConfiguration);
    return this;
  }

  public RunnerBuilder p2pTLSConfiguration(final Optional<TLSConfiguration> p2pTLSConfiguration) {
    if (null != p2pTLSConfiguration) {
      this.p2pTLSConfiguration = p2pTLSConfiguration;
    }
    return this;
  }

  public RunnerBuilder discovery(final boolean discovery) {
    this.discovery = discovery;
    return this;
  }

  public RunnerBuilder ethNetworkConfig(final EthNetworkConfig ethNetworkConfig) {
    this.ethNetworkConfig = ethNetworkConfig;
    return this;
  }

  public RunnerBuilder networkingConfiguration(
      final NetworkingConfiguration networkingConfiguration) {
    this.networkingConfiguration = networkingConfiguration;
    return this;
  }

  public RunnerBuilder p2pAdvertisedHost(final String p2pAdvertisedHost) {
    this.p2pAdvertisedHost = p2pAdvertisedHost;
    return this;
  }

  public RunnerBuilder p2pListenInterface(final String ip) {
    checkArgument(!isNull(ip), "Invalid null value supplied for p2pListenInterface");
    this.p2pListenInterface = ip;
    return this;
  }

  public RunnerBuilder p2pListenPort(final int p2pListenPort) {
    this.p2pListenPort = p2pListenPort;
    return this;
  }

  public RunnerBuilder natMethod(final NatMethod natMethod) {
    this.natMethod = natMethod;
    return this;
  }

  public RunnerBuilder natManagerServiceName(final String natManagerServiceName) {
    this.natManagerServiceName = natManagerServiceName;
    return this;
  }

  public RunnerBuilder natMethodFallbackEnabled(final boolean natMethodFallbackEnabled) {
    this.natMethodFallbackEnabled = natMethodFallbackEnabled;
    return this;
  }

  public RunnerBuilder maxPeers(final int maxPeers) {
    this.maxPeers = maxPeers;
    return this;
  }

  public RunnerBuilder limitRemoteWireConnectionsEnabled(
      final boolean limitRemoteWireConnectionsEnabled) {
    this.limitRemoteWireConnectionsEnabled = limitRemoteWireConnectionsEnabled;
    return this;
  }

  public RunnerBuilder fractionRemoteConnectionsAllowed(
      final float fractionRemoteConnectionsAllowed) {
    this.fractionRemoteConnectionsAllowed = fractionRemoteConnectionsAllowed;
    return this;
  }

  public RunnerBuilder randomPeerPriority(final boolean randomPeerPriority) {
    this.randomPeerPriority = randomPeerPriority;
    return this;
  }

  public RunnerBuilder ethstatsUrl(final String ethstatsUrl) {
    this.ethstatsUrl = ethstatsUrl;
    return this;
  }

  public RunnerBuilder ethstatsContact(final String ethstatsContact) {
    this.ethstatsContact = ethstatsContact;
    return this;
  }

  public RunnerBuilder jsonRpcConfiguration(final JsonRpcConfiguration jsonRpcConfiguration) {
    this.jsonRpcConfiguration = jsonRpcConfiguration;
    return this;
  }

  public RunnerBuilder graphQLConfiguration(final GraphQLConfiguration graphQLConfiguration) {
    this.graphQLConfiguration = graphQLConfiguration;
    return this;
  }

  public RunnerBuilder webSocketConfiguration(final WebSocketConfiguration webSocketConfiguration) {
    this.webSocketConfiguration = webSocketConfiguration;
    return this;
  }

  public RunnerBuilder apiConfiguration(final ApiConfiguration apiConfiguration) {
    this.apiConfiguration = apiConfiguration;
    return this;
  }

  public RunnerBuilder permissioningConfiguration(
      final Optional<PermissioningConfiguration> permissioningConfiguration) {
    this.permissioningConfiguration = permissioningConfiguration;
    return this;
  }

  public RunnerBuilder pidPath(final Path pidPath) {
    this.pidPath = Optional.ofNullable(pidPath);
    return this;
  }

  public RunnerBuilder dataDir(final Path dataDir) {
    this.dataDir = dataDir;
    return this;
  }

  public RunnerBuilder bannedNodeIds(final Collection<Bytes> bannedNodeIds) {
    this.bannedNodeIds.addAll(bannedNodeIds);
    return this;
  }

  public RunnerBuilder metricsConfiguration(final MetricsConfiguration metricsConfiguration) {
    this.metricsConfiguration = metricsConfiguration;
    return this;
  }

  public RunnerBuilder metricsSystem(final ObservableMetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public RunnerBuilder permissioningService(final PermissioningServiceImpl permissioningService) {
    this.permissioningService = permissioningService;
    return this;
  }

  public RunnerBuilder staticNodes(final Collection<EnodeURL> staticNodes) {
    this.staticNodes = staticNodes;
    return this;
  }

  public RunnerBuilder identityString(final Optional<String> identityString) {
    this.identityString = identityString;
    return this;
  }

  public RunnerBuilder besuPluginContext(final BesuPluginContextImpl besuPluginContext) {
    this.besuPluginContext = besuPluginContext;
    return this;
  }

  public RunnerBuilder autoLogBloomCaching(final boolean autoLogBloomCaching) {
    this.autoLogBloomCaching = autoLogBloomCaching;
    return this;
  }

  public RunnerBuilder storageProvider(final StorageProvider storageProvider) {
    this.storageProvider = storageProvider;
    return this;
  }

  public RunnerBuilder forkIdSupplier(final Supplier<List<Bytes>> forkIdSupplier) {
    this.forkIdSupplier = forkIdSupplier;
    return this;
  }

  public RunnerBuilder rpcEndpointService(final RpcEndpointServiceImpl rpcEndpointService) {
    this.rpcEndpointServiceImpl = rpcEndpointService;
    return this;
  }

  public Runner build() {

    Preconditions.checkNotNull(besuController);

    final DiscoveryConfiguration discoveryConfiguration =
        DiscoveryConfiguration.create()
            .setBindHost(p2pListenInterface)
            .setBindPort(p2pListenPort)
            .setAdvertisedHost(p2pAdvertisedHost);
    if (discovery) {
      final List<EnodeURL> bootstrap;
      if (ethNetworkConfig.getBootNodes() == null) {
        bootstrap = DiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
      } else {
        bootstrap = ethNetworkConfig.getBootNodes();
      }
      discoveryConfiguration.setBootnodes(bootstrap);
      discoveryConfiguration.setDnsDiscoveryURL(ethNetworkConfig.getDnsDiscoveryUrl());
    } else {
      discoveryConfiguration.setActive(false);
    }

    final NodeKey nodeKey = besuController.getNodeKey();

    final SubProtocolConfiguration subProtocolConfiguration =
        besuController.getSubProtocolConfiguration();

    final ProtocolSchedule protocolSchedule = besuController.getProtocolSchedule();
    final ProtocolContext context = besuController.getProtocolContext();

    final List<SubProtocol> subProtocols = subProtocolConfiguration.getSubProtocols();
    final List<ProtocolManager> protocolManagers = subProtocolConfiguration.getProtocolManagers();
    final Set<Capability> supportedCapabilities =
        protocolManagers.stream()
            .flatMap(protocolManager -> protocolManager.getSupportedCapabilities().stream())
            .collect(Collectors.toSet());

    final RlpxConfiguration rlpxConfiguration =
        RlpxConfiguration.create()
            .setBindHost(p2pListenInterface)
            .setBindPort(p2pListenPort)
            .setMaxPeers(maxPeers)
            .setSupportedProtocols(subProtocols)
            .setClientId(BesuInfo.nodeName(identityString))
            .setLimitRemoteWireConnectionsEnabled(limitRemoteWireConnectionsEnabled)
            .setFractionRemoteWireConnectionsAllowed(fractionRemoteConnectionsAllowed);
    networkingConfiguration.setRlpx(rlpxConfiguration).setDiscovery(discoveryConfiguration);

    final PeerPermissionsDenylist bannedNodes = PeerPermissionsDenylist.create();
    bannedNodeIds.forEach(bannedNodes::add);

    final List<EnodeURL> bootnodes = discoveryConfiguration.getBootnodes();

    final Synchronizer synchronizer = besuController.getSynchronizer();

    final TransactionSimulator transactionSimulator =
        new TransactionSimulator(
            context.getBlockchain(), context.getWorldStateArchive(), protocolSchedule);

    final Bytes localNodeId = nodeKey.getPublicKey().getEncodedBytes();
    final Optional<NodePermissioningController> nodePermissioningController =
        buildNodePermissioningController(
            bootnodes, synchronizer, transactionSimulator, localNodeId, context.getBlockchain());

    final PeerPermissions peerPermissions =
        nodePermissioningController
            .map(nodePC -> new PeerPermissionsAdapter(nodePC, bootnodes, context.getBlockchain()))
            .map(nodePerms -> PeerPermissions.combine(nodePerms, bannedNodes))
            .orElse(bannedNodes);

    LOG.info("Detecting NAT service.");
    final boolean fallbackEnabled = natMethod == NatMethod.AUTO || natMethodFallbackEnabled;
    final NatService natService = new NatService(buildNatManager(natMethod), fallbackEnabled);
    final NetworkBuilder inactiveNetwork = caps -> new NoopP2PNetwork();
    final NetworkBuilder activeNetwork =
        caps ->
            DefaultP2PNetwork.builder()
                .vertx(vertx)
                .nodeKey(nodeKey)
                .config(networkingConfiguration)
                .peerPermissions(peerPermissions)
                .metricsSystem(metricsSystem)
                .supportedCapabilities(caps)
                .natService(natService)
                .randomPeerPriority(randomPeerPriority)
                .storageProvider(storageProvider)
                .forkIdSupplier(forkIdSupplier)
                .p2pTLSConfiguration(p2pTLSConfiguration)
                .build();

    final NetworkRunner networkRunner =
        NetworkRunner.builder()
            .protocolManagers(protocolManagers)
            .subProtocols(subProtocols)
            .network(p2pEnabled ? activeNetwork : inactiveNetwork)
            .metricsSystem(metricsSystem)
            .build();

    final P2PNetwork network = networkRunner.getNetwork();
    // ForkId in Ethereum Node Record needs updating when we transition to a new protocol spec
    context
        .getBlockchain()
        .observeBlockAdded(
            blockAddedEvent -> {
              if (protocolSchedule
                  .streamMilestoneBlocks()
                  .anyMatch(
                      blockNumber ->
                          blockNumber == blockAddedEvent.getBlock().getHeader().getNumber())) {
                network.updateNodeRecord();
              }
            });
    nodePermissioningController.ifPresent(
        n ->
            n.setInsufficientPeersPermissioningProvider(
                new InsufficientPeersPermissioningProvider(network, bootnodes)));

    final TransactionPool transactionPool = besuController.getTransactionPool();
    final PrivateMarkerTransactionPool privateMarkerTransactionPool =
        besuController.getPrivateMarkerTransactionPool();
    final MiningCoordinator miningCoordinator = besuController.getMiningCoordinator();

    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(
            context.getBlockchain(),
            context.getWorldStateArchive(),
            Optional.of(dataDir.resolve(CACHE_PATH)),
            Optional.of(besuController.getProtocolManager().ethContext().getScheduler()),
            apiConfiguration);

    final PrivacyParameters privacyParameters = besuController.getPrivacyParameters();

    final FilterManager filterManager =
        new FilterManagerBuilder()
            .blockchainQueries(blockchainQueries)
            .transactionPool(transactionPool)
            .privacyParameters(privacyParameters)
            .build();
    vertx.deployVerticle(filterManager);

    createPrivateTransactionObserver(filterManager, privacyParameters);

    final P2PNetwork peerNetwork = networkRunner.getNetwork();

    final MiningParameters miningParameters = besuController.getMiningParameters();
    Optional<StratumServer> stratumServer = Optional.empty();
    if (miningParameters.isStratumMiningEnabled()) {
      stratumServer =
          Optional.of(
              new StratumServer(
                  vertx,
                  miningCoordinator,
                  miningParameters.getStratumPort(),
                  miningParameters.getStratumNetworkInterface(),
                  miningParameters.getStratumExtranonce(),
                  metricsSystem));
      miningCoordinator.addEthHashObserver(stratumServer.get());
    }

    sanitizePeers(network, staticNodes)
        .map(DefaultPeer::fromEnodeURL)
        .forEach(peerNetwork::addMaintainConnectionPeer);

    final Optional<NodeLocalConfigPermissioningController> nodeLocalConfigPermissioningController =
        nodePermissioningController.flatMap(NodePermissioningController::localConfigController);

    final Optional<AccountPermissioningController> accountPermissioningController =
        buildAccountPermissioningController(
            permissioningConfiguration,
            besuController,
            transactionSimulator,
            context.getBlockchain());

    final Optional<AccountLocalConfigPermissioningController>
        accountLocalConfigPermissioningController =
            accountPermissioningController.flatMap(
                AccountPermissioningController::getAccountLocalConfigPermissioningController);

    Optional<JsonRpcHttpService> jsonRpcHttpService = Optional.empty();
    if (jsonRpcConfiguration.isEnabled()) {
      final Map<String, JsonRpcMethod> jsonRpcMethods =
          jsonRpcMethods(
              protocolSchedule,
              besuController,
              peerNetwork,
              blockchainQueries,
              synchronizer,
              transactionPool,
              privateMarkerTransactionPool,
              miningCoordinator,
              metricsSystem,
              supportedCapabilities,
              jsonRpcConfiguration.getRpcApis(),
              filterManager,
              accountLocalConfigPermissioningController,
              nodeLocalConfigPermissioningController,
              privacyParameters,
              jsonRpcConfiguration,
              webSocketConfiguration,
              metricsConfiguration,
              natService,
              besuPluginContext.getNamedPlugins(),
              dataDir,
              rpcEndpointServiceImpl);
      jsonRpcHttpService =
          Optional.of(
              new JsonRpcHttpService(
                  vertx,
                  dataDir,
                  jsonRpcConfiguration,
                  metricsSystem,
                  natService,
                  jsonRpcMethods,
                  new HealthService(new LivenessCheck()),
                  new HealthService(new ReadinessCheck(peerNetwork, synchronizer))));
    }

    Optional<GraphQLHttpService> graphQLHttpService = Optional.empty();
    if (graphQLConfiguration.isEnabled()) {
      final GraphQLDataFetchers fetchers =
          new GraphQLDataFetchers(
              supportedCapabilities, privacyParameters.getGoQuorumPrivacyParameters());
      final GraphQLDataFetcherContextImpl dataFetcherContext =
          new GraphQLDataFetcherContextImpl(
              blockchainQueries,
              protocolSchedule,
              transactionPool,
              miningCoordinator,
              synchronizer);
      final GraphQL graphQL;
      try {
        graphQL = GraphQLProvider.buildGraphQL(fetchers);
      } catch (final IOException ioe) {
        throw new RuntimeException(ioe);
      }

      graphQLHttpService =
          Optional.of(
              new GraphQLHttpService(
                  vertx,
                  dataDir,
                  graphQLConfiguration,
                  graphQL,
                  dataFetcherContext,
                  besuController.getProtocolManager().ethContext().getScheduler()));
    }

    Optional<WebSocketService> webSocketService = Optional.empty();
    if (webSocketConfiguration.isEnabled()) {
      final Map<String, JsonRpcMethod> webSocketsJsonRpcMethods =
          jsonRpcMethods(
              protocolSchedule,
              besuController,
              peerNetwork,
              blockchainQueries,
              synchronizer,
              transactionPool,
              privateMarkerTransactionPool,
              miningCoordinator,
              metricsSystem,
              supportedCapabilities,
              webSocketConfiguration.getRpcApis(),
              filterManager,
              accountLocalConfigPermissioningController,
              nodeLocalConfigPermissioningController,
              privacyParameters,
              jsonRpcConfiguration,
              webSocketConfiguration,
              metricsConfiguration,
              natService,
              besuPluginContext.getNamedPlugins(),
              dataDir,
              rpcEndpointServiceImpl);

      final SubscriptionManager subscriptionManager =
          createSubscriptionManager(vertx, transactionPool, blockchainQueries);

      createLogsSubscriptionService(
          context.getBlockchain(),
          context.getWorldStateArchive(),
          subscriptionManager,
          privacyParameters);

      createNewBlockHeadersSubscriptionService(
          context.getBlockchain(), blockchainQueries, subscriptionManager);

      createSyncingSubscriptionService(synchronizer, subscriptionManager);

      webSocketService =
          Optional.of(
              createWebsocketService(
                  vertx,
                  webSocketConfiguration,
                  subscriptionManager,
                  webSocketsJsonRpcMethods,
                  privacyParameters,
                  protocolSchedule,
                  blockchainQueries));

      createPrivateTransactionObserver(subscriptionManager, privacyParameters);
    }

    Optional<MetricsService> metricsService = createMetricsService(vertx, metricsConfiguration);

    final Optional<EthStatsService> ethStatsService;
    if (!Strings.isNullOrEmpty(ethstatsUrl)) {
      ethStatsService =
          Optional.of(
              new EthStatsService(
                  NetstatsUrl.fromParams(ethstatsUrl, ethstatsContact),
                  blockchainQueries,
                  besuController.getProtocolManager(),
                  transactionPool,
                  miningCoordinator,
                  besuController.getSyncState(),
                  vertx,
                  BesuInfo.nodeName(identityString),
                  besuController.getGenesisConfigOptions(),
                  network));
    } else {
      ethStatsService = Optional.empty();
    }

    return new Runner(
        vertx,
        networkRunner,
        natService,
        jsonRpcHttpService,
        graphQLHttpService,
        webSocketService,
        stratumServer,
        metricsService,
        ethStatsService,
        besuController,
        dataDir,
        pidPath,
        autoLogBloomCaching ? blockchainQueries.getTransactionLogBloomCacher() : Optional.empty(),
        context.getBlockchain());
  }

  private Stream<EnodeURL> sanitizePeers(
      final P2PNetwork network, final Collection<EnodeURL> enodeURLS) {
    if (network.getLocalEnode().isEmpty()) {
      return enodeURLS.stream();
    }
    final EnodeURL localEnodeURL = network.getLocalEnode().get();
    return enodeURLS.stream()
        .filter(enodeURL -> !enodeURL.getNodeId().equals(localEnodeURL.getNodeId()));
  }

  private Optional<NodePermissioningController> buildNodePermissioningController(
      final List<EnodeURL> bootnodesAsEnodeURLs,
      final Synchronizer synchronizer,
      final TransactionSimulator transactionSimulator,
      final Bytes localNodeId,
      final Blockchain blockchain) {
    final Collection<EnodeURL> fixedNodes = getFixedNodes(bootnodesAsEnodeURLs, staticNodes);

    if (permissioningConfiguration.isPresent()) {
      final PermissioningConfiguration configuration = this.permissioningConfiguration.get();
      final NodePermissioningController nodePermissioningController =
          new NodePermissioningControllerFactory()
              .create(
                  configuration,
                  synchronizer,
                  fixedNodes,
                  localNodeId,
                  transactionSimulator,
                  metricsSystem,
                  blockchain,
                  permissioningService.getConnectionPermissioningProviders());

      return Optional.of(nodePermissioningController);
    } else if (permissioningService.getConnectionPermissioningProviders().size() > 0) {
      final NodePermissioningController nodePermissioningController =
          new NodePermissioningControllerFactory()
              .create(
                  new PermissioningConfiguration(
                      Optional.empty(), Optional.empty(), Optional.empty()),
                  synchronizer,
                  fixedNodes,
                  localNodeId,
                  transactionSimulator,
                  metricsSystem,
                  blockchain,
                  permissioningService.getConnectionPermissioningProviders());

      return Optional.of(nodePermissioningController);
    } else {
      return Optional.empty();
    }
  }

  private Optional<AccountPermissioningController> buildAccountPermissioningController(
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final BesuController besuController,
      final TransactionSimulator transactionSimulator,
      final Blockchain blockchain) {

    if (permissioningConfiguration.isPresent()) {
      final Optional<AccountPermissioningController> accountPermissioningController =
          AccountPermissioningControllerFactory.create(
              permissioningConfiguration.get(), transactionSimulator, metricsSystem, blockchain);

      accountPermissioningController.ifPresent(
          permissioningController ->
              besuController
                  .getProtocolSchedule()
                  .setTransactionFilter(permissioningController::isPermitted));

      return accountPermissioningController;
    } else {
      return Optional.empty();
    }
  }

  private Optional<NatManager> buildNatManager(final NatMethod natMethod) {

    final NatMethod detectedNatMethod =
        Optional.of(natMethod)
            .filter(not(isEqual(NatMethod.AUTO)))
            .orElse(NatService.autoDetectNatMethod(new KubernetesDetector(), new DockerDetector()));
    switch (detectedNatMethod) {
      case UPNP:
        return Optional.of(new UpnpNatManager());
      case DOCKER:
        return Optional.of(
            new DockerNatManager(p2pAdvertisedHost, p2pListenPort, jsonRpcConfiguration.getPort()));
      case KUBERNETES:
        return Optional.of(new KubernetesNatManager(natManagerServiceName));
      case NONE:
      default:
        return Optional.empty();
    }
  }

  @VisibleForTesting
  public static Collection<EnodeURL> getFixedNodes(
      final Collection<EnodeURL> someFixedNodes, final Collection<EnodeURL> moreFixedNodes) {
    final Collection<EnodeURL> fixedNodes = new ArrayList<>(someFixedNodes);
    fixedNodes.addAll(moreFixedNodes);
    return fixedNodes;
  }

  private Map<String, JsonRpcMethod> jsonRpcMethods(
      final ProtocolSchedule protocolSchedule,
      final BesuController besuController,
      final P2PNetwork network,
      final BlockchainQueries blockchainQueries,
      final Synchronizer synchronizer,
      final TransactionPool transactionPool,
      final PrivateMarkerTransactionPool privateMarkerTransactionPool,
      final MiningCoordinator miningCoordinator,
      final ObservableMetricsSystem metricsSystem,
      final Set<Capability> supportedCapabilities,
      final Collection<String> jsonRpcApis,
      final FilterManager filterManager,
      final Optional<AccountLocalConfigPermissioningController> accountAllowlistController,
      final Optional<NodeLocalConfigPermissioningController> nodeAllowlistController,
      final PrivacyParameters privacyParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final NatService natService,
      final Map<String, BesuPlugin> namedPlugins,
      final Path dataDir,
      final RpcEndpointServiceImpl rpcEndpointServiceImpl) {
    final Map<String, JsonRpcMethod> methods =
        new JsonRpcMethodsFactory()
            .methods(
                BesuInfo.nodeName(identityString),
                ethNetworkConfig.getNetworkId(),
                besuController.getGenesisConfigOptions(),
                network,
                blockchainQueries,
                synchronizer,
                protocolSchedule,
                filterManager,
                transactionPool,
                privateMarkerTransactionPool,
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
                natService,
                namedPlugins,
                dataDir,
                besuController.getProtocolManager().ethContext().getEthPeers());
    methods.putAll(besuController.getAdditionalJsonRpcMethods(jsonRpcApis));

    var pluginMethods = rpcEndpointServiceImpl.getPluginMethods(jsonRpcConfiguration.getRpcApis());

    var overriddenMethods =
        methods.keySet().stream().filter(pluginMethods::containsKey).collect(Collectors.toList());
    if (overriddenMethods.size() > 0) {
      throw new RuntimeException("You can not override built in methods " + overriddenMethods);
    }

    methods.putAll(pluginMethods);
    return methods;
  }

  private SubscriptionManager createSubscriptionManager(
      final Vertx vertx,
      final TransactionPool transactionPool,
      final BlockchainQueries blockchainQueries) {
    final SubscriptionManager subscriptionManager =
        new SubscriptionManager(metricsSystem, blockchainQueries.getBlockchain());
    final PendingTransactionSubscriptionService pendingTransactions =
        new PendingTransactionSubscriptionService(subscriptionManager);
    final PendingTransactionDroppedSubscriptionService pendingTransactionsRemoved =
        new PendingTransactionDroppedSubscriptionService(subscriptionManager);
    transactionPool.subscribePendingTransactions(pendingTransactions);
    transactionPool.subscribeDroppedTransactions(pendingTransactionsRemoved);
    vertx.deployVerticle(subscriptionManager);

    return subscriptionManager;
  }

  private void createLogsSubscriptionService(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final SubscriptionManager subscriptionManager,
      final PrivacyParameters privacyParameters) {

    Optional<PrivacyQueries> privacyQueries = Optional.empty();
    if (privacyParameters.isEnabled()) {
      final BlockchainQueries blockchainQueries =
          new BlockchainQueries(
              blockchain, worldStateArchive, Optional.empty(), Optional.empty(), apiConfiguration);
      privacyQueries =
          Optional.of(
              new PrivacyQueries(
                  blockchainQueries, privacyParameters.getPrivateWorldStateReader()));
    }

    final LogsSubscriptionService logsSubscriptionService =
        new LogsSubscriptionService(subscriptionManager, privacyQueries);

    // monitoring public logs
    blockchain.observeLogs(logsSubscriptionService);

    // monitoring private logs
    if (privacyParameters.isEnabled()) {
      blockchain.observeBlockAdded(logsSubscriptionService::checkPrivateLogs);
    }
  }

  private void createPrivateTransactionObserver(
      final PrivateTransactionObserver privateTransactionObserver,
      final PrivacyParameters privacyParameters) {
    // register privateTransactionObserver as observer of events fired by the onchain precompile.
    if (privacyParameters.isOnchainPrivacyGroupsEnabled()
        && privacyParameters.isMultiTenancyEnabled()) {
      final OnchainPrivacyPrecompiledContract onchainPrivacyPrecompiledContract =
          (OnchainPrivacyPrecompiledContract)
              besuController
                  .getProtocolSchedule()
                  .getByBlockNumber(1)
                  .getPrecompileContractRegistry()
                  .get(ONCHAIN_PRIVACY);
      onchainPrivacyPrecompiledContract.addPrivateTransactionObserver(privateTransactionObserver);
    }
  }

  private void createSyncingSubscriptionService(
      final Synchronizer synchronizer, final SubscriptionManager subscriptionManager) {
    new SyncingSubscriptionService(subscriptionManager, synchronizer);
  }

  private void createNewBlockHeadersSubscriptionService(
      final Blockchain blockchain,
      final BlockchainQueries blockchainQueries,
      final SubscriptionManager subscriptionManager) {
    final NewBlockHeadersSubscriptionService newBlockHeadersSubscriptionService =
        new NewBlockHeadersSubscriptionService(subscriptionManager, blockchainQueries);

    blockchain.observeBlockAdded(newBlockHeadersSubscriptionService);
  }

  private WebSocketService createWebsocketService(
      final Vertx vertx,
      final WebSocketConfiguration configuration,
      final SubscriptionManager subscriptionManager,
      final Map<String, JsonRpcMethod> jsonRpcMethods,
      final PrivacyParameters privacyParameters,
      final ProtocolSchedule protocolSchedule,
      final BlockchainQueries blockchainQueries) {

    final WebSocketMethodsFactory websocketMethodsFactory =
        new WebSocketMethodsFactory(subscriptionManager, jsonRpcMethods);

    if (privacyParameters.isEnabled()) {
      final PrivateWebSocketMethodsFactory privateWebSocketMethodsFactory =
          new PrivateWebSocketMethodsFactory(
              privacyParameters,
              subscriptionManager,
              protocolSchedule,
              blockchainQueries,
              besuController.getPrivateMarkerTransactionPool());

      privateWebSocketMethodsFactory.methods().forEach(websocketMethodsFactory::addMethods);
    }

    rpcEndpointServiceImpl
        .getPluginMethods(configuration.getRpcApis())
        .values()
        .forEach(websocketMethodsFactory::addMethods);

    final WebSocketRequestHandler websocketRequestHandler =
        new WebSocketRequestHandler(
            vertx,
            websocketMethodsFactory.methods(),
            besuController.getProtocolManager().ethContext().getScheduler(),
            webSocketConfiguration.getTimeoutSec());

    return new WebSocketService(vertx, configuration, websocketRequestHandler);
  }

  private Optional<MetricsService> createMetricsService(
      final Vertx vertx, final MetricsConfiguration configuration) {
    return MetricsService.create(vertx, configuration, metricsSystem);
  }
}

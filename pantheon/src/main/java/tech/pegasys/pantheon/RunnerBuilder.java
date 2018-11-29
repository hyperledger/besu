/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon;

import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcHttpService;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcMethodsFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterIdGenerator;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterRepository;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketRequestHandler;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketService;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscriptionService;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.logs.LogsSubscriptionService;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.pending.PendingTransactionSubscriptionService;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing.SyncingSubscriptionService;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.NetworkRunner;
import tech.pegasys.pantheon.ethereum.p2p.api.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.discovery.internal.PeerRequirement;
import tech.pegasys.pantheon.ethereum.p2p.netty.NettyP2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.PrometheusMetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import io.vertx.core.Vertx;

public class RunnerBuilder {

  public Runner build(
      final Vertx vertx,
      final PantheonController<?> pantheonController,
      final boolean discovery,
      final Collection<?> bootstrapPeers,
      final String discoveryHost,
      final int listenPort,
      final int maxPeers,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final Path dataDir,
      final Collection<String> bannedNodeIds) {

    Preconditions.checkNotNull(pantheonController);

    final MetricsSystem metricsSystem = PrometheusMetricsSystem.init();
    final DiscoveryConfiguration discoveryConfiguration;
    if (discovery) {
      final Collection<?> bootstrap;
      if (bootstrapPeers == null) {
        bootstrap = DiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
      } else {
        bootstrap = bootstrapPeers;
      }
      discoveryConfiguration =
          DiscoveryConfiguration.create()
              .setBindPort(listenPort)
              .setAdvertisedHost(discoveryHost)
              .setBootstrapPeers(bootstrap);
    } else {
      discoveryConfiguration = DiscoveryConfiguration.create().setActive(false);
    }

    final KeyPair keyPair = pantheonController.getLocalNodeKeyPair();

    final SubProtocolConfiguration subProtocolConfiguration =
        pantheonController.subProtocolConfiguration();

    final ProtocolSchedule<?> protocolSchedule = pantheonController.getProtocolSchedule();
    final ProtocolContext<?> context = pantheonController.getProtocolContext();

    final List<SubProtocol> subProtocols = subProtocolConfiguration.getSubProtocols();
    final List<ProtocolManager> protocolManagers = subProtocolConfiguration.getProtocolManagers();
    final Set<Capability> supportedCapabilities =
        protocolManagers
            .stream()
            .flatMap(protocolManager -> protocolManager.getSupportedCapabilities().stream())
            .collect(Collectors.toSet());

    final NetworkingConfiguration networkConfig =
        new NetworkingConfiguration()
            .setRlpx(RlpxConfiguration.create().setBindPort(listenPort).setMaxPeers(maxPeers))
            .setDiscovery(discoveryConfiguration)
            .setClientId(PantheonInfo.version())
            .setSupportedProtocols(subProtocols);

    final PeerBlacklist peerBlacklist =
        new PeerBlacklist(
            bannedNodeIds.stream().map(BytesValue::fromHexString).collect(Collectors.toSet()));

    final NetworkRunner networkRunner =
        NetworkRunner.builder()
            .protocolManagers(protocolManagers)
            .subProtocols(subProtocols)
            .network(
                caps ->
                    new NettyP2PNetwork(
                        vertx,
                        keyPair,
                        networkConfig,
                        caps,
                        PeerRequirement.aggregateOf(protocolManagers),
                        peerBlacklist,
                        metricsSystem))
            .build();

    final Synchronizer synchronizer = pantheonController.getSynchronizer();
    final TransactionPool transactionPool = pantheonController.getTransactionPool();
    final MiningCoordinator miningCoordinator = pantheonController.getMiningCoordinator();

    final FilterManager filterManager = createFilterManager(vertx, context, transactionPool);

    Optional<JsonRpcHttpService> jsonRpcHttpService = Optional.empty();
    if (jsonRpcConfiguration.isEnabled()) {
      final Map<String, JsonRpcMethod> jsonRpcMethods =
          jsonRpcMethods(
              context,
              protocolSchedule,
              pantheonController,
              networkRunner,
              synchronizer,
              transactionPool,
              miningCoordinator,
              metricsSystem,
              supportedCapabilities,
              jsonRpcConfiguration.getRpcApis(),
              filterManager);
      jsonRpcHttpService =
          Optional.of(
              new JsonRpcHttpService(
                  vertx, dataDir, jsonRpcConfiguration, metricsSystem, jsonRpcMethods));
    }

    Optional<WebSocketService> webSocketService = Optional.empty();
    if (webSocketConfiguration.isEnabled()) {
      final Map<String, JsonRpcMethod> webSocketsJsonRpcMethods =
          jsonRpcMethods(
              context,
              protocolSchedule,
              pantheonController,
              networkRunner,
              synchronizer,
              transactionPool,
              miningCoordinator,
              metricsSystem,
              supportedCapabilities,
              webSocketConfiguration.getRpcApis(),
              filterManager);

      final SubscriptionManager subscriptionManager =
          createSubscriptionManager(vertx, context.getBlockchain(), transactionPool);

      createLogsSubscriptionService(
          context.getBlockchain(), context.getWorldStateArchive(), subscriptionManager);

      createNewBlockHeadersSubscriptionService(
          context.getBlockchain(), context.getWorldStateArchive(), subscriptionManager);

      createSyncingSubscriptionService(synchronizer, subscriptionManager);

      webSocketService =
          Optional.of(
              createWebsocketService(
                  vertx, webSocketConfiguration, subscriptionManager, webSocketsJsonRpcMethods));
    }

    return new Runner(
        vertx, networkRunner, jsonRpcHttpService, webSocketService, pantheonController, dataDir);
  }

  private FilterManager createFilterManager(
      final Vertx vertx, final ProtocolContext<?> context, final TransactionPool transactionPool) {
    final FilterManager filterManager =
        new FilterManager(
            new BlockchainQueries(context.getBlockchain(), context.getWorldStateArchive()),
            transactionPool,
            new FilterIdGenerator(),
            new FilterRepository());
    vertx.deployVerticle(filterManager);
    return filterManager;
  }

  private Map<String, JsonRpcMethod> jsonRpcMethods(
      final ProtocolContext<?> context,
      final ProtocolSchedule<?> protocolSchedule,
      final PantheonController<?> pantheonController,
      final NetworkRunner networkRunner,
      final Synchronizer synchronizer,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final MetricsSystem metricsSystem,
      final Set<Capability> supportedCapabilities,
      final Collection<RpcApi> jsonRpcApis,
      final FilterManager filterManager) {
    final Map<String, JsonRpcMethod> methods =
        new JsonRpcMethodsFactory()
            .methods(
                PantheonInfo.version(),
                networkRunner.getNetwork(),
                context.getBlockchain(),
                context.getWorldStateArchive(),
                synchronizer,
                transactionPool,
                protocolSchedule,
                miningCoordinator,
                metricsSystem,
                supportedCapabilities,
                jsonRpcApis,
                filterManager);
    methods.putAll(pantheonController.getAdditionalJsonRpcMethods(jsonRpcApis));
    return methods;
  }

  private SubscriptionManager createSubscriptionManager(
      final Vertx vertx, final Blockchain blockchain, final TransactionPool transactionPool) {
    final SubscriptionManager subscriptionManager = new SubscriptionManager();
    final PendingTransactionSubscriptionService pendingTransactions =
        new PendingTransactionSubscriptionService(subscriptionManager);
    transactionPool.addTransactionListener(pendingTransactions);
    vertx.deployVerticle(subscriptionManager);

    return subscriptionManager;
  }

  private LogsSubscriptionService createLogsSubscriptionService(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final SubscriptionManager subscriptionManager) {
    final LogsSubscriptionService logsSubscriptionService =
        new LogsSubscriptionService(
            subscriptionManager, new BlockchainQueries(blockchain, worldStateArchive));

    blockchain.observeBlockAdded(logsSubscriptionService);

    return logsSubscriptionService;
  }

  private SyncingSubscriptionService createSyncingSubscriptionService(
      final Synchronizer synchronizer, final SubscriptionManager subscriptionManager) {
    return new SyncingSubscriptionService(subscriptionManager, synchronizer);
  }

  private NewBlockHeadersSubscriptionService createNewBlockHeadersSubscriptionService(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final SubscriptionManager subscriptionManager) {
    final NewBlockHeadersSubscriptionService newBlockHeadersSubscriptionService =
        new NewBlockHeadersSubscriptionService(
            subscriptionManager, new BlockchainQueries(blockchain, worldStateArchive));

    blockchain.observeBlockAdded(newBlockHeadersSubscriptionService);

    return newBlockHeadersSubscriptionService;
  }

  private WebSocketService createWebsocketService(
      final Vertx vertx,
      final WebSocketConfiguration configuration,
      final SubscriptionManager subscriptionManager,
      final Map<String, JsonRpcMethod> jsonRpcMethods) {
    final WebSocketMethodsFactory websocketMethodsFactory =
        new WebSocketMethodsFactory(subscriptionManager, jsonRpcMethods);
    final WebSocketRequestHandler websocketRequestHandler =
        new WebSocketRequestHandler(vertx, websocketMethodsFactory.methods());

    return new WebSocketService(vertx, configuration, websocketRequestHandler);
  }
}

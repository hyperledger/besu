package net.consensys.pantheon;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.jsonrpc.CliqueJsonRpcMethodsFactory;
import net.consensys.pantheon.consensus.ibft.IbftContext;
import net.consensys.pantheon.consensus.ibft.jsonrpc.IbftJsonRpcMethodsFactory;
import net.consensys.pantheon.controller.PantheonController;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcHttpService;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcMethodsFactory;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.WebSocketRequestHandler;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.WebSocketService;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.blockheaders.NewBlockHeadersSubscriptionService;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.logs.LogsSubscriptionService;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.pending.PendingTransactionSubscriptionService;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.subscription.syncing.SyncingSubscriptionService;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.p2p.NetworkRunner;
import net.consensys.pantheon.ethereum.p2p.api.ProtocolManager;
import net.consensys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import net.consensys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import net.consensys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import net.consensys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import net.consensys.pantheon.ethereum.p2p.discovery.internal.PeerRequirement;
import net.consensys.pantheon.ethereum.p2p.netty.NettyP2PNetwork;
import net.consensys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import net.consensys.pantheon.ethereum.p2p.wire.Capability;
import net.consensys.pantheon.ethereum.p2p.wire.SubProtocol;

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
      final Path dataDir) {

    Preconditions.checkNotNull(pantheonController);

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
                        new PeerBlacklist()))
            .build();

    final Synchronizer synchronizer = pantheonController.getSynchronizer();
    final TransactionPool transactionPool = pantheonController.getTransactionPool();
    final MiningCoordinator miningCoordinator = pantheonController.getMiningCoordinator();

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
              supportedCapabilities,
              jsonRpcConfiguration.getRpcApis());
      jsonRpcHttpService =
          Optional.of(new JsonRpcHttpService(vertx, jsonRpcConfiguration, jsonRpcMethods));
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
              supportedCapabilities,
              webSocketConfiguration.getRpcApis());

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

  private Map<String, JsonRpcMethod> jsonRpcMethods(
      final ProtocolContext<?> context,
      final ProtocolSchedule<?> protocolSchedule,
      final PantheonController<?> pantheonController,
      final NetworkRunner networkRunner,
      final Synchronizer synchronizer,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final Set<Capability> supportedCapabilities,
      final Collection<RpcApis> jsonRpcApis) {
    final Map<String, JsonRpcMethod> methods =
        new JsonRpcMethodsFactory()
            .methods(
                PantheonInfo.version(),
                String.valueOf(pantheonController.getGenesisConfig().getChainId()),
                networkRunner.getNetwork(),
                context.getBlockchain(),
                context.getWorldStateArchive(),
                synchronizer,
                transactionPool,
                protocolSchedule,
                miningCoordinator,
                supportedCapabilities,
                jsonRpcApis);

    if (context.getConsensusState() instanceof CliqueContext) {
      // This is checked before entering this if branch
      @SuppressWarnings("unchecked")
      final ProtocolContext<CliqueContext> cliqueProtocolContext =
          (ProtocolContext<CliqueContext>) context;
      methods.putAll(new CliqueJsonRpcMethodsFactory().methods(cliqueProtocolContext));
    }

    if (context.getConsensusState() instanceof IbftContext) {
      // This is checked before entering this if branch
      @SuppressWarnings("unchecked")
      final ProtocolContext<IbftContext> ibftProtocolContext =
          (ProtocolContext<IbftContext>) context;
      methods.putAll(new IbftJsonRpcMethodsFactory().methods(ibftProtocolContext));
    }
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

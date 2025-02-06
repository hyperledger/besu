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
package org.hyperledger.besu.tests.acceptance.dsl.node;

import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;

import org.hyperledger.besu.Runner;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.cryptoservices.KeyPairSecurityModule;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.InProcessRpcConfiguration;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.ImmutableMiningConfiguration;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginConfiguration;
import org.hyperledger.besu.ethereum.core.plugins.PluginInfo;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCacheModule;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoaderModule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.MetricCategoryRegistryImpl;
import org.hyperledger.besu.metrics.MetricsSystemModule;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.PrivacyPluginService;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.SecurityModuleService;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.TransactionPoolValidatorService;
import org.hyperledger.besu.plugin.services.TransactionSelectionService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService;
import org.hyperledger.besu.plugin.services.metrics.MetricCategoryRegistry;
import org.hyperledger.besu.plugin.services.mining.MiningService;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuEventsImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.BlockchainServiceImpl;
import org.hyperledger.besu.services.MiningServiceImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.PrivacyPluginServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.TransactionPoolServiceImpl;
import org.hyperledger.besu.services.TransactionPoolValidatorServiceImpl;
import org.hyperledger.besu.services.TransactionSelectionServiceImpl;
import org.hyperledger.besu.services.TransactionSimulationServiceImpl;
import org.hyperledger.besu.services.kvstore.InMemoryStoragePlugin;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public class ThreadBesuNodeRunner implements BesuNodeRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadBesuNodeRunner.class);
  private final Map<String, Runner> besuRunners = new HashMap<>();

  private final Map<Node, BesuPluginContextImpl> besuPluginContextMap = new ConcurrentHashMap<>();

  @Override
  public void startNode(final BesuNode node) {

    if (MDC.get("node") != null) {
      LOG.error("ThreadContext node is already set to {}", MDC.get("node"));
    }
    MDC.put("node", node.getName());

    if (!node.getRunCommand().isEmpty()) {
      throw new UnsupportedOperationException("commands are not supported with thread runner");
    }

    BesuNodeProviderModule module = new BesuNodeProviderModule(node);
    AcceptanceTestBesuComponent component =
        DaggerThreadBesuNodeRunner_AcceptanceTestBesuComponent.builder()
            .besuNodeProviderModule(module)
            .build();

    final Path dataDir = node.homeDirectory();
    final PermissioningServiceImpl permissioningService = new PermissioningServiceImpl();

    GlobalOpenTelemetry.resetForTest();
    final ObservableMetricsSystem metricsSystem =
        (ObservableMetricsSystem) component.getMetricsSystem();
    final List<EnodeURL> bootnodes =
        node.getConfiguration().getBootnodes().stream().map(EnodeURLImpl::fromURI).toList();

    final EthNetworkConfig.Builder networkConfigBuilder = component.ethNetworkConfigBuilder();
    networkConfigBuilder.setBootNodes(bootnodes);
    node.getConfiguration()
        .getGenesisConfig()
        .map(GenesisConfig::fromConfig)
        .ifPresent(networkConfigBuilder::setGenesisConfig);
    final EthNetworkConfig ethNetworkConfig = networkConfigBuilder.build();
    final BesuControllerBuilder builder = component.besuControllerBuilder();
    builder.isRevertReasonEnabled(node.isRevertReasonEnabled());
    builder.networkConfiguration(node.getNetworkingConfiguration());

    builder.dataDirectory(dataDir);
    builder.nodeKey(new NodeKey(new KeyPairSecurityModule(KeyPairUtil.loadKeyPair(dataDir))));
    builder.privacyParameters(node.getPrivacyParameters());

    node.getGenesisConfig().map(GenesisConfig::fromConfig).ifPresent(builder::genesisConfig);

    final BesuController besuController = component.besuController();

    InProcessRpcConfiguration inProcessRpcConfiguration = node.inProcessRpcConfiguration();

    final BesuPluginContextImpl besuPluginContext =
        besuPluginContextMap.computeIfAbsent(node, n -> component.getBesuPluginContext());

    final RunnerBuilder runnerBuilder = new RunnerBuilder();
    runnerBuilder.permissioningConfiguration(node.getPermissioningConfiguration());
    runnerBuilder.apiConfiguration(node.getApiConfiguration());

    runnerBuilder
        .vertx(Vertx.vertx())
        .besuController(besuController)
        .ethNetworkConfig(ethNetworkConfig)
        .discoveryEnabled(node.isDiscoveryEnabled())
        .p2pAdvertisedHost(node.getHostName())
        .p2pListenPort(0)
        .networkingConfiguration(node.getNetworkingConfiguration())
        .jsonRpcConfiguration(node.jsonRpcConfiguration())
        .webSocketConfiguration(node.webSocketConfiguration())
        .jsonRpcIpcConfiguration(node.jsonRpcIpcConfiguration())
        .dataDir(node.homeDirectory())
        .metricsSystem(metricsSystem)
        .permissioningService(permissioningService)
        .metricsConfiguration(node.getMetricsConfiguration())
        .p2pEnabled(node.isP2pEnabled())
        .graphQLConfiguration(GraphQLConfiguration.createDefault())
        .staticNodes(node.getStaticNodes().stream().map(EnodeURLImpl::fromString).toList())
        .besuPluginContext(besuPluginContext)
        .autoLogBloomCaching(false)
        .storageProvider(besuController.getStorageProvider())
        .rpcEndpointService(component.rpcEndpointService())
        .inProcessRpcConfiguration(inProcessRpcConfiguration);
    node.engineRpcConfiguration().ifPresent(runnerBuilder::engineJsonRpcConfiguration);
    besuPluginContext.beforeExternalServices();
    final Runner runner = runnerBuilder.build();

    runner.startExternalServices();

    besuPluginContext.addService(
        BesuEvents.class,
        new BesuEventsImpl(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolManager().getBlockBroadcaster(),
            besuController.getTransactionPool(),
            besuController.getSyncState(),
            besuController.getProtocolContext().getBadBlockManager()));
    besuPluginContext.addService(
        TransactionPoolService.class,
        new TransactionPoolServiceImpl(besuController.getTransactionPool()));
    besuPluginContext.addService(
        MiningService.class, new MiningServiceImpl(besuController.getMiningCoordinator()));

    component.rpcEndpointService().init(runner.getInProcessRpcMethods());

    besuPluginContext.startPlugins();

    runner.startEthereumMainLoop();

    besuRunners.put(node.getName(), runner);
    MDC.remove("node");
  }

  @Override
  public void stopNode(final BesuNode node) {
    final BesuPluginContextImpl pluginContext = besuPluginContextMap.remove(node);
    if (pluginContext != null) {
      pluginContext.stopPlugins();
    }
    node.stop();
    killRunner(node.getName());
  }

  @Override
  public void shutdown() {
    // stop all plugins from pluginContext
    besuPluginContextMap.values().forEach(BesuPluginContextImpl::stopPlugins);
    besuPluginContextMap.clear();

    // iterate over a copy of the set so that besuRunner can be updated when a runner is killed
    new HashSet<>(besuRunners.keySet()).forEach(this::killRunner);
  }

  @Override
  public boolean isActive(final String nodeName) {
    return besuRunners.containsKey(nodeName);
  }

  private void killRunner(final String name) {
    LOG.info("Killing " + name + " runner");

    if (besuRunners.containsKey(name)) {
      try {
        besuRunners.get(name).close();
        besuRunners.remove(name);
      } catch (final Exception e) {
        throw new RuntimeException("Error shutting down node " + name, e);
      }
    } else {
      LOG.error("There was a request to kill an unknown node: {}", name);
    }
  }

  @Override
  public void startConsoleCapture() {
    throw new RuntimeException("Console contents can only be captured in process execution");
  }

  @Override
  public String getConsoleContents() {
    throw new RuntimeException("Console contents can only be captured in process execution");
  }

  @Module
  static class BesuNodeProviderModule {

    private final BesuNode toProvide;

    public BesuNodeProviderModule(final BesuNode toProvide) {
      this.toProvide = toProvide;
    }

    @Provides
    @Singleton
    MetricsConfiguration provideMetricsConfiguration() {
      if (toProvide.getMetricsConfiguration() != null) {
        return toProvide.getMetricsConfiguration();
      } else {
        return MetricsConfiguration.builder().build();
      }
    }

    @Provides
    public BesuNode provideBesuNodeRunner() {
      return toProvide;
    }

    @Provides
    @Named("ExtraCLIOptions")
    public List<String> provideExtraCLIOptions() {
      return toProvide.getExtraCLIOptions();
    }

    @Provides
    @Named("RequestedPlugins")
    public List<String> provideRequestedPlugins() {
      return toProvide.getRequestedPlugins();
    }

    @Provides
    Path provideDataDir() {
      return toProvide.homeDirectory();
    }

    @Provides
    @Singleton
    RpcEndpointServiceImpl provideRpcEndpointService() {
      return new RpcEndpointServiceImpl();
    }

    @Provides
    @Singleton
    BlockchainServiceImpl provideBlockchainService(final BesuController besuController) {
      BlockchainServiceImpl retval = new BlockchainServiceImpl();
      retval.init(
          besuController.getProtocolContext().getBlockchain(),
          besuController.getProtocolSchedule());
      return retval;
    }

    @Provides
    @Singleton
    Blockchain provideBlockchain(final BesuController besuController) {
      return besuController.getProtocolContext().getBlockchain();
    }

    @Provides
    @SuppressWarnings("CloseableProvides")
    WorldStateArchive provideWorldStateArchive(final BesuController besuController) {
      return besuController.getProtocolContext().getWorldStateArchive();
    }

    @Provides
    ProtocolSchedule provideProtocolSchedule(final BesuController besuController) {
      return besuController.getProtocolSchedule();
    }

    @Provides
    ApiConfiguration provideApiConfiguration(final BesuNode node) {
      return node.getApiConfiguration();
    }

    @Provides
    @Singleton
    TransactionPoolValidatorServiceImpl provideTransactionPoolValidatorService() {
      return new TransactionPoolValidatorServiceImpl();
    }

    @Provides
    @Singleton
    TransactionSelectionServiceImpl provideTransactionSelectionService() {
      return new TransactionSelectionServiceImpl();
    }

    @Provides
    @Singleton
    TransactionPoolConfiguration provideTransactionPoolConfiguration(
        final BesuNode node,
        final TransactionPoolValidatorServiceImpl transactionPoolValidatorServiceImpl) {

      TransactionPoolConfiguration txPoolConfig =
          ImmutableTransactionPoolConfiguration.builder()
              .from(node.getTransactionPoolConfiguration())
              .strictTransactionReplayProtectionEnabled(node.isStrictTxReplayProtectionEnabled())
              .transactionPoolValidatorService(transactionPoolValidatorServiceImpl)
              .build();
      return txPoolConfig;
    }

    @Provides
    @Singleton
    TransactionSimulator provideTransactionSimulator(
        final Blockchain blockchain,
        final WorldStateArchive worldStateArchive,
        final ProtocolSchedule protocolSchedule,
        final MiningConfiguration miningConfiguration,
        final ApiConfiguration apiConfiguration) {
      return new TransactionSimulator(
          blockchain,
          worldStateArchive,
          protocolSchedule,
          miningConfiguration,
          apiConfiguration.getGasCap());
    }

    @Provides
    @Singleton
    TransactionSimulationServiceImpl provideTransactionSimulationService(
        final Blockchain blockchain, final TransactionSimulator transactionSimulator) {
      TransactionSimulationServiceImpl retval = new TransactionSimulationServiceImpl();
      retval.init(blockchain, transactionSimulator);
      return retval;
    }

    @Provides
    @Singleton
    MetricCategoryRegistryImpl provideMetricCategoryRegistry() {
      return new MetricCategoryRegistryImpl();
    }
  }

  @Module
  public static class ThreadBesuNodeRunnerModule {
    @Provides
    @Singleton
    public ThreadBesuNodeRunner provideThreadBesuNodeRunner() {
      return new ThreadBesuNodeRunner();
    }
  }

  @Module
  @SuppressWarnings("CloseableProvides")
  public static class BesuControllerModule {
    @Provides
    @Singleton
    public SynchronizerConfiguration provideSynchronizationConfiguration() {
      final SynchronizerConfiguration synchronizerConfiguration =
          SynchronizerConfiguration.builder().build();
      return synchronizerConfiguration;
    }

    @Singleton
    @Provides
    public BesuControllerBuilder provideBesuControllerBuilder(
        final EthNetworkConfig ethNetworkConfig,
        final SynchronizerConfiguration synchronizerConfiguration,
        final TransactionPoolConfiguration transactionPoolConfiguration) {

      final BesuControllerBuilder builder =
          new BesuController.Builder()
              .fromEthNetworkConfig(ethNetworkConfig, synchronizerConfiguration.getSyncMode());
      builder.transactionPoolConfiguration(transactionPoolConfiguration);
      return builder;
    }

    @Provides
    @Singleton
    public BesuController provideBesuController(
        final SynchronizerConfiguration synchronizerConfiguration,
        final BesuControllerBuilder builder,
        final MetricsSystem metricsSystem,
        final KeyValueStorageProvider storageProvider,
        final MiningConfiguration miningConfiguration,
        final ApiConfiguration apiConfiguration) {

      builder
          .synchronizerConfiguration(synchronizerConfiguration)
          .metricsSystem((ObservableMetricsSystem) metricsSystem)
          .dataStorageConfiguration(DataStorageConfiguration.DEFAULT_FOREST_CONFIG)
          .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
          .clock(Clock.systemUTC())
          .storageProvider(storageProvider)
          .gasLimitCalculator(GasLimitCalculator.constant())
          .evmConfiguration(EvmConfiguration.DEFAULT)
          .maxPeers(25)
          .maxRemotelyInitiatedPeers(15)
          .miningParameters(miningConfiguration)
          .randomPeerPriority(false)
          .apiConfiguration(apiConfiguration)
          .besuComponent(null);
      return builder.build();
    }

    @Provides
    @Singleton
    public EthNetworkConfig.Builder provideEthNetworkConfigBuilder() {
      final EthNetworkConfig.Builder networkConfigBuilder =
          new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(NetworkName.DEV));
      return networkConfigBuilder;
    }

    @Provides
    public EthNetworkConfig provideEthNetworkConfig(
        final EthNetworkConfig.Builder networkConfigBuilder) {

      final EthNetworkConfig ethNetworkConfig = networkConfigBuilder.build();
      return ethNetworkConfig;
    }

    @Provides
    public BesuPluginContextImpl providePluginContext(
        final StorageServiceImpl storageService,
        final SecurityModuleServiceImpl securityModuleService,
        final TransactionSimulationServiceImpl transactionSimulationServiceImpl,
        final TransactionSelectionServiceImpl transactionSelectionServiceImpl,
        final TransactionPoolValidatorServiceImpl transactionPoolValidatorServiceImpl,
        final BlockchainServiceImpl blockchainServiceImpl,
        final RpcEndpointServiceImpl rpcEndpointServiceImpl,
        final BesuConfiguration commonPluginConfiguration,
        final PermissioningServiceImpl permissioningService,
        final MetricsConfiguration metricsConfiguration,
        final MetricCategoryRegistryImpl metricCategoryRegistry,
        final MetricsSystem metricsSystem,
        final @Named("ExtraCLIOptions") List<String> extraCLIOptions,
        final @Named("RequestedPlugins") List<String> requestedPlugins) {
      final CommandLine commandLine = new CommandLine(CommandSpec.create());
      final BesuPluginContextImpl besuPluginContext = new BesuPluginContextImpl();
      besuPluginContext.addService(StorageService.class, storageService);
      besuPluginContext.addService(SecurityModuleService.class, securityModuleService);
      besuPluginContext.addService(PicoCLIOptions.class, new PicoCLIOptionsImpl(commandLine));
      besuPluginContext.addService(RpcEndpointService.class, rpcEndpointServiceImpl);
      besuPluginContext.addService(
          TransactionSelectionService.class, transactionSelectionServiceImpl);
      besuPluginContext.addService(
          TransactionPoolValidatorService.class, transactionPoolValidatorServiceImpl);
      besuPluginContext.addService(
          TransactionSimulationService.class, transactionSimulationServiceImpl);
      besuPluginContext.addService(BlockchainService.class, blockchainServiceImpl);
      besuPluginContext.addService(BesuConfiguration.class, commonPluginConfiguration);
      metricCategoryRegistry.setMetricsConfiguration(metricsConfiguration);
      besuPluginContext.addService(MetricCategoryRegistry.class, metricCategoryRegistry);
      besuPluginContext.addService(MetricsSystem.class, metricsSystem);

      final Path pluginsPath;
      final String pluginDir = System.getProperty("besu.plugins.dir");
      if (pluginDir == null || pluginDir.isEmpty()) {
        pluginsPath = commonPluginConfiguration.getDataPath().resolve("plugins");
        final File pluginsDirFile = pluginsPath.toFile();
        if (!pluginsDirFile.isDirectory()) {
          pluginsDirFile.mkdirs();
          pluginsDirFile.deleteOnExit();
        }
        System.setProperty("besu.plugins.dir", pluginsPath.toString());
      } else {
        pluginsPath = Path.of(pluginDir);
      }

      besuPluginContext.addService(BesuConfiguration.class, commonPluginConfiguration);
      besuPluginContext.addService(PermissioningService.class, permissioningService);
      besuPluginContext.addService(PrivacyPluginService.class, new PrivacyPluginServiceImpl());

      besuPluginContext.initialize(
          new PluginConfiguration.Builder()
              .pluginsDir(pluginsPath)
              .requestedPlugins(requestedPlugins.stream().map(PluginInfo::new).toList())
              .build());
      besuPluginContext.registerPlugins();
      commandLine.parseArgs(extraCLIOptions.toArray(new String[0]));

      // register built-in plugins
      new RocksDBPlugin().register(besuPluginContext);
      return besuPluginContext;
    }

    @Provides
    public KeyValueStorageProvider provideKeyValueStorageProvider(
        final BesuConfiguration commonPluginConfiguration, final MetricsSystem metricsSystem) {

      final StorageServiceImpl storageService = new StorageServiceImpl();
      storageService.registerKeyValueStorage(
          new InMemoryStoragePlugin.InMemoryKeyValueStorageFactory("memory"));
      final KeyValueStorageProvider storageProvider =
          new KeyValueStorageProviderBuilder()
              .withStorageFactory(storageService.getByName("memory").get())
              .withCommonConfiguration(commonPluginConfiguration)
              .withMetricsSystem(metricsSystem)
              .build();

      return storageProvider;
    }

    @Provides
    public MiningConfiguration provideMiningParameters(
        final TransactionSelectionServiceImpl transactionSelectionServiceImpl,
        final BesuNode node) {
      final var miningParameters =
          ImmutableMiningConfiguration.builder()
              .from(node.getMiningParameters())
              .transactionSelectionService(transactionSelectionServiceImpl)
              .build();

      return miningParameters;
    }

    @Provides
    @Inject
    BesuConfiguration provideBesuConfiguration(
        final Path dataDir, final MiningConfiguration miningConfiguration, final BesuNode node) {
      final BesuConfigurationImpl commonPluginConfiguration = new BesuConfigurationImpl();
      commonPluginConfiguration.init(
          dataDir, dataDir.resolve(DATABASE_PATH), node.getDataStorageConfiguration());
      commonPluginConfiguration.withMiningParameters(miningConfiguration);
      return commonPluginConfiguration;
    }
  }

  @Module
  public static class ObservableMetricsSystemModule {
    @Provides
    @Singleton
    public ObservableMetricsSystem provideObservableMetricsSystem() {
      return new NoOpMetricsSystem();
    }
  }

  @Module
  public static class MockBesuCommandModule {

    @Provides
    BesuCommand provideBesuCommand(final BesuPluginContextImpl pluginContext) {
      final BesuCommand besuCommand =
          new BesuCommand(
              RlpBlockImporter::new,
              JsonBlockImporter::new,
              RlpBlockExporter::new,
              new RunnerBuilder(),
              new BesuController.Builder(),
              pluginContext,
              System.getenv(),
              LoggerFactory.getLogger(MockBesuCommandModule.class));
      besuCommand.toCommandLine();
      return besuCommand;
    }

    @Provides
    @Named("besuCommandLogger")
    @Singleton
    Logger provideBesuCommandLogger() {
      return LoggerFactory.getLogger(MockBesuCommandModule.class);
    }
  }

  @Singleton
  @Component(
      modules = {
        ThreadBesuNodeRunner.BesuControllerModule.class,
        ThreadBesuNodeRunner.MockBesuCommandModule.class,
        ThreadBesuNodeRunner.ObservableMetricsSystemModule.class,
        ThreadBesuNodeRunnerModule.class,
        BonsaiCachedMerkleTrieLoaderModule.class,
        MetricsSystemModule.class,
        ThreadBesuNodeRunner.BesuNodeProviderModule.class,
        BlobCacheModule.class
      })
  public interface AcceptanceTestBesuComponent extends BesuComponent {
    BesuController besuController();

    BesuControllerBuilder besuControllerBuilder(); // TODO: needing this sucks

    EthNetworkConfig.Builder ethNetworkConfigBuilder();

    RpcEndpointServiceImpl rpcEndpointService();

    BlockchainServiceImpl blockchainService();

    ObservableMetricsSystem getObservableMetricsSystem();

    ThreadBesuNodeRunner getThreadBesuNodeRunner();
  }
}

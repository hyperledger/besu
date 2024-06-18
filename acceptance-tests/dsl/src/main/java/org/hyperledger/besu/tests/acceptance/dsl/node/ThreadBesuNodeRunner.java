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
import org.hyperledger.besu.components.BesuPluginContextModule;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfigurationProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.BesuControllerBuilder;
import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.cryptoservices.KeyPairSecurityModule;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCacheModule;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoaderModule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.MetricsSystemModule;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
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
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBPlugin;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.services.BesuEventsImpl;
import org.hyperledger.besu.services.BesuPluginContextImpl;
import org.hyperledger.besu.services.BlockchainServiceImpl;
import org.hyperledger.besu.services.PermissioningServiceImpl;
import org.hyperledger.besu.services.PicoCLIOptionsImpl;
import org.hyperledger.besu.services.PrivacyPluginServiceImpl;
import org.hyperledger.besu.services.RpcEndpointServiceImpl;
import org.hyperledger.besu.services.SecurityModuleServiceImpl;
import org.hyperledger.besu.services.StorageServiceImpl;
import org.hyperledger.besu.services.TransactionPoolValidatorServiceImpl;
import org.hyperledger.besu.services.TransactionSelectionServiceImpl;
import org.hyperledger.besu.services.TransactionSimulationServiceImpl;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
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
    AcceptanceTestBesuComponent component =
        DaggerThreadBesuNodeRunner_AcceptanceTestBesuComponent.create();
    BesuConfiguration commonPluginConfiguration = component.besuConfiguration();
    // final Path dataDir = node.homeDirectory();
    final BesuPluginContextImpl besuPluginContext = component.getBesuPluginContext();

    GlobalOpenTelemetry.resetForTest();

    //final int maxPeers = 25;

    final BesuController besuController = component.besuController();
    final TransactionSimulationServiceImpl transactionSimulationServiceImpl =
            new TransactionSimulationServiceImpl();
    final BlockchainServiceImpl blockchainServiceImpl = new BlockchainServiceImpl();
    final PermissioningServiceImpl permissioningService = new PermissioningServiceImpl();
    final StorageServiceImpl storageService = new StorageServiceImpl();
    final RpcEndpointServiceImpl rpcEndpointServiceImpl = new RpcEndpointServiceImpl();
    initTransactionSimulationService(
        transactionSimulationServiceImpl, besuController, node.getApiConfiguration());
    initBlockchainService(blockchainServiceImpl, besuController);
    final List<EnodeURL> bootnodes =
            node.getConfiguration().getBootnodes().stream()
                    .map(EnodeURLImpl::fromURI)
                    .collect(Collectors.toList());
    final NetworkName network = node.getNetwork() == null ? NetworkName.DEV : node.getNetwork();
    final EthNetworkConfig.Builder networkConfigBuilder =
            new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(network))
                    .setBootNodes(bootnodes);
    node.getConfiguration()
            .getGenesisConfig()
            .map(GenesisConfigFile::fromConfig)
            .ifPresent(networkConfigBuilder::setGenesisConfigFile);
    final EthNetworkConfig ethNetworkConfig = networkConfigBuilder.build();

    final KeyValueStorageProvider storageProvider =
            new KeyValueStorageProviderBuilder()
                    .withStorageFactory(storageService.getByName("rocksdb").get())
                    .withCommonConfiguration(commonPluginConfiguration)
                    .withMetricsSystem(component.getObservableMetricsSystem())
                    .build();


    //node.engineRpcConfiguration().ifPresent(runnerBuilder::engineJsonRpcConfiguration);

    besuPluginContext.beforeExternalServices();

    final Runner runner = component.besuRunner();

    runner.startExternalServices();

    besuPluginContext.addService(
        BesuEvents.class,
        new BesuEventsImpl(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolManager().getBlockBroadcaster(),
            besuController.getTransactionPool(),
            besuController.getSyncState(),
            besuController.getProtocolContext().getBadBlockManager()));
    besuPluginContext.startPlugins();

    runner.startEthereumMainLoop();

    besuRunners.put(node.getName(), runner);
    MDC.remove("node");
  }

  private void initBlockchainService(
      final BlockchainServiceImpl blockchainServiceImpl, final BesuController besuController) {
    blockchainServiceImpl.init(
        besuController.getProtocolContext(), besuController.getProtocolSchedule());
  }

  private void initTransactionSimulationService(
      final TransactionSimulationServiceImpl transactionSimulationService,
      final BesuController besuController,
      final ApiConfiguration apiConfiguration) {
    transactionSimulationService.init(
        besuController.getProtocolContext().getBlockchain(),
        new TransactionSimulator(
            besuController.getProtocolContext().getBlockchain(),
            besuController.getProtocolContext().getWorldStateArchive(),
            besuController.getProtocolSchedule(),
            apiConfiguration.getGasCap()));
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
  static class RunnerModule {

    @Provides
    public Runner provideBesuRunner(final PermissioningConfiguration permissioningConfiguration,
                                    final ApiConfiguration apiConfiguration,
                                    final BesuController besuController,
                                    final BesuNode node,
                                    final EthNetworkConfig ethNetworkConfig,
                                    final AcceptanceTestBesuComponent component,
                                    final PermissioningServiceImpl permissioningServiceImpl,
                                    final BesuPluginContextImpl besuPluginContextImpl,
                                    final StorageProvider storageProvider,
                                    final RpcEndpointServiceImpl rpcEndpointServiceImpl) {
      final RunnerBuilder runnerBuilder = new RunnerBuilder();

      runnerBuilder
              .permissioningConfiguration(Optional.ofNullable(permissioningConfiguration))
              .apiConfiguration(apiConfiguration)
              .vertx(Vertx.vertx())
              .besuController(besuController)
              .ethNetworkConfig(ethNetworkConfig)
              .discovery(node.isDiscoveryEnabled())
              .p2pAdvertisedHost(node.getHostName())
              .p2pListenPort(0)
              .networkingConfiguration(node.getNetworkingConfiguration())
              .jsonRpcConfiguration(node.jsonRpcConfiguration())
              .webSocketConfiguration(node.webSocketConfiguration())
              .jsonRpcIpcConfiguration(node.jsonRpcIpcConfiguration())
              .dataDir(node.homeDirectory())
              .metricsSystem(component.getObservableMetricsSystem())
              .permissioningService(permissioningServiceImpl) //TODO: it is bad that this requires an impl
              .metricsConfiguration(node.getMetricsConfiguration())
              .p2pEnabled(node.isP2pEnabled())
              .p2pTLSConfiguration(node.getTLSConfiguration())
              .graphQLConfiguration(GraphQLConfiguration.createDefault())
              .staticNodes(
                      node.getStaticNodes().stream()
                              .map(EnodeURLImpl::fromString)
                              .collect(Collectors.toList()))
              .besuPluginContext(besuPluginContextImpl) //TODO: it is bad that this requires an impl
              .autoLogBloomCaching(false)
              .storageProvider(storageProvider)
              .rpcEndpointService(rpcEndpointServiceImpl); //TODO: it is bad that this requires an impl
      return runnerBuilder.build();
    }
  }
  @Module
  @SuppressWarnings("CloseableProvides")
  static class BesuControllerModule {
    @Provides
    public BesuController provideBesuController(
            final EthNetworkConfig ethNetworkConfig,
            final ObservableMetricsSystem metricsSystem,
            final KeyValueStorageProvider storageProvider,
            final ImmutableTransactionPoolConfiguration txPoolConfig,
            final int maxPeers,
            final Path dataDir,
            final PrivacyParameters privacyParameters) {
      final SynchronizerConfiguration synchronizerConfiguration =
          SynchronizerConfiguration.builder().build();
      final BesuControllerBuilder builder =
          new BesuController.Builder()
              .fromEthNetworkConfig(
                  ethNetworkConfig,
                  synchronizerConfiguration.getSyncMode());
      builder
          .synchronizerConfiguration(synchronizerConfiguration)
          .dataDirectory(dataDir)
          .privacyParameters(privacyParameters)
          .nodeKey(new NodeKey(new KeyPairSecurityModule(KeyPairUtil.loadKeyPair(dataDir))))
          .metricsSystem(metricsSystem)
          .transactionPoolConfiguration(txPoolConfig)
          .dataStorageConfiguration(DataStorageConfiguration.DEFAULT_FOREST_CONFIG)
          .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
          .clock(Clock.systemUTC())
          .isRevertReasonEnabled(node.isRevertReasonEnabled())
          .storageProvider(storageProvider)
          .gasLimitCalculator(GasLimitCalculator.constant())
          .evmConfiguration(EvmConfiguration.DEFAULT)
          .maxPeers(maxPeers)
          .maxRemotelyInitiatedPeers(15)
          .networkConfiguration(node.getNetworkingConfiguration())
          .randomPeerPriority(false)
          .besuComponent(null);
      return builder.build();
    }

    @Provides
    public EthNetworkConfig provideEthNetworkConfig(
        final BesuNode node, final NetworkName network, final List<EnodeURL> bootnodes) {
      final EthNetworkConfig.Builder networkConfigBuilder =
          new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(network))
              .setBootNodes(bootnodes);
      node.getConfiguration().getGenesisConfig().ifPresent(s -> networkConfigBuilder.setGenesisConfigFile(GenesisConfigFile.fromResource(s)));
      final EthNetworkConfig ethNetworkConfig = networkConfigBuilder.build();
      return ethNetworkConfig;
    }

    @Provides
    @Inject
    public BesuPluginContextImpl providePluginContext(
        final BesuNode node,
        final StorageServiceImpl storageService,
        final SecurityModuleServiceImpl securityModuleService,
        final TransactionSimulationServiceImpl transactionSimulationServiceImpl,
        final TransactionSelectionServiceImpl transactionSelectionServiceImpl,
        final TransactionPoolValidatorServiceImpl transactionPoolValidatorServiceImpl,
        final BlockchainServiceImpl blockchainServiceImpl,
        final RpcEndpointServiceImpl rpcEndpointServiceImpl,
        final BesuConfiguration commonPluginConfiguration,
        final PermissioningServiceImpl permissioningService) {
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

      final Path pluginsPath;
      final String pluginDir = System.getProperty("besu.plugins.dir");
      if (pluginDir == null || pluginDir.isEmpty()) {
        pluginsPath = node.homeDirectory().resolve("plugins");
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

      besuPluginContext.registerPlugins(pluginsPath);
      commandLine.parseArgs(node.getConfiguration().getExtraCLIOptions().toArray(new String[0]));

      // register built-in plugins
      new RocksDBPlugin().register(besuPluginContext);
      return besuPluginContext;
    }

    @Provides
    public KeyValueStorageProvider provideKeyValueStorageProvider(
        final BesuConfiguration commonPluginConfiguration, final MetricsSystem metricsSystem) {
      final KeyValueStorageProvider storageProvider =
          new KeyValueStorageProviderBuilder()
              .withStorageFactory(storageService.getByName("rocksdb").get())
              .withCommonConfiguration(commonPluginConfiguration)
              .withMetricsSystem(metricsSystem)
              .build();
      return storageProvider;
    }

    @Provides
    public MiningParameters provideMiningParameters(
        final BesuNode node,
        final Path dataDir,
        final TransactionSelectionServiceImpl transactionSelectionServiceImpl) {
      final var miningParameters =
          ImmutableMiningParameters.builder()
              .from(node.getMiningParameters())
              .transactionSelectionService(transactionSelectionServiceImpl)
              .build();

      return miningParameters;
    }

    @Provides
    @Inject
    BesuConfiguration provideBesuConfiguration(
        final BesuConfigurationImpl commonPluginConfiguration,
        final DataStorageConfiguration storageConfiguration,
        final Path dataDir) {

      commonPluginConfiguration.init(
          dataDir, dataDir.resolve(DATABASE_PATH), storageConfiguration);
      return commonPluginConfiguration;
    }

    @Provides
    TransactionPoolConfiguration provideTransactionPoolConfiguration(
        final BesuNode node,
        final TransactionPoolValidatorServiceImpl transactionPoolValidatorServiceImpl) {
      return ImmutableTransactionPoolConfiguration.builder()
          .from(node.getTransactionPoolConfiguration())
          .strictTransactionReplayProtectionEnabled(node.isStrictTxReplayProtectionEnabled())
          .transactionPoolValidatorService(transactionPoolValidatorServiceImpl)
          .build();
    }
  }

  @Module
  static class MockBesuCommandModule {

    @Provides
    BesuCommand provideBesuCommand(final AcceptanceTestBesuComponent component) {
      final BesuCommand besuCommand =
          new BesuCommand(
              component,
              RlpBlockImporter::new,
              JsonBlockImporter::new,
              RlpBlockExporter::new,
              new RunnerBuilder(),
              new BesuController.Builder(),
              Optional.ofNullable(component.getBesuPluginContext()).orElse(null),
              System.getenv());
      besuCommand.toCommandLine();
      return besuCommand;
    }

    @Provides
    @Singleton
    MetricsConfiguration provideMetricsConfiguration() {
      return MetricsConfiguration.builder().build();
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
        ThreadBesuNodeRunner.RunnerModule.class,
        ThreadBesuNodeRunner.MockBesuCommandModule.class,
        BonsaiCachedMerkleTrieLoaderModule.class,
        MetricsSystemModule.class,
        BesuPluginContextModule.class,
        BlobCacheModule.class
      })
  public interface AcceptanceTestBesuComponent extends BesuComponent {
    BesuController besuController();

    BesuConfiguration besuConfiguration();

    Runner besuRunner();
  }
}

/*
 *  Copyright ConsenSys AG.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.controller;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.common.bft.BftForkSpec;
import org.hyperledger.besu.consensus.common.bft.BftForksSchedule;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftMiningCoordinator;
import org.hyperledger.besu.consensus.common.forking.ForkingBftMiningCoordinator;
import org.hyperledger.besu.consensus.common.forking.ForkingProtocolManager;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MutableProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ForkingControllerBuilder extends BesuControllerBuilder {

  private final List<BftBesuControllerBuilder> besuControllerBuilders;
  private Blockchain blockchain;

  private static class ForkingContext {

    private final Map<BftBesuControllerBuilder, Object> contexts;

    public ForkingContext(final Map<BftBesuControllerBuilder, Object> contexts) {
      this.contexts = contexts;
    }

    public Object getContext(final BftBesuControllerBuilder builder) {
      return contexts.get(builder);
    }
  }

  public ForkingControllerBuilder(final List<BftBesuControllerBuilder> besuControllerBuilders) {
    this.besuControllerBuilders = besuControllerBuilders;
  }

  private ProtocolContext createProtocolContext(
      final ProtocolContext protocolContext,
      final ForkingContext forkingContext,
      final BftBesuControllerBuilder builder) {
    return new ProtocolContext(
        protocolContext.getBlockchain(),
        protocolContext.getWorldStateArchive(),
        forkingContext.getContext(builder));
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    final ForkingContext forkingContext = protocolContext.getConsensusState(ForkingContext.class);
    final List<BftForkSpec<BftMiningCoordinator>> miningCoordinatorForks =
        besuControllerBuilders.stream()
            .map(
                builder ->
                    new BftForkSpec<>(
                        builder.bftConfigOptions().getBlock(),
                        builder.createMiningCoordinator(
                            protocolSchedule,
                            createProtocolContext(protocolContext, forkingContext, builder),
                            transactionPool,
                            miningParameters,
                            syncState,
                            ethProtocolManager)))
            .collect(Collectors.toList());
    final BftForkSpec<BftMiningCoordinator> genesisFork = miningCoordinatorForks.get(0);
    final List<BftForkSpec<BftMiningCoordinator>> forks =
        miningCoordinatorForks.subList(1, miningCoordinatorForks.size());
    final BftForksSchedule<BftMiningCoordinator> forksSchedule =
        new BftForksSchedule<BftMiningCoordinator>(genesisFork, forks);

    final ForkingBftMiningCoordinator forkingBftMiningCoordinator =
        new ForkingBftMiningCoordinator(forksSchedule, blockchain.getChainHeadBlockNumber());
    blockchain.observeBlockAdded(forkingBftMiningCoordinator);
    return forkingBftMiningCoordinator;
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    final MutableProtocolSchedule combinedProtocolSchedule =
        new MutableProtocolSchedule(genesisConfig.getConfigOptions().getChainId());

    for (BftBesuControllerBuilder builder : besuControllerBuilders) {
      final MutableProtocolSchedule protocolSchedule =
          (MutableProtocolSchedule) builder.createProtocolSchedule();
      final long migrationBlock = builder.bftConfigOptions().getBlock();
      for (ScheduledProtocolSpec scheduledProtocolSpec :
          protocolSchedule.getScheduledProtocolSpecs()) {
        final long milestoneBlock = migrationBlock + scheduledProtocolSpec.getBlock();
        combinedProtocolSchedule.putMilestone(milestoneBlock, scheduledProtocolSpec.getSpec());
      }
    }

    return combinedProtocolSchedule;
  }

  @Override
  protected Object createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    final Map<BftBesuControllerBuilder, Object> contexts =
        besuControllerBuilders.stream()
            .collect(
                Collectors.toMap(
                    k -> k,
                    builder ->
                        builder.createConsensusContext(
                            blockchain, worldStateArchive, protocolSchedule)));
    this.blockchain = blockchain;
    return new ForkingContext(contexts);
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    final ForkingContext forkingContext = protocolContext.getConsensusState(ForkingContext.class);
    final List<PluginServiceFactory> pluginServices =
        besuControllerBuilders.stream()
            .map(
                builder ->
                    builder.createAdditionalPluginServices(
                        blockchain,
                        createProtocolContext(protocolContext, forkingContext, builder)))
            .collect(Collectors.toList());
    return pluginServices.get(0);
  }

  @Override
  protected void prepForBuild() {
    besuControllerBuilders.forEach(BesuControllerBuilder::prepForBuild);
    super.prepForBuild();
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {
    final ForkingContext forkingContext = protocolContext.getConsensusState(ForkingContext.class);
    besuControllerBuilders.forEach(
        builder ->
            builder.createAdditionalJsonRpcMethodFactory(
                createProtocolContext(protocolContext, forkingContext, builder)));
    return super.createAdditionalJsonRpcMethodFactory(protocolContext);
  }

  @Override
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager) {
    final List<BftForkSpec<SubProtocolConfiguration>> subProtocolSpecs =
        besuControllerBuilders.stream()
            .map(
                builder ->
                    new BftForkSpec<>(
                        builder.bftConfigOptions().getBlock(),
                        builder.createSubProtocolConfiguration(ethProtocolManager)))
            .collect(Collectors.toList());

    final List<BftForkSpec<ProtocolManager>> protocolManagerSpecs =
        subProtocolSpecs.stream()
            .flatMap(
                subProtocolSpec ->
                    subProtocolSpec.getConfigOptions().getProtocolManagers().stream()
                        .map(
                            protocolManager ->
                                new BftForkSpec<>(subProtocolSpec.getBlock(), protocolManager)))
            .collect(Collectors.toList());

    final List<SubProtocolConfiguration> collect =
        subProtocolSpecs.stream()
            .flatMap(
                subProtocolSpec -> {
                  final BftForkSpec<ProtocolManager> genesisFork = protocolManagerSpecs.get(0);
                  final List<BftForkSpec<ProtocolManager>> forks =
                      protocolManagerSpecs.subList(1, protocolManagerSpecs.size());

                  return subProtocolSpec.getConfigOptions().getSubProtocols().stream()
                      .map(
                          subProtocol -> {
                            final BftForksSchedule<ProtocolManager>
                                protocolManagerBftForksSchedule =
                                    new BftForksSchedule<>(genesisFork, forks);
                            final ForkingProtocolManager forkingProtocolManager =
                                new ForkingProtocolManager(
                                    protocolManagerBftForksSchedule,
                                    blockchain.getChainHeadBlockNumber());

                            return new SubProtocolConfiguration()
                                .withSubProtocol(subProtocol, forkingProtocolManager);
                          });
                })
            .collect(Collectors.toList());

    return unifiedSubProtocolConfiguration(collect);
  }

  private SubProtocolConfiguration unifiedSubProtocolConfiguration(
      final List<SubProtocolConfiguration> subProtocolConfigurations) {
    final SubProtocolConfiguration unifiedSubProtocolConfiguration = new SubProtocolConfiguration();
    for (SubProtocolConfiguration subProtocolConfiguration : subProtocolConfigurations) {
      for (int i = 0; i < subProtocolConfiguration.getSubProtocols().size(); i++) {
        final SubProtocol subProtocol = subProtocolConfiguration.getSubProtocols().get(i);
        final ProtocolManager protocolManager =
            subProtocolConfiguration.getProtocolManagers().get(i);
        unifiedSubProtocolConfiguration.withSubProtocol(subProtocol, protocolManager);
      }
    }
    return unifiedSubProtocolConfiguration;
  }

  @Override
  protected String getSupportedProtocol() {
    besuControllerBuilders.forEach(BesuControllerBuilder::getSupportedProtocol);
    return super.getSupportedProtocol();
  }

  @Override
  protected EthProtocolManager createEthProtocolManager(
      final ProtocolContext protocolContext,
      final boolean fastSyncEnabled,
      final TransactionPool transactionPool,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration,
      final EthPeers ethPeers,
      final EthContext ethContext,
      final EthMessages ethMessages,
      final EthScheduler scheduler,
      final List<PeerValidator> peerValidators) {
    final ForkingContext forkingContext = protocolContext.getConsensusState(ForkingContext.class);
    final List<BftBesuControllerBuilder> besuControllerBuilders = this.besuControllerBuilders;
    besuControllerBuilders.forEach(
        builder ->
            builder.createEthProtocolManager(
                createProtocolContext(protocolContext, forkingContext, builder),
                fastSyncEnabled,
                transactionPool,
                ethereumWireProtocolConfiguration,
                ethPeers,
                ethContext,
                ethMessages,
                scheduler,
                peerValidators));
    return super.createEthProtocolManager(
        protocolContext,
        fastSyncEnabled,
        transactionPool,
        ethereumWireProtocolConfiguration,
        ethPeers,
        ethContext,
        ethMessages,
        scheduler,
        peerValidators);
  }

  @Override
  protected void validateContext(final ProtocolContext protocolContext) {
    final ForkingContext forkingContext = protocolContext.getConsensusState(ForkingContext.class);
    besuControllerBuilders.forEach(
        builder ->
            builder.validateContext(
                createProtocolContext(protocolContext, forkingContext, builder)));
    super.validateContext(protocolContext);
  }

  // Initialise delegate BesuControllerBuilders values

  @Override
  public BesuControllerBuilder privacyParameters(final PrivacyParameters privacyParameters) {
    besuControllerBuilders.forEach(builder -> builder.privacyParameters(privacyParameters));
    return super.privacyParameters(privacyParameters);
  }

  @Override
  public BesuControllerBuilder storageProvider(final StorageProvider storageProvider) {
    besuControllerBuilders.forEach(builder -> builder.storageProvider(storageProvider));
    return super.storageProvider(storageProvider);
  }

  @Override
  public BesuControllerBuilder genesisConfigFile(final GenesisConfigFile genesisConfig) {
    besuControllerBuilders.forEach(builder -> builder.genesisConfigFile(genesisConfig));
    return super.genesisConfigFile(genesisConfig);
  }

  @Override
  public BesuControllerBuilder synchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfig) {
    besuControllerBuilders.forEach(
        builder -> builder.synchronizerConfiguration(synchronizerConfig));
    return super.synchronizerConfiguration(synchronizerConfig);
  }

  @Override
  public BesuControllerBuilder ethProtocolConfiguration(
      final EthProtocolConfiguration ethProtocolConfiguration) {
    besuControllerBuilders.forEach(
        builder -> builder.ethProtocolConfiguration(ethProtocolConfiguration));
    return super.ethProtocolConfiguration(ethProtocolConfiguration);
  }

  @Override
  public BesuControllerBuilder networkId(final BigInteger networkId) {
    besuControllerBuilders.forEach(builder -> builder.networkId(networkId));
    return super.networkId(networkId);
  }

  @Override
  public BesuControllerBuilder miningParameters(final MiningParameters miningParameters) {
    besuControllerBuilders.forEach(builder -> builder.miningParameters(miningParameters));
    return super.miningParameters(miningParameters);
  }

  @Override
  public BesuControllerBuilder messagePermissioningProviders(
      final List<NodeMessagePermissioningProvider> messagePermissioningProviders) {
    besuControllerBuilders.forEach(
        builder -> builder.messagePermissioningProviders(messagePermissioningProviders));
    return super.messagePermissioningProviders(messagePermissioningProviders);
  }

  @Override
  public BesuControllerBuilder nodeKey(final NodeKey nodeKey) {
    besuControllerBuilders.forEach(builder -> builder.nodeKey(nodeKey));
    return super.nodeKey(nodeKey);
  }

  @Override
  public BesuControllerBuilder metricsSystem(final ObservableMetricsSystem metricsSystem) {
    besuControllerBuilders.forEach(builder -> builder.metricsSystem(metricsSystem));
    return super.metricsSystem(metricsSystem);
  }

  @Override
  public BesuControllerBuilder dataDirectory(final Path dataDirectory) {
    besuControllerBuilders.forEach(builder -> builder.dataDirectory(dataDirectory));
    return super.dataDirectory(dataDirectory);
  }

  @Override
  public BesuControllerBuilder clock(final Clock clock) {
    besuControllerBuilders.forEach(builder -> builder.clock(clock));
    return super.clock(clock);
  }

  @Override
  public BesuControllerBuilder transactionPoolConfiguration(
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    besuControllerBuilders.forEach(
        builder -> builder.transactionPoolConfiguration(transactionPoolConfiguration));
    return super.transactionPoolConfiguration(transactionPoolConfiguration);
  }

  @Override
  public BesuControllerBuilder isRevertReasonEnabled(final boolean isRevertReasonEnabled) {
    besuControllerBuilders.forEach(builder -> builder.isRevertReasonEnabled(isRevertReasonEnabled));
    return super.isRevertReasonEnabled(isRevertReasonEnabled);
  }

  @Override
  public BesuControllerBuilder isPruningEnabled(final boolean isPruningEnabled) {
    besuControllerBuilders.forEach(builder -> builder.isPruningEnabled(isPruningEnabled));
    return super.isPruningEnabled(isPruningEnabled);
  }

  @Override
  public BesuControllerBuilder pruningConfiguration(final PrunerConfiguration prunerConfiguration) {
    besuControllerBuilders.forEach(builder -> builder.pruningConfiguration(prunerConfiguration));
    return super.pruningConfiguration(prunerConfiguration);
  }

  @Override
  public BesuControllerBuilder genesisConfigOverrides(
      final Map<String, String> genesisConfigOverrides) {
    besuControllerBuilders.forEach(
        builder -> builder.genesisConfigOverrides(genesisConfigOverrides));
    return super.genesisConfigOverrides(genesisConfigOverrides);
  }

  @Override
  public BesuControllerBuilder gasLimitCalculator(final GasLimitCalculator gasLimitCalculator) {
    besuControllerBuilders.forEach(builder -> builder.gasLimitCalculator(gasLimitCalculator));
    return super.gasLimitCalculator(gasLimitCalculator);
  }

  @Override
  public BesuControllerBuilder requiredBlocks(final Map<Long, Hash> requiredBlocks) {
    besuControllerBuilders.forEach(builder -> builder.requiredBlocks(requiredBlocks));
    return super.requiredBlocks(requiredBlocks);
  }

  @Override
  public BesuControllerBuilder reorgLoggingThreshold(final long reorgLoggingThreshold) {
    besuControllerBuilders.forEach(builder -> builder.reorgLoggingThreshold(reorgLoggingThreshold));
    return super.reorgLoggingThreshold(reorgLoggingThreshold);
  }

  @Override
  public BesuControllerBuilder dataStorageConfiguration(
      final DataStorageConfiguration dataStorageConfiguration) {
    besuControllerBuilders.forEach(
        builder -> builder.dataStorageConfiguration(dataStorageConfiguration));
    return super.dataStorageConfiguration(dataStorageConfiguration);
  }

  @Override
  public BesuControllerBuilder evmConfiguration(final EvmConfiguration evmConfiguration) {
    besuControllerBuilders.forEach(builder -> builder.evmConfiguration(evmConfiguration));
    return super.evmConfiguration(evmConfiguration);
  }
}

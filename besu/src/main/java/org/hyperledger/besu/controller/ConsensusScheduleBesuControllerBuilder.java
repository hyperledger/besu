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
package org.hyperledger.besu.controller;

import static org.hyperledger.besu.ethereum.core.BlockHeader.GENESIS_BLOCK_NUMBER;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.consensus.common.CombinedProtocolScheduleFactory;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.MigratingConsensusContext;
import org.hyperledger.besu.consensus.common.MigratingMiningCoordinator;
import org.hyperledger.besu.consensus.common.MigratingProtocolContext;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.MergePeerFilter;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.forkid.ForkIdManager;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * This is a placeholder class for the QBFT migration logic. For now, all it does is to delegate any
 * BesuControllerBuilder to the first controller in the list.
 */
public class ConsensusScheduleBesuControllerBuilder extends BesuControllerBuilder {

  private final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule = new HashMap<>();
  private final BiFunction<
          NavigableSet<ForkSpec<ProtocolSchedule>>, Optional<BigInteger>, ProtocolSchedule>
      combinedProtocolScheduleFactory;

  /**
   * Instantiates a new Consensus schedule Besu controller builder.
   *
   * @param besuControllerBuilderSchedule the besu controller builder schedule
   */
  public ConsensusScheduleBesuControllerBuilder(
      final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule) {
    this(
        besuControllerBuilderSchedule,
        (protocolScheduleSpecs, chainId) ->
            new CombinedProtocolScheduleFactory().create(protocolScheduleSpecs, chainId));
  }

  /**
   * Instantiates a new Consensus schedule besu controller builder. Visible for testing.
   *
   * @param besuControllerBuilderSchedule the besu controller builder schedule
   * @param combinedProtocolScheduleFactory the combined protocol schedule factory
   */
  @VisibleForTesting
  protected ConsensusScheduleBesuControllerBuilder(
      final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule,
      final BiFunction<
              NavigableSet<ForkSpec<ProtocolSchedule>>, Optional<BigInteger>, ProtocolSchedule>
          combinedProtocolScheduleFactory) {
    Preconditions.checkNotNull(
        besuControllerBuilderSchedule, "BesuControllerBuilder schedule can't be null");
    Preconditions.checkArgument(
        !besuControllerBuilderSchedule.isEmpty(), "BesuControllerBuilder schedule can't be empty");
    this.besuControllerBuilderSchedule.putAll(besuControllerBuilderSchedule);
    this.combinedProtocolScheduleFactory = combinedProtocolScheduleFactory;
  }

  @Override
  protected void prepForBuild() {
    besuControllerBuilderSchedule.values().forEach(BesuControllerBuilder::prepForBuild);
    super.prepForBuild();
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningConfiguration miningConfiguration,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {

    final List<ForkSpec<MiningCoordinator>> miningCoordinatorForkSpecs =
        besuControllerBuilderSchedule.entrySet().stream()
            .map(
                e ->
                    new ForkSpec<>(
                        e.getKey(),
                        e.getValue()
                            .createMiningCoordinator(
                                protocolSchedule,
                                protocolContext,
                                transactionPool,
                                miningConfiguration,
                                syncState,
                                ethProtocolManager)))
            .collect(Collectors.toList());
    final ForksSchedule<MiningCoordinator> miningCoordinatorSchedule =
        new ForksSchedule<>(miningCoordinatorForkSpecs);

    return new MigratingMiningCoordinator(
        miningCoordinatorSchedule, protocolContext.getBlockchain());
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    final NavigableSet<ForkSpec<ProtocolSchedule>> protocolScheduleSpecs =
        besuControllerBuilderSchedule.entrySet().stream()
            .map(e -> new ForkSpec<>(e.getKey(), e.getValue().createProtocolSchedule()))
            .collect(Collectors.toCollection(() -> new TreeSet<>(ForkSpec.COMPARATOR)));
    final Optional<BigInteger> chainId = genesisConfigOptions.getChainId();
    return combinedProtocolScheduleFactory.apply(protocolScheduleSpecs, chainId);
  }

  @Override
  protected ProtocolContext createProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ConsensusContext consensusContext) {
    return new MigratingProtocolContext(
        blockchain,
        worldStateArchive,
        consensusContext.as(MigratingConsensusContext.class),
        badBlockManager);
  }

  @Override
  protected ConsensusContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    final List<ForkSpec<ConsensusContext>> consensusContextSpecs =
        besuControllerBuilderSchedule.entrySet().stream()
            .map(
                e ->
                    new ForkSpec<>(
                        e.getKey(),
                        e.getValue()
                            .createConsensusContext(
                                blockchain, worldStateArchive, protocolSchedule)))
            .toList();
    final ForksSchedule<ConsensusContext> consensusContextsSchedule =
        new ForksSchedule<>(consensusContextSpecs);
    return new MigratingConsensusContext(consensusContextsSchedule);
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return besuControllerBuilderSchedule
        .get(0L)
        .createAdditionalPluginServices(blockchain, protocolContext);
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MiningConfiguration miningConfiguration) {
    return besuControllerBuilderSchedule
        .get(0L)
        .createAdditionalJsonRpcMethodFactory(
            protocolContext, protocolSchedule, miningConfiguration);
  }

  @Override
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager,
      final Optional<SnapProtocolManager> maybeSnapProtocolManager) {
    return besuControllerBuilderSchedule
        .get(0L)
        .createSubProtocolConfiguration(ethProtocolManager, maybeSnapProtocolManager);
  }

  @Override
  protected void validateContext(final ProtocolContext context) {
    besuControllerBuilderSchedule.get(GENESIS_BLOCK_NUMBER).validateContext(context);
  }

  @Override
  protected EthProtocolManager createEthProtocolManager(
      final ProtocolContext protocolContext,
      final SynchronizerConfiguration synchronizerConfiguration,
      final TransactionPool transactionPool,
      final EthProtocolConfiguration ethereumWireProtocolConfiguration,
      final EthPeers ethPeers,
      final EthContext ethContext,
      final EthMessages ethMessages,
      final EthScheduler scheduler,
      final List<PeerValidator> peerValidators,
      final Optional<MergePeerFilter> mergePeerFilter,
      final ForkIdManager forkIdManager) {
    return besuControllerBuilderSchedule
        .get(0L)
        .createEthProtocolManager(
            protocolContext,
            synchronizerConfiguration,
            transactionPool,
            ethereumWireProtocolConfiguration,
            ethPeers,
            ethContext,
            ethMessages,
            scheduler,
            peerValidators,
            mergePeerFilter,
            forkIdManager);
  }

  @Override
  public BesuControllerBuilder storageProvider(final StorageProvider storageProvider) {
    besuControllerBuilderSchedule.values().forEach(b -> b.storageProvider(storageProvider));
    return super.storageProvider(storageProvider);
  }

  @Override
  public BesuControllerBuilder genesisConfig(final GenesisConfig genesisConfig) {
    besuControllerBuilderSchedule.values().forEach(b -> b.genesisConfig(genesisConfig));
    return super.genesisConfig(genesisConfig);
  }

  @Override
  public BesuControllerBuilder synchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfig) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.synchronizerConfiguration(synchronizerConfig));
    return super.synchronizerConfiguration(synchronizerConfig);
  }

  @Override
  public BesuControllerBuilder ethProtocolConfiguration(
      final EthProtocolConfiguration ethProtocolConfiguration) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.ethProtocolConfiguration(ethProtocolConfiguration));
    return super.ethProtocolConfiguration(ethProtocolConfiguration);
  }

  @Override
  public BesuControllerBuilder networkId(final BigInteger networkId) {
    besuControllerBuilderSchedule.values().forEach(b -> b.networkId(networkId));
    return super.networkId(networkId);
  }

  @Override
  public BesuControllerBuilder miningParameters(final MiningConfiguration miningConfiguration) {
    besuControllerBuilderSchedule.values().forEach(b -> b.miningParameters(miningConfiguration));
    return super.miningParameters(miningConfiguration);
  }

  @Override
  public BesuControllerBuilder messagePermissioningProviders(
      final List<NodeMessagePermissioningProvider> messagePermissioningProviders) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.messagePermissioningProviders(messagePermissioningProviders));
    return super.messagePermissioningProviders(messagePermissioningProviders);
  }

  @Override
  public BesuControllerBuilder nodeKey(final NodeKey nodeKey) {
    besuControllerBuilderSchedule.values().forEach(b -> b.nodeKey(nodeKey));
    return super.nodeKey(nodeKey);
  }

  @Override
  public BesuControllerBuilder metricsSystem(final ObservableMetricsSystem metricsSystem) {
    besuControllerBuilderSchedule.values().forEach(b -> b.metricsSystem(metricsSystem));
    return super.metricsSystem(metricsSystem);
  }

  @Override
  public BesuControllerBuilder privacyParameters(final PrivacyParameters privacyParameters) {
    besuControllerBuilderSchedule.values().forEach(b -> b.privacyParameters(privacyParameters));
    return super.privacyParameters(privacyParameters);
  }

  @Override
  public BesuControllerBuilder dataDirectory(final Path dataDirectory) {
    besuControllerBuilderSchedule.values().forEach(b -> b.dataDirectory(dataDirectory));
    return super.dataDirectory(dataDirectory);
  }

  @Override
  public BesuControllerBuilder clock(final Clock clock) {
    besuControllerBuilderSchedule.values().forEach(b -> b.clock(clock));
    return super.clock(clock);
  }

  @Override
  public BesuControllerBuilder transactionPoolConfiguration(
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.transactionPoolConfiguration(transactionPoolConfiguration));
    return super.transactionPoolConfiguration(transactionPoolConfiguration);
  }

  @Override
  public BesuControllerBuilder isRevertReasonEnabled(final boolean isRevertReasonEnabled) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.isRevertReasonEnabled(isRevertReasonEnabled));
    return super.isRevertReasonEnabled(isRevertReasonEnabled);
  }

  @Override
  public BesuControllerBuilder isParallelTxProcessingEnabled(
      final boolean isParallelTxProcessingEnabled) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.isParallelTxProcessingEnabled(isParallelTxProcessingEnabled));
    return super.isParallelTxProcessingEnabled(isParallelTxProcessingEnabled);
  }

  @Override
  public BesuControllerBuilder gasLimitCalculator(final GasLimitCalculator gasLimitCalculator) {
    besuControllerBuilderSchedule.values().forEach(b -> b.gasLimitCalculator(gasLimitCalculator));
    return super.gasLimitCalculator(gasLimitCalculator);
  }

  @Override
  public BesuControllerBuilder requiredBlocks(final Map<Long, Hash> requiredBlocks) {
    besuControllerBuilderSchedule.values().forEach(b -> b.requiredBlocks(requiredBlocks));
    return super.requiredBlocks(requiredBlocks);
  }

  @Override
  public BesuControllerBuilder reorgLoggingThreshold(final long reorgLoggingThreshold) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.reorgLoggingThreshold(reorgLoggingThreshold));
    return super.reorgLoggingThreshold(reorgLoggingThreshold);
  }

  @Override
  public BesuControllerBuilder dataStorageConfiguration(
      final DataStorageConfiguration dataStorageConfiguration) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.dataStorageConfiguration(dataStorageConfiguration));
    return super.dataStorageConfiguration(dataStorageConfiguration);
  }

  @Override
  public BesuControllerBuilder evmConfiguration(final EvmConfiguration evmConfiguration) {
    besuControllerBuilderSchedule.values().forEach(b -> b.evmConfiguration(evmConfiguration));
    return super.evmConfiguration(evmConfiguration);
  }

  /**
   * Gets besu controller builder schedule. Visible for testing.
   *
   * @return the Besu controller builder schedule
   */
  @VisibleForTesting
  Map<Long, BesuControllerBuilder> getBesuControllerBuilderSchedule() {
    return besuControllerBuilderSchedule;
  }
}

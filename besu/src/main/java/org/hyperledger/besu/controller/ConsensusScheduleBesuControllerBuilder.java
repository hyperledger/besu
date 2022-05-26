/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.controller;

import static org.hyperledger.besu.ethereum.core.BlockHeader.GENESIS_BLOCK_NUMBER;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.consensus.common.CombinedProtocolScheduleFactory;
import org.hyperledger.besu.consensus.common.ForkSpec;
import org.hyperledger.besu.consensus.common.ForksSchedule;
import org.hyperledger.besu.consensus.common.MigratingContext;
import org.hyperledger.besu.consensus.common.MigratingMiningCoordinator;
import org.hyperledger.besu.consensus.common.MigratingProtocolContext;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfiguration;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ConsensusContextFactory;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
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

/*
 This is a placeholder class for the QBFT migration logic. For now, all it does is to delegate any
 BesuControllerBuilder to the first controller in the list.
*/
public class ConsensusScheduleBesuControllerBuilder extends BesuControllerBuilder {

  private final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule = new HashMap<>();
  private final BiFunction<
          NavigableSet<ForkSpec<ProtocolSchedule>>, Optional<BigInteger>, ProtocolSchedule>
      combinedProtocolScheduleFactory;

  public ConsensusScheduleBesuControllerBuilder(
      final Map<Long, BesuControllerBuilder> besuControllerBuilderSchedule) {
    this(
        besuControllerBuilderSchedule,
        (protocolScheduleSpecs, chainId) ->
            new CombinedProtocolScheduleFactory().create(protocolScheduleSpecs, chainId));
  }

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
      final MiningParameters miningParameters,
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
                                miningParameters,
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
    final Optional<BigInteger> chainId = configOptionsSupplier.get().getChainId();
    return combinedProtocolScheduleFactory.apply(protocolScheduleSpecs, chainId);
  }

  @Override
  protected ProtocolContext createProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final ConsensusContextFactory consensusContextFactory) {
    return MigratingProtocolContext.init(
        blockchain, worldStateArchive, protocolSchedule, consensusContextFactory);
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
            .collect(Collectors.toList());
    final ForksSchedule<ConsensusContext> consensusContextsSchedule =
        new ForksSchedule<>(consensusContextSpecs);
    return new MigratingContext(consensusContextsSchedule);
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
      final ProtocolContext protocolContext) {
    return besuControllerBuilderSchedule
        .get(0L)
        .createAdditionalJsonRpcMethodFactory(protocolContext);
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
  protected String getSupportedProtocol() {
    return besuControllerBuilderSchedule.get(0L).getSupportedProtocol();
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
    return besuControllerBuilderSchedule
        .get(0L)
        .createEthProtocolManager(
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
  public BesuControllerBuilder storageProvider(final StorageProvider storageProvider) {
    besuControllerBuilderSchedule.values().forEach(b -> b.storageProvider(storageProvider));
    return super.storageProvider(storageProvider);
  }

  @Override
  public BesuControllerBuilder genesisConfigFile(final GenesisConfigFile genesisConfig) {
    besuControllerBuilderSchedule.values().forEach(b -> b.genesisConfigFile(genesisConfig));
    return super.genesisConfigFile(genesisConfig);
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
  public BesuControllerBuilder miningParameters(final MiningParameters miningParameters) {
    besuControllerBuilderSchedule.values().forEach(b -> b.miningParameters(miningParameters));
    return super.miningParameters(miningParameters);
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
  public BesuControllerBuilder pkiBlockCreationConfiguration(
      final Optional<PkiBlockCreationConfiguration> pkiBlockCreationConfiguration) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.pkiBlockCreationConfiguration(pkiBlockCreationConfiguration));
    return super.pkiBlockCreationConfiguration(pkiBlockCreationConfiguration);
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
  public BesuControllerBuilder isPruningEnabled(final boolean isPruningEnabled) {
    besuControllerBuilderSchedule.values().forEach(b -> b.isPruningEnabled(isPruningEnabled));
    return super.isPruningEnabled(isPruningEnabled);
  }

  @Override
  public BesuControllerBuilder pruningConfiguration(final PrunerConfiguration prunerConfiguration) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.pruningConfiguration(prunerConfiguration));
    return super.pruningConfiguration(prunerConfiguration);
  }

  @Override
  public BesuControllerBuilder genesisConfigOverrides(
      final Map<String, String> genesisConfigOverrides) {
    besuControllerBuilderSchedule
        .values()
        .forEach(b -> b.genesisConfigOverrides(genesisConfigOverrides));
    return super.genesisConfigOverrides(genesisConfigOverrides);
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

  @VisibleForTesting
  Map<Long, BesuControllerBuilder> getBesuControllerBuilderSchedule() {
    return besuControllerBuilderSchedule;
  }
}

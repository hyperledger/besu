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

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.QbftConfigOptions;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfiguration;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ConsensusContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 This is a placeholder class for the QBFT migration logic. For now, all it does is to delegate any
 BesuControllerBuilder to the first controller in the list.
*/
public class SwitchableBesuControllerBuilder extends BesuControllerBuilder {

  private static final Logger LOG = LogManager.getLogger();

  private final List<BesuControllerBuilder> besuControllerBuilders = new ArrayList<>();

  private Long qbftStartBlock;

  public SwitchableBesuControllerBuilder(final List<BesuControllerBuilder> besuControllerBuilders) {
    Preconditions.checkNotNull(besuControllerBuilders);
    Preconditions.checkArgument(!besuControllerBuilders.isEmpty());
    this.besuControllerBuilders.addAll(besuControllerBuilders);
  }

  @Override
  protected void prepForBuild() {
    final QbftConfigOptions qbftConfigOptions =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getQbftConfigOptions();

    // TODO-lucas better validation/error handling
    qbftStartBlock = qbftConfigOptions.getStartBlock().orElseThrow();
    if (qbftStartBlock <= 0) {
      throw new IllegalStateException("Invalid QBFT startBlock");
    }

    LOG.info(">>> Preparing for build: QBFT startBlock is {}", qbftStartBlock);

    besuControllerBuilders.get(0).prepForBuild();
  }

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    return besuControllerBuilders
        .get(0)
        .createMiningCoordinator(
            protocolSchedule,
            protocolContext,
            transactionPool,
            miningParameters,
            syncState,
            ethProtocolManager);
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return besuControllerBuilders.get(0).createProtocolSchedule();
  }

  @Override
  protected ConsensusContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    return besuControllerBuilders
        .get(0)
        .createConsensusContext(blockchain, worldStateArchive, protocolSchedule);
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return besuControllerBuilders
        .get(0)
        .createAdditionalPluginServices(blockchain, protocolContext);
  }

  @Override
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {
    return besuControllerBuilders.get(0).createAdditionalJsonRpcMethodFactory(protocolContext);
  }

  @Override
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager) {
    return besuControllerBuilders.get(0).createSubProtocolConfiguration(ethProtocolManager);
  }

  @Override
  protected void validateContext(final ProtocolContext context) {
    besuControllerBuilders.get(0).validateContext(context);
  }

  @Override
  protected String getSupportedProtocol() {
    return besuControllerBuilders.get(0).getSupportedProtocol();
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
    return besuControllerBuilders
        .get(0)
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
    besuControllerBuilders.get(0).storageProvider(storageProvider);
    return super.storageProvider(storageProvider);
  }

  @Override
  public BesuControllerBuilder genesisConfigFile(final GenesisConfigFile genesisConfig) {
    besuControllerBuilders.get(0).genesisConfigFile(genesisConfig);
    besuControllerBuilders.get(0).genesisConfigFile(genesisConfig);
    return super.genesisConfigFile(genesisConfig);
  }

  @Override
  public BesuControllerBuilder synchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfig) {
    besuControllerBuilders.get(0).synchronizerConfiguration(synchronizerConfig);
    return super.synchronizerConfiguration(synchronizerConfig);
  }

  @Override
  public BesuControllerBuilder ethProtocolConfiguration(
      final EthProtocolConfiguration ethProtocolConfiguration) {
    besuControllerBuilders.get(0).ethProtocolConfiguration(ethProtocolConfiguration);
    return super.ethProtocolConfiguration(ethProtocolConfiguration);
  }

  @Override
  public BesuControllerBuilder networkId(final BigInteger networkId) {
    besuControllerBuilders.get(0).networkId(networkId);
    return super.networkId(networkId);
  }

  @Override
  public BesuControllerBuilder miningParameters(final MiningParameters miningParameters) {
    besuControllerBuilders.get(0).miningParameters(miningParameters);
    return super.miningParameters(miningParameters);
  }

  @Override
  public BesuControllerBuilder messagePermissioningProviders(
      final List<NodeMessagePermissioningProvider> messagePermissioningProviders) {
    besuControllerBuilders.get(0).messagePermissioningProviders(messagePermissioningProviders);
    return super.messagePermissioningProviders(messagePermissioningProviders);
  }

  @Override
  public BesuControllerBuilder nodeKey(final NodeKey nodeKey) {
    besuControllerBuilders.get(0).nodeKey(nodeKey);
    return super.nodeKey(nodeKey);
  }

  @Override
  public BesuControllerBuilder metricsSystem(final ObservableMetricsSystem metricsSystem) {
    besuControllerBuilders.get(0).metricsSystem(metricsSystem);
    return super.metricsSystem(metricsSystem);
  }

  @Override
  public BesuControllerBuilder privacyParameters(final PrivacyParameters privacyParameters) {
    besuControllerBuilders.get(0).privacyParameters(privacyParameters);
    return super.privacyParameters(privacyParameters);
  }

  @Override
  public BesuControllerBuilder pkiBlockCreationConfiguration(
      final Optional<PkiBlockCreationConfiguration> pkiBlockCreationConfiguration) {
    besuControllerBuilders.get(0).pkiBlockCreationConfiguration(pkiBlockCreationConfiguration);
    return super.pkiBlockCreationConfiguration(pkiBlockCreationConfiguration);
  }

  @Override
  public BesuControllerBuilder dataDirectory(final Path dataDirectory) {
    besuControllerBuilders.get(0).dataDirectory(dataDirectory);
    return super.dataDirectory(dataDirectory);
  }

  @Override
  public BesuControllerBuilder clock(final Clock clock) {
    besuControllerBuilders.get(0).clock(clock);
    return super.clock(clock);
  }

  @Override
  public BesuControllerBuilder transactionPoolConfiguration(
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    besuControllerBuilders.get(0).transactionPoolConfiguration(transactionPoolConfiguration);
    return super.transactionPoolConfiguration(transactionPoolConfiguration);
  }

  @Override
  public BesuControllerBuilder isRevertReasonEnabled(final boolean isRevertReasonEnabled) {
    besuControllerBuilders.get(0).isRevertReasonEnabled(isRevertReasonEnabled);
    return super.isRevertReasonEnabled(isRevertReasonEnabled);
  }

  @Override
  public BesuControllerBuilder isPruningEnabled(final boolean isPruningEnabled) {
    besuControllerBuilders.get(0).isPruningEnabled(isPruningEnabled);
    return super.isPruningEnabled(isPruningEnabled);
  }

  @Override
  public BesuControllerBuilder pruningConfiguration(final PrunerConfiguration prunerConfiguration) {
    besuControllerBuilders.get(0).pruningConfiguration(prunerConfiguration);
    return super.pruningConfiguration(prunerConfiguration);
  }

  @Override
  public BesuControllerBuilder genesisConfigOverrides(
      final Map<String, String> genesisConfigOverrides) {
    besuControllerBuilders.get(0).genesisConfigOverrides(genesisConfigOverrides);
    return super.genesisConfigOverrides(genesisConfigOverrides);
  }

  @Override
  public BesuControllerBuilder gasLimitCalculator(final GasLimitCalculator gasLimitCalculator) {
    besuControllerBuilders.get(0).gasLimitCalculator(gasLimitCalculator);
    return super.gasLimitCalculator(gasLimitCalculator);
  }

  @Override
  public BesuControllerBuilder requiredBlocks(final Map<Long, Hash> requiredBlocks) {
    besuControllerBuilders.get(0).requiredBlocks(requiredBlocks);
    return super.requiredBlocks(requiredBlocks);
  }

  @Override
  public BesuControllerBuilder reorgLoggingThreshold(final long reorgLoggingThreshold) {
    besuControllerBuilders.get(0).reorgLoggingThreshold(reorgLoggingThreshold);
    return super.reorgLoggingThreshold(reorgLoggingThreshold);
  }

  @Override
  public BesuControllerBuilder dataStorageConfiguration(
      final DataStorageConfiguration dataStorageConfiguration) {
    besuControllerBuilders.get(0).dataStorageConfiguration(dataStorageConfiguration);
    return super.dataStorageConfiguration(dataStorageConfiguration);
  }

  @Override
  public BesuControllerBuilder evmConfiguration(final EvmConfiguration evmConfiguration) {
    besuControllerBuilders.get(0).evmConfiguration(evmConfiguration);
    return super.evmConfiguration(evmConfiguration);
  }
}

/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.controller;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.hyperledger.besu.controller.KeyPairUtil.loadKeyPair;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethodFactory;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.peervalidation.DaoForkPeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidatorRunner;
import org.hyperledger.besu.ethereum.eth.sync.DefaultSynchronizer;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolFactory;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.MarkSweepPruner;
import org.hyperledger.besu.ethereum.worldstate.Pruner;
import org.hyperledger.besu.ethereum.worldstate.PruningConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BesuControllerBuilder<C> {

  private static final Logger LOG = LogManager.getLogger();

  protected GenesisConfigFile genesisConfig;
  protected SynchronizerConfiguration syncConfig;
  protected EthProtocolManager ethProtocolManager;
  protected EthProtocolConfiguration ethereumWireProtocolConfiguration;
  protected TransactionPoolConfiguration transactionPoolConfiguration;
  protected BigInteger networkId;
  protected MiningParameters miningParameters;
  protected ObservableMetricsSystem metricsSystem;
  protected PrivacyParameters privacyParameters;
  protected Path dataDirectory;
  protected Clock clock;
  protected KeyPair nodeKeys;
  protected boolean isRevertReasonEnabled;
  private StorageProvider storageProvider;
  private final List<Runnable> shutdownActions = new ArrayList<>();
  private boolean isPruningEnabled;
  private PruningConfiguration pruningConfiguration;
  Map<String, String> genesisConfigOverrides;

  public BesuControllerBuilder<C> storageProvider(final StorageProvider storageProvider) {
    this.storageProvider = storageProvider;
    return this;
  }

  public BesuControllerBuilder<C> genesisConfigFile(final GenesisConfigFile genesisConfig) {
    this.genesisConfig = genesisConfig;
    return this;
  }

  public BesuControllerBuilder<C> synchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfig) {
    this.syncConfig = synchronizerConfig;
    return this;
  }

  public BesuControllerBuilder<C> ethProtocolConfiguration(
      final EthProtocolConfiguration ethProtocolConfiguration) {
    this.ethereumWireProtocolConfiguration = ethProtocolConfiguration;
    return this;
  }

  public BesuControllerBuilder<C> networkId(final BigInteger networkId) {
    this.networkId = networkId;
    return this;
  }

  public BesuControllerBuilder<C> miningParameters(final MiningParameters miningParameters) {
    this.miningParameters = miningParameters;
    return this;
  }

  public BesuControllerBuilder<C> nodePrivateKeyFile(final File nodePrivateKeyFile)
      throws IOException {
    this.nodeKeys = loadKeyPair(nodePrivateKeyFile);
    return this;
  }

  public BesuControllerBuilder<C> nodeKeys(final KeyPair nodeKeys) {
    this.nodeKeys = nodeKeys;
    return this;
  }

  public BesuControllerBuilder<C> metricsSystem(final ObservableMetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public BesuControllerBuilder<C> privacyParameters(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
    return this;
  }

  public BesuControllerBuilder<C> dataDirectory(final Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    return this;
  }

  public BesuControllerBuilder<C> clock(final Clock clock) {
    this.clock = clock;
    return this;
  }

  public BesuControllerBuilder<C> transactionPoolConfiguration(
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    this.transactionPoolConfiguration = transactionPoolConfiguration;
    return this;
  }

  public BesuControllerBuilder<C> isRevertReasonEnabled(final boolean isRevertReasonEnabled) {
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    return this;
  }

  public BesuControllerBuilder<C> isPruningEnabled(final boolean pruningEnabled) {
    this.isPruningEnabled = pruningEnabled;
    return this;
  }

  public BesuControllerBuilder<C> pruningConfiguration(
      final PruningConfiguration pruningConfiguration) {
    this.pruningConfiguration = pruningConfiguration;
    return this;
  }

  public BesuControllerBuilder<C> genesisConfigOverrides(
      final Map<String, String> genesisConfigOverrides) {
    this.genesisConfigOverrides = genesisConfigOverrides;
    return this;
  }

  public BesuController<C> build() {
    checkNotNull(genesisConfig, "Missing genesis config");
    checkNotNull(syncConfig, "Missing sync config");
    checkNotNull(ethereumWireProtocolConfiguration, "Missing ethereum protocol configuration");
    checkNotNull(networkId, "Missing network ID");
    checkNotNull(miningParameters, "Missing mining parameters");
    checkNotNull(metricsSystem, "Missing metrics system");
    checkNotNull(privacyParameters, "Missing privacy parameters");
    checkNotNull(dataDirectory, "Missing data directory"); // Why do we need this?
    checkNotNull(clock, "Mising clock");
    checkNotNull(transactionPoolConfiguration, "Missing transaction pool configuration");
    checkNotNull(nodeKeys, "Missing node keys");
    checkNotNull(storageProvider, "Must supply a storage provider");

    prepForBuild();

    final ProtocolSchedule<C> protocolSchedule = createProtocolSchedule();
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule);
    final ProtocolContext<C> protocolContext =
        ProtocolContext.init(
            storageProvider,
            genesisState,
            protocolSchedule,
            metricsSystem,
            this::createConsensusContext);
    validateContext(protocolContext);

    final MutableBlockchain blockchain = protocolContext.getBlockchain();

    Optional<Pruner> maybePruner = Optional.empty();
    if (isPruningEnabled) {
      checkState(
          storageProvider.isWorldStateIterable(),
          "Cannot enable pruning with current database version. Resync to get the latest version.");
      maybePruner =
          Optional.of(
              new Pruner(
                  new MarkSweepPruner(
                      protocolContext.getWorldStateArchive().getWorldStateStorage(),
                      blockchain,
                      storageProvider.createPruningStorage(),
                      metricsSystem),
                  blockchain,
                  Executors.newSingleThreadExecutor(
                      new ThreadFactoryBuilder()
                          .setDaemon(true)
                          .setPriority(Thread.MIN_PRIORITY)
                          .setNameFormat("StatePruning-%d")
                          .build()),
                  pruningConfiguration));
    }

    final Optional<Pruner> finalMaybePruner = maybePruner;
    addShutdownAction(
        () ->
            finalMaybePruner.ifPresent(
                pruner -> {
                  try {
                    pruner.stop();
                  } catch (final InterruptedException ie) {
                    throw new RuntimeException(ie);
                  }
                }));

    final boolean fastSyncEnabled = syncConfig.getSyncMode().equals(SyncMode.FAST);
    ethProtocolManager = createEthProtocolManager(protocolContext, fastSyncEnabled);
    final SyncState syncState =
        new SyncState(blockchain, ethProtocolManager.ethContext().getEthPeers());
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            protocolContext.getWorldStateArchive().getWorldStateStorage(),
            ethProtocolManager.getBlockBroadcaster(),
            maybePruner,
            ethProtocolManager.ethContext(),
            syncState,
            dataDirectory,
            clock,
            metricsSystem);

    final OptionalLong daoBlock =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getDaoForkBlock();
    if (daoBlock.isPresent()) {
      // Setup dao validator
      final EthContext ethContext = ethProtocolManager.ethContext();
      final DaoForkPeerValidator daoForkPeerValidator =
          new DaoForkPeerValidator(
              ethContext, protocolSchedule, metricsSystem, daoBlock.getAsLong());
      PeerValidatorRunner.runValidator(ethContext, daoForkPeerValidator);
    }

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            ethProtocolManager.ethContext(),
            clock,
            metricsSystem,
            syncState,
            miningParameters.getMinTransactionGasPrice(),
            transactionPoolConfiguration);

    final MiningCoordinator miningCoordinator =
        createMiningCoordinator(
            protocolSchedule,
            protocolContext,
            transactionPool,
            miningParameters,
            syncState,
            ethProtocolManager);

    final SubProtocolConfiguration subProtocolConfiguration =
        createSubProtocolConfiguration(ethProtocolManager);

    final JsonRpcMethodFactory additionalJsonRpcMethodFactory =
        createAdditionalJsonRpcMethodFactory(protocolContext);
    return new BesuController<>(
        protocolSchedule,
        protocolContext,
        ethProtocolManager,
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        subProtocolConfiguration,
        synchronizer,
        syncState,
        transactionPool,
        miningCoordinator,
        privacyParameters,
        () -> {
          shutdownActions.forEach(Runnable::run);
          try {
            storageProvider.close();
            if (privacyParameters.getPrivateStorageProvider() != null) {
              privacyParameters.getPrivateStorageProvider().close();
            }
          } catch (final IOException e) {
            LOG.error("Failed to close storage provider", e);
          }
        },
        additionalJsonRpcMethodFactory,
        nodeKeys);
  }

  protected void prepForBuild() {}

  protected JsonRpcMethodFactory createAdditionalJsonRpcMethodFactory(
      final ProtocolContext<C> protocolContext) {
    return apis -> Collections.emptyMap();
  }

  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager) {
    return new SubProtocolConfiguration().withSubProtocol(EthProtocol.get(), ethProtocolManager);
  }

  protected final void addShutdownAction(final Runnable action) {
    shutdownActions.add(action);
  }

  protected abstract MiningCoordinator createMiningCoordinator(
      ProtocolSchedule<C> protocolSchedule,
      ProtocolContext<C> protocolContext,
      TransactionPool transactionPool,
      MiningParameters miningParameters,
      SyncState syncState,
      EthProtocolManager ethProtocolManager);

  protected abstract ProtocolSchedule<C> createProtocolSchedule();

  protected void validateContext(final ProtocolContext<C> context) {}

  protected abstract C createConsensusContext(
      Blockchain blockchain, WorldStateArchive worldStateArchive);

  protected EthProtocolManager createEthProtocolManager(
      final ProtocolContext<C> protocolContext, final boolean fastSyncEnabled) {
    return new EthProtocolManager(
        protocolContext.getBlockchain(),
        protocolContext.getWorldStateArchive(),
        networkId,
        fastSyncEnabled,
        syncConfig.getDownloaderParallelism(),
        syncConfig.getTransactionsParallelism(),
        syncConfig.getComputationParallelism(),
        clock,
        metricsSystem,
        ethereumWireProtocolConfiguration);
  }
}

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
package tech.pegasys.pantheon.controller;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.pantheon.controller.KeyPairUtil.loadKeyPair;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.peervalidation.DaoForkPeerValidator;
import tech.pegasys.pantheon.ethereum.eth.peervalidation.PeerValidatorRunner;
import tech.pegasys.pantheon.ethereum.eth.sync.DefaultSynchronizer;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethodFactory;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.worldstate.MarkSweepPruner;
import tech.pegasys.pantheon.ethereum.worldstate.Pruner;
import tech.pegasys.pantheon.ethereum.worldstate.PruningConfiguration;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.ObservableMetricsSystem;

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

public abstract class PantheonControllerBuilder<C> {

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

  public PantheonControllerBuilder<C> storageProvider(final StorageProvider storageProvider) {
    this.storageProvider = storageProvider;
    return this;
  }

  public PantheonControllerBuilder<C> genesisConfigFile(final GenesisConfigFile genesisConfig) {
    this.genesisConfig = genesisConfig;
    return this;
  }

  public PantheonControllerBuilder<C> synchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfig) {
    this.syncConfig = synchronizerConfig;
    return this;
  }

  public PantheonControllerBuilder<C> ethProtocolConfiguration(
      final EthProtocolConfiguration ethProtocolConfiguration) {
    this.ethereumWireProtocolConfiguration = ethProtocolConfiguration;
    return this;
  }

  public PantheonControllerBuilder<C> networkId(final BigInteger networkId) {
    this.networkId = networkId;
    return this;
  }

  public PantheonControllerBuilder<C> miningParameters(final MiningParameters miningParameters) {
    this.miningParameters = miningParameters;
    return this;
  }

  public PantheonControllerBuilder<C> nodePrivateKeyFile(final File nodePrivateKeyFile)
      throws IOException {
    this.nodeKeys = loadKeyPair(nodePrivateKeyFile);
    return this;
  }

  public PantheonControllerBuilder<C> nodeKeys(final KeyPair nodeKeys) {
    this.nodeKeys = nodeKeys;
    return this;
  }

  public PantheonControllerBuilder<C> metricsSystem(final ObservableMetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  public PantheonControllerBuilder<C> privacyParameters(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
    return this;
  }

  public PantheonControllerBuilder<C> dataDirectory(final Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    return this;
  }

  public PantheonControllerBuilder<C> clock(final Clock clock) {
    this.clock = clock;
    return this;
  }

  public PantheonControllerBuilder<C> transactionPoolConfiguration(
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    this.transactionPoolConfiguration = transactionPoolConfiguration;
    return this;
  }

  public PantheonControllerBuilder<C> isRevertReasonEnabled(final boolean isRevertReasonEnabled) {
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    return this;
  }

  public PantheonControllerBuilder<C> isPruningEnabled(final boolean pruningEnabled) {
    this.isPruningEnabled = pruningEnabled;
    return this;
  }

  public PantheonControllerBuilder<C> pruningConfiguration(
      final PruningConfiguration pruningConfiguration) {
    this.pruningConfiguration = pruningConfiguration;
    return this;
  }

  public PantheonControllerBuilder<C> genesisConfigOverrides(
      final Map<String, String> genesisConfigOverrides) {
    this.genesisConfigOverrides = genesisConfigOverrides;
    return this;
  }

  public PantheonController<C> build() {
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
    return new PantheonController<>(
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

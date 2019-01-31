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
package tech.pegasys.pantheon.controller;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.DefaultBlockScheduler;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMinerExecutor;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.BlockchainStorage;
import tech.pegasys.pantheon.ethereum.chain.DefaultMutableBlockchain;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.DefaultSynchronizer;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolFactory;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainnetPantheonController implements PantheonController<Void> {

  private static final Logger LOG = LogManager.getLogger();

  private final ProtocolSchedule<Void> protocolSchedule;
  private final ProtocolContext<Void> protocolContext;
  private final ProtocolManager ethProtocolManager;
  private final KeyPair keyPair;
  private final Synchronizer synchronizer;

  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final PrivacyParameters privacyParameters;
  private final Runnable close;

  public MainnetPantheonController(
      final ProtocolSchedule<Void> protocolSchedule,
      final ProtocolContext<Void> protocolContext,
      final ProtocolManager ethProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final PrivacyParameters privacyParameters,
      final Runnable close) {
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
    this.ethProtocolManager = ethProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.privacyParameters = privacyParameters;
    this.close = close;
  }

  public static PantheonController<Void> init(
      final StorageProvider storageProvider,
      final GenesisConfigFile genesisConfig,
      final ProtocolSchedule<Void> protocolSchedule,
      final SynchronizerConfiguration taintedSyncConfig,
      final MiningParameters miningParams,
      final KeyPair nodeKeys,
      final MetricsSystem metricsSystem,
      final PrivacyParameters privacyParameters) {

    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule);
    final BlockchainStorage blockchainStorage =
        storageProvider.createBlockchainStorage(protocolSchedule);
    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(genesisState.getBlock(), blockchainStorage, metricsSystem);

    final WorldStateStorage worldStateStorage = storageProvider.createWorldStateStorage();
    final WorldStateArchive worldStateArchive = new WorldStateArchive(worldStateStorage);
    genesisState.writeStateTo(worldStateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final ProtocolContext<Void> protocolContext =
        new ProtocolContext<>(blockchain, worldStateArchive, null);

    final SynchronizerConfiguration syncConfig = taintedSyncConfig.validated(blockchain);
    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            protocolContext.getBlockchain(),
            protocolContext.getWorldStateArchive(),
            genesisConfig
                .getConfigOptions()
                .getChainId()
                .orElse(MainnetProtocolSchedule.DEFAULT_CHAIN_ID),
            fastSyncEnabled,
            syncConfig.downloaderParallelism(),
            syncConfig.transactionsParallelism());
    final SyncState syncState =
        new SyncState(
            protocolContext.getBlockchain(), ethProtocolManager.ethContext().getEthPeers());
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            worldStateStorage,
            ethProtocolManager.ethContext(),
            syncState,
            metricsSystem.createLabelledTimer(
                MetricCategory.SYNCHRONIZER, "task", "Internal processing tasks", "taskName"));

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule, protocolContext, ethProtocolManager.ethContext());

    final ExecutorService minerThreadPool = Executors.newCachedThreadPool();
    final EthHashMinerExecutor executor =
        new EthHashMinerExecutor(
            protocolContext,
            minerThreadPool,
            protocolSchedule,
            transactionPool.getPendingTransactions(),
            miningParams,
            new DefaultBlockScheduler(
                MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT,
                MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S,
                Clock.systemUTC()));

    final EthHashMiningCoordinator miningCoordinator =
        new EthHashMiningCoordinator(protocolContext.getBlockchain(), executor, syncState);
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);
    if (miningParams.isMiningEnabled()) {
      miningCoordinator.enable();
    }

    return new MainnetPantheonController(
        protocolSchedule,
        protocolContext,
        ethProtocolManager,
        synchronizer,
        nodeKeys,
        transactionPool,
        miningCoordinator,
        privacyParameters,
        () -> {
          miningCoordinator.disable();
          minerThreadPool.shutdownNow();
          try {
            minerThreadPool.awaitTermination(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            LOG.error("Failed to shutdown miner executor");
          }
          try {
            storageProvider.close();
          } catch (final IOException e) {
            LOG.error("Failed to close storage provider", e);
          }
        });
  }

  @Override
  public ProtocolContext<Void> getProtocolContext() {
    return protocolContext;
  }

  @Override
  public ProtocolSchedule<Void> getProtocolSchedule() {
    return protocolSchedule;
  }

  @Override
  public Synchronizer getSynchronizer() {
    return synchronizer;
  }

  @Override
  public SubProtocolConfiguration subProtocolConfiguration() {
    return new SubProtocolConfiguration().withSubProtocol(EthProtocol.get(), ethProtocolManager);
  }

  @Override
  public KeyPair getLocalNodeKeyPair() {
    return keyPair;
  }

  @Override
  public TransactionPool getTransactionPool() {
    return transactionPool;
  }

  @Override
  public MiningCoordinator getMiningCoordinator() {
    return miningCoordinator;
  }

  @Override
  public void close() {
    close.run();
  }

  @Override
  public PrivacyParameters getPrivacyParameters() {
    return privacyParameters;
  }
}

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

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.config.CliqueConfigOptions;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.consensus.clique.CliqueBlockInterface;
import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueMiningTracker;
import tech.pegasys.pantheon.consensus.clique.CliqueProtocolSchedule;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueBlockScheduler;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueMinerExecutor;
import tech.pegasys.pantheon.consensus.clique.blockcreation.CliqueMiningCoordinator;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueJsonRpcMethodsFactory;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.EthereumWireProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.sync.DefaultSynchronizer;
import tech.pegasys.pantheon.ethereum.eth.sync.SyncMode;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.state.SyncState;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPoolFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;

public class CliquePantheonController implements PantheonController<CliqueContext> {

  private static final Logger LOG = getLogger();
  private final ProtocolSchedule<CliqueContext> protocolSchedule;
  private final ProtocolContext<CliqueContext> context;
  private final GenesisConfigOptions genesisConfigOptions;
  private final Synchronizer synchronizer;
  private final ProtocolManager ethProtocolManager;
  private final KeyPair keyPair;
  private final TransactionPool transactionPool;
  private final Runnable closer;

  private final MiningCoordinator miningCoordinator;
  private final PrivacyParameters privacyParameters;

  private CliquePantheonController(
      final ProtocolSchedule<CliqueContext> protocolSchedule,
      final ProtocolContext<CliqueContext> context,
      final GenesisConfigOptions genesisConfigOptions,
      final ProtocolManager ethProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final PrivacyParameters privacyParameters,
      final Runnable closer) {

    this.protocolSchedule = protocolSchedule;
    this.context = context;
    this.genesisConfigOptions = genesisConfigOptions;
    this.ethProtocolManager = ethProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.closer = closer;
    this.miningCoordinator = miningCoordinator;
    this.privacyParameters = privacyParameters;
  }

  static PantheonController<CliqueContext> init(
      final StorageProvider storageProvider,
      final GenesisConfigFile genesisConfig,
      final SynchronizerConfiguration syncConfig,
      final EthereumWireProtocolConfiguration ethereumWireProtocolConfiguration,
      final MiningParameters miningParams,
      final int networkId,
      final KeyPair nodeKeys,
      final Path dataDirectory,
      final MetricsSystem metricsSystem,
      final Clock clock,
      final int maxPendingTransactions,
      final PrivacyParameters privacyParameters) {
    final Address localAddress = Util.publicKeyToAddress(nodeKeys.getPublicKey());
    final CliqueConfigOptions cliqueConfig =
        genesisConfig.getConfigOptions().getCliqueConfigOptions();
    final long blocksPerEpoch = cliqueConfig.getEpochLength();
    final long secondsBetweenBlocks = cliqueConfig.getBlockPeriodSeconds();

    final EpochManager epochManager = new EpochManager(blocksPerEpoch);
    final ProtocolSchedule<CliqueContext> protocolSchedule =
        CliqueProtocolSchedule.create(
            genesisConfig.getConfigOptions(), nodeKeys, privacyParameters);
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule);

    final ProtocolContext<CliqueContext> protocolContext =
        ProtocolContext.init(
            storageProvider,
            genesisState,
            protocolSchedule,
            metricsSystem,
            (blockchain, worldStateArchive) ->
                new CliqueContext(
                    new VoteTallyCache(
                        blockchain,
                        new VoteTallyUpdater(epochManager, new CliqueBlockInterface()),
                        epochManager,
                        new CliqueBlockInterface()),
                    new VoteProposer(),
                    epochManager));
    final MutableBlockchain blockchain = protocolContext.getBlockchain();

    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            blockchain,
            protocolContext.getWorldStateArchive(),
            networkId,
            fastSyncEnabled,
            syncConfig.downloaderParallelism(),
            syncConfig.transactionsParallelism(),
            syncConfig.computationParallelism(),
            metricsSystem,
            ethereumWireProtocolConfiguration);
    final SyncState syncState =
        new SyncState(blockchain, ethProtocolManager.ethContext().getEthPeers());
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            protocolContext.getWorldStateArchive().getStorage(),
            ethProtocolManager.ethContext(),
            syncState,
            dataDirectory,
            clock,
            metricsSystem);

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            ethProtocolManager.ethContext(),
            clock,
            maxPendingTransactions,
            metricsSystem,
            syncState);

    final ExecutorService minerThreadPool = Executors.newCachedThreadPool();
    final CliqueMinerExecutor miningExecutor =
        new CliqueMinerExecutor(
            protocolContext,
            minerThreadPool,
            protocolSchedule,
            transactionPool.getPendingTransactions(),
            nodeKeys,
            miningParams,
            new CliqueBlockScheduler(
                clock,
                protocolContext.getConsensusState().getVoteTallyCache(),
                localAddress,
                secondsBetweenBlocks),
            epochManager);
    final CliqueMiningCoordinator miningCoordinator =
        new CliqueMiningCoordinator(
            blockchain,
            miningExecutor,
            syncState,
            new CliqueMiningTracker(localAddress, protocolContext));
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);

    // Clique mining is implicitly enabled.
    miningCoordinator.enable();

    return new CliquePantheonController(
        protocolSchedule,
        protocolContext,
        genesisConfig.getConfigOptions(),
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
            if (privacyParameters.isEnabled()) {
              privacyParameters.getPrivateStorageProvider().close();
            }
          } catch (final IOException e) {
            LOG.error("Failed to close storage provider", e);
          }
        });
  }

  @Override
  public ProtocolContext<CliqueContext> getProtocolContext() {
    return context;
  }

  @Override
  public ProtocolSchedule<CliqueContext> getProtocolSchedule() {
    return protocolSchedule;
  }

  @Override
  public GenesisConfigOptions getGenesisConfigOptions() {
    return genesisConfigOptions;
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
  public PrivacyParameters getPrivacyParameters() {
    return privacyParameters;
  }

  @Override
  public Map<String, JsonRpcMethod> getAdditionalJsonRpcMethods(
      final Collection<RpcApi> enabledRpcApis) {
    return new CliqueJsonRpcMethodsFactory().methods(context, enabledRpcApis);
  }

  @Override
  public void close() {
    closer.run();
  }
}

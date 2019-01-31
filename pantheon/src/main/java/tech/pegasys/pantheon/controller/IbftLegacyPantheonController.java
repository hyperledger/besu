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

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.config.GenesisConfigOptions;
import tech.pegasys.pantheon.config.IbftConfigOptions;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.IbftJsonRpcMethodsFactory;
import tech.pegasys.pantheon.consensus.ibftlegacy.IbftLegacyBlockInterface;
import tech.pegasys.pantheon.consensus.ibftlegacy.IbftProtocolSchedule;
import tech.pegasys.pantheon.consensus.ibftlegacy.protocol.Istanbul64Protocol;
import tech.pegasys.pantheon.consensus.ibftlegacy.protocol.Istanbul64ProtocolManager;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.MiningCoordinator;
import tech.pegasys.pantheon.ethereum.chain.BlockchainStorage;
import tech.pegasys.pantheon.ethereum.chain.DefaultMutableBlockchain;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Hash;
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
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.ProtocolManager;
import tech.pegasys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.apache.logging.log4j.Logger;

public class IbftLegacyPantheonController implements PantheonController<IbftContext> {

  private static final Logger LOG = getLogger();
  private final GenesisConfigOptions genesisConfig;
  private final ProtocolSchedule<IbftContext> protocolSchedule;
  private final ProtocolContext<IbftContext> context;
  private final Synchronizer synchronizer;
  private final SubProtocol ethSubProtocol;
  private final ProtocolManager ethProtocolManager;
  private final KeyPair keyPair;
  private final TransactionPool transactionPool;
  private final Runnable closer;
  private final MetricsSystem metricsStystem;

  IbftLegacyPantheonController(
      final GenesisConfigOptions genesisConfig,
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final ProtocolContext<IbftContext> context,
      final SubProtocol ethSubProtocol,
      final ProtocolManager ethProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final Runnable closer,
      final MetricsSystem metricsSystem) {

    this.genesisConfig = genesisConfig;
    this.protocolSchedule = protocolSchedule;
    this.context = context;
    this.ethSubProtocol = ethSubProtocol;
    this.ethProtocolManager = ethProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.closer = closer;
    this.metricsStystem = metricsSystem;
  }

  public static PantheonController<IbftContext> init(
      final StorageProvider storageProvider,
      final GenesisConfigFile genesisConfig,
      final SynchronizerConfiguration taintedSyncConfig,
      final boolean ottomanTestnetOperation,
      final int networkId,
      final KeyPair nodeKeys,
      final MetricsSystem metricsSystem) {
    final ProtocolSchedule<IbftContext> protocolSchedule =
        IbftProtocolSchedule.create(genesisConfig.getConfigOptions());
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule);
    final BlockchainStorage blockchainStorage =
        storageProvider.createBlockchainStorage(protocolSchedule);
    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(genesisState.getBlock(), blockchainStorage, metricsSystem);

    final WorldStateStorage worldStateStorage = storageProvider.createWorldStateStorage();
    final WorldStateArchive worldStateArchive = new WorldStateArchive(worldStateStorage);
    genesisState.writeStateTo(worldStateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final IbftConfigOptions ibftConfig = genesisConfig.getConfigOptions().getIbftConfigOptions();
    final EpochManager epochManager = new EpochManager(ibftConfig.getEpochLength());

    final VoteTally voteTally =
        new VoteTallyUpdater(epochManager, new IbftLegacyBlockInterface())
            .buildVoteTallyFromBlockchain(blockchain);

    final VoteProposer voteProposer = new VoteProposer();

    final ProtocolContext<IbftContext> protocolContext =
        new ProtocolContext<>(
            blockchain, worldStateArchive, new IbftContext(voteTally, voteProposer));

    final SynchronizerConfiguration syncConfig = taintedSyncConfig.validated(blockchain);
    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    final EthProtocolManager ethProtocolManager;
    final SubProtocol ethSubProtocol;
    if (ottomanTestnetOperation) {
      LOG.info("Operating on Ottoman testnet.");
      ethSubProtocol = Istanbul64Protocol.get();
      ethProtocolManager =
          new Istanbul64ProtocolManager(
              protocolContext.getBlockchain(),
              protocolContext.getWorldStateArchive(),
              networkId,
              fastSyncEnabled,
              syncConfig.downloaderParallelism(),
              syncConfig.transactionsParallelism());
    } else {
      ethSubProtocol = EthProtocol.get();
      ethProtocolManager =
          new EthProtocolManager(
              protocolContext.getBlockchain(),
              protocolContext.getWorldStateArchive(),
              networkId,
              fastSyncEnabled,
              syncConfig.downloaderParallelism(),
              syncConfig.transactionsParallelism());
    }

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

    final Runnable closer =
        () -> {
          try {
            storageProvider.close();
          } catch (final IOException e) {
            LOG.error("Failed to close storage provider", e);
          }
        };

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule, protocolContext, ethProtocolManager.ethContext());

    return new IbftLegacyPantheonController(
        genesisConfig.getConfigOptions(),
        protocolSchedule,
        protocolContext,
        ethSubProtocol,
        ethProtocolManager,
        synchronizer,
        nodeKeys,
        transactionPool,
        closer,
        metricsSystem);
  }

  @Override
  public ProtocolContext<IbftContext> getProtocolContext() {
    return context;
  }

  @Override
  public ProtocolSchedule<IbftContext> getProtocolSchedule() {
    return protocolSchedule;
  }

  @Override
  public Synchronizer getSynchronizer() {
    return synchronizer;
  }

  @Override
  public SubProtocolConfiguration subProtocolConfiguration() {
    return new SubProtocolConfiguration().withSubProtocol(ethSubProtocol, ethProtocolManager);
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
    return null;
  }

  @Override
  public PrivacyParameters getPrivacyParameters() {
    LOG.warn("IbftLegacyPantheonController does not currently support private transactions.");
    return PrivacyParameters.noPrivacy();
  }

  @Override
  public Map<String, JsonRpcMethod> getAdditionalJsonRpcMethods(
      final Collection<RpcApi> enabledRpcApis) {
    return new IbftJsonRpcMethodsFactory().methods(context, enabledRpcApis);
  }

  @Override
  public void close() {
    closer.run();
  }
}

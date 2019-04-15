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
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
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
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
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
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.metrics.MetricsSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;

import org.apache.logging.log4j.Logger;

public class IbftLegacyPantheonController implements PantheonController<IbftContext> {

  private static final Logger LOG = getLogger();
  private final ProtocolSchedule<IbftContext> protocolSchedule;
  private final ProtocolContext<IbftContext> context;
  private final GenesisConfigOptions genesisConfigOptions;
  private final Synchronizer synchronizer;
  private final SubProtocol istanbulSubProtocol;
  private final ProtocolManager istanbulProtocolManager;
  private final KeyPair keyPair;
  private final TransactionPool transactionPool;
  private final Runnable closer;
  private final PrivacyParameters privacyParameters;

  private IbftLegacyPantheonController(
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final ProtocolContext<IbftContext> context,
      final GenesisConfigOptions genesisConfigOptions,
      final SubProtocol istanbulSubProtocol,
      final ProtocolManager istanbulProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final PrivacyParameters privacyParameters,
      final Runnable closer) {

    this.protocolSchedule = protocolSchedule;
    this.context = context;
    this.genesisConfigOptions = genesisConfigOptions;
    this.istanbulSubProtocol = istanbulSubProtocol;
    this.istanbulProtocolManager = istanbulProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.privacyParameters = privacyParameters;
    this.closer = closer;
  }

  static PantheonController<IbftContext> init(
      final StorageProvider storageProvider,
      final GenesisConfigFile genesisConfig,
      final SynchronizerConfiguration syncConfig,
      final EthereumWireProtocolConfiguration ethereumWireProtocolConfiguration,
      final int networkId,
      final KeyPair nodeKeys,
      final Path dataDirectory,
      final MetricsSystem metricsSystem,
      final Clock clock,
      final int maxPendingTransactions,
      final PrivacyParameters privacyParameters) {
    final ProtocolSchedule<IbftContext> protocolSchedule =
        IbftProtocolSchedule.create(genesisConfig.getConfigOptions(), privacyParameters);
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule);
    final IbftConfigOptions ibftConfig =
        genesisConfig.getConfigOptions().getIbftLegacyConfigOptions();

    final ProtocolContext<IbftContext> protocolContext =
        ProtocolContext.init(
            storageProvider,
            genesisState,
            protocolSchedule,
            metricsSystem,
            (blockchain, worldStateArchive) -> {
              final EpochManager epochManager = new EpochManager(ibftConfig.getEpochLength());
              final VoteTallyCache voteTallyCache =
                  new VoteTallyCache(
                      blockchain,
                      new VoteTallyUpdater(epochManager, new IbftLegacyBlockInterface()),
                      epochManager,
                      new IbftLegacyBlockInterface());

              final VoteProposer voteProposer = new VoteProposer();
              return new IbftContext(voteTallyCache, voteProposer);
            });
    final MutableBlockchain blockchain = protocolContext.getBlockchain();

    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    final EthProtocolManager istanbul64ProtocolManager;
    final SubProtocol istanbul64SubProtocol;
    LOG.info("Operating on IBFT-1.0 network.");
    istanbul64SubProtocol = Istanbul64Protocol.get();
    istanbul64ProtocolManager =
        new Istanbul64ProtocolManager(
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
        new SyncState(blockchain, istanbul64ProtocolManager.ethContext().getEthPeers());
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig,
            protocolSchedule,
            protocolContext,
            protocolContext.getWorldStateArchive().getStorage(),
            istanbul64ProtocolManager.ethContext(),
            syncState,
            dataDirectory,
            clock,
            metricsSystem);

    final Runnable closer =
        () -> {
          try {
            storageProvider.close();
            if (privacyParameters.isEnabled()) {
              privacyParameters.getPrivateStorageProvider().close();
            }
          } catch (final IOException e) {
            LOG.error("Failed to close storage provider", e);
          }
        };

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            istanbul64ProtocolManager.ethContext(),
            clock,
            maxPendingTransactions,
            metricsSystem,
            syncState);

    return new IbftLegacyPantheonController(
        protocolSchedule,
        protocolContext,
        genesisConfig.getConfigOptions(),
        istanbul64SubProtocol,
        istanbul64ProtocolManager,
        synchronizer,
        nodeKeys,
        transactionPool,
        privacyParameters,
        closer);
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
  public GenesisConfigOptions getGenesisConfigOptions() {
    return genesisConfigOptions;
  }

  @Override
  public Synchronizer getSynchronizer() {
    return synchronizer;
  }

  @Override
  public SubProtocolConfiguration subProtocolConfiguration() {
    return new SubProtocolConfiguration()
        .withSubProtocol(istanbulSubProtocol, istanbulProtocolManager);
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
    return privacyParameters;
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

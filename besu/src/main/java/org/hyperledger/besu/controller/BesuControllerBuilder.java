/*
 * Copyright ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.config.CheckpointConfigOptions;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.UnverifiedForkchoiceSupplier;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfiguration;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ConsensusContextFactory;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.ChainDataPruner;
import org.hyperledger.besu.ethereum.chain.ChainDataPrunerStorage;
import org.hyperledger.besu.ethereum.chain.ChainPrunerConfiguration;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.chain.VariablesStorage;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthMessages;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.MergePeerFilter;
import org.hyperledger.besu.ethereum.eth.manager.MonitoredExecutors;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.eth.peervalidation.CheckpointBlocksPeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.ClassicForkPeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.DaoForkPeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.PeerValidator;
import org.hyperledger.besu.ethereum.eth.peervalidation.RequiredBlocksPeerValidator;
import org.hyperledger.besu.ethereum.eth.sync.DefaultSynchronizer;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotSelectorFromPeers;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotSelectorFromSafeBlock;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.Checkpoint;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.checkpoint.ImmutableCheckpoint;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolFactory;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.MarkSweepPruner;
import org.hyperledger.besu.ethereum.worldstate.Pruner;
import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelectorFactory;

import java.io.Closeable;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Besu controller builder that builds Besu Controller. */
public abstract class BesuControllerBuilder implements MiningParameterOverrides {
  private static final Logger LOG = LoggerFactory.getLogger(BesuControllerBuilder.class);

  private GenesisConfigFile genesisConfig;
  private Map<String, String> genesisConfigOverrides = Collections.emptyMap();

  /** The Config options supplier. */
  protected Supplier<GenesisConfigOptions> configOptionsSupplier =
      () ->
          Optional.ofNullable(genesisConfig)
              .map(conf -> conf.getConfigOptions(genesisConfigOverrides))
              .orElseThrow();

  /** The Sync config. */
  protected SynchronizerConfiguration syncConfig;
  /** The Ethereum wire protocol configuration. */
  protected EthProtocolConfiguration ethereumWireProtocolConfiguration;
  /** The Transaction pool configuration. */
  protected TransactionPoolConfiguration transactionPoolConfiguration;
  /** The Network id. */
  protected BigInteger networkId;
  /** The Mining parameters. */
  protected MiningParameters miningParameters;
  /** The Metrics system. */
  protected ObservableMetricsSystem metricsSystem;
  /** The Privacy parameters. */
  protected PrivacyParameters privacyParameters;
  /** The Pki block creation configuration. */
  protected Optional<PkiBlockCreationConfiguration> pkiBlockCreationConfiguration =
      Optional.empty();
  /** The Data directory. */
  protected Path dataDirectory;
  /** The Clock. */
  protected Clock clock;
  /** The Node key. */
  protected NodeKey nodeKey;
  /** The Is revert reason enabled. */
  protected boolean isRevertReasonEnabled;
  /** The Gas limit calculator. */
  GasLimitCalculator gasLimitCalculator;
  /** The Storage provider. */
  protected StorageProvider storageProvider;
  /** The Is pruning enabled. */
  protected boolean isPruningEnabled;
  /** The Pruner configuration. */
  protected PrunerConfiguration prunerConfiguration;
  /** The Required blocks. */
  protected Map<Long, Hash> requiredBlocks = Collections.emptyMap();
  /** The Reorg logging threshold. */
  protected long reorgLoggingThreshold;
  /** The Data storage configuration. */
  protected DataStorageConfiguration dataStorageConfiguration =
      DataStorageConfiguration.DEFAULT_CONFIG;
  /** The Message permissioning providers. */
  protected List<NodeMessagePermissioningProvider> messagePermissioningProviders =
      Collections.emptyList();
  /** The Evm configuration. */
  protected EvmConfiguration evmConfiguration;
  /** The Max peers. */
  protected int maxPeers;

  private int peerLowerBound;
  private int maxRemotelyInitiatedPeers;
  /** The Chain pruner configuration. */
  protected ChainPrunerConfiguration chainPrunerConfiguration = ChainPrunerConfiguration.DEFAULT;

  private NetworkingConfiguration networkingConfiguration;
  private Boolean randomPeerPriority;
  private Optional<TransactionSelectorFactory> transactionSelectorFactory = Optional.empty();
  /** the Dagger configured context that can provide dependencies */
  protected Optional<BesuComponent> besuComponent = Optional.empty();

  /**
   * Provide a BesuComponent which can be used to get other dependencies
   *
   * @param besuComponent application context that can be used to get other dependencies
   * @return the besu controller builder
   */
  public BesuControllerBuilder besuComponent(final BesuComponent besuComponent) {
    this.besuComponent = Optional.ofNullable(besuComponent);
    return this;
  }

  /**
   * Storage provider besu controller builder.
   *
   * @param storageProvider the storage provider
   * @return the besu controller builder
   */
  public BesuControllerBuilder storageProvider(final StorageProvider storageProvider) {
    this.storageProvider = storageProvider;
    return this;
  }

  /**
   * Genesis config file besu controller builder.
   *
   * @param genesisConfig the genesis config
   * @return the besu controller builder
   */
  public BesuControllerBuilder genesisConfigFile(final GenesisConfigFile genesisConfig) {
    this.genesisConfig = genesisConfig;
    return this;
  }

  /**
   * Synchronizer configuration besu controller builder.
   *
   * @param synchronizerConfig the synchronizer config
   * @return the besu controller builder
   */
  public BesuControllerBuilder synchronizerConfiguration(
      final SynchronizerConfiguration synchronizerConfig) {
    this.syncConfig = synchronizerConfig;
    return this;
  }

  /**
   * Eth protocol configuration besu controller builder.
   *
   * @param ethProtocolConfiguration the eth protocol configuration
   * @return the besu controller builder
   */
  public BesuControllerBuilder ethProtocolConfiguration(
      final EthProtocolConfiguration ethProtocolConfiguration) {
    this.ethereumWireProtocolConfiguration = ethProtocolConfiguration;
    return this;
  }

  /**
   * Network id besu controller builder.
   *
   * @param networkId the network id
   * @return the besu controller builder
   */
  public BesuControllerBuilder networkId(final BigInteger networkId) {
    this.networkId = networkId;
    return this;
  }

  /**
   * Mining parameters besu controller builder.
   *
   * @param miningParameters the mining parameters
   * @return the besu controller builder
   */
  public BesuControllerBuilder miningParameters(final MiningParameters miningParameters) {
    this.miningParameters = miningParameters;
    return this;
  }

  /**
   * Message permissioning providers besu controller builder.
   *
   * @param messagePermissioningProviders the message permissioning providers
   * @return the besu controller builder
   */
  public BesuControllerBuilder messagePermissioningProviders(
      final List<NodeMessagePermissioningProvider> messagePermissioningProviders) {
    this.messagePermissioningProviders = messagePermissioningProviders;
    return this;
  }

  /**
   * Node key besu controller builder.
   *
   * @param nodeKey the node key
   * @return the besu controller builder
   */
  public BesuControllerBuilder nodeKey(final NodeKey nodeKey) {
    this.nodeKey = nodeKey;
    return this;
  }

  /**
   * Metrics system besu controller builder.
   *
   * @param metricsSystem the metrics system
   * @return the besu controller builder
   */
  public BesuControllerBuilder metricsSystem(final ObservableMetricsSystem metricsSystem) {
    this.metricsSystem = metricsSystem;
    return this;
  }

  /**
   * Privacy parameters besu controller builder.
   *
   * @param privacyParameters the privacy parameters
   * @return the besu controller builder
   */
  public BesuControllerBuilder privacyParameters(final PrivacyParameters privacyParameters) {
    this.privacyParameters = privacyParameters;
    return this;
  }

  /**
   * Pki block creation configuration besu controller builder.
   *
   * @param pkiBlockCreationConfiguration the pki block creation configuration
   * @return the besu controller builder
   */
  public BesuControllerBuilder pkiBlockCreationConfiguration(
      final Optional<PkiBlockCreationConfiguration> pkiBlockCreationConfiguration) {
    this.pkiBlockCreationConfiguration = pkiBlockCreationConfiguration;
    return this;
  }

  /**
   * Data directory besu controller builder.
   *
   * @param dataDirectory the data directory
   * @return the besu controller builder
   */
  public BesuControllerBuilder dataDirectory(final Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    return this;
  }

  /**
   * Clock besu controller builder.
   *
   * @param clock the clock
   * @return the besu controller builder
   */
  public BesuControllerBuilder clock(final Clock clock) {
    this.clock = clock;
    return this;
  }

  /**
   * Transaction pool configuration besu controller builder.
   *
   * @param transactionPoolConfiguration the transaction pool configuration
   * @return the besu controller builder
   */
  public BesuControllerBuilder transactionPoolConfiguration(
      final TransactionPoolConfiguration transactionPoolConfiguration) {
    this.transactionPoolConfiguration = transactionPoolConfiguration;
    return this;
  }

  /**
   * Is revert reason enabled besu controller builder.
   *
   * @param isRevertReasonEnabled the is revert reason enabled
   * @return the besu controller builder
   */
  public BesuControllerBuilder isRevertReasonEnabled(final boolean isRevertReasonEnabled) {
    this.isRevertReasonEnabled = isRevertReasonEnabled;
    return this;
  }

  /**
   * Is pruning enabled besu controller builder.
   *
   * @param isPruningEnabled the is pruning enabled
   * @return the besu controller builder
   */
  public BesuControllerBuilder isPruningEnabled(final boolean isPruningEnabled) {
    this.isPruningEnabled = isPruningEnabled;
    return this;
  }

  /**
   * Pruning configuration besu controller builder.
   *
   * @param prunerConfiguration the pruner configuration
   * @return the besu controller builder
   */
  public BesuControllerBuilder pruningConfiguration(final PrunerConfiguration prunerConfiguration) {
    this.prunerConfiguration = prunerConfiguration;
    return this;
  }

  /**
   * Genesis config overrides besu controller builder.
   *
   * @param genesisConfigOverrides the genesis config overrides
   * @return the besu controller builder
   */
  public BesuControllerBuilder genesisConfigOverrides(
      final Map<String, String> genesisConfigOverrides) {
    this.genesisConfigOverrides = genesisConfigOverrides;
    return this;
  }

  /**
   * Gas limit calculator besu controller builder.
   *
   * @param gasLimitCalculator the gas limit calculator
   * @return the besu controller builder
   */
  public BesuControllerBuilder gasLimitCalculator(final GasLimitCalculator gasLimitCalculator) {
    this.gasLimitCalculator = gasLimitCalculator;
    return this;
  }

  /**
   * Required blocks besu controller builder.
   *
   * @param requiredBlocks the required blocks
   * @return the besu controller builder
   */
  public BesuControllerBuilder requiredBlocks(final Map<Long, Hash> requiredBlocks) {
    this.requiredBlocks = requiredBlocks;
    return this;
  }

  /**
   * Reorg logging threshold besu controller builder.
   *
   * @param reorgLoggingThreshold the reorg logging threshold
   * @return the besu controller builder
   */
  public BesuControllerBuilder reorgLoggingThreshold(final long reorgLoggingThreshold) {
    this.reorgLoggingThreshold = reorgLoggingThreshold;
    return this;
  }

  /**
   * Data storage configuration besu controller builder.
   *
   * @param dataStorageConfiguration the data storage configuration
   * @return the besu controller builder
   */
  public BesuControllerBuilder dataStorageConfiguration(
      final DataStorageConfiguration dataStorageConfiguration) {
    this.dataStorageConfiguration = dataStorageConfiguration;
    return this;
  }

  /**
   * Evm configuration besu controller builder.
   *
   * @param evmConfiguration the evm configuration
   * @return the besu controller builder
   */
  public BesuControllerBuilder evmConfiguration(final EvmConfiguration evmConfiguration) {
    this.evmConfiguration = evmConfiguration;
    return this;
  }

  /**
   * Max peers besu controller builder.
   *
   * @param maxPeers the max peers
   * @return the besu controller builder
   */
  public BesuControllerBuilder maxPeers(final int maxPeers) {
    this.maxPeers = maxPeers;
    return this;
  }

  /**
   * Lower bound of peers where we stop actively trying to initiate new outgoing connections
   *
   * @param peerLowerBound lower bound of peers where we stop actively trying to initiate new
   *     outgoing connections
   * @return the besu controller builder
   */
  public BesuControllerBuilder lowerBoundPeers(final int peerLowerBound) {
    this.peerLowerBound = peerLowerBound;
    return this;
  }

  /**
   * Maximum number of remotely initiated peer connections
   *
   * @param maxRemotelyInitiatedPeers aximum number of remotely initiated peer connections
   * @return the besu controller builder
   */
  public BesuControllerBuilder maxRemotelyInitiatedPeers(final int maxRemotelyInitiatedPeers) {
    this.maxRemotelyInitiatedPeers = maxRemotelyInitiatedPeers;
    return this;
  }

  /**
   * Chain pruning configuration besu controller builder.
   *
   * @param chainPrunerConfiguration the chain pruner configuration
   * @return the besu controller builder
   */
  public BesuControllerBuilder chainPruningConfiguration(
      final ChainPrunerConfiguration chainPrunerConfiguration) {
    this.chainPrunerConfiguration = chainPrunerConfiguration;
    return this;
  }

  /**
   * sets the networkConfiguration in the builder
   *
   * @param networkingConfiguration the networking config
   * @return the besu controller builder
   */
  public BesuControllerBuilder networkConfiguration(
      final NetworkingConfiguration networkingConfiguration) {
    this.networkingConfiguration = networkingConfiguration;
    return this;
  }

  /**
   * sets the randomPeerPriority flag in the builder
   *
   * @param randomPeerPriority the random peer priority flag
   * @return the besu controller builder
   */
  public BesuControllerBuilder randomPeerPriority(final Boolean randomPeerPriority) {
    this.randomPeerPriority = randomPeerPriority;
    return this;
  }

  /**
   * sets the transactionSelectorFactory in the builder
   *
   * @param transactionSelectorFactory the optional transaction selector factory
   * @return the besu controller builder
   */
  public BesuControllerBuilder transactionSelectorFactory(
      final Optional<TransactionSelectorFactory> transactionSelectorFactory) {
    this.transactionSelectorFactory = transactionSelectorFactory;
    return this;
  }

  /**
   * Build besu controller.
   *
   * @return the besu controller
   */
  public BesuController build() {
    checkNotNull(genesisConfig, "Missing genesis config");
    checkNotNull(syncConfig, "Missing sync config");
    checkNotNull(ethereumWireProtocolConfiguration, "Missing ethereum protocol configuration");
    checkNotNull(networkId, "Missing network ID");
    checkNotNull(miningParameters, "Missing mining parameters");
    checkNotNull(metricsSystem, "Missing metrics system");
    checkNotNull(privacyParameters, "Missing privacy parameters");
    checkNotNull(dataDirectory, "Missing data directory"); // Why do we need this?
    checkNotNull(clock, "Missing clock");
    checkNotNull(transactionPoolConfiguration, "Missing transaction pool configuration");
    checkNotNull(nodeKey, "Missing node key");
    checkNotNull(storageProvider, "Must supply a storage provider");
    checkNotNull(gasLimitCalculator, "Missing gas limit calculator");
    checkNotNull(evmConfiguration, "Missing evm config");
    checkNotNull(networkingConfiguration, "Missing network configuration");
    prepForBuild();

    final ProtocolSchedule protocolSchedule = createProtocolSchedule();
    final GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule);

    final VariablesStorage variablesStorage = storageProvider.createVariablesStorage();

    final WorldStateStorage worldStateStorage =
        storageProvider.createWorldStateStorage(dataStorageConfiguration.getDataStorageFormat());


    final BlockchainStorage blockchainStorage =
        storageProvider.createBlockchainStorage(protocolSchedule, variablesStorage);

    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisState.getBlock(),
            blockchainStorage,
            metricsSystem,
            reorgLoggingThreshold,
            dataDirectory.toString());

    final CachedMerkleTrieLoader cachedMerkleTrieLoader =
        besuComponent
            .map(BesuComponent::getCachedMerkleTrieLoader)
            .orElseGet(() -> new CachedMerkleTrieLoader(metricsSystem));

    final WorldStateArchive worldStateArchive =
        createWorldStateArchive(worldStateStorage, blockchain, cachedMerkleTrieLoader);

    if (blockchain.getChainHeadBlockNumber() < 1) {
      genesisState.writeStateTo(worldStateArchive.getMutable());
    }

    final ProtocolContext protocolContext =
        createProtocolContext(
            blockchain,
            worldStateArchive,
            protocolSchedule,
            this::createConsensusContext,
            transactionSelectorFactory);
    validateContext(protocolContext);

    if (chainPrunerConfiguration.getChainPruningEnabled()) {
      protocolContext
          .safeConsensusContext(MergeContext.class)
          .ifPresent(
              mergeContext -> {
                mergeContext.setIsChainPruningEnabled(true);
              });
      final ChainDataPruner chainDataPruner = createChainPruner(blockchainStorage);
      blockchain.observeBlockAdded(chainDataPruner);
      LOG.info(
          "Chain data pruning enabled with recent blocks retained to be: "
              + chainPrunerConfiguration.getChainPruningBlocksRetained()
              + " and frequency to be: "
              + chainPrunerConfiguration.getChainPruningBlocksFrequency());
    }

    protocolSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(
        protocolContext.getWorldStateArchive());

    Optional<Pruner> maybePruner = Optional.empty();
    if (isPruningEnabled) {
      if (dataStorageConfiguration.getDataStorageFormat().equals(DataStorageFormat.BONSAI)) {
        LOG.warn(
            "Cannot enable pruning with Bonsai data storage format. Disabling. Change the data storage format or disable pruning explicitly on the command line to remove this warning.");
      } else {
        maybePruner =
            Optional.of(
                new Pruner(
                    new MarkSweepPruner(
                        ((DefaultWorldStateArchive) worldStateArchive).getWorldStateStorage(),
                        blockchain,
                        storageProvider.getStorageBySegmentIdentifier(
                            KeyValueSegmentIdentifier.PRUNING_STATE),
                        metricsSystem),
                    blockchain,
                    prunerConfiguration));
      }
    }
    final int maxMessageSize = ethereumWireProtocolConfiguration.getMaxMessageSize();
    final Supplier<ProtocolSpec> currentProtocolSpecSupplier =
        () -> protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader());
    final EthPeers ethPeers =
        new EthPeers(
            getSupportedProtocol(),
            currentProtocolSpecSupplier,
            clock,
            metricsSystem,
            maxMessageSize,
            messagePermissioningProviders,
            nodeKey.getPublicKey().getEncodedBytes(),
            peerLowerBound,
            maxPeers,
            maxRemotelyInitiatedPeers,
            randomPeerPriority);

    final EthMessages ethMessages = new EthMessages();
    final EthMessages snapMessages = new EthMessages();

    final EthScheduler scheduler =
        new EthScheduler(
            syncConfig.getDownloaderParallelism(),
            syncConfig.getTransactionsParallelism(),
            syncConfig.getComputationParallelism(),
            metricsSystem);

    final GenesisConfigOptions configOptions =
        genesisConfig.getConfigOptions(genesisConfigOverrides);

    Optional<Checkpoint> checkpoint = Optional.empty();
    if (configOptions.getCheckpointOptions().isValid()) {
      checkpoint =
          Optional.of(
              ImmutableCheckpoint.builder()
                  .blockHash(
                      Hash.fromHexString(configOptions.getCheckpointOptions().getHash().get()))
                  .blockNumber(configOptions.getCheckpointOptions().getNumber().getAsLong())
                  .totalDifficulty(
                      Difficulty.fromHexString(
                          configOptions.getCheckpointOptions().getTotalDifficulty().get()))
                  .build());
    }

    final EthContext ethContext = new EthContext(ethPeers, ethMessages, snapMessages, scheduler);
    final boolean fullSyncDisabled = !SyncMode.isFullSync(syncConfig.getSyncMode());
    final SyncState syncState = new SyncState(blockchain, ethPeers, fullSyncDisabled, checkpoint);

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule,
            protocolContext,
            ethContext,
            clock,
            metricsSystem,
            syncState,
            miningParameters,
            transactionPoolConfiguration);

    final List<PeerValidator> peerValidators = createPeerValidators(protocolSchedule);

    final EthProtocolManager ethProtocolManager =
        createEthProtocolManager(
            protocolContext,
            syncConfig,
            transactionPool,
            ethereumWireProtocolConfiguration,
            ethPeers,
            ethContext,
            ethMessages,
            scheduler,
            peerValidators,
            Optional.empty());

    final Optional<SnapProtocolManager> maybeSnapProtocolManager =
        createSnapProtocolManager(peerValidators, ethPeers, snapMessages, worldStateArchive);

    final PivotBlockSelector pivotBlockSelector =
        createPivotSelector(
            protocolSchedule, protocolContext, ethContext, syncState, metricsSystem);

    final Synchronizer synchronizer =
        createSynchronizer(
            protocolSchedule,
            worldStateStorage,
            protocolContext,
            maybePruner,
            ethContext,
            syncState,
            ethProtocolManager,
            pivotBlockSelector);

    protocolContext.setSynchronizer(Optional.of(synchronizer));

    final MiningCoordinator miningCoordinator =
        createMiningCoordinator(
            protocolSchedule,
            protocolContext,
            transactionPool,
            miningParameters,
            syncState,
            ethProtocolManager);

    final PluginServiceFactory additionalPluginServices =
        createAdditionalPluginServices(blockchain, protocolContext);

    final SubProtocolConfiguration subProtocolConfiguration =
        createSubProtocolConfiguration(ethProtocolManager, maybeSnapProtocolManager);
    ;

    final JsonRpcMethods additionalJsonRpcMethodFactory =
        createAdditionalJsonRpcMethodFactory(protocolContext);

    final List<Closeable> closeables = new ArrayList<>();
    closeables.add(protocolContext.getWorldStateArchive());
    closeables.add(storageProvider);
    if (privacyParameters.getPrivateStorageProvider() != null) {
      closeables.add(privacyParameters.getPrivateStorageProvider());
    }

    return new BesuController(
        protocolSchedule,
        protocolContext,
        ethProtocolManager,
        configOptionsSupplier.get(),
        subProtocolConfiguration,
        synchronizer,
        syncState,
        transactionPool,
        miningCoordinator,
        privacyParameters,
        miningParameters,
        additionalJsonRpcMethodFactory,
        nodeKey,
        closeables,
        additionalPluginServices,
        ethPeers,
        storageProvider);
  }

  /**
   * Create synchronizer synchronizer.
   *
   * @param protocolSchedule the protocol schedule
   * @param worldStateStorage the world state storage
   * @param protocolContext the protocol context
   * @param maybePruner the maybe pruner
   * @param ethContext the eth context
   * @param syncState the sync state
   * @param ethProtocolManager the eth protocol manager
   * @param pivotBlockSelector the pivot block selector
   * @return the synchronizer
   */
  protected Synchronizer createSynchronizer(
      final ProtocolSchedule protocolSchedule,
      final WorldStateStorage worldStateStorage,
      final ProtocolContext protocolContext,
      final Optional<Pruner> maybePruner,
      final EthContext ethContext,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager,
      final PivotBlockSelector pivotBlockSelector) {

    final DefaultSynchronizer toUse =
        new DefaultSynchronizer(
            syncConfig,
            protocolSchedule,
            protocolContext,
            worldStateStorage,
            ethProtocolManager.getBlockBroadcaster(),
            maybePruner,
            ethContext,
            syncState,
            dataDirectory,
            storageProvider,
            clock,
            metricsSystem,
            getFullSyncTerminationCondition(protocolContext.getBlockchain()),
            pivotBlockSelector);

    return toUse;
  }

  private PivotBlockSelector createPivotSelector(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final SyncState syncState,
      final MetricsSystem metricsSystem) {

    final GenesisConfigOptions genesisConfigOptions = configOptionsSupplier.get();

    if (genesisConfigOptions.getTerminalTotalDifficulty().isPresent()) {
      LOG.info("TTD difficulty is present, creating initial sync for PoS");

      final MergeContext mergeContext = protocolContext.getConsensusContext(MergeContext.class);
      final UnverifiedForkchoiceSupplier unverifiedForkchoiceSupplier =
          new UnverifiedForkchoiceSupplier();
      final long subscriptionId =
          mergeContext.addNewUnverifiedForkchoiceListener(unverifiedForkchoiceSupplier);

      final Runnable unsubscribeForkchoiceListener =
          () -> {
            mergeContext.removeNewUnverifiedForkchoiceListener(subscriptionId);
            LOG.info("Initial sync done, unsubscribe forkchoice supplier");
          };

      return new PivotSelectorFromSafeBlock(
          protocolContext,
          protocolSchedule,
          ethContext,
          metricsSystem,
          genesisConfigOptions,
          unverifiedForkchoiceSupplier,
          unsubscribeForkchoiceListener);
    } else {
      LOG.info("TTD difficulty is not present, creating initial sync phase for PoW");
      return new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem);
    }
  }

  /**
   * Gets full sync termination condition.
   *
   * @param blockchain the blockchain
   * @return the full sync termination condition
   */
  protected SyncTerminationCondition getFullSyncTerminationCondition(final Blockchain blockchain) {
    return configOptionsSupplier
        .get()
        .getTerminalTotalDifficulty()
        .map(difficulty -> SyncTerminationCondition.difficulty(difficulty, blockchain))
        .orElse(SyncTerminationCondition.never());
  }

  /** Prep for build. */
  protected void prepForBuild() {}

  /**
   * Create additional json rpc method factory json rpc methods.
   *
   * @param protocolContext the protocol context
   * @return the json rpc methods
   */
  protected JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext) {
    return apis -> Collections.emptyMap();
  }

  /**
   * Create sub protocol configuration sub protocol configuration.
   *
   * @param ethProtocolManager the eth protocol manager
   * @param maybeSnapProtocolManager the maybe snap protocol manager
   * @return the sub protocol configuration
   */
  protected SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager,
      final Optional<SnapProtocolManager> maybeSnapProtocolManager) {
    final SubProtocolConfiguration subProtocolConfiguration =
        new SubProtocolConfiguration().withSubProtocol(EthProtocol.get(), ethProtocolManager);
    maybeSnapProtocolManager.ifPresent(
        snapProtocolManager -> {
          subProtocolConfiguration.withSubProtocol(SnapProtocol.get(), snapProtocolManager);
        });
    return subProtocolConfiguration;
  }

  /**
   * Create mining coordinator mining coordinator.
   *
   * @param protocolSchedule the protocol schedule
   * @param protocolContext the protocol context
   * @param transactionPool the transaction pool
   * @param miningParameters the mining parameters
   * @param syncState the sync state
   * @param ethProtocolManager the eth protocol manager
   * @return the mining coordinator
   */
  protected abstract MiningCoordinator createMiningCoordinator(
      ProtocolSchedule protocolSchedule,
      ProtocolContext protocolContext,
      TransactionPool transactionPool,
      MiningParameters miningParameters,
      SyncState syncState,
      EthProtocolManager ethProtocolManager);

  /**
   * Create protocol schedule protocol schedule.
   *
   * @return the protocol schedule
   */
  protected abstract ProtocolSchedule createProtocolSchedule();

  /**
   * Validate context.
   *
   * @param context the context
   */
  protected void validateContext(final ProtocolContext context) {}

  /**
   * Create consensus context consensus context.
   *
   * @param blockchain the blockchain
   * @param worldStateArchive the world state archive
   * @param protocolSchedule the protocol schedule
   * @return the consensus context
   */
  protected abstract ConsensusContext createConsensusContext(
      Blockchain blockchain,
      WorldStateArchive worldStateArchive,
      ProtocolSchedule protocolSchedule);

  /**
   * Gets supported protocol.
   *
   * @return the supported protocol
   */
  protected String getSupportedProtocol() {
    return EthProtocol.NAME;
  }

  /**
   * Create eth protocol manager eth protocol manager.
   *
   * @param protocolContext the protocol context
   * @param synchronizerConfiguration the synchronizer configuration
   * @param transactionPool the transaction pool
   * @param ethereumWireProtocolConfiguration the ethereum wire protocol configuration
   * @param ethPeers the eth peers
   * @param ethContext the eth context
   * @param ethMessages the eth messages
   * @param scheduler the scheduler
   * @param peerValidators the peer validators
   * @param mergePeerFilter the merge peer filter
   * @return the eth protocol manager
   */
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
      final Optional<MergePeerFilter> mergePeerFilter) {
    return new EthProtocolManager(
        protocolContext.getBlockchain(),
        networkId,
        protocolContext.getWorldStateArchive(),
        transactionPool,
        ethereumWireProtocolConfiguration,
        ethPeers,
        ethMessages,
        ethContext,
        peerValidators,
        mergePeerFilter,
        synchronizerConfiguration,
        scheduler,
        genesisConfig.getForkBlockNumbers(),
        genesisConfig.getForkTimestamps());
  }

  /**
   * Create protocol context protocol context.
   *
   * @param blockchain the blockchain
   * @param worldStateArchive the world state archive
   * @param protocolSchedule the protocol schedule
   * @param consensusContextFactory the consensus context factory
   * @param transactionSelectorFactory optional transaction selector factory
   * @return the protocol context
   */
  protected ProtocolContext createProtocolContext(
      final MutableBlockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule,
      final ConsensusContextFactory consensusContextFactory,
      final Optional<TransactionSelectorFactory> transactionSelectorFactory) {
    return ProtocolContext.init(
        blockchain,
        worldStateArchive,
        protocolSchedule,
        consensusContextFactory,
        transactionSelectorFactory);
  }

  private Optional<SnapProtocolManager> createSnapProtocolManager(
      final List<PeerValidator> peerValidators,
      final EthPeers ethPeers,
      final EthMessages snapMessages,
      final WorldStateArchive worldStateArchive) {
    return Optional.of(
        new SnapProtocolManager(peerValidators, ethPeers, snapMessages, worldStateArchive));
  }

  WorldStateArchive createWorldStateArchive(
      final WorldStateStorage worldStateStorage,
      final Blockchain blockchain,
      final CachedMerkleTrieLoader cachedMerkleTrieLoader) {
    switch (dataStorageConfiguration.getDataStorageFormat()) {
      case BONSAI:
        // just for PoC, explicitly set our bonsai context chain head:
        ((BonsaiWorldStateKeyValueStorage) worldStateStorage).getFlatDbStrategy()
            .updateBlockContext(blockchain.getChainHeadHeader());

        return new BonsaiWorldStateProvider(
            (BonsaiWorldStateKeyValueStorage) worldStateStorage,
            blockchain,
            Optional.of(dataStorageConfiguration.getBonsaiMaxLayersToLoad()),
            cachedMerkleTrieLoader,
            metricsSystem,
            besuComponent.map(BesuComponent::getBesuPluginContext).orElse(null));

      case FOREST:
      default:
        final WorldStatePreimageStorage preimageStorage =
            storageProvider.createWorldStatePreimageStorage();
        return new DefaultWorldStateArchive(worldStateStorage, preimageStorage);
    }
  }

  private ChainDataPruner createChainPruner(final BlockchainStorage blockchainStorage) {
    return new ChainDataPruner(
        blockchainStorage,
        new ChainDataPrunerStorage(
            storageProvider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.CHAIN_PRUNER_STATE)),
        chainPrunerConfiguration.getChainPruningBlocksRetained(),
        chainPrunerConfiguration.getChainPruningBlocksFrequency(),
        MonitoredExecutors.newBoundedThreadPool(
            ChainDataPruner.class.getSimpleName(),
            1,
            1,
            ChainDataPruner.MAX_PRUNING_THREAD_QUEUE_SIZE,
            metricsSystem));
  }

  /**
   * Create peer validators list.
   *
   * @param protocolSchedule the protocol schedule
   * @return the list
   */
  protected List<PeerValidator> createPeerValidators(final ProtocolSchedule protocolSchedule) {
    final List<PeerValidator> validators = new ArrayList<>();

    final OptionalLong daoBlock = configOptionsSupplier.get().getDaoForkBlock();
    if (daoBlock.isPresent()) {
      // Setup dao validator
      validators.add(
          new DaoForkPeerValidator(protocolSchedule, metricsSystem, daoBlock.getAsLong()));
    }

    final OptionalLong classicBlock = configOptionsSupplier.get().getClassicForkBlock();
    // setup classic validator
    if (classicBlock.isPresent()) {
      validators.add(
          new ClassicForkPeerValidator(protocolSchedule, metricsSystem, classicBlock.getAsLong()));
    }

    for (final Map.Entry<Long, Hash> requiredBlock : requiredBlocks.entrySet()) {
      validators.add(
          new RequiredBlocksPeerValidator(
              protocolSchedule, metricsSystem, requiredBlock.getKey(), requiredBlock.getValue()));
    }

    final CheckpointConfigOptions checkpointConfigOptions =
        genesisConfig.getConfigOptions(genesisConfigOverrides).getCheckpointOptions();
    if (SyncMode.X_CHECKPOINT.equals(syncConfig.getSyncMode())
        && checkpointConfigOptions.isValid()) {
      validators.add(
          new CheckpointBlocksPeerValidator(
              protocolSchedule,
              metricsSystem,
              checkpointConfigOptions.getNumber().orElseThrow(),
              checkpointConfigOptions.getHash().map(Hash::fromHexString).orElseThrow()));
    }
    return validators;
  }

  /**
   * Create additional plugin services plugin service factory.
   *
   * @param blockchain the blockchain
   * @param protocolContext the protocol context
   * @return the plugin service factory
   */
  protected abstract PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext);
}

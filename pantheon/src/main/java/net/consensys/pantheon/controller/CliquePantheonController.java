package net.consensys.pantheon.controller;

import static org.apache.logging.log4j.LogManager.getLogger;

import net.consensys.pantheon.consensus.clique.CliqueContext;
import net.consensys.pantheon.consensus.clique.CliqueVoteTallyUpdater;
import net.consensys.pantheon.consensus.clique.VoteTallyCache;
import net.consensys.pantheon.consensus.clique.blockcreation.CliqueBlockMiner;
import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.AbstractMiningCoordinator;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.BlockHashFunction;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Synchronizer;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.eth.EthProtocol;
import net.consensys.pantheon.ethereum.eth.manager.EthProtocolManager;
import net.consensys.pantheon.ethereum.eth.sync.DefaultSynchronizer;
import net.consensys.pantheon.ethereum.eth.sync.SyncMode;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import net.consensys.pantheon.ethereum.eth.transactions.TransactionPoolFactory;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import net.consensys.pantheon.ethereum.p2p.api.ProtocolManager;
import net.consensys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.RocksDbKeyValueStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.Logger;

public class CliquePantheonController
    implements PantheonController<CliqueContext, CliqueBlockMiner> {

  private static final Logger LOG = getLogger();
  private final GenesisConfig<CliqueContext> genesisConfig;
  private final ProtocolContext<CliqueContext> context;
  private final Synchronizer synchronizer;
  private final ProtocolManager ethProtocolManager;
  private final KeyPair keyPair;
  private final TransactionPool transactionPool;
  private final Runnable closer;

  CliquePantheonController(
      final GenesisConfig<CliqueContext> genesisConfig,
      final ProtocolContext<CliqueContext> context,
      final ProtocolManager ethProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final Runnable closer) {

    this.genesisConfig = genesisConfig;
    this.context = context;
    this.ethProtocolManager = ethProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.closer = closer;
  }

  public static PantheonController<CliqueContext, CliqueBlockMiner> init(
      final Path home,
      final GenesisConfig<CliqueContext> genesisConfig,
      final SynchronizerConfiguration taintedSyncConfig,
      final int networkId,
      final KeyPair nodeKeys)
      throws IOException {
    final RocksDbKeyValueStorage kv =
        RocksDbKeyValueStorage.create(Files.createDirectories(home.resolve(DATABASE_PATH)));
    final ProtocolSchedule<CliqueContext> protocolSchedule = genesisConfig.getProtocolSchedule();
    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);
    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(genesisConfig.getBlock(), kv, blockHashFunction);
    final KeyValueStorageWorldStateStorage worldStateStorage =
        new KeyValueStorageWorldStateStorage(kv);
    final WorldStateArchive worldStateArchive = new WorldStateArchive(worldStateStorage);
    genesisConfig.writeStateTo(worldStateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final EpochManager epochManger = new EpochManager(30_000);
    final ProtocolContext<CliqueContext> protocolContext =
        new ProtocolContext<>(
            blockchain,
            worldStateArchive,
            new CliqueContext(
                new VoteTallyCache(
                    blockchain, new CliqueVoteTallyUpdater(epochManger), epochManger),
                new VoteProposer()));

    final SynchronizerConfiguration syncConfig = taintedSyncConfig.validated(blockchain);
    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            protocolContext.getBlockchain(),
            genesisConfig.getChainId(),
            fastSyncEnabled,
            networkId);
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig, protocolSchedule, protocolContext, ethProtocolManager.ethContext());

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule, protocolContext, ethProtocolManager.ethContext());

    return new CliquePantheonController(
        genesisConfig,
        protocolContext,
        ethProtocolManager,
        synchronizer,
        nodeKeys,
        transactionPool,
        kv::close);
  }

  @Override
  public ProtocolContext<CliqueContext> getProtocolContext() {
    return context;
  }

  @Override
  public GenesisConfig<CliqueContext> getGenesisConfig() {
    return genesisConfig;
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
  public AbstractMiningCoordinator<CliqueContext, CliqueBlockMiner> getMiningCoordinator() {
    return null;
  }

  @Override
  public void close() {
    closer.run();
  }
}

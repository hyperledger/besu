package net.consensys.pantheon.controller;

import static net.consensys.pantheon.controller.KeyPairUtil.loadKeyPair;
import static org.apache.logging.log4j.LogManager.getLogger;

import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.DefaultBlockScheduler;
import net.consensys.pantheon.ethereum.blockcreation.EthHashMinerExecutor;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.blockcreation.MiningParameters;
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
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHeaderValidator;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import net.consensys.pantheon.ethereum.p2p.api.ProtocolManager;
import net.consensys.pantheon.ethereum.p2p.config.SubProtocolConfiguration;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.RocksDbKeyValueStorage;
import net.consensys.pantheon.util.time.SystemClock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;

public class MainnetPantheonController implements PantheonController<Void> {

  private static final Logger LOG = getLogger();
  public static final int MAINNET_NETWORK_ID = 1;

  private final GenesisConfig<Void> genesisConfig;
  private final ProtocolContext<Void> protocolContext;
  private final ProtocolManager ethProtocolManager;
  private final KeyPair keyPair;
  private final Synchronizer synchronizer;

  private final TransactionPool transactionPool;
  private final MiningCoordinator miningCoordinator;
  private final Runnable close;

  public MainnetPantheonController(
      final GenesisConfig<Void> genesisConfig,
      final ProtocolContext<Void> protocolContext,
      final ProtocolManager ethProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final MiningCoordinator miningCoordinator,
      final Runnable close) {
    this.genesisConfig = genesisConfig;
    this.protocolContext = protocolContext;
    this.ethProtocolManager = ethProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.miningCoordinator = miningCoordinator;
    this.close = close;
  }

  public static PantheonController<Void> mainnet(final Path home) throws IOException {
    final MiningParameters miningParams = new MiningParameters(null, null, null, false);
    final KeyPair nodeKeys = loadKeyPair(home);
    return init(
        home,
        GenesisConfig.mainnet(),
        SynchronizerConfiguration.builder().build(),
        miningParams,
        MAINNET_NETWORK_ID,
        nodeKeys);
  }

  public static PantheonController<Void> init(
      final Path home,
      final GenesisConfig<Void> genesisConfig,
      final SynchronizerConfiguration taintedSyncConfig,
      final MiningParameters miningParams,
      final int networkId,
      final KeyPair nodeKeys)
      throws IOException {
    final RocksDbKeyValueStorage kv =
        RocksDbKeyValueStorage.create(Files.createDirectories(home.resolve(DATABASE_PATH)));
    final ProtocolSchedule<Void> protocolSchedule = genesisConfig.getProtocolSchedule();
    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);
    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(genesisConfig.getBlock(), kv, blockHashFunction);
    final WorldStateArchive worldStateArchive =
        new WorldStateArchive(new KeyValueStorageWorldStateStorage(kv));
    genesisConfig.writeStateTo(worldStateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final ProtocolContext<Void> protocolContext =
        new ProtocolContext<>(blockchain, worldStateArchive, null);

    final SynchronizerConfiguration syncConfig = taintedSyncConfig.validated(blockchain);
    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    final EthProtocolManager ethProtocolManager =
        new EthProtocolManager(
            protocolContext.getBlockchain(),
            genesisConfig.getChainId(),
            fastSyncEnabled,
            syncConfig.downloaderParallelism());
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig, protocolSchedule, protocolContext, ethProtocolManager.ethContext());

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
                new SystemClock()));

    final MiningCoordinator miningCoordinator =
        new MiningCoordinator(protocolContext.getBlockchain(), executor);
    miningCoordinator.addMinedBlockObserver(ethProtocolManager);
    if (miningParams.isMiningEnabled()) {
      miningCoordinator.enable();
    }

    return new MainnetPantheonController(
        genesisConfig,
        protocolContext,
        ethProtocolManager,
        synchronizer,
        nodeKeys,
        transactionPool,
        miningCoordinator,
        () -> {
          miningCoordinator.disable();
          minerThreadPool.shutdownNow();
          try {
            minerThreadPool.awaitTermination(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            LOG.error("Failed to shutdown miner executor");
          }
          kv.close();
        });
  }

  @Override
  public ProtocolContext<Void> getProtocolContext() {
    return protocolContext;
  }

  @Override
  public GenesisConfig<Void> getGenesisConfig() {
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
  public MiningCoordinator getMiningCoordinator() {
    return miningCoordinator;
  }

  @Override
  public void close() {
    close.run();
  }
}

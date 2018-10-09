package net.consensys.pantheon.controller;

import static org.apache.logging.log4j.LogManager.getLogger;

import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.consensus.common.VoteTally;
import net.consensys.pantheon.consensus.ibft.IbftContext;
import net.consensys.pantheon.consensus.ibft.IbftEventQueue;
import net.consensys.pantheon.consensus.ibft.IbftProcessor;
import net.consensys.pantheon.consensus.ibft.IbftProtocolSchedule;
import net.consensys.pantheon.consensus.ibft.IbftStateMachine;
import net.consensys.pantheon.consensus.ibft.VoteTallyUpdater;
import net.consensys.pantheon.consensus.ibft.protocol.IbftProtocolManager;
import net.consensys.pantheon.consensus.ibft.protocol.IbftSubProtocol;
import net.consensys.pantheon.consensus.ibft.protocol.Istanbul64Protocol;
import net.consensys.pantheon.consensus.ibft.protocol.Istanbul64ProtocolManager;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
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
import net.consensys.pantheon.ethereum.p2p.wire.SubProtocol;
import net.consensys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import net.consensys.pantheon.services.kvstore.RocksDbKeyValueStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Logger;

public class IbftPantheonController implements PantheonController<IbftContext> {

  private static final int DEFAULT_ROUND_EXPIRY_MILLISECONDS = 10000;
  private static final Logger LOG = getLogger();
  private final GenesisConfig<IbftContext> genesisConfig;
  private final ProtocolContext<IbftContext> context;
  private final Synchronizer synchronizer;
  private final SubProtocol ethSubProtocol;
  private final ProtocolManager ethProtocolManager;
  private final IbftProtocolManager ibftProtocolManager;
  private final KeyPair keyPair;
  private final TransactionPool transactionPool;
  private final IbftProcessor ibftProcessor;
  private final Runnable closer;

  IbftPantheonController(
      final GenesisConfig<IbftContext> genesisConfig,
      final ProtocolContext<IbftContext> context,
      final SubProtocol ethSubProtocol,
      final ProtocolManager ethProtocolManager,
      final IbftProtocolManager ibftProtocolManager,
      final Synchronizer synchronizer,
      final KeyPair keyPair,
      final TransactionPool transactionPool,
      final IbftProcessor ibftProcessor,
      final Runnable closer) {

    this.genesisConfig = genesisConfig;
    this.context = context;
    this.ethSubProtocol = ethSubProtocol;
    this.ethProtocolManager = ethProtocolManager;
    this.ibftProtocolManager = ibftProtocolManager;
    this.synchronizer = synchronizer;
    this.keyPair = keyPair;
    this.transactionPool = transactionPool;
    this.ibftProcessor = ibftProcessor;
    this.closer = closer;
  }

  public static PantheonController<IbftContext> init(
      final Path home,
      final GenesisConfig<IbftContext> genesisConfig,
      final SynchronizerConfiguration taintedSyncConfig,
      final boolean ottomanTestnetOperation,
      final JsonObject ibftConfig,
      final int networkId,
      final KeyPair nodeKeys)
      throws IOException {
    final RocksDbKeyValueStorage kv =
        RocksDbKeyValueStorage.create(Files.createDirectories(home.resolve(DATABASE_PATH)));
    final ProtocolSchedule<IbftContext> protocolSchedule = genesisConfig.getProtocolSchedule();
    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);
    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(genesisConfig.getBlock(), kv, blockHashFunction);
    final KeyValueStorageWorldStateStorage worldStateStorage =
        new KeyValueStorageWorldStateStorage(kv);
    final WorldStateArchive worldStateArchive = new WorldStateArchive(worldStateStorage);
    genesisConfig.writeStateTo(worldStateArchive.getMutable(Hash.EMPTY_TRIE_HASH));

    final EpochManager epochManager =
        new EpochManager(IbftProtocolSchedule.getEpochLength(Optional.of(ibftConfig)));

    final VoteTally voteTally =
        new VoteTallyUpdater(epochManager).buildVoteTallyFromBlockchain(blockchain);

    final VoteProposer voteProposer = new VoteProposer();

    final ProtocolContext<IbftContext> protocolContext =
        new ProtocolContext<>(
            blockchain, worldStateArchive, new IbftContext(voteTally, voteProposer));

    final SynchronizerConfiguration syncConfig = taintedSyncConfig.validated(blockchain);
    final boolean fastSyncEnabled = syncConfig.syncMode().equals(SyncMode.FAST);
    EthProtocolManager ethProtocolManager;
    SubProtocol ethSubProtocol;
    if (ottomanTestnetOperation) {
      LOG.info("Operating on Ottoman testnet.");
      ethSubProtocol = Istanbul64Protocol.get();
      ethProtocolManager =
          new Istanbul64ProtocolManager(
              protocolContext.getBlockchain(), networkId, fastSyncEnabled, 1);
    } else {
      ethSubProtocol = EthProtocol.get();
      ethProtocolManager =
          new EthProtocolManager(protocolContext.getBlockchain(), networkId, fastSyncEnabled, 1);
    }
    final Synchronizer synchronizer =
        new DefaultSynchronizer<>(
            syncConfig, protocolSchedule, protocolContext, ethProtocolManager.ethContext());

    final IbftEventQueue ibftEventQueue = new IbftEventQueue();

    final IbftStateMachine ibftStateMachine = new IbftStateMachine();
    final IbftProcessor ibftProcessor =
        new IbftProcessor(
            ibftEventQueue,
            ibftConfig.getInteger("requestTimeout", DEFAULT_ROUND_EXPIRY_MILLISECONDS),
            ibftStateMachine);
    final ExecutorService processorExecutor = Executors.newSingleThreadExecutor();
    processorExecutor.submit(ibftProcessor);

    final Runnable closer =
        () -> {
          ibftProcessor.stop();
          processorExecutor.shutdownNow();
          try {
            processorExecutor.awaitTermination(5, TimeUnit.SECONDS);
          } catch (final InterruptedException e) {
            LOG.error("Failed to shutdown ibft processor executor");
          }
          kv.close();
        };

    final TransactionPool transactionPool =
        TransactionPoolFactory.createTransactionPool(
            protocolSchedule, protocolContext, ethProtocolManager.ethContext());

    return new IbftPantheonController(
        genesisConfig,
        protocolContext,
        ethSubProtocol,
        ethProtocolManager,
        new IbftProtocolManager(ibftEventQueue),
        synchronizer,
        nodeKeys,
        transactionPool,
        ibftProcessor,
        closer);
  }

  @Override
  public ProtocolContext<IbftContext> getProtocolContext() {
    return context;
  }

  @Override
  public GenesisConfig<IbftContext> getGenesisConfig() {
    return genesisConfig;
  }

  @Override
  public Synchronizer getSynchronizer() {
    return synchronizer;
  }

  @Override
  public SubProtocolConfiguration subProtocolConfiguration() {
    return new SubProtocolConfiguration()
        .withSubProtocol(ethSubProtocol, ethProtocolManager)
        .withSubProtocol(IbftSubProtocol.get(), ibftProtocolManager);
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
  public void close() {
    closer.run();
  }
}

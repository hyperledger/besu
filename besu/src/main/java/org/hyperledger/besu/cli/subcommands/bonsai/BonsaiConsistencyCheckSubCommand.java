package org.hyperledger.besu.cli.subcommands.bonsai;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.subcommands.bonsai.BonsaiConsistencyCheckSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateArchive;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.CachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.util.Optional;

import graphql.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(
    name = COMMAND_NAME,
    description =
        "Check that the bonsai database has a consistent chain head and worldstate, attempt to repair if not.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class BonsaiConsistencyCheckSubCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiConsistencyCheckSubCommand.class);
  private static final String ROCKSDB = "rocksdb";
  public static final String COMMAND_NAME = "bonsaiCheck";

  @CommandLine.Option(
      names = "--run",
      description = "Start besu after checking bonsai consistency.")
  private final Boolean runBesu = true;

  @CommandLine.ParentCommand BesuCommand besuCommand;

  @Override
  public void run() {
    checkCommand(besuCommand);

    final KeyValueStorageProvider storageProvider =
        besuCommand.keyValueStorageProvider(besuCommand.keyValueStorageName);
    final KeyValueStorage keyValueStorage =
        storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN);

    KeyValueStoragePrefixedKeyBlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            keyValueStorage, new MainnetBlockHeaderFunctions());

    BonsaiWorldStateKeyValueStorage bonsaiStorage =
        (BonsaiWorldStateKeyValueStorage)
            storageProvider.createWorldStateStorage(DataStorageFormat.BONSAI);

    maybeRepairChainAndWorldState(blockchainStorage, bonsaiStorage);

    if (runBesu) {
      besuCommand.run();
    }
  }

  @VisibleForTesting
  boolean maybeRepairChainAndWorldState(
      final KeyValueStoragePrefixedKeyBlockchainStorage blockchainStorage,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {

    Optional<BlockHeader> chainHeadHeader =
        blockchainStorage.getChainHead().flatMap(blockchainStorage::getBlockHeader);

    if (chainHeadHeader.isEmpty()) {
      LOG.info("No chain head found in the blockchain storage, not attempting to repair.");
      return false;
    }

    Optional<Hash> worldstateBlockHash =
        worldStateStorage.getWorldStateBlockHash().map(Bytes32.class::cast).map(Hash::wrap);

    if (worldstateBlockHash.isEmpty()) {
      LOG.info("Worldstate block hash is empty, not attempting to repair.");
      return false;
    }

    Optional<BlockHeader> worldstateBlockHeader =
        worldstateBlockHash.flatMap(blockchainStorage::getBlockHeader);

    if (worldstateBlockHeader.isEmpty()
        || worldstateBlockHeader.get().getNumber() != chainHeadHeader.get().getNumber()
        || !worldstateBlockHeader.get().getHash().equals(chainHeadHeader.get().getHash())) {
      LOG.info("Attempting to roll the worldstate to the chain head.");

      if (maybeRollWorldStateToHash(
          worldstateBlockHeader, chainHeadHeader.get(), worldStateStorage, blockchainStorage)) {
        LOG.info("Successfully rolled world state to the chain head.");
        return true;
      }

      if (worldstateBlockHeader.isPresent()
          && worldstateBlockHeader.get().getNumber() < chainHeadHeader.get().getNumber()) {
        LOG.info("Attempting to roll the chain head back to the worldstate head.");

        if (maybeRollBackChain(
            worldstateBlockHeader.get(), chainHeadHeader.get(), blockchainStorage)) {
          LOG.info("Successfully rolled world state to the chain head.");
          return true;
        }
      }
      exitWithError("Bonsai worldstate and blockchain are inconsistent.");
    }

    LOG.info("Bonsai worldstate and blockchain are consistent.");
    return false;
  }

  @VisibleForTesting
  boolean maybeRollWorldStateToHash(
      final Optional<BlockHeader> worldBlockHeader,
      final BlockHeader chainHeadHeader,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final BlockchainStorage blockchainStorage) {

    BonsaiWorldStateArchive archive = getWorldStateArchive(blockchainStorage, worldStateStorage);

    if (!archive
        .getMutable(chainHeadHeader.getStateRoot(), chainHeadHeader.getBlockHash())
        .isPresent()) {
      LOG.error(
          "Failed to roll worldstate state from {} to chain head {}.",
          worldBlockHeader.map(BlockHeader::toLogString).orElse("none"),
          chainHeadHeader.toLogString());
      return false;
    }
    return true;
  }

  @VisibleForTesting
  boolean maybeRollBackChain(
      final BlockHeader worldBlockHeader,
      final BlockHeader chainHeadHeader,
      final KeyValueStoragePrefixedKeyBlockchainStorage blockchainStorage) {

    // simple straight path back to worldstate block only:
    var updater = blockchainStorage.updater();
    BlockHeader currentHeader = chainHeadHeader;
    while (currentHeader.getNumber() > worldBlockHeader.getNumber()) {
      updater.removeBlockBody(currentHeader.getHash());
      updater.removeBlockHeader(currentHeader.getHash());
      updater.removeBlockHash(currentHeader.getNumber());

      var parentHeader = blockchainStorage.getBlockHeader(currentHeader.getParentHash());
      if (parentHeader.isPresent()) {
        currentHeader = parentHeader.get();
      } else {
        LOG.error("Failed to roll back chain to worldstate head.");
        updater.rollback();
        break;
      }
    }

    if (currentHeader.getHash().equals(worldBlockHeader.getHash())) {
      updater.setChainHead(currentHeader.getHash());
      updater.commit();
      return true;
    } else {
      LOG.error("Failed to roll back chain to worldstate head.");
      updater.rollback();
    }
    return false;
  }

  private void checkCommand(final BesuCommand besuCommand) {
    checkNotNull(besuCommand);
    DataStorageConfiguration storageConfig = besuCommand.dataStorageOptions.toDomainObject();
    if (storageConfig.getDataStorageFormat() != DataStorageFormat.BONSAI
        || !besuCommand.keyValueStorageName.equals(ROCKSDB)) {
      exitWithError(
          "Bonsai consistency check is only supported with RocksDB Bonsai storage format.");
    }
  }

  @VisibleForTesting
  BonsaiWorldStateArchive getWorldStateArchive(
      final BlockchainStorage blockchainStorage,
      final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    return new BonsaiWorldStateArchive(
        worldStateStorage,
        getBlockchain(blockchainStorage),
        Optional.empty(),
        new CachedMerkleTrieLoader(new NoOpMetricsSystem()));
  }

  @VisibleForTesting
  Blockchain getBlockchain(final BlockchainStorage blockchainStorage) {
    return DefaultBlockchain.create(blockchainStorage, new NoOpMetricsSystem(), Long.MAX_VALUE);
  }

  @VisibleForTesting
  void exitWithError(final String error) {
    LOG.error(error);
    System.exit(-1);
  }
}

package net.consensys.pantheon.ethereum.eth.manager.ethtaskutils;

import static net.consensys.pantheon.ethereum.core.InMemoryWorldState.createInMemoryWorldStateArchive;
import static org.assertj.core.util.Preconditions.checkArgument;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHashFunction;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockImporter;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import net.consensys.pantheon.ethereum.util.RawBlockIterator;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.rules.TemporaryFolder;

public class BlockchainSetupUtil<C> {
  private final GenesisConfig<C> genesisConfig;
  private final KeyValueStorage kvStore;
  private final MutableBlockchain blockchain;
  private final ProtocolContext<C> protocolContext;
  private final ProtocolSchedule<C> protocolSchedule;
  private final WorldStateArchive worldArchive;
  private final List<Block> blocks;
  private long maxBlockNumber;

  public BlockchainSetupUtil(
      final GenesisConfig<C> genesisConfig,
      final KeyValueStorage kvStore,
      final MutableBlockchain blockchain,
      final ProtocolContext<C> protocolContext,
      final ProtocolSchedule<C> protocolSchedule,
      final WorldStateArchive worldArchive,
      final List<Block> blocks) {
    this.genesisConfig = genesisConfig;
    this.kvStore = kvStore;
    this.blockchain = blockchain;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.worldArchive = worldArchive;
    this.blocks = blocks;
  }

  public Blockchain importAllBlocks() {
    importBlocks(blocks);
    return blockchain;
  }

  public void importFirstBlocks(final int count) {
    importBlocks(blocks.subList(0, count));
  }

  public void importBlockAtIndex(final int index) {
    importBlocks(Collections.singletonList(blocks.get(index)));
  }

  public Block getBlock(final int index) {
    checkArgument(index < blocks.size(), "Invalid block index");
    return blocks.get(index);
  }

  public int blockCount() {
    return blocks.size();
  }

  public static BlockchainSetupUtil<Void> forTesting() {
    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
    final TemporaryFolder temp = new TemporaryFolder();
    try {
      temp.create();
      final URL genesisFileUrl = getResourceUrl(temp, "testGenesis.json");
      final GenesisConfig<Void> genesisConfig =
          GenesisConfig.fromJson(
              Resources.toString(genesisFileUrl, Charsets.UTF_8), protocolSchedule);
      final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
      final MutableBlockchain blockchain =
          new DefaultMutableBlockchain(
              genesisConfig.getBlock(), kvStore, MainnetBlockHashFunction::createHash);
      final WorldStateArchive worldArchive = createInMemoryWorldStateArchive();

      genesisConfig.writeStateTo(worldArchive.getMutable());
      final ProtocolContext<Void> protocolContext =
          new ProtocolContext<>(blockchain, worldArchive, null);

      final Path blocksPath = getResourcePath(temp, "testBlockchain.blocks");
      final List<Block> blocks = new ArrayList<>();
      final BlockHashFunction blockHashFunction =
          ScheduleBasedBlockHashFunction.create(protocolSchedule);
      try (final RawBlockIterator iterator =
          new RawBlockIterator(blocksPath, rlp -> BlockHeader.readFrom(rlp, blockHashFunction))) {
        while (iterator.hasNext()) {
          blocks.add(iterator.next());
        }
      }
      return new BlockchainSetupUtil<>(
          genesisConfig,
          kvStore,
          blockchain,
          protocolContext,
          protocolSchedule,
          worldArchive,
          blocks);
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    } finally {
      temp.delete();
    }
  }

  private static Path getResourcePath(final TemporaryFolder temp, final String resource)
      throws IOException {
    final URL url = Resources.getResource(resource);
    final Path path =
        Files.write(
            temp.newFile().toPath(),
            Resources.toByteArray(url),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
    return path;
  }

  private static URL getResourceUrl(final TemporaryFolder temp, final String resource)
      throws IOException {
    final Path path = getResourcePath(temp, resource);
    return path.toUri().toURL();
  }

  public long getMaxBlockNumber() {
    return maxBlockNumber;
  }

  public GenesisConfig<C> getGenesisConfig() {
    return genesisConfig;
  }

  public KeyValueStorage getKvStore() {
    return kvStore;
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public ProtocolContext<C> getProtocolContext() {
    return protocolContext;
  }

  public ProtocolSchedule<C> getProtocolSchedule() {
    return protocolSchedule;
  }

  public WorldStateArchive getWorldArchive() {
    return worldArchive;
  }

  private void importBlocks(final List<Block> blocks) {
    for (final Block block : blocks) {
      if (block.getHeader().getNumber() == BlockHeader.GENESIS_BLOCK_NUMBER) {
        continue;
      }
      final ProtocolSpec<C> protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
      final BlockImporter<C> blockImporter = protocolSpec.getBlockImporter();
      final boolean result =
          blockImporter.importBlock(protocolContext, block, HeaderValidationMode.FULL);
      if (!result) {
        throw new IllegalStateException("Unable to import block " + block.getHeader().getNumber());
      }
    }
    this.maxBlockNumber = blockchain.getChainHeadBlockNumber();
  }
}

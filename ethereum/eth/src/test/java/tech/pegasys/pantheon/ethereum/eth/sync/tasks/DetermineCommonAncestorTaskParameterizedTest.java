package net.consensys.pantheon.ethereum.eth.sync.tasks;

import static net.consensys.pantheon.ethereum.core.InMemoryWorldState.createInMemoryWorldStateArchive;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthProtocolManager;
import net.consensys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import net.consensys.pantheon.ethereum.eth.manager.EthTask;
import net.consensys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;
import net.consensys.pantheon.util.uint.UInt256;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DetermineCommonAncestorTaskParameterizedTest {
  private final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  private static Block genesisBlock;
  private static KeyValueStorage localKvStore;
  private static DefaultMutableBlockchain localBlockchain;
  private static final int chainHeight = 50;
  private final int headerRequestSize;
  private final int commonAncestorHeight;

  private KeyValueStorage remoteKvStore;
  private DefaultMutableBlockchain remoteBlockchain;

  public DetermineCommonAncestorTaskParameterizedTest(
      final int headerRequestSize, final int commonAncestorHeight) {
    this.headerRequestSize = headerRequestSize;
    this.commonAncestorHeight = commonAncestorHeight;
  }

  @BeforeClass
  public static void setupClass() {
    genesisBlock = blockDataGenerator.genesisBlock();
    localKvStore = new InMemoryKeyValueStorage();
    localBlockchain =
        new DefaultMutableBlockchain(
            genesisBlock, localKvStore, MainnetBlockHashFunction::createHash);

    // Setup local chain
    for (int i = 1; i <= chainHeight; i++) {
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block = blockDataGenerator.block(options);
      final List<TransactionReceipt> receipts = blockDataGenerator.receipts(block);
      localBlockchain.appendBlock(block, receipts);
    }
  }

  @Before
  public void setup() {
    remoteKvStore = new InMemoryKeyValueStorage();
    remoteBlockchain =
        new DefaultMutableBlockchain(
            genesisBlock, remoteKvStore, MainnetBlockHashFunction::createHash);
  }

  @Parameters(name = "requestSize={0}, commonAncestor={1}")
  public static Collection<Object[]> parameters() throws IOException {
    final int[] requestSizes = {5, 12, chainHeight, chainHeight * 2};
    final List<Object[]> params = new ArrayList<>();
    for (final int requestSize : requestSizes) {
      for (int i = 0; i <= chainHeight; i++) {
        params.add(new Object[] {requestSize, i});
      }
    }
    return params;
  }

  @Test
  public void searchesAgainstNetwork() {
    BlockHeader commonHeader = genesisBlock.getHeader();
    for (long i = 1; i <= commonAncestorHeight; i++) {
      commonHeader = localBlockchain.getBlockHeader(i).get();
      final List<TransactionReceipt> receipts =
          localBlockchain.getTxReceipts(commonHeader.getHash()).get();
      final BlockBody commonBody = localBlockchain.getBlockBody(commonHeader.getHash()).get();
      remoteBlockchain.appendBlock(new Block(commonHeader, commonBody), receipts);
    }

    // Remaining blocks are disparate...
    for (long i = commonAncestorHeight + 1L; i <= chainHeight; i++) {
      final BlockDataGenerator.BlockOptions localOptions =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block localBlock = blockDataGenerator.block(localOptions);
      final List<TransactionReceipt> localReceipts = blockDataGenerator.receipts(localBlock);
      localBlockchain.appendBlock(localBlock, localReceipts);

      final BlockDataGenerator.BlockOptions remoteOptions =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(UInt256.ONE) // differentiator
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).get());
      final Block remoteBlock = blockDataGenerator.block(remoteOptions);
      final List<TransactionReceipt> remoteReceipts = blockDataGenerator.receipts(remoteBlock);
      remoteBlockchain.appendBlock(remoteBlock, remoteReceipts);
    }

    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestUtil.create(localBlockchain);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final AtomicReference<BlockHeader> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);

    final EthContext ethContext = ethProtocolManager.ethContext();
    final ProtocolContext<Void> protocolContext =
        new ProtocolContext<>(localBlockchain, createInMemoryWorldStateArchive(), null);

    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            headerRequestSize);

    final CompletableFuture<BlockHeader> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    future.whenComplete(
        (response, error) -> {
          actualResult.set(response);
          done.compareAndSet(false, true);
        });

    assertThat(actualResult.get()).isNotNull();
    assertThat(actualResult.get().getHash())
        .isEqualTo(MainnetBlockHashFunction.createHash(commonHeader));
  }
}

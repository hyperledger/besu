package net.consensys.pantheon.ethereum.eth.sync.tasks;

import static net.consensys.pantheon.ethereum.core.InMemoryWorldState.createInMemoryWorldStateArchive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.TransactionReceipt;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.eth.manager.EthProtocolManager;
import net.consensys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import net.consensys.pantheon.ethereum.eth.manager.EthTask;
import net.consensys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import net.consensys.pantheon.ethereum.eth.manager.exceptions.EthTaskException.FailureReason;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;
import net.consensys.pantheon.util.ExceptionUtils;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class DetermineCommonAncestorTaskTest {

  private final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private KeyValueStorage localKvStore;
  private DefaultMutableBlockchain localBlockchain;
  private final int defaultHeaderRequestSize = 10;
  Block genesisBlock;
  private EthProtocolManager ethProtocolManager;
  private EthContext ethContext;
  private ProtocolContext<Void> protocolContext;

  @Before
  public void setup() {
    genesisBlock = blockDataGenerator.genesisBlock();
    localKvStore = new InMemoryKeyValueStorage();
    localBlockchain =
        new DefaultMutableBlockchain(
            genesisBlock, localKvStore, MainnetBlockHashFunction::createHash);
    ethProtocolManager = EthProtocolManagerTestUtil.create(localBlockchain);
    ethContext = ethProtocolManager.ethContext();
    protocolContext =
        new ProtocolContext<>(localBlockchain, createInMemoryWorldStateArchive(), null);
  }

  @Test
  public void shouldThrowExceptionNoCommonBlock() {
    // Populate local chain
    for (long i = 1; i <= 9; i++) {
      final BlockDataGenerator.BlockOptions options00 =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block00 = blockDataGenerator.block(options00);
      final List<TransactionReceipt> receipts00 = blockDataGenerator.receipts(block00);
      localBlockchain.appendBlock(block00, receipts00);
    }

    // Populate remote chain
    final Block remoteGenesisBlock = blockDataGenerator.genesisBlock();
    final DefaultMutableBlockchain remoteBlockchain =
        new DefaultMutableBlockchain(
            remoteGenesisBlock,
            new InMemoryKeyValueStorage(),
            MainnetBlockHashFunction::createHash);
    for (long i = 1; i <= 9; i++) {
      final BlockDataGenerator.BlockOptions options01 =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(UInt256.ONE)
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block01 = blockDataGenerator.block(options01);
      final List<TransactionReceipt> receipts01 = blockDataGenerator.receipts(block01);
      remoteBlockchain.appendBlock(block01, receipts01);
    }

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize);

    final CompletableFuture<BlockHeader> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          failure.set(error);
        });

    assertThat(failure.get()).isNotNull();
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No common ancestor.");
  }

  @Test
  public void shouldFailIfPeerDisconnects() {
    final Block block = blockDataGenerator.nextBlock(localBlockchain.getChainHeadBlock());
    localBlockchain.appendBlock(block, blockDataGenerator.receipts(block));

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(localBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Disconnect the target peer
    respondingEthPeer.getEthPeer().disconnect(DisconnectReason.BREACH_OF_PROTOCOL);

    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize);

    // Execute task and wait for response
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final CompletableFuture<BlockHeader> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (response, error) -> {
          failure.set(error);
        });

    assertThat(failure.get()).isNotNull();
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(EthTaskException.class);
    assertThat(((EthTaskException) error).reason()).isEqualTo(FailureReason.PEER_DISCONNECTED);
  }

  @Test
  public void shouldCorrectlyCalculateSkipIntervalAndCount() {
    final long maximumPossibleCommonAncestorNumber = 100;
    final long minimumPossibleCommonAncestorNumber = 0;
    final int headerRequestSize = 10;

    final long range = maximumPossibleCommonAncestorNumber - minimumPossibleCommonAncestorNumber;
    final int skipInterval =
        DetermineCommonAncestorTask.calculateSkipInterval(range, headerRequestSize);
    final int count = DetermineCommonAncestorTask.calculateCount(range, skipInterval);

    assertThat(count).isEqualTo(11);
    assertThat(skipInterval).isEqualTo(9);
  }

  @Test
  public void shouldGracefullyHandleExecutionsForNoCommonAncestor() {
    // Populate local chain
    for (long i = 1; i <= 99; i++) {
      final BlockDataGenerator.BlockOptions options00 =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block00 = blockDataGenerator.block(options00);
      final List<TransactionReceipt> receipts00 = blockDataGenerator.receipts(block00);
      localBlockchain.appendBlock(block00, receipts00);
    }

    // Populate remote chain
    final Block remoteGenesisBlock = blockDataGenerator.genesisBlock();
    final DefaultMutableBlockchain remoteBlockchain =
        new DefaultMutableBlockchain(
            remoteGenesisBlock,
            new InMemoryKeyValueStorage(),
            MainnetBlockHashFunction::createHash);
    for (long i = 1; i <= 99; i++) {
      final BlockDataGenerator.BlockOptions options01 =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(UInt256.ONE)
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block01 = blockDataGenerator.block(options01);
      final List<TransactionReceipt> receipts01 = blockDataGenerator.receipts(block01);
      remoteBlockchain.appendBlock(block01, receipts01);
    }

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    final DetermineCommonAncestorTask<Void> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize);
    final DetermineCommonAncestorTask<Void> spy = spy(task);

    // Execute task
    final CompletableFuture<BlockHeader> future = spy.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          result.set(response);
        });

    Assertions.assertThat(result.get().getHash())
        .isEqualTo(MainnetBlockHashFunction.createHash(genesisBlock.getHeader()));

    verify(spy, times(2)).requestHeaders();
  }

  @Test
  public void shouldIssueConsistentNumberOfRequestsToPeer() {
    // Populate local chain
    for (long i = 1; i <= 100; i++) {
      final BlockDataGenerator.BlockOptions options00 =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block00 = blockDataGenerator.block(options00);
      final List<TransactionReceipt> receipts00 = blockDataGenerator.receipts(block00);
      localBlockchain.appendBlock(block00, receipts00);
    }

    // Populate remote chain
    final DefaultMutableBlockchain remoteBlockchain =
        new DefaultMutableBlockchain(
            genesisBlock, new InMemoryKeyValueStorage(), MainnetBlockHashFunction::createHash);
    for (long i = 1; i <= 100; i++) {
      final BlockDataGenerator.BlockOptions options01 =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(UInt256.ONE)
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block01 = blockDataGenerator.block(options01);
      final List<TransactionReceipt> receipts01 = blockDataGenerator.receipts(block01);
      remoteBlockchain.appendBlock(block01, receipts01);
    }

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    final DetermineCommonAncestorTask<Void> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize);
    final DetermineCommonAncestorTask<Void> spy = spy(task);

    // Execute task
    final CompletableFuture<BlockHeader> future = spy.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          result.set(response);
        });

    Assertions.assertThat(result.get().getHash())
        .isEqualTo(MainnetBlockHashFunction.createHash(genesisBlock.getHeader()));

    verify(spy, times(3)).requestHeaders();
  }

  @Test
  public void shouldShortCircuitOnHeaderInInitialRequest() {
    DefaultMutableBlockchain remoteBlockchain =
        new DefaultMutableBlockchain(
            genesisBlock, new InMemoryKeyValueStorage(), MainnetBlockHashFunction::createHash);

    Block commonBlock = null;

    // Populate common chain
    for (long i = 1; i <= 95; i++) {
      BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      commonBlock = blockDataGenerator.block(options);
      List<TransactionReceipt> receipts = blockDataGenerator.receipts(commonBlock);
      localBlockchain.appendBlock(commonBlock, receipts);
      remoteBlockchain.appendBlock(commonBlock, receipts);
    }

    // Populate local chain
    for (long i = 96; i <= 99; i++) {
      BlockDataGenerator.BlockOptions options00 =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      Block block00 = blockDataGenerator.block(options00);
      List<TransactionReceipt> receipts00 = blockDataGenerator.receipts(block00);
      localBlockchain.appendBlock(block00, receipts00);
    }

    // Populate remote chain
    for (long i = 96; i <= 99; i++) {
      BlockDataGenerator.BlockOptions options01 =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(UInt256.ONE)
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).get());
      Block block01 = blockDataGenerator.block(options01);
      List<TransactionReceipt> receipts01 = blockDataGenerator.receipts(block01);
      remoteBlockchain.appendBlock(block01, receipts01);
    }

    RespondingEthPeer.Responder responder = RespondingEthPeer.blockchainResponder(remoteBlockchain);
    RespondingEthPeer respondingEthPeer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    DetermineCommonAncestorTask<Void> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize);
    DetermineCommonAncestorTask<Void> spy = spy(task);

    // Execute task
    CompletableFuture<BlockHeader> future = spy.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    AtomicReference<BlockHeader> result = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          result.set(response);
        });

    Assertions.assertThat(result.get().getHash())
        .isEqualTo(MainnetBlockHashFunction.createHash(commonBlock.getHeader()));

    verify(spy, times(1)).requestHeaders();
  }

  @Test
  public void returnsImmediatelyWhenThereIsNoWorkToDo() throws Exception {
    final RespondingEthPeer respondingEthPeer =
        spy(EthProtocolManagerTestUtil.createPeer(ethProtocolManager));
    final EthPeer peer = spy(respondingEthPeer.getEthPeer());

    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule, protocolContext, ethContext, peer, defaultHeaderRequestSize);

    final CompletableFuture<BlockHeader> result = task.run();
    assertThat(result).isCompletedWithValue(genesisBlock.getHeader());

    // Make sure we didn't ask for any headers
    verify(peer, times(0)).getHeadersByHash(any(), anyInt(), anyBoolean(), anyInt());
    verify(peer, times(0)).getHeadersByNumber(anyLong(), anyInt(), anyBoolean(), anyInt());
    verify(peer, times(0)).send(any());
  }
}

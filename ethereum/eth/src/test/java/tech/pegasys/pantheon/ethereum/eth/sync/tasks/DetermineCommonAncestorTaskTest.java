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
package tech.pegasys.pantheon.ethereum.eth.sync.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.pantheon.ethereum.core.InMemoryTestFixture.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryTestFixture.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException.FailureReason;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;
import tech.pegasys.pantheon.util.ExceptionUtils;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class DetermineCommonAncestorTaskTest {

  private final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private MutableBlockchain localBlockchain;
  private final int defaultHeaderRequestSize = 10;
  Block genesisBlock;
  private EthProtocolManager ethProtocolManager;
  private EthContext ethContext;
  private ProtocolContext<Void> protocolContext;

  @Before
  public void setup() {
    genesisBlock = blockDataGenerator.genesisBlock();
    localBlockchain = createInMemoryBlockchain(genesisBlock);
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
    final MutableBlockchain remoteBlockchain = createInMemoryBlockchain(remoteGenesisBlock);
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
    final MutableBlockchain remoteBlockchain = createInMemoryBlockchain(remoteGenesisBlock);
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
    final MutableBlockchain remoteBlockchain = createInMemoryBlockchain(genesisBlock);
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
    final MutableBlockchain remoteBlockchain = createInMemoryBlockchain(genesisBlock);

    Block commonBlock = null;

    // Populate common chain
    for (long i = 1; i <= 95; i++) {
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      commonBlock = blockDataGenerator.block(options);
      final List<TransactionReceipt> receipts = blockDataGenerator.receipts(commonBlock);
      localBlockchain.appendBlock(commonBlock, receipts);
      remoteBlockchain.appendBlock(commonBlock, receipts);
    }

    // Populate local chain
    for (long i = 96; i <= 99; i++) {
      final BlockDataGenerator.BlockOptions options00 =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(localBlockchain.getBlockHashByNumber(i - 1).get());
      final Block block00 = blockDataGenerator.block(options00);
      final List<TransactionReceipt> receipts00 = blockDataGenerator.receipts(block00);
      localBlockchain.appendBlock(block00, receipts00);
    }

    // Populate remote chain
    for (long i = 96; i <= 99; i++) {
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
    verify(peer, times(0)).getHeadersByHash(any(), anyInt(), anyInt(), anyBoolean());
    verify(peer, times(0)).getHeadersByNumber(anyLong(), anyInt(), anyInt(), anyBoolean());
    verify(peer, times(0)).send(any());
  }
}

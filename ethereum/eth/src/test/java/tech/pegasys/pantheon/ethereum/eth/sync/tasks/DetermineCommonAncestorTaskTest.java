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

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.EthContext;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException;
import tech.pegasys.pantheon.ethereum.eth.manager.exceptions.EthTaskException.FailureReason;
import tech.pegasys.pantheon.ethereum.eth.manager.task.EthTask;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.ExceptionUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class DetermineCommonAncestorTaskTest {

  private final ProtocolSchedule<?> protocolSchedule = MainnetProtocolSchedule.create();
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final int defaultHeaderRequestSize = 10;
  private MutableBlockchain localBlockchain;
  private Block localGenesisBlock;
  private EthProtocolManager ethProtocolManager;
  private EthContext ethContext;
  private ProtocolContext<?> protocolContext;

  @Before
  public void setup() {
    localGenesisBlock = blockDataGenerator.genesisBlock();
    localBlockchain = createInMemoryBlockchain(localGenesisBlock);
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    ethProtocolManager = EthProtocolManagerTestUtil.create(localBlockchain, worldStateArchive);
    ethContext = ethProtocolManager.ethContext();
    protocolContext = new ProtocolContext<>(localBlockchain, worldStateArchive, null);
  }

  @Test
  public void shouldFailIfPeerDisconnects() {
    final Block block = blockDataGenerator.nextBlock(localBlockchain.getChainHeadBlock());
    localBlockchain.appendBlock(block, blockDataGenerator.receipts(block));

    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize,
            metricsSystem);

    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final CompletableFuture<BlockHeader> future = task.run();
    future.whenComplete(
        (response, error) -> {
          failure.set(error);
        });

    // Disconnect the target peer
    respondingEthPeer.disconnect(DisconnectReason.CLIENT_QUITTING);

    assertThat(failure.get()).isNotNull();
    final Throwable error = ExceptionUtils.rootCause(failure.get());
    assertThat(error).isInstanceOf(EthTaskException.class);
    assertThat(((EthTaskException) error).reason()).isEqualTo(FailureReason.PEER_DISCONNECTED);
  }

  @Test
  public void shouldHandleEmptyResponses() {
    final Blockchain remoteBlockchain = setupLocalAndRemoteChains(11, 11, 5);

    final RespondingEthPeer.Responder emptyResponder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer.Responder fullResponder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize,
            metricsSystem);

    // Empty response should be handled without any error
    final CompletableFuture<BlockHeader> future = task.run();
    respondingEthPeer.respond(emptyResponder);
    assertThat(future).isNotDone();

    // Task should continue on and complete when valid responses are received
    // Execute task and wait for response
    respondingEthPeer.respondWhile(fullResponder, () -> !future.isDone());

    assertThat(future).isDone();
    assertThat(future).isNotCompletedExceptionally();
    final BlockHeader expectedResult = remoteBlockchain.getBlockHeader(4).get();
    assertThat(future).isCompletedWithValue(expectedResult);
  }

  @Test
  public void calculateSkipInterval() {
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
  public void shouldIssueConsistentNumberOfRequestsToPeer() {
    final Blockchain remoteBlockchain = setupLocalAndRemoteChains(101, 101, 1);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    final DetermineCommonAncestorTask task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            defaultHeaderRequestSize,
            metricsSystem);
    final DetermineCommonAncestorTask spy = spy(task);

    // Execute task
    final CompletableFuture<BlockHeader> future = spy.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          result.set(response);
        });

    Assertions.assertThat(result.get().getHash())
        .isEqualTo(MainnetBlockHeaderFunctions.createHash(localGenesisBlock.getHeader()));

    verify(spy, times(3)).requestHeaders();
  }

  @Test
  public void shouldShortCircuitOnHeaderInInitialRequest() {
    final Blockchain remoteBlockchain = setupLocalAndRemoteChains(100, 100, 96);
    final BlockHeader commonHeader = localBlockchain.getBlockHeader(95).get();

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    final DetermineCommonAncestorTask task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            respondingEthPeer.getEthPeer(),
            10,
            metricsSystem);
    final DetermineCommonAncestorTask spy = spy(task);

    // Execute task
    final CompletableFuture<BlockHeader> future = spy.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());

    final AtomicReference<BlockHeader> result = new AtomicReference<>();
    future.whenComplete(
        (response, error) -> {
          result.set(response);
        });

    Assertions.assertThat(result.get().getHash())
        .isEqualTo(MainnetBlockHeaderFunctions.createHash(commonHeader));

    verify(spy, times(1)).requestHeaders();
  }

  @Test
  public void returnsImmediatelyWhenThereIsNoWorkToDo() throws Exception {
    final RespondingEthPeer respondingEthPeer =
        spy(EthProtocolManagerTestUtil.createPeer(ethProtocolManager));
    final EthPeer peer = spy(respondingEthPeer.getEthPeer());

    final EthTask<BlockHeader> task =
        DetermineCommonAncestorTask.create(
            protocolSchedule,
            protocolContext,
            ethContext,
            peer,
            defaultHeaderRequestSize,
            metricsSystem);

    final CompletableFuture<BlockHeader> result = task.run();
    assertThat(result).isCompletedWithValue(localGenesisBlock.getHeader());

    // Make sure we didn't ask for any headers
    verify(peer, times(0)).getHeadersByHash(any(), anyInt(), anyInt(), anyBoolean());
    verify(peer, times(0)).getHeadersByNumber(anyLong(), anyInt(), anyInt(), anyBoolean());
    verify(peer, times(0)).send(any());
  }

  /**
   * @param localBlockCount The number of local blocks to create. Highest block will be: {@code
   *     localBlockHeight} - 1.
   * @param remoteBlockCount The number of remote blocks to create. Highest block will be: {@code
   *     remoteBlockCount} - 1.
   * @param blocksInCommon The number of blocks shared between local and remote. If a common
   *     ancestor exists, its block number will be: {@code blocksInCommon} - 1
   * @return
   */
  private Blockchain setupLocalAndRemoteChains(
      final int localBlockCount, final int remoteBlockCount, final int blocksInCommon) {
    checkArgument(localBlockCount >= 1);
    checkArgument(remoteBlockCount >= 1);
    checkArgument(blocksInCommon >= 0);
    checkArgument(blocksInCommon <= Math.min(localBlockCount, remoteBlockCount));

    final Block remoteGenesis =
        (blocksInCommon > 0) ? localGenesisBlock : blockDataGenerator.genesisBlock();
    MutableBlockchain remoteChain = createInMemoryBlockchain(remoteGenesis);

    // Build common chain
    if (blocksInCommon > 1) {
      List<Block> commonBlocks =
          blockDataGenerator.blockSequence(remoteGenesis, blocksInCommon - 1);
      for (Block commonBlock : commonBlocks) {
        List<TransactionReceipt> receipts = blockDataGenerator.receipts(commonBlock);
        localBlockchain.appendBlock(commonBlock, receipts);
        remoteChain.appendBlock(commonBlock, receipts);
      }
    }

    // Build divergent local blocks
    if (localBlockCount > blocksInCommon) {
      Block localChainHead = localBlockchain.getChainHeadBlock();
      final int currentHeight =
          Math.toIntExact(
              localBlockchain.getChainHeadBlockNumber() - BlockHeader.GENESIS_BLOCK_NUMBER + 1);
      List<Block> localBlocks =
          blockDataGenerator.blockSequence(localChainHead, localBlockCount - currentHeight);
      for (Block localBlock : localBlocks) {
        List<TransactionReceipt> receipts = blockDataGenerator.receipts(localBlock);
        localBlockchain.appendBlock(localBlock, receipts);
      }
    }

    // Build divergent remote blocks
    if (remoteBlockCount > blocksInCommon) {
      Block remoteChainHead = remoteChain.getChainHeadBlock();
      final int currentHeight =
          Math.toIntExact(
              remoteChain.getChainHeadBlockNumber() - BlockHeader.GENESIS_BLOCK_NUMBER + 1);
      List<Block> remoteBlocks =
          blockDataGenerator.blockSequence(remoteChainHead, remoteBlockCount - currentHeight);
      for (Block remoteBlock : remoteBlocks) {
        List<TransactionReceipt> receipts = blockDataGenerator.receipts(remoteBlock);
        remoteChain.appendBlock(remoteBlock, receipts);
      }
    }

    return remoteChain;
  }
}

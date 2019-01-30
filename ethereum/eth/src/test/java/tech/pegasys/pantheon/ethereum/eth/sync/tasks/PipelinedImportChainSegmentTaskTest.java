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
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator.BlockOptions;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.AbstractMessageTaskTest;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV63;
import tech.pegasys.pantheon.ethereum.eth.sync.fullsync.FullSyncBlockHandler;
import tech.pegasys.pantheon.ethereum.eth.sync.tasks.exceptions.InvalidBlockException;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.Test;

public class PipelinedImportChainSegmentTaskTest
    extends AbstractMessageTaskTest<List<Block>, List<Block>> {

  @Override
  protected List<Block> generateDataToBeRequested() {
    final long chainHead = blockchain.getChainHeadBlockNumber();
    final long importSize = 5;
    final long startNumber = chainHead - importSize + 1;
    final List<Block> blocksToImport = new ArrayList<>();
    for (long i = 0; i < importSize; i++) {
      blocksToImport.add(getBlockAtNumber(startNumber + i));
    }
    return blocksToImport;
  }

  private Block getBlockAtNumber(final long number) {
    final BlockHeader header = blockchain.getBlockHeader(number).get();
    final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
    return new Block(header, body);
  }

  private CompletableFuture<List<Block>> validateAndImportBlocks(final List<Block> blocks) {
    return PersistBlockTask.forSequentialBlocks(
            protocolSchedule,
            protocolContext,
            blocks,
            HeaderValidationMode.SKIP_DETACHED,
            ethTasksTimer)
        .get();
  }

  @Override
  protected EthTask<List<Block>> createTask(final List<Block> requestedData) {
    final Block firstBlock = requestedData.get(0);
    final Block lastBlock = requestedData.get(requestedData.size() - 1);
    final Block previousBlock = getBlockAtNumber(firstBlock.getHeader().getNumber() - 1);
    final MutableBlockchain shortBlockchain = createShortChain(firstBlock.getHeader().getNumber());
    final ProtocolContext<Void> modifiedContext =
        new ProtocolContext<>(
            shortBlockchain,
            protocolContext.getWorldStateArchive(),
            protocolContext.getConsensusState());
    return PipelinedImportChainSegmentTask.forCheckpoints(
        protocolSchedule,
        modifiedContext,
        ethContext,
        1,
        ethTasksTimer,
        createBlockHandler(),
        HeaderValidationMode.DETACHED_ONLY,
        previousBlock.getHeader(),
        lastBlock.getHeader());
  }

  @Override
  protected void assertResultMatchesExpectation(
      final List<Block> requestedData, final List<Block> response, final EthPeer respondingPeer) {
    assertThat(response).isEqualTo(requestedData);
  }

  @Test
  public void betweenContiguousHeadersSucceeds() {
    // Setup a responsive peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup task and expectations
    final Block firstBlock = getBlockAtNumber(5L);
    final Block secondBlock = getBlockAtNumber(6L);
    final List<Block> expectedResult = Collections.singletonList(secondBlock);
    final MutableBlockchain shortBlockchain = createShortChain(firstBlock.getHeader().getNumber());
    final ProtocolContext<Void> modifiedContext =
        new ProtocolContext<>(
            shortBlockchain,
            protocolContext.getWorldStateArchive(),
            protocolContext.getConsensusState());
    final EthTask<List<Block>> task =
        PipelinedImportChainSegmentTask.forCheckpoints(
            protocolSchedule,
            modifiedContext,
            ethContext,
            1,
            ethTasksTimer,
            createBlockHandler(),
            HeaderValidationMode.DETACHED_ONLY,
            firstBlock.getHeader(),
            secondBlock.getHeader());

    // Sanity check
    assertThat(shortBlockchain.contains(secondBlock.getHash())).isFalse();

    // Execute task and wait for response
    final AtomicReference<List<Block>> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);

    final CompletableFuture<List<Block>> future = task.run();
    respondingPeer.respond(responder);
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertResultMatchesExpectation(expectedResult, actualResult.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void betweenUnconnectedHeadersFails() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    // Setup a responsive peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup data
    final Block fakeFirstBlock = gen.block(BlockOptions.create().setBlockNumber(5L));
    final Block firstBlock = getBlockAtNumber(5L);
    final Block secondBlock = getBlockAtNumber(6L);
    final Block thirdBlock = getBlockAtNumber(7L);

    // Setup task
    final MutableBlockchain shortBlockchain = createShortChain(firstBlock.getHeader().getNumber());
    final ProtocolContext<Void> modifiedContext =
        new ProtocolContext<>(
            shortBlockchain,
            protocolContext.getWorldStateArchive(),
            protocolContext.getConsensusState());
    final EthTask<List<Block>> task =
        PipelinedImportChainSegmentTask.forCheckpoints(
            protocolSchedule,
            modifiedContext,
            ethContext,
            1,
            ethTasksTimer,
            createBlockHandler(),
            HeaderValidationMode.DETACHED_ONLY,
            fakeFirstBlock.getHeader(),
            thirdBlock.getHeader());

    // Sanity check
    assertThat(shortBlockchain.contains(secondBlock.getHash())).isFalse();

    // Execute task and wait for response
    final AtomicReference<Throwable> actualError = new AtomicReference<>();
    final AtomicReference<List<Block>> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);

    final CompletableFuture<List<Block>> future = task.run();
    respondingPeer.respond(responder);
    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          actualError.set(error);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertThat(actualResult.get()).isNull();
    assertThat(actualError.get()).hasCauseInstanceOf(InvalidBlockException.class);
  }

  @Test
  public void shouldSyncInSequencesOfChunksSequentially() {
    // Setup a responsive peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup task for three chunks
    final List<BlockHeader> checkpointHeaders =
        LongStream.range(0, 13)
            .filter(n -> n % 4 == 0)
            .mapToObj(this::getBlockAtNumber)
            .map(Block::getHeader)
            .collect(Collectors.toList());
    final List<Block> expectedResult =
        LongStream.range(1, 13).mapToObj(this::getBlockAtNumber).collect(Collectors.toList());
    final MutableBlockchain shortBlockchain = createShortChain(0);
    final ProtocolContext<Void> modifiedContext =
        new ProtocolContext<>(
            shortBlockchain,
            protocolContext.getWorldStateArchive(),
            protocolContext.getConsensusState());
    final EthTask<List<Block>> task =
        PipelinedImportChainSegmentTask.forCheckpoints(
            protocolSchedule,
            modifiedContext,
            ethContext,
            1,
            ethTasksTimer,
            createBlockHandler(),
            HeaderValidationMode.DETACHED_ONLY,
            checkpointHeaders);

    // Execute task and wait for response
    final AtomicReference<List<Block>> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);

    final CompletableFuture<List<Block>> future = task.run();
    final CountingResponder countingResponder = CountingResponder.wrap(responder);

    // Import first segment's headers and bodies
    respondingPeer.respondTimes(countingResponder, 2);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(1);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(1);
    // Import second segment's headers and bodies
    respondingPeer.respondTimes(countingResponder, 2);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(2);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(2);
    // Import third segment's headers and bodies
    respondingPeer.respondTimes(countingResponder, 2);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(3);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(3);

    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertResultMatchesExpectation(expectedResult, actualResult.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void shouldPipelineChainSegmentImportsUpToMaxActiveChunks() {
    // Setup a responsive peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup task and expectations
    final List<BlockHeader> checkpointHeaders =
        LongStream.range(0, 13)
            .filter(n -> n % 4 == 0)
            .mapToObj(this::getBlockAtNumber)
            .map(Block::getHeader)
            .collect(Collectors.toList());
    final List<Block> expectedResult =
        LongStream.range(1, 13).mapToObj(this::getBlockAtNumber).collect(Collectors.toList());
    final MutableBlockchain shortBlockchain = createShortChain(0);
    final ProtocolContext<Void> modifiedContext =
        new ProtocolContext<>(
            shortBlockchain,
            protocolContext.getWorldStateArchive(),
            protocolContext.getConsensusState());
    final EthTask<List<Block>> task =
        PipelinedImportChainSegmentTask.forCheckpoints(
            protocolSchedule,
            modifiedContext,
            ethContext,
            2,
            ethTasksTimer,
            createBlockHandler(),
            HeaderValidationMode.DETACHED_ONLY,
            checkpointHeaders);

    // Execute task and wait for response
    final AtomicReference<List<Block>> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);

    final CompletableFuture<List<Block>> future = task.run();
    final CountingResponder countingResponder = CountingResponder.wrap(responder);

    // Import first segment's header
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(1);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(0);
    // Import first segment's body and second segment's header
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(2);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(1);
    // Import second segment's body and third segment's header
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(3);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(2);
    // Import third segment's body
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(3);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(3);

    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertResultMatchesExpectation(expectedResult, actualResult.get(), respondingPeer.getEthPeer());
  }

  @Test
  public void shouldPipelineChainSegmentImportsWithinMaxActiveChunks() {
    // Setup a responsive peer
    final Responder responder = RespondingEthPeer.blockchainResponder(blockchain);
    final RespondingEthPeer respondingPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Setup task and expectations
    final List<BlockHeader> checkpointHeaders =
        LongStream.range(0, 13)
            .filter(n -> n % 4 == 0)
            .mapToObj(this::getBlockAtNumber)
            .map(Block::getHeader)
            .collect(Collectors.toList());
    final List<Block> expectedResult =
        LongStream.range(1, 13).mapToObj(this::getBlockAtNumber).collect(Collectors.toList());
    final MutableBlockchain shortBlockchain = createShortChain(0);
    final ProtocolContext<Void> modifiedContext =
        new ProtocolContext<>(
            shortBlockchain,
            protocolContext.getWorldStateArchive(),
            protocolContext.getConsensusState());
    final EthTask<List<Block>> task =
        PipelinedImportChainSegmentTask.forCheckpoints(
            protocolSchedule,
            modifiedContext,
            ethContext,
            3,
            ethTasksTimer,
            createBlockHandler(),
            HeaderValidationMode.DETACHED_ONLY,
            checkpointHeaders);

    // Execute task and wait for response
    final AtomicReference<List<Block>> actualResult = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);

    final CompletableFuture<List<Block>> future = task.run();
    final CountingResponder countingResponder = CountingResponder.wrap(responder);

    // Import first segment's header
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(1);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(0);
    // Import first segment's body and second segment's header
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(2);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(1);
    // Import second segment's body and third segment's header
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(3);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(2);
    // Import third segment's body
    respondingPeer.respond(countingResponder);
    assertThat(countingResponder.getBlockHeaderMessages()).isEqualTo(3);
    assertThat(countingResponder.getBlockBodiesMessages()).isEqualTo(3);

    future.whenComplete(
        (result, error) -> {
          actualResult.set(result);
          done.compareAndSet(false, true);
        });

    assertThat(done).isTrue();
    assertResultMatchesExpectation(expectedResult, actualResult.get(), respondingPeer.getEthPeer());
  }

  private MutableBlockchain createShortChain(final long lastBlockToInclude) {
    final BlockHeader genesisHeader =
        blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get();
    final BlockBody genesisBody = blockchain.getBlockBody(genesisHeader.getHash()).get();
    final Block genesisBlock = new Block(genesisHeader, genesisBody);
    final MutableBlockchain shortChain = createInMemoryBlockchain(genesisBlock);
    long nextBlock = genesisHeader.getNumber() + 1;
    while (nextBlock <= lastBlockToInclude) {
      final BlockHeader header = blockchain.getBlockHeader(nextBlock).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      final List<TransactionReceipt> receipts = blockchain.getTxReceipts(header.getHash()).get();
      final Block block = new Block(header, body);
      shortChain.appendBlock(block, receipts);
      nextBlock++;
    }
    return shortChain;
  }

  private FullSyncBlockHandler<Void> createBlockHandler() {
    return new FullSyncBlockHandler<>(protocolSchedule, protocolContext, ethContext, ethTasksTimer);
  }

  private static class CountingResponder implements Responder {

    private final Responder delegate;
    private int getBlockHeaderMessages = 0;
    private int getBlockBodiesMessages = 0;
    private int getReceiptsMessages = 0;
    private int getNodeDataMessages = 0;

    private static CountingResponder wrap(final Responder delegate) {
      return new CountingResponder(delegate);
    }

    private CountingResponder(final Responder delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<MessageData> respond(final Capability cap, final MessageData msg) {
      final MessageData response = null;
      switch (msg.getCode()) {
        case EthPV62.GET_BLOCK_HEADERS:
          getBlockHeaderMessages++;
          break;
        case EthPV62.GET_BLOCK_BODIES:
          getBlockBodiesMessages++;
          break;
        case EthPV63.GET_RECEIPTS:
          getReceiptsMessages++;
          break;
        case EthPV63.GET_NODE_DATA:
          getNodeDataMessages++;
          break;
      }
      return delegate.respond(cap, msg);
    }

    public int getBlockHeaderMessages() {
      return getBlockHeaderMessages;
    }

    public int getBlockBodiesMessages() {
      return getBlockBodiesMessages;
    }

    public int getReceiptsMessages() {
      return getReceiptsMessages;
    }

    public int getNodeDataMessages() {
      return getNodeDataMessages;
    }
  }
}

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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.InMemoryTestFixture;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.eth.manager.AbstractPeerTask.PeerTaskResult;
import tech.pegasys.pantheon.ethereum.eth.manager.EthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.EthTask;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.AbstractMessageTaskTest;
import tech.pegasys.pantheon.ethereum.eth.messages.BlockHeadersMessage;
import tech.pegasys.pantheon.ethereum.eth.messages.EthPV62;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.collect.Lists;
import org.junit.Test;

public class ImportBlocksTaskTest
    extends AbstractMessageTaskTest<List<Block>, PeerTaskResult<List<Block>>> {

  @Override
  protected List<Block> generateDataToBeRequested() {
    final long chainHead = blockchain.getChainHeadBlockNumber();
    final long importSize = 5;
    final long startNumber = chainHead - importSize + 1;
    final List<Block> blocksToImport = new ArrayList<>();
    for (long i = 0; i < importSize; i++) {
      final BlockHeader header = blockchain.getBlockHeader(startNumber + i).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      blocksToImport.add(new Block(header, body));
    }
    return blocksToImport;
  }

  @Override
  protected EthTask<PeerTaskResult<List<Block>>> createTask(final List<Block> requestedData) {
    final Block firstBlock = requestedData.get(0);
    final MutableBlockchain shortBlockchain =
        createShortChain(firstBlock.getHeader().getNumber() - 1);
    final ProtocolContext<Void> modifiedContext =
        new ProtocolContext<>(
            shortBlockchain,
            protocolContext.getWorldStateArchive(),
            protocolContext.getConsensusState());
    return ImportBlocksTask.<Void>fromHeader(
        protocolSchedule,
        modifiedContext,
        ethContext,
        firstBlock.getHeader(),
        requestedData.size());
  }

  @Override
  protected void assertResultMatchesExpectation(
      final List<Block> requestedData,
      final PeerTaskResult<List<Block>> response,
      final EthPeer respondingPeer) {
    assertThat(response.getResult()).isEqualTo(requestedData);
    assertThat(response.getPeer()).isEqualTo(respondingPeer);
  }

  @Test
  public void completesWhenPeerReturnsPartialResult()
      throws ExecutionException, InterruptedException {

    // Respond with some headers and all corresponding bodies
    final Responder fullResponder = RespondingEthPeer.blockchainResponder(blockchain);
    final Responder partialResponder =
        (final Capability cap, final MessageData msg) -> {
          final Optional<MessageData> fullReponse = fullResponder.respond(cap, msg);
          if (msg.getCode() == EthPV62.GET_BLOCK_HEADERS) {
            // Return a partial headers response
            final BlockHeadersMessage headersMessage =
                BlockHeadersMessage.readFrom(fullReponse.get());
            final List<BlockHeader> originalHeaders =
                Lists.newArrayList(headersMessage.getHeaders(protocolSchedule));
            final List<BlockHeader> partialHeaders =
                originalHeaders.subList(0, originalHeaders.size() / 2);
            return Optional.of(BlockHeadersMessage.create(partialHeaders));
          }
          return fullReponse;
        };

    final RespondingEthPeer peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task
    final AtomicReference<List<Block>> actualResult = new AtomicReference<>();
    final AtomicReference<EthPeer> actualPeer = new AtomicReference<>();
    final AtomicBoolean done = new AtomicBoolean(false);
    final List<Block> requestedData = generateDataToBeRequested();
    final EthTask<PeerTaskResult<List<Block>>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<List<Block>>> future = task.run();
    future.whenComplete(
        (response, error) -> {
          actualResult.set(response.getResult());
          actualPeer.set(response.getPeer());
          done.compareAndSet(false, true);
        });

    // Send partial responses
    peer.respondWhile(partialResponder, () -> !future.isDone());

    assertThat(done).isTrue();
    assertThat(actualPeer.get()).isEqualTo(peer.getEthPeer());
    assertThat(actualResult.get().size()).isLessThan(requestedData.size());
    for (final Block block : actualResult.get()) {
      assertThat(requestedData).contains(block);
      assertThat(blockchain.contains(block.getHash())).isTrue();
    }
  }

  @Test
  public void completesWhenPeersSendEmptyResponses()
      throws ExecutionException, InterruptedException {
    // Setup a unresponsive peer
    final Responder responder = RespondingEthPeer.emptyResponder();
    final RespondingEthPeer respondingEthPeer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager);

    // Execute task and wait for response
    final AtomicBoolean done = new AtomicBoolean(false);
    final List<Block> requestedData = generateDataToBeRequested();
    final EthTask<PeerTaskResult<List<Block>>> task = createTask(requestedData);
    final CompletableFuture<PeerTaskResult<List<Block>>> future = task.run();
    respondingEthPeer.respondWhile(responder, () -> !future.isDone());
    future.whenComplete(
        (response, error) -> {
          done.compareAndSet(false, true);
        });
    assertThat(future.isDone()).isTrue();
    assertThat(future.isCompletedExceptionally()).isFalse();
  }

  private MutableBlockchain createShortChain(final long truncateAtBlockNumber) {
    final BlockHeader genesisHeader =
        blockchain.getBlockHeader(BlockHeader.GENESIS_BLOCK_NUMBER).get();
    final BlockBody genesisBody = blockchain.getBlockBody(genesisHeader.getHash()).get();
    final Block genesisBlock = new Block(genesisHeader, genesisBody);
    final MutableBlockchain shortChain = InMemoryTestFixture.createInMemoryBlockchain(genesisBlock);
    long nextBlock = genesisHeader.getNumber() + 1;
    while (nextBlock <= truncateAtBlockNumber) {
      final BlockHeader header = blockchain.getBlockHeader(nextBlock).get();
      final BlockBody body = blockchain.getBlockBody(header.getHash()).get();
      final List<TransactionReceipt> receipts = blockchain.getTxReceipts(header.getHash()).get();
      final Block block = new Block(header, body);
      shortChain.appendBlock(block, receipts);
      nextBlock++;
    }
    return shortChain;
  }
}

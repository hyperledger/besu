/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BackwardSyncPhaseTest {

  public static final int REMOTE_HEIGHT = 50;
  public static final int LOCAL_HEIGHT = 25;
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private BackwardSyncContext context;

  private final ProtocolSchedule protocolSchedule =
      MainnetProtocolSchedule.fromConfig(new StubGenesisConfigOptions());

  private MutableBlockchain remoteBlockchain;
  private RespondingEthPeer peer;
  GenericKeyValueStorageFacade<Hash, BlockHeader> headersStorage;
  GenericKeyValueStorageFacade<Hash, Block> blocksStorage;

  @Before
  public void setup() {
    headersStorage =
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            new BlocksHeadersConvertor(new MainnetBlockHeaderFunctions()),
            new InMemoryKeyValueStorage());
    blocksStorage =
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            new BlocksConvertor(new MainnetBlockHeaderFunctions()),
            new InMemoryKeyValueStorage());

    Block genesisBlock = blockDataGenerator.genesisBlock();
    remoteBlockchain = createInMemoryBlockchain(genesisBlock);
    MutableBlockchain localBlockchain = createInMemoryBlockchain(genesisBlock);

    for (int i = 1; i <= REMOTE_HEIGHT; i++) {
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(remoteBlockchain.getBlockHashByNumber(i - 1).orElseThrow());
      final Block block = blockDataGenerator.block(options);
      final List<TransactionReceipt> receipts = blockDataGenerator.receipts(block);

      remoteBlockchain.appendBlock(block, receipts);
      if (i <= LOCAL_HEIGHT) {
        localBlockchain.appendBlock(block, receipts);
      }
    }
    when(context.getProtocolContext().getBlockchain()).thenReturn(localBlockchain);
    when(context.getProtocolSchedule()).thenReturn(protocolSchedule);
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();

    peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    EthContext ethContext = ethProtocolManager.ethContext();
    when(context.getEthContext()).thenReturn(ethContext);
  }

  @Test
  public void shouldWaitWhenTTDNotReached()
      throws ExecutionException, InterruptedException, TimeoutException {
    final BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT + 3);
    when(context.isOnTTD()).thenReturn(false).thenReturn(false).thenReturn(true);
    BackwardSyncPhase step = new BackwardSyncPhase(context, backwardChain);
    step.waitForTTD();
    verify(context, Mockito.times(2)).getEthContext();
  }

  @Test
  public void shouldFindHeaderWhenRequested() throws Exception {
    final BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT + 3);
    when(context.isOnTTD()).thenReturn(true);
    BackwardSyncPhase step = new BackwardSyncPhase(context, backwardChain);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);

    final CompletableFuture<Void> future = step.executeStep();
    peer.respondWhileOtherThreadsWork(responder, () -> !future.isDone());
    future.get();
  }

  @Test
  public void shouldFindHashToSync() {

    BackwardSyncPhase step =
        new BackwardSyncPhase(context, createBackwardChain(REMOTE_HEIGHT - 4, REMOTE_HEIGHT));

    final Hash hash = step.earliestUnprocessedHash(null);

    assertThat(hash).isEqualTo(getBlockByNumber(REMOTE_HEIGHT - 4).getHeader().getParentHash());
  }

  @Test
  public void shouldFailWhenNothingToSync() {
    final BackwardChain chain = createBackwardChain(REMOTE_HEIGHT);
    chain.dropFirstHeader();
    BackwardSyncPhase step = new BackwardSyncPhase(context, chain);
    assertThatThrownBy(() -> step.earliestUnprocessedHash(null))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("No unprocessed hashes during backward sync");
  }

  @Test
  public void shouldRequestHeaderWhenAsked() throws Exception {
    BackwardSyncPhase step = new BackwardSyncPhase(context, createBackwardChain(REMOTE_HEIGHT - 1));
    final Block lookingForBlock = getBlockByNumber(REMOTE_HEIGHT - 2);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);

    final CompletableFuture<BlockHeader> future =
        step.requestHeader(lookingForBlock.getHeader().getHash());
    peer.respondWhileOtherThreadsWork(responder, () -> !future.isDone());

    final BlockHeader blockHeader = future.get();
    assertThat(blockHeader).isEqualTo(lookingForBlock.getHeader());
  }

  @Test
  public void shouldThrowWhenResponseIsEmptyWhenRequestingHeader() throws Exception {
    BackwardSyncPhase step = new BackwardSyncPhase(context, createBackwardChain(REMOTE_HEIGHT - 1));
    final Block lookingForBlock = getBlockByNumber(REMOTE_HEIGHT - 2);

    final RespondingEthPeer.Responder responder = RespondingEthPeer.emptyResponder();

    final CompletableFuture<BlockHeader> future =
        step.requestHeader(lookingForBlock.getHeader().getHash());
    peer.respondWhileOtherThreadsWork(responder, () -> !future.isDone());

    assertThatThrownBy(future::get)
        .getCause()
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("Did not receive a header for hash");
  }

  @Test
  public void shouldSaveHeaderDelegatesProperly() {
    final BackwardChain chain = Mockito.mock(BackwardChain.class);
    final BlockHeader header = Mockito.mock(BlockHeader.class);

    when(header.getNumber()).thenReturn(12345L);

    BackwardSyncPhase step = new BackwardSyncPhase(context, chain);

    step.saveHeader(header);

    verify(chain).prependAncestorsHeader(header);
    verify(context).putCurrentChainToHeight(12345L, chain);
  }

  @Test
  public void shouldMergeWhenPossible() {
    BackwardChain backwardChain = createBackwardChain(REMOTE_HEIGHT - 3, REMOTE_HEIGHT);
    backwardChain = spy(backwardChain);
    BackwardSyncPhase step = new BackwardSyncPhase(context, backwardChain);

    final BackwardChain historicalChain =
        createBackwardChain(REMOTE_HEIGHT - 10, REMOTE_HEIGHT - 4);
    when(context.findCorrectChainFromPivot(REMOTE_HEIGHT - 4))
        .thenReturn(Optional.of(historicalChain));

    assertThat(backwardChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(REMOTE_HEIGHT - 3).getHeader());
    step.possibleMerge(null);
    assertThat(backwardChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(REMOTE_HEIGHT - 10).getHeader());

    verify(backwardChain).prependChain(historicalChain);
  }

  @Test
  public void shouldNotMergeWhenNotPossible() {
    BackwardChain backwardChain = createBackwardChain(REMOTE_HEIGHT - 5, REMOTE_HEIGHT);
    backwardChain = spy(backwardChain);
    when(context.findCorrectChainFromPivot(any(Long.class))).thenReturn(Optional.empty());
    BackwardSyncPhase step = new BackwardSyncPhase(context, backwardChain);

    step.possibleMerge(null);

    verify(backwardChain, never()).prependChain(any());
  }

  @Test
  public void shouldFinishWhenNoMoreSteps() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT, LOCAL_HEIGHT + 10);
    BackwardSyncPhase step = new BackwardSyncPhase(context, backwardChain);

    final CompletableFuture<Void> completableFuture =
        step.possiblyMoreBackwardSteps(getBlockByNumber(LOCAL_HEIGHT).getHeader());

    assertThat(completableFuture.isDone()).isTrue();
    assertThat(completableFuture.isCompletedExceptionally()).isFalse();
  }

  @Test
  public void shouldFinishExceptionallyWhenHeaderIsBellowBlockchainHeightButUnknown() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT, LOCAL_HEIGHT + 10);
    BackwardSyncPhase step = new BackwardSyncPhase(context, backwardChain);

    final CompletableFuture<Void> completableFuture =
        step.possiblyMoreBackwardSteps(
            ChainForTestCreator.createEmptyBlock((long) LOCAL_HEIGHT - 1).getHeader());

    assertThat(completableFuture.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void shouldCreateAnotherStepWhenThereIsWorkToBeDone() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT + 3, LOCAL_HEIGHT + 10);
    BackwardSyncPhase step = spy(new BackwardSyncPhase(context, backwardChain));

    step.possiblyMoreBackwardSteps(backwardChain.getFirstAncestorHeader().orElseThrow());

    verify(step).executeAsync(any());
  }

  private BackwardChain createBackwardChain(final int from, final int until) {
    BackwardChain chain = createBackwardChain(until);
    for (int i = until; i > from; --i) {
      chain.prependAncestorsHeader(getBlockByNumber(i - 1).getHeader());
    }
    return chain;
  }

  @NotNull
  private BackwardChain createBackwardChain(final int number) {
    return new BackwardChain(
        headersStorage, blocksStorage, remoteBlockchain.getBlockByNumber(number).orElseThrow());
  }

  @NotNull
  private Block getBlockByNumber(final int number) {
    return remoteBlockchain.getBlockByNumber(number).orElseThrow();
  }
}

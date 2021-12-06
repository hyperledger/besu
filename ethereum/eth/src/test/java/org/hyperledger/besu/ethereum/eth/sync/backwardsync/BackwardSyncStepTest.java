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
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BackwardSyncStepTest {

  public static final int REMOTE_HEIGHT = 50;
  public static final int LOCAL_HEIGHT = 25;
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private BackwardsSyncContext context;

  private MutableBlockchain remoteBlockchain;
  private RespondingEthPeer peer;

  private final ProtocolSchedule protocolSchedule =
      MainnetProtocolSchedule.fromConfig(new StubGenesisConfigOptions());

  @Before
  public void setup() {
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
  public void shouldFindHeaderWhenRequested() throws Exception {
    final BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT + 3);
    BackwardSyncStep step = new BackwardSyncStep(context, backwardChain);

    when(context.getCurrentChain()).thenReturn(Optional.of(backwardChain));
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);

    final CompletableFuture<Void> future = step.executeStep();
    peer.respondWhile(responder, () -> !future.isDone());
    future.get();
  }

  @Test
  public void shouldFindHashToSync() {

    BackwardSyncStep step =
        new BackwardSyncStep(context, createBackwardChain(REMOTE_HEIGHT - 4, REMOTE_HEIGHT));

    final Hash hash = step.earliestUnprocessedHash();

    assertThat(hash).isEqualTo(getBlockByNumber(REMOTE_HEIGHT - 4).getHeader().getParentHash());
  }

  @Test
  public void shouldFailWhenNothingToSync() {
    final BackwardChain chain = createBackwardChain(REMOTE_HEIGHT);
    chain.dropFirstHeader();
    BackwardSyncStep step = new BackwardSyncStep(context, chain);
    assertThatThrownBy(step::earliestUnprocessedHash)
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("No unprocessed hashes during backward sync");
  }

  @Test
  public void shouldRequestHeaderWhenAsked() throws Exception {
    BackwardSyncStep step = new BackwardSyncStep(context, createBackwardChain(REMOTE_HEIGHT - 1));
    final Block lookingForBlock = getBlockByNumber(REMOTE_HEIGHT - 2);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);

    final CompletableFuture<BlockHeader> future =
        step.requestHeader(lookingForBlock.getHeader().getHash());
    peer.respondWhile(responder, () -> !future.isDone());

    final BlockHeader blockHeader = future.get();
    assertThat(blockHeader).isEqualTo(lookingForBlock.getHeader());
  }

  @Test
  public void shouldThrowWhenResponseIsEmptyWhenRequestingHeader() throws Exception {
    BackwardSyncStep step = new BackwardSyncStep(context, createBackwardChain(REMOTE_HEIGHT - 1));
    final Block lookingForBlock = getBlockByNumber(REMOTE_HEIGHT - 2);

    final RespondingEthPeer.Responder responder = RespondingEthPeer.emptyResponder();

    final CompletableFuture<BlockHeader> future =
        step.requestHeader(lookingForBlock.getHeader().getHash());
    peer.respondWhile(responder, () -> !future.isDone());

    assertThatThrownBy(future::get)
        .getCause()
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("Did not receive a header for hash");
  }

  @Test
  public void shouldSaveHeaderDelegatesProperly() {
    final BackwardChain chain = Mockito.mock(BackwardChain.class);
    final BlockHeader header = Mockito.mock(BlockHeader.class);
    BackwardSyncStep step = new BackwardSyncStep(context, chain);

    step.saveHeader(header);

    verify(chain).saveHeader(header);
  }

  @Test
  public void shouldMergeWhenPossible() {
    BackwardChain backwardChain = createBackwardChain(REMOTE_HEIGHT - 3, REMOTE_HEIGHT);
    backwardChain = spy(backwardChain);
    BackwardSyncStep step = new BackwardSyncStep(context, backwardChain);

    final BackwardChain historicalChain =
        createBackwardChain(REMOTE_HEIGHT - 10, REMOTE_HEIGHT - 4);
    when(context.findCorrectChainFromPivot(REMOTE_HEIGHT - 4)).thenReturn(historicalChain);

    assertThat(backwardChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(REMOTE_HEIGHT - 3).getHeader());
    step.possibleMerge(null);
    assertThat(backwardChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(REMOTE_HEIGHT - 10).getHeader());

    verify(backwardChain).merge(historicalChain);
  }

  @Test
  public void shouldNotMergeWhenNotPossible() {
    BackwardChain backwardChain = createBackwardChain(REMOTE_HEIGHT - 5, REMOTE_HEIGHT);
    backwardChain = spy(backwardChain);
    when(context.findCorrectChainFromPivot(any(Long.class))).thenReturn(null);
    BackwardSyncStep step = new BackwardSyncStep(context, backwardChain);

    step.possibleMerge(null);

    verify(backwardChain, never()).merge(any());
  }

  @Test
  public void shouldFinishWhenNoMoreSteps() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT, LOCAL_HEIGHT + 10);
    BackwardSyncStep step = new BackwardSyncStep(context, backwardChain);

    final CompletableFuture<Void> completableFuture =
        step.possiblyMoreBackwardSteps(getBlockByNumber(LOCAL_HEIGHT).getHeader());

    assertThat(completableFuture.isDone()).isTrue();
    assertThat(completableFuture.isCompletedExceptionally()).isFalse();
  }

  @Test
  public void shouldFinishExceptionallyWhenHeaderIsBellowBlockchainHeightButUnknown() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT, LOCAL_HEIGHT + 10);
    BackwardSyncStep step = new BackwardSyncStep(context, backwardChain);

    final CompletableFuture<Void> completableFuture =
        step.possiblyMoreBackwardSteps(
            ChainForTestCreator.createEmptyBlock((long) LOCAL_HEIGHT - 1).getHeader());

    assertThat(completableFuture.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void shouldCreateAnotherStepWhenThereIsWorkToBeDone() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT + 3, LOCAL_HEIGHT + 10);
    BackwardSyncStep step = spy(new BackwardSyncStep(context, backwardChain));

    step.possiblyMoreBackwardSteps(backwardChain.getFirstAncestorHeader().orElseThrow());

    verify(step).executeAsync(any());
  }

  private BackwardChain createBackwardChain(final int from, final int until) {
    BackwardChain chain = createBackwardChain(until);
    for (int i = until; i > from; --i) {
      chain.saveHeader(getBlockByNumber(i - 1).getHeader());
    }
    return chain;
  }

  @NotNull
  private BackwardChain createBackwardChain(final int number) {
    return new BackwardChain(remoteBlockchain.getBlockByNumber(number).orElseThrow());
  }

  @NotNull
  private Block getBlockByNumber(final int number) {
    return remoteBlockchain.getBlockByNumber(number).orElseThrow();
  }
}

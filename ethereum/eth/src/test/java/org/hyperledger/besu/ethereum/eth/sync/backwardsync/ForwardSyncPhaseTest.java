/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
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
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ForwardSyncPhaseTest {

  public static final int REMOTE_HEIGHT = 50;
  public static final int LOCAL_HEIGHT = 25;
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private BackwardSyncContext context;

  private MutableBlockchain remoteBlockchain;
  private RespondingEthPeer peer;

  private final ProtocolSchedule protocolSchedule =
      MainnetProtocolSchedule.fromConfig(new StubGenesisConfigOptions());
  private MutableBlockchain localBlockchain;
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
    localBlockchain = createInMemoryBlockchain(genesisBlock);

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

    when(context.getBlockValidator(anyLong()).validateAndProcessBlock(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final Object[] arguments = invocation.getArguments();
              Block block = (Block) arguments[1];
              return new Result(
                  new BlockValidator.BlockProcessingOutputs(
                      new ReferenceTestWorldState(), blockDataGenerator.receipts(block)));
            });
  }

  @Test
  public void shouldExecuteForwardSyncWhenPossible() throws Exception {
    final BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT, LOCAL_HEIGHT + 3);
    ForwardSyncPhase step = new ForwardSyncPhase(context, backwardChain);

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);

    final CompletableFuture<Void> completableFuture = step.executeStep();

    peer.respondWhile(
        responder,
        () -> {
          try {
            Thread.sleep(5);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return !completableFuture.isDone();
        });

    completableFuture.get();
  }

  @Test
  public void shouldDropHeadersAsLongAsWeKnowThem() {
    final BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT - 5, LOCAL_HEIGHT + 3);
    ForwardSyncPhase step = new ForwardSyncPhase(context, backwardChain);

    assertThat(backwardChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(LOCAL_HEIGHT - 5).getHeader());
    step.returnFirstNUnknownHeaders(null);
    assertThat(backwardChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(LOCAL_HEIGHT + 1).getHeader());
  }

  @Test
  public void shouldDropBlocksThatWeTrust() {
    final BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT - 5, LOCAL_HEIGHT);
    backwardChain.appendExpectedBlock(getBlockByNumber(LOCAL_HEIGHT + 1));
    final BackwardChain finalChain = createBackwardChain(LOCAL_HEIGHT + 2, LOCAL_HEIGHT + 5);
    finalChain.prependChain(backwardChain);

    ForwardSyncPhase step = new ForwardSyncPhase(context, finalChain);

    assertThat(finalChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(LOCAL_HEIGHT - 5).getHeader());
    step.processKnownAncestors(null);
    assertThat(finalChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(LOCAL_HEIGHT + 2).getHeader());
  }

  @Test
  public void shouldMergeEvenLongerChains() {
    final BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT - 5, LOCAL_HEIGHT + 7);
    backwardChain.appendExpectedBlock(getBlockByNumber(LOCAL_HEIGHT + 1));
    final BackwardChain finalChain = createBackwardChain(LOCAL_HEIGHT + 2, LOCAL_HEIGHT + 5);
    finalChain.prependChain(backwardChain);

    ForwardSyncPhase step = new ForwardSyncPhase(context, finalChain);

    assertThat(finalChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(LOCAL_HEIGHT - 5).getHeader());
    step.processKnownAncestors(null);
    assertThat(finalChain.getFirstAncestorHeader().orElseThrow())
        .isEqualTo(getBlockByNumber(LOCAL_HEIGHT + 2).getHeader());
  }

  @Test
  public void shouldNotRequestWhenNull() {
    ForwardSyncPhase phase = new ForwardSyncPhase(null, null);
    final CompletableFuture<Void> completableFuture = phase.possibleRequestBlock(null);
    assertThat(completableFuture.isDone()).isTrue();

    final CompletableFuture<Void> completableFuture1 =
        phase.possibleRequestBodies(Collections.emptyList());
    assertThat(completableFuture1.isDone()).isTrue();
  }

  @Test
  public void shouldFindBlockWhenRequested() throws Exception {
    ForwardSyncPhase step =
        new ForwardSyncPhase(context, createBackwardChain(LOCAL_HEIGHT + 1, LOCAL_HEIGHT + 3));

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);

    final CompletableFuture<Block> future =
        step.requestBlock(getBlockByNumber(LOCAL_HEIGHT + 1).getHeader());
    peer.respondWhile(responder, () -> !future.isDone());
    final Block block = future.get();
    assertThat(block).isEqualTo(getBlockByNumber(LOCAL_HEIGHT + 1));
  }

  @Test
  public void shouldCreateAnotherStepWhenThereIsWorkToBeDone() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT + 1, LOCAL_HEIGHT + 10);
    ForwardSyncPhase step = spy(new ForwardSyncPhase(context, backwardChain));

    step.possiblyMoreForwardSteps(backwardChain.getFirstAncestorHeader().orElseThrow());

    verify(step).executeAsync(any());
  }

  @Test
  public void shouldCreateBackwardStepWhenParentOfWorkIsNotImportedYet() {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT + 3, LOCAL_HEIGHT + 10);
    ForwardSyncPhase step = spy(new ForwardSyncPhase(context, backwardChain));

    step.possiblyMoreForwardSteps(backwardChain.getFirstAncestorHeader().orElseThrow());

    verify(step).executeBackwardAsync(any());
  }

  @Test
  public void shouldAddSuccessorsWhenNoUnknownBlockSet() throws Exception {
    BackwardChain backwardChain = createBackwardChain(LOCAL_HEIGHT - 3, LOCAL_HEIGHT);
    backwardChain.appendExpectedBlock(getBlockByNumber(LOCAL_HEIGHT + 1));
    backwardChain.appendExpectedBlock(getBlockByNumber(LOCAL_HEIGHT + 2));
    backwardChain.appendExpectedBlock(getBlockByNumber(LOCAL_HEIGHT + 3));

    ForwardSyncPhase step = new ForwardSyncPhase(context, backwardChain);
    final BlockHeader header = step.processKnownAncestors(null);
    assertThat(header).isNull();

    final CompletableFuture<Void> future = step.possiblyMoreForwardSteps(null);
    assertThat(future.isDone()).isTrue();
    assertThat(localBlockchain.getChainHeadBlock()).isEqualTo(getBlockByNumber(LOCAL_HEIGHT + 3));
  }

  private BackwardChain createBackwardChain(final int from, final int until) {
    BackwardChain chain = backwardChainFromBlock(until);
    for (int i = until; i > from; --i) {
      chain.prependAncestorsHeader(getBlockByNumber(i - 1).getHeader());
    }
    return chain;
  }

  @NotNull
  private BackwardChain backwardChainFromBlock(final int number) {
    return new BackwardChain(
        headersStorage, blocksStorage, remoteBlockchain.getBlockByNumber(number).orElseThrow());
  }

  @NotNull
  private Block getBlockByNumber(final int number) {
    return remoteBlockchain.getBlockByNumber(number).orElseThrow();
  }
}

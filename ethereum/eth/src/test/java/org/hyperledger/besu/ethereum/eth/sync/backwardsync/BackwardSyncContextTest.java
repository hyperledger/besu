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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestWorldState;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BackwardSyncContextTest {

  public static final int REMOTE_HEIGHT = 50;
  public static final int LOCAL_HEIGHT = 25;
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  private BackwardSyncContext context;

  private MutableBlockchain remoteBlockchain;
  private RespondingEthPeer peer;
  private MutableBlockchain localBlockchain;

  @Spy
  private ProtocolSchedule protocolSchedule =
      MainnetProtocolSchedule.fromConfig(new StubGenesisConfigOptions());

  @Spy private ProtocolSpec mockProtocolSpec = protocolSchedule.getByBlockNumber(0L);

  @Mock private ProtocolContext protocolContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private MetricsSystem metricsSystem;

  @Mock private BlockValidator blockValidator;
  @Mock private SyncState syncState;

  private BackwardChain backwardChain;

  @Before
  public void setup() {
    when(mockProtocolSpec.getBlockValidator()).thenReturn(blockValidator);
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(mockProtocolSpec);
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
    when(protocolContext.getBlockchain()).thenReturn(localBlockchain);
    EthProtocolManager ethProtocolManager = EthProtocolManagerTestUtil.create();

    peer = EthProtocolManagerTestUtil.createPeer(ethProtocolManager);
    EthContext ethContext = ethProtocolManager.ethContext();

    when(blockValidator.validateAndProcessBlock(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final Object[] arguments = invocation.getArguments();
              Block block = (Block) arguments[1];
              return new Result(
                  new BlockValidator.BlockProcessingOutputs(
                      new ReferenceTestWorldState(), blockDataGenerator.receipts(block)));
            });

    backwardChain = newBackwardChain();
    context =
        spy(
            new BackwardSyncContext(
                protocolContext,
                protocolSchedule,
                metricsSystem,
                ethContext,
                syncState,
                backwardChain));
    doReturn(true).when(context).isReady();
    doReturn(2).when(context).getBatchSize();
  }

  private BackwardChain newBackwardChain() {
    final GenericKeyValueStorageFacade<Hash, BlockHeader> headersStorage =
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            new BlocksHeadersConvertor(new MainnetBlockHeaderFunctions()),
            new InMemoryKeyValueStorage());
    final GenericKeyValueStorageFacade<Hash, Block> blocksStorage =
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe,
            new BlocksConvertor(new MainnetBlockHeaderFunctions()),
            new InMemoryKeyValueStorage());
    final GenericKeyValueStorageFacade<Hash, Hash> chainStorage =
        new GenericKeyValueStorageFacade<>(
            Hash::toArrayUnsafe, new HashConvertor(), new InMemoryKeyValueStorage());
    return new BackwardChain(headersStorage, blocksStorage, chainStorage);
  }

  @Test
  public void shouldSyncUntilHash() throws Exception {
    final Hash hash = getBlockByNumber(REMOTE_HEIGHT).getHash();
    final CompletableFuture<Void> future = context.syncBackwardsUntil(hash);

    respondUntilFutureIsDone(future);

    future.get();
    assertThat(localBlockchain.getChainHeadBlock()).isEqualTo(remoteBlockchain.getChainHeadBlock());
  }

  @Test
  public void shouldSyncUntilRemoteBranch() throws Exception {

    final CompletableFuture<Void> future =
        context.syncBackwardsUntil(getBlockByNumber(REMOTE_HEIGHT));

    respondUntilFutureIsDone(future);

    future.get();
    assertThat(localBlockchain.getChainHeadBlock()).isEqualTo(remoteBlockchain.getChainHeadBlock());
  }

  @Test
  public void shouldAddExpectedBlock() throws Exception {

    final CompletableFuture<Void> future =
        context.syncBackwardsUntil(getBlockByNumber(REMOTE_HEIGHT - 1));

    final CompletableFuture<Void> secondFuture =
        context.syncBackwardsUntil(getBlockByNumber(REMOTE_HEIGHT));

    assertThat(future).isSameAs(secondFuture);

    respondUntilFutureIsDone(future);

    secondFuture.get();
    assertThat(localBlockchain.getChainHeadBlock()).isEqualTo(remoteBlockchain.getChainHeadBlock());
  }

  private void respondUntilFutureIsDone(final CompletableFuture<Void> future) {
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(remoteBlockchain);

    peer.respondWhileOtherThreadsWork(responder, () -> !future.isDone());
  }

  @Nonnull
  private Block getBlockByNumber(final int number) {
    return remoteBlockchain.getBlockByNumber(number).orElseThrow();
  }

  @Captor ArgumentCaptor<BesuEvents.TTDReachedListener> captor;

  @Test
  public void shouldWaitWhenTTDNotReached()
      throws ExecutionException, InterruptedException, TimeoutException {
    doReturn(false).when(context).isReady();
    when(syncState.isInitialSyncPhaseDone()).thenReturn(Boolean.TRUE);
    when(syncState.subscribeTTDReached(any())).thenReturn(88L);

    final CompletableFuture<Void> voidCompletableFuture = context.waitForTTD();

    verify(syncState).subscribeTTDReached(captor.capture());
    verify(syncState, never()).unsubscribeTTDReached(anyLong());
    assertThat(voidCompletableFuture).isNotCompleted();

    captor.getValue().onTTDReached(true);

    voidCompletableFuture.get(1, TimeUnit.SECONDS);

    verify(syncState).unsubscribeTTDReached(88L);
  }

  @Test
  public void shouldNotWaitWhenTTDReached()
      throws ExecutionException, InterruptedException, TimeoutException {
    doReturn(true).when(context).isReady();
    when(syncState.subscribeTTDReached(any())).thenReturn(88L);
    final CompletableFuture<Void> voidCompletableFuture = context.waitForTTD();
    voidCompletableFuture.get(1, TimeUnit.SECONDS);
    assertThat(voidCompletableFuture).isCompleted();

    verify(syncState).subscribeTTDReached(captor.capture());
    verify(syncState).unsubscribeTTDReached(88L);
  }

  @Test
  public void shouldStartForwardStepWhenOnLocalHeight() {
    createBackwardChain(LOCAL_HEIGHT, LOCAL_HEIGHT + 10);
    doReturn(CompletableFuture.completedFuture(null)).when(context).executeForwardAsync(any());

    context.executeNextStep(null);
    verify(context).executeForwardAsync(any());
  }

  @Test
  public void shouldFinishWhenWorkIsDone() {

    final CompletableFuture<Void> completableFuture = context.executeNextStep(null);
    assertThat(completableFuture.isDone()).isTrue();
  }

  @Test
  public void shouldCreateAnotherBackwardStepWhenNotOnLocalHeight() {
    createBackwardChain(LOCAL_HEIGHT + 3, LOCAL_HEIGHT + 10);
    doReturn(CompletableFuture.completedFuture(null)).when(context).executeBackwardAsync(any());

    context.executeNextStep(null);
    verify(context).executeBackwardAsync(any());
  }

  private void createBackwardChain(final int from, final int until) {
    for (int i = until; i > from; --i) {
      backwardChain.prependAncestorsHeader(getBlockByNumber(i - 1).getHeader());
    }
  }
}

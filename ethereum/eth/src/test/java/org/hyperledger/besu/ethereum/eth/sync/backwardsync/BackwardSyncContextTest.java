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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
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
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BackwardSyncContextTest {

  public static final int REMOTE_HEIGHT = 50;
  public static final int LOCAL_HEIGHT = 25;
  public static final int UNCLE_HEIGHT = 25 - 3;
  private static final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

  private BackwardSyncContext context;

  private MutableBlockchain remoteBlockchain;
  private RespondingEthPeer peer;
  private MutableBlockchain localBlockchain;

  @Spy
  private ProtocolSchedule protocolSchedule =
      MainnetProtocolSchedule.fromConfig(new StubGenesisConfigOptions());

  @Spy private ProtocolSpec mockProtocolSpec = protocolSchedule.getByBlockNumber(0L);

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ProtocolContext protocolContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private MetricsSystem metricsSystem;

  @Mock private BlockValidator blockValidator;
  @Mock private SyncState syncState;
  private BackwardChain backwardChain;
  private Block uncle;

  @Before
  public void setup() {
    when(mockProtocolSpec.getBlockValidator()).thenReturn(blockValidator);
    when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(mockProtocolSpec);
    Block genesisBlock = blockDataGenerator.genesisBlock();
    remoteBlockchain = createInMemoryBlockchain(genesisBlock);
    localBlockchain = createInMemoryBlockchain(genesisBlock);

    for (int i = 1; i <= REMOTE_HEIGHT; i++) {
      final Hash parentHash = remoteBlockchain.getBlockHashByNumber(i - 1).orElseThrow();
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions().setBlockNumber(i).setParentHash(parentHash);
      final Block block = blockDataGenerator.block(options);
      final List<TransactionReceipt> receipts = blockDataGenerator.receipts(block);

      remoteBlockchain.appendBlock(block, receipts);
      if (i <= LOCAL_HEIGHT) {
        if (i == UNCLE_HEIGHT) {
          uncle =
              createUncle(
                  i, localBlockchain.getBlockByNumber(LOCAL_HEIGHT - 4).orElseThrow().getHash());
          localBlockchain.appendBlock(uncle, blockDataGenerator.receipts(uncle));
          localBlockchain.rewindToBlock(i - 1);
        }
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

    backwardChain = inMemoryBackwardChain();
    backwardChain.appendTrustedBlock(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 1).orElseThrow());
    backwardChain.appendTrustedBlock(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 2).orElseThrow());
    backwardChain.appendTrustedBlock(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 3).orElseThrow());
    backwardChain.appendTrustedBlock(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 4).orElseThrow());
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

  private Block createUncle(final int i, final Hash parentHash) {
    final BlockDataGenerator.BlockOptions options =
        new BlockDataGenerator.BlockOptions()
            .setBlockNumber(i)
            .setParentHash(parentHash)
            .transactionTypes(TransactionType.ACCESS_LIST);
    final Block block = blockDataGenerator.block(options);
    return block;
  }

  public static BackwardChain inMemoryBackwardChain() {
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

  @Test
  public void testUpdatingHead() {
    context.updateHeads(null, null);
    context.possiblyMoveHead(null);
    assertThat(localBlockchain.getChainHeadBlock().getHeader().getNumber()).isEqualTo(LOCAL_HEIGHT);

    context.updateHeads(Hash.ZERO, null);
    context.possiblyMoveHead(null);

    assertThat(localBlockchain.getChainHeadBlock().getHeader().getNumber()).isEqualTo(LOCAL_HEIGHT);

    context.updateHeads(localBlockchain.getBlockByNumber(4).orElseThrow().getHash(), null);
    context.possiblyMoveHead(null);

    assertThat(localBlockchain.getChainHeadBlock().getHeader().getNumber()).isEqualTo(4);
  }

  @Test
  public void testSuccessionRuleAfterUpdatingFinalized() {

    backwardChain.appendTrustedBlock(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 1).orElseThrow());
    // null check
    context.updateHeads(null, null);
    context.checkFinalizedSuccessionRuleBeforeSave(null);
    // zero check
    context.updateHeads(null, Hash.ZERO);
    context.checkFinalizedSuccessionRuleBeforeSave(null);

    // cannot save if we don't know what is finalized
    context.updateHeads(
        null, remoteBlockchain.getBlockHashByNumber(LOCAL_HEIGHT + 10).orElseThrow());
    assertThatThrownBy(() -> context.checkFinalizedSuccessionRuleBeforeSave(null))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining(
            "was finalized, but we don't have it downloaded yet, cannot save new block");

    // updating with new finalized
    context.updateHeads(
        null, remoteBlockchain.getBlockHashByNumber(LOCAL_HEIGHT + 1).orElseThrow());
    context.checkFinalizedSuccessionRuleBeforeSave(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 1).orElseThrow());

    // updating when we know finalized is in futre
    context.updateHeads(
        null, remoteBlockchain.getBlockHashByNumber(LOCAL_HEIGHT + 4).orElseThrow());
    context.checkFinalizedSuccessionRuleBeforeSave(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 1).orElseThrow());

    // updating with block that is not finalized when we expected finalized on this height
    context.updateHeads(
        null, remoteBlockchain.getBlockHashByNumber(LOCAL_HEIGHT + 1).orElseThrow());
    assertThatThrownBy(
            () ->
                context.checkFinalizedSuccessionRuleBeforeSave(
                    createUncle(LOCAL_HEIGHT + 1, localBlockchain.getChainHeadHash())))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("This block is not the target finalized block");

    // updating with a block when finalized is not on canonical chain
    context.updateHeads(null, uncle.getHash());
    assertThatThrownBy(
            () ->
                context.checkFinalizedSuccessionRuleBeforeSave(
                    remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 1).orElseThrow()))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("is not on canonical chain. Canonical is");

    // updating when finalized is on canonical chain
    context.updateHeads(null, localBlockchain.getBlockHashByNumber(UNCLE_HEIGHT).orElseThrow());
    context.checkFinalizedSuccessionRuleBeforeSave(
        remoteBlockchain.getBlockByNumber(LOCAL_HEIGHT + 1).orElseThrow());
  }

  @Test
  public void shouldProcessExceptionsCorrectly() {
    assertThatThrownBy(
            () ->
                context.processException(
                    new RuntimeException(new BackwardSyncException("shouldThrow"))))
        .isInstanceOf(BackwardSyncException.class)
        .hasMessageContaining("shouldThrow");
    context.processException(
        new RuntimeException(new BackwardSyncException("shouldNotThrow", true)));
    context.processException(new RuntimeException(new RuntimeException("shouldNotThrow")));
  }
}

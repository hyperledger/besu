/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.blockheaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.JsonRpcResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NewBlockHeadersSubscriptionServiceTest {

  @Captor ArgumentCaptor<Long> subscriptionIdCaptor;
  @Captor ArgumentCaptor<JsonRpcResult> responseCaptor;

  private final BlockResultFactory blockResultFactory = new BlockResultFactory();
  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final BlockchainStorage blockchainStorage =
      new KeyValueStoragePrefixedKeyBlockchainStorage(
          new InMemoryKeyValueStorage(),
          new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
          new MainnetBlockHeaderFunctions(),
          false);
  private final Block genesisBlock = gen.genesisBlock();
  private final MutableBlockchain blockchain =
      DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);

  @Spy
  private final SubscriptionManager subscriptionManagerSpy =
      new SubscriptionManager(new NoOpMetricsSystem());

  @Mock ProtocolSchedule protocolSchedule;

  @Spy
  private final BlockchainQueries blockchainQueriesSpy =
      Mockito.spy(
          new BlockchainQueries(
              protocolSchedule,
              blockchain,
              createInMemoryWorldStateArchive(),
              MiningConfiguration.newDefault()));

  @BeforeEach
  public void before() {
    final NewBlockHeadersSubscriptionService newBlockHeadersSubscriptionService =
        new NewBlockHeadersSubscriptionService(subscriptionManagerSpy, blockchainQueriesSpy);
    blockchain.observeBlockAdded(newBlockHeadersSubscriptionService);
  }

  @Test
  public void shouldSendMessageWhenBlockAddedOnCanonicalChain() {
    final NewBlockHeadersSubscription subscription = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription);
    final Block testBlock = appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(testBlock.getHash()).orElse(null));

    verify(subscriptionManagerSpy)
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());
    assertThat(responseCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedNewBlock);
  }

  @Test
  public void shouldNotSendMessageWhenBlockAddedIsNotOnCanonicalChain() {
    final NewBlockHeadersSubscription subscription = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription);

    final Block canonicalBlock = appendBlockWithParent(blockchain, genesisBlock);
    final BlockOptions options =
        new BlockOptions()
            .setBlockNumber(genesisBlock.getHeader().getNumber() + 1)
            .setParentHash(genesisBlock.getHash())
            .setDifficulty(genesisBlock.getHeader().getDifficulty().divide(100L));
    appendBlockWithParent(blockchain, options);
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(canonicalBlock.getHash()).orElse(null));

    verify(subscriptionManagerSpy, times(1)).notifySubscribersOnWorkerThread(any(), any(), any());
    verify(subscriptionManagerSpy, times(1))
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());
    List<JsonRpcResult> capturedNewBlocks = responseCaptor.getAllValues();
    assertThat(capturedNewBlocks.size()).isEqualTo(1);
    assertThat(capturedNewBlocks.get(0)).usingRecursiveComparison().isEqualTo(expectedNewBlock);
  }

  @Test
  public void shouldSendMessagesWhenReorgBlockAdded() {
    final NewBlockHeadersSubscription subscription = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription);

    final Block canonicalBlock = appendBlockWithParent(blockchain, genesisBlock);
    final BlockOptions options =
        new BlockOptions()
            .setBlockNumber(genesisBlock.getHeader().getNumber() + 1)
            .setParentHash(genesisBlock.getHash())
            .setDifficulty(genesisBlock.getHeader().getDifficulty().divide(100L));
    final Block forkBlock = appendBlockWithParent(blockchain, options);
    options.setDifficulty(forkBlock.getHeader().getDifficulty().divide(100L));
    appendBlockWithParent(blockchain, options);
    options.setDifficulty(blockchain.getChainHeadBlock().getHeader().getDifficulty().multiply(2L));
    final Block forkBlock2 = appendBlockWithParent(blockchain, options);
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(canonicalBlock.getHash()).orElse(null));
    final BlockResult expectedNewBlock1 =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(forkBlock2.getHash()).orElse(null));

    verify(subscriptionManagerSpy, times(2)).notifySubscribersOnWorkerThread(any(), any(), any());
    verify(subscriptionManagerSpy, times(2))
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());
    List<JsonRpcResult> capturedNewBlocks = responseCaptor.getAllValues();
    assertThat(capturedNewBlocks.size()).isEqualTo(2);
    assertThat(capturedNewBlocks.get(0)).usingRecursiveComparison().isEqualTo(expectedNewBlock);
    assertThat(capturedNewBlocks.get(1)).usingRecursiveComparison().isEqualTo(expectedNewBlock1);
  }

  @Test
  public void shouldReturnTxHashesWhenIncludeTransactionsFalse() {
    final NewBlockHeadersSubscription subscription = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription);

    final Block testBlock = appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(testBlock.getHash()).orElse(null));

    verify(subscriptionManagerSpy)
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());
    final Object actualBlock = responseCaptor.getValue();
    assertThat(actualBlock).isInstanceOf(BlockResult.class);
    assertThat(((BlockResult) actualBlock).getTransactions())
        .hasSize(expectedNewBlock.getTransactions().size());
    assertThat(actualBlock).usingRecursiveComparison().isEqualTo(expectedNewBlock);

    verify(blockchainQueriesSpy, times(2)).blockByHashWithTxHashes(any());
    verify(blockchainQueriesSpy, times(0)).blockByHash(any());
  }

  @Test
  public void shouldReturnCompleteTxWhenParameterTrue() {
    final NewBlockHeadersSubscription subscription = createSubscription(true);
    mockSubscriptionManagerNotifyMethod(subscription);

    final Block testBlock = appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionComplete(
            blockchainQueriesSpy.blockByHash(testBlock.getHeader().getHash()).orElse(null));

    verify(subscriptionManagerSpy)
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());

    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());

    final Object actualBlock = responseCaptor.getValue();
    assertThat(actualBlock).isInstanceOf(BlockResult.class);
    assertThat(((BlockResult) actualBlock).getTransactions())
        .hasSize(testBlock.getBody().getTransactions().size());
    assertThat(actualBlock).usingRecursiveComparison().isEqualTo(expectedNewBlock);

    verify(subscriptionManagerSpy, times(1)).sendMessage(any(), any());
    verify(blockchainQueriesSpy, times(0)).blockByHashWithTxHashes(any());
    verify(blockchainQueriesSpy, times(2)).blockByHash(any());
  }

  @Test
  public void shouldOnlyCreateResponsesOnce() {
    final NewBlockHeadersSubscription subscription1 = createSubscription(true);
    final NewBlockHeadersSubscription subscription2 = createSubscription(false);
    final NewBlockHeadersSubscription subscription3 = createSubscription(true);
    final NewBlockHeadersSubscription subscription4 = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription1, subscription2, subscription3, subscription4);

    appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());

    verify(subscriptionManagerSpy, times(4))
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getAllValues())
        .containsExactly(
            subscription1.getSubscriptionId(),
            subscription2.getSubscriptionId(),
            subscription3.getSubscriptionId(),
            subscription4.getSubscriptionId());

    verify(blockchainQueriesSpy, times(1)).blockByHashWithTxHashes(any());
    verify(blockchainQueriesSpy, times(1)).blockByHash(any());
  }

  private void mockSubscriptionManagerNotifyMethod(
      final NewBlockHeadersSubscription... subscriptions) {
    doAnswer(
            invocation -> {
              Consumer<List<NewBlockHeadersSubscription>> consumer = invocation.getArgument(2);
              consumer.accept(List.of(subscriptions));
              return null;
            })
        .when(subscriptionManagerSpy)
        .notifySubscribersOnWorkerThread(any(), any(), any());
  }

  private NewBlockHeadersSubscription createSubscription(final boolean includeTransactions) {
    return new NewBlockHeadersSubscription(1L, "conn", includeTransactions);
  }

  private Block appendBlockWithParent(final MutableBlockchain blockchain, final Block parent) {
    final BlockOptions options =
        new BlockOptions()
            .setBlockNumber(parent.getHeader().getNumber() + 1)
            .setParentHash(parent.getHash());

    return appendBlockWithParent(blockchain, options);
  }

  private Block appendBlockWithParent(
      final MutableBlockchain blockchain, final BlockOptions options) {
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);
    blockchain.appendBlock(newBlock, receipts);

    return newBlock;
  }
}

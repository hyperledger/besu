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
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.JsonRpcResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NewBlockHeadersSubscriptionServiceTest {

  private NewBlockHeadersSubscriptionService newBlockHeadersSubscriptionService;

  @Captor ArgumentCaptor<Long> subscriptionIdCaptor;
  @Captor ArgumentCaptor<JsonRpcResult> responseCaptor;

  @Mock private SubscriptionManager subscriptionManager;

  private final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();
  private final TransactionTestFixture txTestFixture = new TransactionTestFixture();
  private final BlockHeader blockHeader = blockHeaderTestFixture.buildHeader();
  private final BlockResultFactory blockResultFactory = new BlockResultFactory();
  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final BlockchainStorage blockchainStorage =
      new KeyValueStoragePrefixedKeyBlockchainStorage(
          new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
  private final Block genesisBlock = gen.genesisBlock();
  private final MutableBlockchain blockchain =
      DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, new NoOpMetricsSystem());

  @Spy
  private BlockchainQueries blockchainQueriesSpy =
      Mockito.spy(new BlockchainQueries(blockchain, createInMemoryWorldStateArchive()));

  @Before
  public void before() {
    newBlockHeadersSubscriptionService =
        new NewBlockHeadersSubscriptionService(subscriptionManager, blockchainQueriesSpy);
  }

  @Test
  public void shouldSendMessageWhenBlockAddedOnCanonicalChain() {
    final NewBlockHeadersSubscription subscription = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription);

    final Block testBlock = appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());
    simulateAddingBlockOnCanonicalChain(testBlock);
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(testBlock.getHash()).orElse(null));

    verify(subscriptionManager)
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());
    assertThat(responseCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedNewBlock);
  }

  @Test
  public void shouldNotSendMessageWhenBlockAddedIsNotOnCanonicalChain() {
    simulateAddingBlockOnNonCanonicalChain();

    verifyNoInteractions(subscriptionManager);
  }

  @Test
  public void shouldSendMessagesWhenReorgBlockAdded() {
    final NewBlockHeadersSubscription subscription = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription);

    appendBlockWithParent(blockchain, genesisBlock);
    final Block forkBlock = appendBlockWithParent(blockchain, genesisBlock);
    final Block forkBlock1 = appendBlockWithParent(blockchain, forkBlock);
    final Block forkBlock2 = appendBlockWithParent(blockchain, forkBlock1);
    simulateAddingReorgBlock(forkBlock2, genesisBlock);
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(forkBlock2.getHash()).orElse(null));

    verify(subscriptionManager, times(3)).notifySubscribersOnWorkerThread(any(), any(), any());
    verify(subscriptionManager, times(3))
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());
    assertThat(responseCaptor.getValue()).usingRecursiveComparison().isEqualTo(expectedNewBlock);
  }

  @Test
  public void shouldReturnTxHashesWhenIncludeTransactionsFalse() {
    final NewBlockHeadersSubscription subscription = createSubscription(false);
    mockSubscriptionManagerNotifyMethod(subscription);
    final List<Transaction> transactions = transactions();

    final Block testBlock =
        appendBlockWithParent(blockchain, blockchain.getChainHeadBlock(), transactions);
    simulateAddingBlockOnCanonicalChain(testBlock);
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionHash(
            blockchainQueriesSpy.blockByHashWithTxHashes(testBlock.getHash()).orElse(null));

    verify(subscriptionManager)
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());
    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());
    final Object actualBlock = responseCaptor.getValue();
    assertThat(actualBlock).isInstanceOf(BlockResult.class);
    assertThat(((BlockResult) actualBlock).getTransactions()).hasSize(transactions.size());
    assertThat(actualBlock).usingRecursiveComparison().isEqualTo(expectedNewBlock);

    verify(blockchainQueriesSpy, times(2)).blockByHashWithTxHashes(any());
    verify(blockchainQueriesSpy, times(0)).blockByHash(any());
  }

  @Test
  public void shouldReturnCompleteTxWhenParameterTrue() {
    final NewBlockHeadersSubscription subscription = createSubscription(true);
    mockSubscriptionManagerNotifyMethod(subscription);
    final List<TransactionWithMetadata> transactionsWithMetadata = transactionsWithMetadata();

    final List<Transaction> transactions =
        transactionsWithMetadata.stream()
            .map(TransactionWithMetadata::getTransaction)
            .collect(Collectors.toList());
    final Block testBlock =
        appendBlockWithParent(blockchain, blockchain.getChainHeadBlock(), transactions);
    simulateAddingBlockOnCanonicalChain(testBlock);
    final BlockResult expectedNewBlock =
        blockResultFactory.transactionComplete(
            blockchainQueriesSpy.blockByHash(testBlock.getHeader().getHash()).orElse(null));

    verify(subscriptionManager)
        .sendMessage(subscriptionIdCaptor.capture(), responseCaptor.capture());

    assertThat(subscriptionIdCaptor.getValue()).isEqualTo(subscription.getSubscriptionId());

    final Object actualBlock = responseCaptor.getValue();
    assertThat(actualBlock).isInstanceOf(BlockResult.class);
    assertThat(((BlockResult) actualBlock).getTransactions())
        .hasSize(testBlock.getBody().getTransactions().size());
    assertThat(actualBlock).usingRecursiveComparison().isEqualTo(expectedNewBlock);

    verify(subscriptionManager, times(1)).sendMessage(any(), any());
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

    simulateAddingBlockOnCanonicalChain(
        appendBlockWithParent(blockchain, blockchain.getChainHeadBlock()));

    verify(subscriptionManager, times(4))
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
        .when(subscriptionManager)
        .notifySubscribersOnWorkerThread(any(), any(), any());
  }

  private void simulateAddingBlockOnCanonicalChain(final Block block) {
    newBlockHeadersSubscriptionService.onBlockAdded(
        BlockAddedEvent.createForHeadAdvancement(block, Collections.emptyList()),
        blockchainQueriesSpy.getBlockchain());
    verify(blockchainQueriesSpy, times(1)).getBlockchain();
  }

  private void simulateAddingBlockOnNonCanonicalChain() {
    final BlockBody blockBody = new BlockBody(Collections.emptyList(), Collections.emptyList());
    final Block testBlock = new Block(blockHeader, blockBody);
    newBlockHeadersSubscriptionService.onBlockAdded(
        BlockAddedEvent.createForFork(testBlock), blockchainQueriesSpy.getBlockchain());
    verify(blockchainQueriesSpy, times(1)).getBlockchain();
  }

  private void simulateAddingReorgBlock(final Block block, final Block commonAncestorBlock) {
    newBlockHeadersSubscriptionService.onBlockAdded(
        BlockAddedEvent.createForChainReorg(
            block,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            commonAncestorBlock.getHash()),
        blockchainQueriesSpy.getBlockchain());
    verify(blockchainQueriesSpy, times(1)).getBlockchain();
  }

  private List<TransactionWithMetadata> transactionsWithMetadata() {
    final TransactionWithMetadata t1 =
        new TransactionWithMetadata(
            txTestFixture.createTransaction(KeyPair.generate()), 0L, Hash.ZERO, 0);
    final TransactionWithMetadata t2 =
        new TransactionWithMetadata(
            txTestFixture.createTransaction(KeyPair.generate()), 1L, Hash.ZERO, 1);
    return Lists.newArrayList(t1, t2);
  }

  private List<Transaction> transactions() {
    return transactionsWithMetadata().stream()
        .map(TransactionWithMetadata::getTransaction)
        .collect(Collectors.toList());
  }

  private NewBlockHeadersSubscription createSubscription(final boolean includeTransactions) {
    return new NewBlockHeadersSubscription(1L, "conn", includeTransactions);
  }

  private Block appendBlockWithParent(final MutableBlockchain blockchain, final Block parent) {
    return appendBlockWithParent(blockchain, parent, Collections.emptyList());
  }

  private Block appendBlockWithParent(
      final MutableBlockchain blockchain,
      final Block parent,
      final List<Transaction> transactions) {
    final BlockOptions options =
        new BlockOptions()
            .setBlockNumber(parent.getHeader().getNumber() + 1)
            .setParentHash(parent.getHash())
            .addTransaction(transactions);
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);
    blockchain.appendBlock(newBlock, receipts);

    return newBlock;
  }
}

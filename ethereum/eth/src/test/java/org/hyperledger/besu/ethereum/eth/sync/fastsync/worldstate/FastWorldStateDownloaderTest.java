/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProtocolScheduleFixture;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestBuilder;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManagerTestUtil;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.manager.RespondingEthPeer;
import org.hyperledger.besu.ethereum.eth.messages.EthPV63;
import org.hyperledger.besu.ethereum.eth.messages.GetNodeDataMessage;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.FastSyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.StalledDownloadException;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloader;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.forest.ForestWorldStateArchive;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.patricia.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.patricia.TrieNodeDecoder;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.metrics.SyncDurationMetrics;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.tasks.InMemoryTasksPriorityQueues;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;
import org.hyperledger.besu.testutil.MockExecutorService;
import org.hyperledger.besu.testutil.TestClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Disabled("PIE-1434 - Ignored while working to make test more reliable")
class FastWorldStateDownloaderTest {

  private static final Hash EMPTY_TRIE_ROOT = Hash.wrap(MerkleTrie.EMPTY_TRIE_NODE_HASH);

  private final BlockDataGenerator dataGen = new BlockDataGenerator(1);
  private final ExecutorService persistenceThread =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .setNameFormat(FastWorldStateDownloaderTest.class.getSimpleName() + "-persistence-%d")
              .build());

  final EthProtocolManager ethProtocolManager =
      EthProtocolManagerTestBuilder.builder()
          .setEthScheduler(new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()))
          .build();

  @AfterEach
  public void tearDown() throws Exception {
    persistenceThread.shutdownNow();
    assertThat(persistenceThread.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    ethProtocolManager.stop();
  }

  @Test
  @Timeout(value = 60)
  void downloadWorldStateFromPeers_onePeerOneWithManyRequestsOneAtATime() {
    downloadAvailableWorldStateFromPeers(1, 50, 1, 1);
  }

  @Test
  @Timeout(value = 60)
  void downloadWorldStateFromPeers_onePeerOneWithManyRequests() {
    downloadAvailableWorldStateFromPeers(1, 50, 1, 10);
  }

  @Test
  @Timeout(value = 60)
  void downloadWorldStateFromPeers_onePeerWithSingleRequest() {
    downloadAvailableWorldStateFromPeers(1, 1, 100, 10);
  }

  @Test
  @Timeout(value = 60)
  void downloadWorldStateFromPeers_largeStateFromMultiplePeers() {
    downloadAvailableWorldStateFromPeers(5, 100, 10, 10);
  }

  @Test
  @Timeout(value = 60)
  void downloadWorldStateFromPeers_smallStateFromMultiplePeers() {
    downloadAvailableWorldStateFromPeers(5, 5, 1, 10);
  }

  @Test
  @Timeout(value = 60)
  void downloadWorldStateFromPeers_singleRequestWithMultiplePeers() {
    downloadAvailableWorldStateFromPeers(5, 1, 50, 50);
  }

  @Test
  @Timeout(value = 60)
  void downloadEmptyWorldState() {

    final BlockHeader header =
        dataGen
            .block(BlockOptions.create().setStateRoot(EMPTY_TRIE_ROOT).setBlockNumber(10))
            .getHeader();

    final FastSyncState fastSyncState = new FastSyncState(header);

    // Create some peers
    final List<RespondingEthPeer> peers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(5)
            .toList();

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateDownloader downloader =
        createDownloader(ethProtocolManager.ethContext(), localStorage, taskCollection);

    final CompletableFuture<Void> future = downloader.run(null, fastSyncState);
    assertThat(future).isDone();

    // Peers should not have been queried
    for (final RespondingEthPeer peer : peers) {
      assertThat(peer.hasOutstandingRequests()).isFalse();
    }
  }

  @Test
  @Timeout(value = 60)
  void downloadAlreadyAvailableWorldState() {
    // Setup existing state
    final ForestWorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    final MutableWorldState worldState = worldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    dataGen.createRandomAccounts(worldState, 20);
    final Hash stateRoot = worldState.rootHash();
    assertThat(stateRoot).isNotEqualTo(EMPTY_TRIE_ROOT); // Sanity check
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Create some peers
    final List<RespondingEthPeer> peers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(5)
            .toList();

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final WorldStateDownloader downloader =
        createDownloader(
            ethProtocolManager.ethContext(),
            worldStateArchive.getWorldStateStorage(),
            taskCollection);

    final FastSyncState fastSyncState = new FastSyncState(header);

    final CompletableFuture<Void> future = downloader.run(null, fastSyncState);
    assertThat(future).isDone();

    // Peers should not have been queried because we already had the state
    for (final RespondingEthPeer peer : peers) {
      assertThat(peer.hasOutstandingRequests()).isFalse();
    }
  }

  @Test
  @Timeout(value = 60)
  void canRecoverFromTimeouts() {
    final DeterministicEthScheduler.TimeoutPolicy timeoutPolicy =
        DeterministicEthScheduler.TimeoutPolicy.timeoutXTimes(2);
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setEthScheduler(new DeterministicEthScheduler(timeoutPolicy))
            .build();
    final MockExecutorService serviceExecutor =
        ((DeterministicEthScheduler) ethProtocolManager.ethContext().getScheduler())
            .mockServiceExecutor();
    serviceExecutor.setAutoRun(false);

    // Setup "remote" state
    final WorldStateArchive remoteWorldStateArchive = createInMemoryWorldStateArchive();
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    final List<Account> accounts = dataGen.createRandomAccounts(remoteWorldState, 20);
    final Hash stateRoot = remoteWorldState.rootHash();
    assertThat(stateRoot).isNotEqualTo(EMPTY_TRIE_ROOT); // Sanity check
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Create some peers
    final List<RespondingEthPeer> peers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(5)
            .collect(Collectors.toList());

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateDownloader downloader =
        createDownloader(ethProtocolManager.ethContext(), localStorage, taskCollection);

    final FastSyncState fastSyncState = new FastSyncState(header);

    final CompletableFuture<Void> result = downloader.run(null, fastSyncState);

    serviceExecutor.runPendingFuturesInSeparateThreads(persistenceThread);

    // Respond to node data requests
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);

    respondUntilDone(peers, responder, result);

    // Check that all expected account data was downloaded
    final WorldStateArchive localWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(localStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final WorldState localWorldState = localWorldStateArchive.get(stateRoot, null).get();
    assertThat(result).isDone();
    assertAccountsMatch(localWorldState, accounts);
  }

  @Test
  @Timeout(value = 60)
  void handlesPartialResponsesFromNetwork() {
    downloadAvailableWorldStateFromPeers(5, 100, 10, 10, this::respondPartially);
  }

  @Test
  @Timeout(value = 60)
  void doesNotRequestKnownCodeFromNetwork() {
    // Setup "remote" state
    final WorldStateArchive remoteWorldStateArchive = createInMemoryWorldStateArchive();
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    final List<Account> accounts =
        dataGen.createRandomContractAccountsWithNonEmptyStorage(remoteWorldState, 20);
    final Hash stateRoot = remoteWorldState.rootHash();
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Create some peers
    final List<RespondingEthPeer> peers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(5)
            .collect(Collectors.toList());

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    // Seed local storage with some contract values
    final Map<Bytes32, Bytes> knownCode = new HashMap<>();
    accounts.subList(0, 5).forEach(a -> knownCode.put(a.getCodeHash(), a.getCode()));
    final ForestWorldStateKeyValueStorage.Updater localStorageUpdater = localStorage.updater();
    knownCode.forEach((bytes32, code) -> localStorageUpdater.putCode(code));
    localStorageUpdater.commit();

    final WorldStateDownloader downloader =
        createDownloader(ethProtocolManager.ethContext(), localStorage, taskCollection);

    final FastSyncState fastSyncState = new FastSyncState(header);

    final CompletableFuture<Void> result = downloader.run(null, fastSyncState);

    // Respond to node data requests
    final List<MessageData> sentMessages = new ArrayList<>();
    final RespondingEthPeer.Responder blockChainResponder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.wrapResponderWithCollector(blockChainResponder, sentMessages);

    respondUntilDone(peers, responder, result);

    // Check that known code was not requested
    final List<Bytes32> requestedHashes =
        sentMessages.stream()
            .filter(m -> m.getCode() == EthPV63.GET_NODE_DATA)
            .map(GetNodeDataMessage::readFrom)
            .flatMap(m -> StreamSupport.stream(m.hashes().spliterator(), true))
            .collect(Collectors.toList());
    assertThat(requestedHashes).isNotEmpty();
    assertThat(Collections.disjoint(requestedHashes, knownCode.keySet())).isTrue();

    // Check that all expected account data was downloaded
    final WorldStateArchive localWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(localStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final WorldState localWorldState = localWorldStateArchive.get(stateRoot, null).get();
    assertThat(result).isDone();
    assertAccountsMatch(localWorldState, accounts);
  }

  @Test
  @Timeout(value = 60)
  void cancelDownloader() {
    testCancellation(false);
  }

  @Test
  @Timeout(value = 60)
  void cancelDownloaderFuture() {
    testCancellation(true);
  }

  @SuppressWarnings("unchecked")
  private void testCancellation(final boolean shouldCancelFuture) {
    final EthProtocolManager ethProtocolManager = EthProtocolManagerTestBuilder.builder().build();
    // Prevent the persistence service from running
    final MockExecutorService serviceExecutor =
        ((DeterministicEthScheduler) ethProtocolManager.ethContext().getScheduler())
            .mockServiceExecutor();
    serviceExecutor.setAutoRun(false);

    // Setup "remote" state
    final WorldStateArchive remoteWorldStateArchive = createInMemoryWorldStateArchive();
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    dataGen.createRandomContractAccountsWithNonEmptyStorage(remoteWorldState, 20);
    final Hash stateRoot = remoteWorldState.rootHash();
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Create some peers
    final List<RespondingEthPeer> peers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(5)
            .toList();

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    final WorldStateDownloader downloader =
        createDownloader(ethProtocolManager.ethContext(), localStorage, taskCollection);

    final FastSyncState fastSyncState = new FastSyncState(header);

    final CompletableFuture<Void> result = downloader.run(null, fastSyncState);

    // Send a few responses
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);

    for (int i = 0; i < 3; i++) {
      for (final RespondingEthPeer peer : peers) {
        peer.respond(responder);
      }
      giveOtherThreadsAGo();
    }
    assertThat(result.isDone()).isFalse(); // Sanity check

    // Reset taskCollection so we can track interactions after the cancellation
    reset(taskCollection);
    if (shouldCancelFuture) {
      result.cancel(true);
    } else {
      downloader.cancel();
      assertThat(result).isCancelled();
    }

    // Send some more responses after cancelling
    for (int i = 0; i < 3; i++) {
      for (final RespondingEthPeer peer : peers) {
        peer.respond(responder);
      }
      giveOtherThreadsAGo();
    }

    // Now allow the persistence service to run which should exit immediately
    serviceExecutor.runPendingFutures();

    verify(taskCollection, times(1)).clear();
    verify(taskCollection, never()).remove();
    verify(taskCollection, never()).add(any(NodeDataRequest.class));
    // Target world state should not be available
    assertThat(localStorage.isWorldStateAvailable(header.getStateRoot())).isFalse();
  }

  @Test
  @Timeout(value = 60)
  void doesNotRequestKnownAccountTrieNodesFromNetwork() {
    // Setup "remote" state
    final ForestWorldStateKeyValueStorage remoteStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateArchive remoteWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(remoteStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    final List<Account> accounts =
        dataGen.createRandomContractAccountsWithNonEmptyStorage(remoteWorldState, 20);
    final Hash stateRoot = remoteWorldState.rootHash();
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Create some peers
    final List<RespondingEthPeer> peers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(5)
            .collect(Collectors.toList());

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    // Seed local storage with some trie node values
    final Map<Bytes32, Bytes> allNodes =
        collectTrieNodesToBeRequestedAfterRoot(remoteStorage, remoteWorldState.rootHash(), 5);
    final Set<Bytes32> knownNodes = new HashSet<>();
    final Set<Bytes32> unknownNodes = new HashSet<>();
    assertThat(allNodes).isNotEmpty(); // Sanity check
    final ForestWorldStateKeyValueStorage.Updater localStorageUpdater = localStorage.updater();
    final AtomicBoolean storeNode = new AtomicBoolean(true);
    allNodes.forEach(
        (nodeHash, node) -> {
          if (storeNode.get()) {
            localStorageUpdater.putAccountStateTrieNode(nodeHash, node);
            knownNodes.add(nodeHash);
          } else {
            unknownNodes.add(nodeHash);
          }
          storeNode.set(!storeNode.get());
        });
    localStorageUpdater.commit();

    final WorldStateDownloader downloader =
        createDownloader(ethProtocolManager.ethContext(), localStorage, taskCollection);

    final FastSyncState fastSyncState = new FastSyncState(header);

    final CompletableFuture<Void> result = downloader.run(null, fastSyncState);

    // Respond to node data requests
    final List<MessageData> sentMessages = new ArrayList<>();
    final RespondingEthPeer.Responder blockChainResponder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.wrapResponderWithCollector(blockChainResponder, sentMessages);

    respondUntilDone(peers, responder, result);

    // Check that unknown trie nodes were requested
    final List<Bytes32> requestedHashes =
        sentMessages.stream()
            .filter(m -> m.getCode() == EthPV63.GET_NODE_DATA)
            .map(GetNodeDataMessage::readFrom)
            .flatMap(m -> StreamSupport.stream(m.hashes().spliterator(), true))
            .collect(Collectors.toList());
    assertThat(requestedHashes)
        .isNotEmpty()
        .containsAll(unknownNodes)
        .doesNotContainAnyElementsOf(knownNodes);

    // Check that all expected account data was downloaded
    final WorldStateArchive localWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(localStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final WorldState localWorldState = localWorldStateArchive.get(stateRoot, null).get();
    assertThat(result).isDone();
    assertAccountsMatch(localWorldState, accounts);
  }

  @Test
  @Timeout(value = 60)
  void doesNotRequestKnownStorageTrieNodesFromNetwork() {
    // Setup "remote" state
    final ForestWorldStateKeyValueStorage remoteStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateArchive remoteWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(remoteStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    final List<Account> accounts =
        dataGen.createRandomContractAccountsWithNonEmptyStorage(remoteWorldState, 20);
    final Hash stateRoot = remoteWorldState.rootHash();
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Create some peers
    final List<RespondingEthPeer> peers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(5)
            .collect(Collectors.toList());

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());

    // Seed local storage with some trie node values
    final List<Bytes32> storageRootHashes =
        new StoredMerklePatriciaTrie<>(
                (location, hash) -> remoteStorage.getNodeData(hash),
                remoteWorldState.rootHash(),
                Function.identity(),
                Function.identity())
            .entriesFrom(Bytes32.ZERO, 5).values().stream()
                .map(RLP::input)
                .map(PmtStateTrieAccountValue::readFrom)
                .map(PmtStateTrieAccountValue::getStorageRoot)
                .collect(Collectors.toList());
    final Map<Bytes32, Bytes> allTrieNodes = new HashMap<>();
    final Set<Bytes32> knownNodes = new HashSet<>();
    final Set<Bytes32> unknownNodes = new HashSet<>();
    for (final Bytes32 storageRootHash : storageRootHashes) {
      allTrieNodes.putAll(
          collectTrieNodesToBeRequestedAfterRoot(remoteStorage, storageRootHash, 5));
    }
    assertThat(allTrieNodes).isNotEmpty(); // Sanity check
    final ForestWorldStateKeyValueStorage.Updater localStorageUpdater = localStorage.updater();
    boolean storeNode = true;
    for (final Map.Entry<Bytes32, Bytes> entry : allTrieNodes.entrySet()) {
      final Bytes32 hash = entry.getKey();
      final Bytes data = entry.getValue();
      if (storeNode) {
        localStorageUpdater.putAccountStorageTrieNode(hash, data);
        knownNodes.add(hash);
      } else {
        unknownNodes.add(hash);
      }
      storeNode = !storeNode;
    }
    localStorageUpdater.commit();

    final WorldStateDownloader downloader =
        createDownloader(ethProtocolManager.ethContext(), localStorage, taskCollection);

    final FastSyncState fastSyncState = new FastSyncState(header);

    final CompletableFuture<Void> result = downloader.run(null, fastSyncState);

    // Respond to node data requests
    final List<MessageData> sentMessages = new ArrayList<>();
    final RespondingEthPeer.Responder blockChainResponder =
        RespondingEthPeer.blockchainResponder(
            mock(Blockchain.class), remoteWorldStateArchive, mock(TransactionPool.class));
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.wrapResponderWithCollector(blockChainResponder, sentMessages);

    respondUntilDone(peers, responder, result);
    // World state should be available by the time the result is complete
    assertThat(localStorage.isWorldStateAvailable(stateRoot)).isTrue();

    // Check that unknown trie nodes were requested
    final List<Bytes32> requestedHashes =
        sentMessages.stream()
            .filter(m -> m.getCode() == EthPV63.GET_NODE_DATA)
            .map(GetNodeDataMessage::readFrom)
            .flatMap(m -> StreamSupport.stream(m.hashes().spliterator(), true))
            .collect(Collectors.toList());
    assertThat(requestedHashes)
        .isNotEmpty()
        .containsAll(unknownNodes)
        .doesNotContainAnyElementsOf(knownNodes);

    // Check that all expected account data was downloaded
    final WorldStateArchive localWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(localStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final WorldState localWorldState = localWorldStateArchive.get(stateRoot, null).get();
    assertThat(result).isDone();
    assertAccountsMatch(localWorldState, accounts);
  }

  @Test
  @Timeout(value = 60)
  void stalledDownloader() {
    final EthProtocolManager ethProtocolManager =
        EthProtocolManagerTestBuilder.builder()
            .setEthScheduler(new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem()))
            .build();

    // Setup "remote" state
    final ForestWorldStateKeyValueStorage remoteStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateArchive remoteWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(remoteStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    dataGen.createRandomAccounts(remoteWorldState, 10);
    final Hash stateRoot = remoteWorldState.rootHash();
    assertThat(stateRoot).isNotEqualTo(EMPTY_TRIE_ROOT); // Sanity check
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().worldStateMaxRequestsWithoutProgress(10).build();
    final WorldStateDownloader downloader =
        createDownloader(syncConfig, ethProtocolManager.ethContext(), localStorage, taskCollection);

    // Create a peer that can respond
    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber());

    // Start downloader (with a state root that's not available anywhere

    final CompletableFuture<Void> result =
        downloader.run(
            null,
            new FastSyncState(
                new BlockHeaderTestFixture()
                    .stateRoot(Hash.hash(Bytes.of(1, 2, 3, 4)))
                    .buildHeader()));

    // A second run should return an error without impacting the first result
    final CompletableFuture<?> secondResult = downloader.run(null, new FastSyncState(header));
    assertThat(secondResult).isCompletedExceptionally();
    assertThat(result).isNotCompletedExceptionally();

    final RespondingEthPeer.Responder emptyResponder = RespondingEthPeer.emptyResponder();
    peer.respondWhileOtherThreadsWork(emptyResponder, () -> !result.isDone());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasCauseInstanceOf(StalledDownloadException.class);

    // Finally, check that when we restart the download with state that is available it works

    final CompletableFuture<Void> retryResult = downloader.run(null, new FastSyncState(header));

    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);
    peer.respondWhileOtherThreadsWork(responder, () -> !retryResult.isDone());
    assertThat(retryResult).isCompleted();
  }

  @Test
  @Timeout(value = 60)
  void resumesFromNonEmptyQueue() {
    // Setup "remote" state
    final ForestWorldStateKeyValueStorage remoteStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateArchive remoteWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(remoteStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    List<Account> accounts = dataGen.createRandomAccounts(remoteWorldState, 10);
    final Hash stateRoot = remoteWorldState.rootHash();
    assertThat(stateRoot).isNotEqualTo(EMPTY_TRIE_ROOT); // Sanity check
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Add some nodes to the taskCollection
    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        spy(new InMemoryTasksPriorityQueues<>());
    List<Bytes32> queuedHashes = getFirstSetOfChildNodeRequests(remoteStorage, stateRoot);
    assertThat(queuedHashes).isNotEmpty(); // Sanity check
    for (Bytes32 bytes32 : queuedHashes) {
      taskCollection.add(new AccountTrieNodeDataRequest(Hash.wrap(bytes32), Optional.empty()));
    }
    // Sanity check
    for (final Bytes32 bytes32 : queuedHashes) {
      final Hash hash = Hash.wrap(bytes32);
      verify(taskCollection, times(1)).add(argThat((r) -> r.getHash().equals(hash)));
    }

    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder().worldStateMaxRequestsWithoutProgress(10).build();
    final WorldStateDownloader downloader =
        createDownloader(syncConfig, ethProtocolManager.ethContext(), localStorage, taskCollection);

    // Create a peer that can respond
    final RespondingEthPeer peer =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber());

    // Respond to node data requests
    final List<MessageData> sentMessages = new ArrayList<>();
    final RespondingEthPeer.Responder blockChainResponder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.wrapResponderWithCollector(blockChainResponder, sentMessages);

    CompletableFuture<Void> result = downloader.run(null, new FastSyncState(header));
    peer.respondWhileOtherThreadsWork(responder, () -> !result.isDone());
    assertThat(localStorage.isWorldStateAvailable(stateRoot)).isTrue();

    // Check that already enqueued trie nodes were requested
    final List<Bytes32> requestedHashes =
        sentMessages.stream()
            .filter(m -> m.getCode() == EthPV63.GET_NODE_DATA)
            .map(GetNodeDataMessage::readFrom)
            .flatMap(m -> StreamSupport.stream(m.hashes().spliterator(), true))
            .collect(Collectors.toList());
    assertThat(requestedHashes).isNotEmpty().containsAll(queuedHashes);

    // Check that already enqueued requests were not enqueued more than once
    for (Bytes32 bytes32 : queuedHashes) {
      final Hash hash = Hash.wrap(bytes32);
      verify(taskCollection, times(1)).add(argThat((r) -> r.getHash().equals(hash)));
    }

    // Check that all expected account data was downloaded
    assertThat(result).isDone();
    final WorldStateArchive localWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(localStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final WorldState localWorldState = localWorldStateArchive.get(stateRoot, null).get();
    assertAccountsMatch(localWorldState, accounts);
  }

  /**
   * Walks through trie represented by the given rootHash and returns hash-node pairs that would
   * need to be requested from the network in order to reconstruct this trie, excluding the root
   * node.
   *
   * @param storage Storage holding node data required to reconstitute the trie represented by
   *     rootHash
   * @param rootHash The hash of the root node of some trie
   * @param maxNodes The maximum number of values to collect before returning
   * @return A list of hash-node pairs
   */
  private Map<Bytes32, Bytes> collectTrieNodesToBeRequestedAfterRoot(
      final ForestWorldStateKeyValueStorage storage, final Bytes32 rootHash, final int maxNodes) {
    final Map<Bytes32, Bytes> trieNodes = new HashMap<>();

    TrieNodeDecoder.breadthFirstDecoder((location, hash) -> storage.getNodeData(hash), rootHash)
        .filter(n -> !Objects.equals(n.getHash(), rootHash))
        .filter(Node::isReferencedByHash)
        .limit(maxNodes)
        .forEach((n) -> trieNodes.put(n.getHash(), n.getEncodedBytes()));

    return trieNodes;
  }

  /**
   * Returns the first set of node hashes that would need to be requested from the network after
   * retrieving the root node in order to rebuild the trie represented by the given rootHash and
   * storage.
   *
   * @param storage Storage holding node data required to reconstitute the trie represented by
   *     rootHash
   * @param rootHash The hash of the root node of some trie
   * @return A list of node hashes
   */
  private List<Bytes32> getFirstSetOfChildNodeRequests(
      final ForestWorldStateKeyValueStorage storage, final Bytes32 rootHash) {
    final List<Bytes32> hashesToRequest = new ArrayList<>();

    final Bytes rootNodeRlp = storage.getNodeData(rootHash).get();
    TrieNodeDecoder.decodeNodes(Bytes.EMPTY, rootNodeRlp).stream()
        .filter(n -> !Objects.equals(n.getHash(), rootHash))
        .filter(Node::isReferencedByHash)
        .forEach((n) -> hashesToRequest.add(n.getHash()));

    return hashesToRequest;
  }

  private void downloadAvailableWorldStateFromPeers(
      final int peerCount,
      final int accountCount,
      final int hashesPerRequest,
      final int maxOutstandingRequests) {
    downloadAvailableWorldStateFromPeers(
        peerCount, accountCount, hashesPerRequest, maxOutstandingRequests, this::respondFully);
  }

  private void downloadAvailableWorldStateFromPeers(
      final int peerCount,
      final int accountCount,
      final int hashesPerRequest,
      final int maxOutstandingRequests,
      final NetworkResponder networkResponder) {
    final int trailingPeerCount = 5;

    // Setup "remote" state
    final ForestWorldStateKeyValueStorage remoteStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateArchive remoteWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(remoteStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final MutableWorldState remoteWorldState = remoteWorldStateArchive.getMutable();

    // Generate accounts and save corresponding state root
    final List<Account> accounts = dataGen.createRandomAccounts(remoteWorldState, accountCount);
    final Hash stateRoot = remoteWorldState.rootHash();
    assertThat(stateRoot).isNotEqualTo(EMPTY_TRIE_ROOT); // Sanity check
    final BlockHeader header =
        dataGen.block(BlockOptions.create().setStateRoot(stateRoot).setBlockNumber(10)).getHeader();

    // Generate more data that should not be downloaded
    final List<Account> otherAccounts = dataGen.createRandomAccounts(remoteWorldState, 5);
    final Hash otherStateRoot = remoteWorldState.rootHash();
    final BlockHeader otherHeader =
        dataGen
            .block(BlockOptions.create().setStateRoot(otherStateRoot).setBlockNumber(11))
            .getHeader();
    assertThat(otherStateRoot).isNotEqualTo(stateRoot); // Sanity check

    final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection =
        new InMemoryTasksPriorityQueues<>();
    final ForestWorldStateKeyValueStorage localStorage =
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStateArchive localWorldStateArchive =
        new ForestWorldStateArchive(
            new WorldStateStorageCoordinator(localStorage),
            createPreimageStorage(),
            EvmConfiguration.DEFAULT);
    final SynchronizerConfiguration syncConfig =
        SynchronizerConfiguration.builder()
            .worldStateHashCountPerRequest(hashesPerRequest)
            .worldStateRequestParallelism(maxOutstandingRequests)
            .build();
    final WorldStateDownloader downloader =
        createDownloader(syncConfig, ethProtocolManager.ethContext(), localStorage, taskCollection);

    // Create some peers that can respond
    final List<RespondingEthPeer> usefulPeers =
        Stream.generate(
                () -> EthProtocolManagerTestUtil.createPeer(ethProtocolManager, header.getNumber()))
            .limit(peerCount)
            .collect(Collectors.toList());
    // And some irrelevant peers
    final List<RespondingEthPeer> trailingPeers =
        Stream.generate(
                () ->
                    EthProtocolManagerTestUtil.createPeer(
                        ethProtocolManager, header.getNumber() - 1L))
            .limit(trailingPeerCount)
            .toList();

    // Start downloader
    final CompletableFuture<?> result = downloader.run(null, new FastSyncState(header));
    // A second run should return an error without impacting the first result
    final CompletableFuture<?> secondResult = downloader.run(null, new FastSyncState(header));
    assertThat(secondResult).isCompletedExceptionally();
    assertThat(result).isNotCompletedExceptionally();

    // Respond to node data requests
    // Send one round of full responses, so that we can get multiple requests queued up
    final RespondingEthPeer.Responder fullResponder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);
    for (final RespondingEthPeer peer : usefulPeers) {
      peer.respond(fullResponder);
    }
    // Respond to remaining queued requests in custom way
    networkResponder.respond(usefulPeers, remoteWorldStateArchive, result);

    // Check that trailing peers were not queried for data
    for (final RespondingEthPeer trailingPeer : trailingPeers) {
      assertThat(trailingPeer.hasOutstandingRequests()).isFalse();
    }

    // Check that all expected account data was downloaded
    final WorldState localWorldState = localWorldStateArchive.get(stateRoot, null).get();
    assertThat(result).isDone();
    assertAccountsMatch(localWorldState, accounts);

    // We shouldn't have any extra data locally
    assertThat(localStorage.contains(otherHeader.getStateRoot())).isFalse();
    for (final Account otherAccount : otherAccounts) {
      assertThat(localWorldState.get(otherAccount.getAddress())).isNull();
    }
  }

  private void respondFully(
      final List<RespondingEthPeer> peers,
      final WorldStateArchive remoteWorldStateArchive,
      final CompletableFuture<?> downloaderFuture) {
    final RespondingEthPeer.Responder responder =
        RespondingEthPeer.blockchainResponder(mock(Blockchain.class), remoteWorldStateArchive);
    respondUntilDone(peers, responder, downloaderFuture);
  }

  private void respondPartially(
      final List<RespondingEthPeer> peers,
      final WorldStateArchive remoteWorldStateArchive,
      final CompletableFuture<?> downloaderFuture) {
    final RespondingEthPeer.Responder fullResponder =
        RespondingEthPeer.blockchainResponder(
            mock(Blockchain.class), remoteWorldStateArchive, mock(TransactionPool.class));
    final RespondingEthPeer.Responder partialResponder =
        RespondingEthPeer.partialResponder(
            mock(Blockchain.class),
            remoteWorldStateArchive,
            mock(TransactionPool.class),
            ProtocolScheduleFixture.MAINNET,
            .5f);
    final RespondingEthPeer.Responder emptyResponder = RespondingEthPeer.emptyResponder();

    // Send a few partial responses
    for (int i = 0; i < 5; i++) {
      for (final RespondingEthPeer peer : peers) {
        peer.respond(partialResponder);
      }
      giveOtherThreadsAGo();
    }

    // Downloader should not complete with partial responses
    assertThat(downloaderFuture).isNotDone();

    // Send a few empty responses
    for (int i = 0; i < 3; i++) {
      for (final RespondingEthPeer peer : peers) {
        peer.respond(emptyResponder);
      }
      giveOtherThreadsAGo();
    }

    // Downloader should not complete with empty responses
    assertThat(downloaderFuture).isNotDone();

    respondUntilDone(peers, fullResponder, downloaderFuture);
  }

  private void assertAccountsMatch(
      final WorldState worldState, final List<Account> expectedAccounts) {
    for (final Account expectedAccount : expectedAccounts) {
      final Account actualAccount = worldState.get(expectedAccount.getAddress());
      assertThat(actualAccount).isNotNull();
      // Check each field
      assertThat(actualAccount.getNonce()).isEqualTo(expectedAccount.getNonce());
      assertThat(actualAccount.getCode()).isEqualTo(expectedAccount.getCode());
      assertThat(actualAccount.getBalance()).isEqualTo(expectedAccount.getBalance());

      final Map<Bytes32, AccountStorageEntry> actualStorage =
          actualAccount.storageEntriesFrom(Bytes32.ZERO, 500);
      final Map<Bytes32, AccountStorageEntry> expectedStorage =
          expectedAccount.storageEntriesFrom(Bytes32.ZERO, 500);
      assertThat(actualStorage).isEqualTo(expectedStorage);
    }
  }

  private WorldStateDownloader createDownloader(
      final EthContext context,
      final WorldStateKeyValueStorage storage,
      final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection) {
    return createDownloader(
        SynchronizerConfiguration.builder().build(), context, storage, taskCollection);
  }

  private WorldStateDownloader createDownloader(
      final SynchronizerConfiguration config,
      final EthContext context,
      final WorldStateKeyValueStorage storage,
      final InMemoryTasksPriorityQueues<NodeDataRequest> taskCollection) {
    return new FastWorldStateDownloader(
        context,
        new WorldStateStorageCoordinator(storage),
        taskCollection,
        config.getWorldStateHashCountPerRequest(),
        config.getWorldStateRequestParallelism(),
        config.getWorldStateMaxRequestsWithoutProgress(),
        config.getWorldStateMinMillisBeforeStalling(),
        TestClock.fixed(),
        new NoOpMetricsSystem(),
        SyncDurationMetrics.NO_OP_SYNC_DURATION_METRICS);
  }

  private WorldStatePreimageStorage createPreimageStorage() {
    return new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage());
  }

  private void respondUntilDone(
      final List<RespondingEthPeer> peers,
      final RespondingEthPeer.Responder responder,
      final CompletableFuture<?> result) {
    if (peers.size() == 1) {
      // Use a blocking approach to waiting for the next message when we can.
      peers.get(0).respondWhileOtherThreadsWork(responder, () -> !result.isDone());
      return;
    }
    while (!result.isDone()) {
      for (final RespondingEthPeer peer : peers) {
        peer.respond(responder);
      }
      giveOtherThreadsAGo();
    }
  }

  private void giveOtherThreadsAGo() {
    LockSupport.parkNanos(200);
  }

  @FunctionalInterface
  private interface NetworkResponder {
    void respond(
        final List<RespondingEthPeer> peers,
        final WorldStateArchive remoteWorldStateArchive,
        final CompletableFuture<?> downloaderFuture);
  }
}

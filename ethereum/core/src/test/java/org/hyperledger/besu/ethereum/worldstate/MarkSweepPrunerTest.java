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
package org.hyperledger.besu.ethereum.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MarkSweepPrunerTest {

  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final Map<Bytes, byte[]> hashValueStore = spy(new HashMap<>());
  private final InMemoryKeyValueStorage stateStorage = new TestInMemoryStorage(hashValueStore);
  private final WorldStateStorage worldStateStorage =
      spy(new WorldStateKeyValueStorage(stateStorage));
  private final WorldStateArchive worldStateArchive =
      new DefaultWorldStateArchive(
          worldStateStorage, new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  private final InMemoryKeyValueStorage markStorage = new InMemoryKeyValueStorage();
  private final Block genesisBlock = gen.genesisBlock();
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesisBlock);

  @Test
  public void mark_marksAllExpectedNodes() {
    final MarkSweepPruner pruner =
        new MarkSweepPruner(worldStateStorage, blockchain, markStorage, metricsSystem);

    // Generate accounts and save corresponding state root
    final int numBlocks = 15;
    final int numAccounts = 10;
    generateBlockchainData(numBlocks, numAccounts);

    final int markBlockNumber = 10;
    final BlockHeader markBlock = blockchain.getBlockHeader(markBlockNumber).get();
    // Collect the nodes we expect to keep
    final Set<Bytes> expectedNodes = collectWorldStateNodes(markBlock.getStateRoot());
    assertThat(hashValueStore.size()).isGreaterThan(expectedNodes.size()); // Sanity check

    // Mark and sweep
    pruner.mark(markBlock.getStateRoot());
    pruner.sweepBefore(markBlock.getNumber());

    // Assert that the block we marked is still present and all accounts are accessible
    assertThat(worldStateArchive.get(markBlock.getStateRoot(), markBlock.getHash())).isPresent();
    final WorldState markedState =
        worldStateArchive.get(markBlock.getStateRoot(), markBlock.getHash()).get();
    // Traverse accounts and make sure all are accessible
    final int expectedAccounts = numAccounts * markBlockNumber;
    final long accounts = markedState.streamAccounts(Bytes32.ZERO, expectedAccounts * 2).count();
    assertThat(accounts).isEqualTo(expectedAccounts);
    // Traverse storage to ensure that all storage is accessible
    markedState
        .streamAccounts(Bytes32.ZERO, expectedAccounts * 2)
        .forEach(a -> a.storageEntriesFrom(Bytes32.ZERO, 1000));

    // All other state roots should have been removed
    for (int i = 0; i < numBlocks; i++) {
      final BlockHeader curHeader = blockchain.getBlockHeader(i + 1L).get();
      if (curHeader.getNumber() == markBlock.getNumber()) {
        continue;
      }
      assertThat(worldStateArchive.get(curHeader.getStateRoot(), curHeader.getHash())).isEmpty();
    }

    // Check that storage contains only the values we expect
    assertThat(hashValueStore.size()).isEqualTo(expectedNodes.size());
    assertThat(hashValueStore.values())
        .containsExactlyInAnyOrderElementsOf(
            expectedNodes.stream().map(Bytes::toArrayUnsafe).collect(Collectors.toSet()));
  }

  @Test
  public void sweepBefore_shouldSweepStateRootFirst() {
    final MarkSweepPruner pruner =
        new MarkSweepPruner(worldStateStorage, blockchain, markStorage, metricsSystem);

    // Generate accounts and save corresponding state root
    final int numBlocks = 15;
    final int numAccounts = 10;
    generateBlockchainData(numBlocks, numAccounts);

    final int markBlockNumber = 10;
    final BlockHeader markBlock = blockchain.getBlockHeader(markBlockNumber).get();

    // Collect state roots we expect to be swept first
    final List<Bytes32> stateRoots = new ArrayList<>();
    for (int i = markBlockNumber - 1; i >= 0; i--) {
      stateRoots.add(blockchain.getBlockHeader(i).get().getStateRoot());
    }

    // Mark and sweep
    pruner.mark(markBlock.getStateRoot());
    pruner.sweepBefore(markBlock.getNumber());

    // we use individual inOrders because we only want to make sure we remove a state root before
    // the full prune but without enforcing an ordering between the state root removals
    stateRoots.forEach(
        stateRoot -> {
          final InOrder thisRootsOrdering = inOrder(hashValueStore, worldStateStorage);
          thisRootsOrdering.verify(hashValueStore).remove(stateRoot);
          thisRootsOrdering.verify(worldStateStorage).prune(any());
        });
  }

  @Test
  public void sweepBefore_shouldNotRemoveMarkedStateRoots() {
    final MarkSweepPruner pruner =
        new MarkSweepPruner(worldStateStorage, blockchain, markStorage, metricsSystem);

    // Generate accounts and save corresponding state root
    final int numBlocks = 15;
    final int numAccounts = 10;
    generateBlockchainData(numBlocks, numAccounts);

    final int markBlockNumber = 10;
    final BlockHeader markBlock = blockchain.getBlockHeader(markBlockNumber).get();

    // Collect state roots we expect to be swept first
    final List<Bytes32> stateRoots = new ArrayList<>();
    for (int i = markBlockNumber - 1; i >= 0; i--) {
      stateRoots.add(blockchain.getBlockHeader(i).get().getStateRoot());
    }

    // Mark
    pruner.mark(markBlock.getStateRoot());
    // Mark an extra state root
    Hash markedRoot = Hash.wrap(stateRoots.remove(stateRoots.size() / 2));
    pruner.markNode(markedRoot);
    // Sweep
    pruner.sweepBefore(markBlock.getNumber());

    // we use individual inOrders because we only want to make sure we remove a state root before
    // the full prune but without enforcing an ordering between the state root removals
    stateRoots.forEach(
        stateRoot -> {
          final InOrder thisRootsOrdering = inOrder(hashValueStore, worldStateStorage);
          thisRootsOrdering.verify(hashValueStore).remove(stateRoot);
          thisRootsOrdering.verify(worldStateStorage).prune(any());
        });

    assertThat(stateStorage.containsKey(markedRoot.toArray())).isTrue();
  }

  private void generateBlockchainData(final int numBlocks, final int numAccounts) {
    Block parentBlock = blockchain.getChainHeadBlock();
    for (int i = 0; i < numBlocks; i++) {
      final BlockHeader parentHeader = parentBlock.getHeader();
      final MutableWorldState worldState =
          worldStateArchive.getMutable(parentHeader.getStateRoot(), parentHeader.getHash()).get();
      gen.createRandomContractAccountsWithNonEmptyStorage(worldState, numAccounts);
      final Hash stateRoot = worldState.rootHash();

      final Block block =
          gen.block(
              BlockOptions.create()
                  .setStateRoot(stateRoot)
                  .setBlockNumber(parentHeader.getNumber() + 1L)
                  .setParentHash(parentBlock.getHash()));
      final List<TransactionReceipt> receipts = gen.receipts(block);
      blockchain.appendBlock(block, receipts);
      parentBlock = block;
    }
  }

  private Set<Bytes> collectWorldStateNodes(final Hash stateRootHash) {
    final Set<Bytes> nodeData = new HashSet<>();
    collectWorldStateNodes(stateRootHash, nodeData);
    return nodeData;
  }

  private Set<Bytes> collectWorldStateNodes(final Hash stateRootHash, final Set<Bytes> collector) {
    final List<Hash> storageRoots = new ArrayList<>();
    final MerklePatriciaTrie<Bytes32, Bytes> stateTrie = createStateTrie(stateRootHash);

    // Collect storage roots and code
    stateTrie
        .entriesFrom(Bytes32.ZERO, 1000)
        .forEach(
            (key, val) -> {
              final StateTrieAccountValue accountValue =
                  StateTrieAccountValue.readFrom(RLP.input(val));
              stateStorage
                  .get(accountValue.getCodeHash().toArray())
                  .ifPresent(v -> collector.add(Bytes.wrap(v)));
              storageRoots.add(accountValue.getStorageRoot());
            });

    // Collect state nodes
    collectTrieNodes(stateTrie, collector);
    // Collect storage nodes
    for (Hash storageRoot : storageRoots) {
      final MerklePatriciaTrie<Bytes32, Bytes> storageTrie = createStorageTrie(storageRoot);
      collectTrieNodes(storageTrie, collector);
    }

    return collector;
  }

  private void collectTrieNodes(
      final MerklePatriciaTrie<Bytes32, Bytes> trie, final Set<Bytes> collector) {
    final Bytes32 rootHash = trie.getRootHash();
    trie.visitAll(
        (node) -> {
          if (node.isReferencedByHash() || node.getHash().equals(rootHash)) {
            collector.add(node.getRlp());
          }
        });
  }

  private MerklePatriciaTrie<Bytes32, Bytes> createStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode,
        rootHash,
        Function.identity(),
        Function.identity());
  }

  private MerklePatriciaTrie<Bytes32, Bytes> createStorageTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) -> worldStateStorage.getAccountStorageTrieNode(null, location, hash),
        rootHash,
        Function.identity(),
        Function.identity());
  }

  // Proxy class so that we have access to the constructor that takes our own map
  private static class TestInMemoryStorage extends InMemoryKeyValueStorage {

    public TestInMemoryStorage(final Map<Bytes, byte[]> hashValueStore) {
      super(hashValueStore);
    }
  }
}

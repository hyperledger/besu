/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;

import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator.BlockOptions;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.trie.StoredMerklePatriciaTrie;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Test;
import org.mockito.InOrder;

public class MarkSweepPrunerTest {

  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final Map<BytesValue, byte[]> hashValueStore = spy(new HashMap<>());
  private final InMemoryKeyValueStorage stateStorage = spy(new TestInMemoryStorage(hashValueStore));
  private final WorldStateStorage worldStateStorage = new WorldStateKeyValueStorage(stateStorage);
  private final WorldStateArchive worldStateArchive =
      new WorldStateArchive(
          worldStateStorage, new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  private final InMemoryKeyValueStorage markStorage = new InMemoryKeyValueStorage();
  private final Block genesisBlock = gen.genesisBlock();
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesisBlock);

  @Test
  public void prepareMarkAndSweep_smallState_manyOpsPerTx() {
    testPrepareMarkAndSweep(3, 1, 2, 1000);
  }

  @Test
  public void prepareMarkAndSweep_largeState_fewOpsPerTx() {
    testPrepareMarkAndSweep(20, 5, 5, 5);
  }

  @Test
  public void prepareMarkAndSweep_emptyBlocks() {
    testPrepareMarkAndSweep(10, 0, 5, 10);
  }

  @Test
  public void prepareMarkAndSweep_markChainhead() {
    testPrepareMarkAndSweep(10, 2, 10, 20);
  }

  @Test
  public void prepareMarkAndSweep_markGenesis() {
    testPrepareMarkAndSweep(10, 2, 0, 20);
  }

  @Test
  public void prepareMarkAndSweep_multipleRounds() {
    testPrepareMarkAndSweep(10, 2, 10, 20);
    testPrepareMarkAndSweep(10, 2, 15, 20);
  }

  private void testPrepareMarkAndSweep(
      final int numBlocks,
      final int accountsPerBlock,
      final int markBlockNumber,
      final int opsPerTransaction) {
    final MarkSweepPruner pruner =
        new MarkSweepPruner(
            worldStateStorage, blockchain, markStorage, metricsSystem, opsPerTransaction);
    final int chainHeight = (int) blockchain.getChainHead().getHeight();
    // Generate blocks up to markBlockNumber
    final int blockCountBeforeMarkedBlock = markBlockNumber - chainHeight;
    generateBlockchainData(blockCountBeforeMarkedBlock, accountsPerBlock);

    // Prepare
    pruner.prepare();
    // Mark
    final BlockHeader markBlock = blockchain.getBlockHeader(markBlockNumber).get();
    pruner.mark(markBlock.getStateRoot());

    // Generate more blocks that should be kept
    generateBlockchainData(numBlocks - blockCountBeforeMarkedBlock, accountsPerBlock);

    // Collect the nodes we expect to keep
    final Set<BytesValue> expectedNodes = collectWorldStateNodes(markBlock.getStateRoot());
    for (int i = markBlockNumber; i <= blockchain.getChainHeadBlockNumber(); i++) {
      final Hash stateRoot = blockchain.getBlockHeader(i).get().getStateRoot();
      collectWorldStateNodes(stateRoot, expectedNodes);
    }
    if (accountsPerBlock != 0 && markBlockNumber > 0) {
      assertThat(hashValueStore.size()).isGreaterThan(expectedNodes.size()); // Sanity check
    }

    // Sweep
    pruner.sweepBefore(markBlock.getNumber());

    // Assert that blocks from mark point onward are still accessible
    for (int i = markBlockNumber; i <= blockchain.getChainHeadBlockNumber(); i++) {
      final Hash stateRoot = blockchain.getBlockHeader(i).get().getStateRoot();
      assertThat(worldStateArchive.get(stateRoot)).isPresent();
      final WorldState markedState = worldStateArchive.get(stateRoot).get();
      // Traverse accounts and make sure all are accessible
      final int expectedAccounts = accountsPerBlock * i;
      final long accounts = markedState.streamAccounts(Bytes32.ZERO, expectedAccounts * 2).count();
      assertThat(accounts).isEqualTo(expectedAccounts);
      // Traverse storage to ensure that all storage is accessible
      markedState
          .streamAccounts(Bytes32.ZERO, expectedAccounts * 2)
          .forEach(a -> a.storageEntriesFrom(Bytes32.ZERO, 1000));
    }

    // All other state roots should have been removed
    for (int i = 0; i < markBlockNumber; i++) {
      final BlockHeader curHeader = blockchain.getBlockHeader(i + 1L).get();
      if (curHeader.getNumber() == markBlock.getNumber()) {
        continue;
      }
      if (!curHeader.getStateRoot().equals(Hash.EMPTY_TRIE_HASH)) {
        assertThat(worldStateArchive.get(curHeader.getStateRoot())).isEmpty();
      }
    }

    // Check that storage contains only the values we expect
    assertThat(hashValueStore.size()).isEqualTo(expectedNodes.size());
    assertThat(hashValueStore.values())
        .containsExactlyInAnyOrderElementsOf(
            expectedNodes.stream().map(BytesValue::getArrayUnsafe).collect(Collectors.toSet()));
  }

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
    final Set<BytesValue> expectedNodes = collectWorldStateNodes(markBlock.getStateRoot());
    assertThat(hashValueStore.size()).isGreaterThan(expectedNodes.size()); // Sanity check

    // Mark and sweep
    pruner.mark(markBlock.getStateRoot());
    pruner.sweepBefore(markBlock.getNumber());

    // Assert that the block we marked is still present and all accounts are accessible
    assertThat(worldStateArchive.get(markBlock.getStateRoot())).isPresent();
    final WorldState markedState = worldStateArchive.get(markBlock.getStateRoot()).get();
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
      assertThat(worldStateArchive.get(curHeader.getStateRoot())).isEmpty();
    }

    // Check that storage contains only the values we expect
    assertThat(hashValueStore.size()).isEqualTo(expectedNodes.size());
    assertThat(hashValueStore.values())
        .containsExactlyInAnyOrderElementsOf(
            expectedNodes.stream().map(BytesValue::getArrayUnsafe).collect(Collectors.toSet()));
  }

  @Test
  public void sweepBefore_shouldSweepStateRootFirst() {
    final MarkSweepPruner pruner =
        new MarkSweepPruner(worldStateStorage, blockchain, markStorage, metricsSystem, 1);

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

    // Check stateRoots are marked first
    InOrder inOrder = inOrder(hashValueStore, stateStorage);
    for (Bytes32 stateRoot : stateRoots) {
      inOrder.verify(hashValueStore).remove(stateRoot);
    }
    inOrder.verify(stateStorage).removeAllKeysUnless(any());
  }

  @Test
  public void sweepBefore_shouldNotRemoveMarkedStateRoots() {
    final MarkSweepPruner pruner =
        new MarkSweepPruner(worldStateStorage, blockchain, markStorage, metricsSystem, 1);

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

    // Check stateRoots are marked first
    InOrder inOrder = inOrder(hashValueStore, stateStorage);
    for (Bytes32 stateRoot : stateRoots) {
      inOrder.verify(hashValueStore).remove(stateRoot);
    }
    inOrder.verify(stateStorage).removeAllKeysUnless(any());

    assertThat(stateStorage.containsKey(markedRoot.getArrayUnsafe())).isTrue();
  }

  private void generateBlockchainData(final int numBlocks, final int numAccounts) {
    Block parentBlock = blockchain.getChainHeadBlock();
    for (int i = 0; i < numBlocks; i++) {
      final MutableWorldState worldState =
          worldStateArchive.getMutable(parentBlock.getHeader().getStateRoot()).get();
      gen.createRandomContractAccountsWithNonEmptyStorage(worldState, numAccounts);
      final Hash stateRoot = worldState.rootHash();

      final Block block =
          gen.block(
              BlockOptions.create()
                  .setStateRoot(stateRoot)
                  .setBlockNumber(parentBlock.getHeader().getNumber() + 1L)
                  .setParentHash(parentBlock.getHash()));
      final List<TransactionReceipt> receipts = gen.receipts(block);
      blockchain.appendBlock(block, receipts);
      parentBlock = block;
    }
  }

  private Set<BytesValue> collectWorldStateNodes(final Hash stateRootHash) {
    final Set<BytesValue> nodeData = new HashSet<>();
    collectWorldStateNodes(stateRootHash, nodeData);
    return nodeData;
  }

  private Set<BytesValue> collectWorldStateNodes(
      final Hash stateRootHash, final Set<BytesValue> collector) {
    final List<Hash> storageRoots = new ArrayList<>();
    final MerklePatriciaTrie<Bytes32, BytesValue> stateTrie = createStateTrie(stateRootHash);

    // Collect storage roots and code
    stateTrie
        .entriesFrom(Bytes32.ZERO, 1000)
        .forEach(
            (key, val) -> {
              final StateTrieAccountValue accountValue =
                  StateTrieAccountValue.readFrom(RLP.input(val));
              stateStorage
                  .get(accountValue.getCodeHash().getArrayUnsafe())
                  .ifPresent(v -> collector.add(BytesValue.wrap(v)));
              storageRoots.add(accountValue.getStorageRoot());
            });

    // Collect state nodes
    collectTrieNodes(stateTrie, collector);
    // Collect storage nodes
    for (Hash storageRoot : storageRoots) {
      final MerklePatriciaTrie<Bytes32, BytesValue> storageTrie = createStorageTrie(storageRoot);
      collectTrieNodes(storageTrie, collector);
    }

    return collector;
  }

  private void collectTrieNodes(
      final MerklePatriciaTrie<Bytes32, BytesValue> trie, final Set<BytesValue> collector) {
    final Bytes32 rootHash = trie.getRootHash();
    trie.visitAll(
        (node) -> {
          if (node.isReferencedByHash() || node.getHash().equals(rootHash)) {
            collector.add(node.getRlp());
          }
        });
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> createStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode,
        rootHash,
        Function.identity(),
        Function.identity());
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> createStorageTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStorageTrieNode,
        rootHash,
        Function.identity(),
        Function.identity());
  }

  private static class TestInMemoryStorage extends InMemoryKeyValueStorage {

    public TestInMemoryStorage(final Map<BytesValue, byte[]> hashValueStore) {
      super(hashValueStore);
    }
  }
}

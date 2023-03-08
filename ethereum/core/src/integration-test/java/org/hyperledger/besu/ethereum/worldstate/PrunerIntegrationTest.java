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
import org.hyperledger.besu.ethereum.worldstate.Pruner.PruningPhase;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.testutil.MockExecutorService;

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
import org.junit.jupiter.api.Test;

public class PrunerIntegrationTest {

  private final BlockDataGenerator gen = new BlockDataGenerator();
  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final Map<Bytes, byte[]> hashValueStore = new HashMap<>();
  private final InMemoryKeyValueStorage stateStorage = new TestInMemoryStorage(hashValueStore);
  private final WorldStateStorage worldStateStorage = new WorldStateKeyValueStorage(stateStorage);
  private final WorldStateArchive worldStateArchive =
      new DefaultWorldStateArchive(
          worldStateStorage, new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  private final InMemoryKeyValueStorage markStorage = new InMemoryKeyValueStorage();
  private final Block genesisBlock = gen.genesisBlock();
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesisBlock);

  @Test
  public void pruner_smallState_manyOpsPerTx() {
    testPruner(3, 1, 1, 4, 100_000);
  }

  @Test
  public void pruner_largeState_fewOpsPerTx() {
    testPruner(2, 5, 5, 6, 5);
  }

  @Test
  public void pruner_emptyBlocks() {
    testPruner(5, 0, 2, 5, 10);
  }

  @Test
  public void pruner_markChainhead() {
    testPruner(4, 2, 1, 10, 20);
  }

  @Test
  public void pruner_lowRelativeBlockConfirmations() {
    testPruner(3, 2, 1, 4, 20);
  }

  @Test
  public void pruner_highRelativeBlockConfirmations() {
    testPruner(3, 2, 9, 10, 20);
  }

  private void testPruner(
      final int numCycles,
      final int accountsPerBlock,
      final int blockConfirmations,
      final int numBlocksToKeep,
      final int opsPerTransaction) {

    final var markSweepPruner =
        new MarkSweepPruner(
            worldStateStorage, blockchain, markStorage, metricsSystem, opsPerTransaction);
    final var pruner =
        new Pruner(
            markSweepPruner,
            blockchain,
            new PrunerConfiguration(blockConfirmations, numBlocksToKeep),
            new MockExecutorService());

    pruner.start();

    for (int cycle = 0; cycle < numCycles; ++cycle) {
      int numBlockInCycle =
          numBlocksToKeep
              + 1; // +1 to get it to switch from MARKING_COMPLETE TO SWEEPING on each cycle
      var fullyMarkedBlockNum = cycle * numBlockInCycle + 1;

      // This should cause a full mark and sweep cycle
      assertThat(pruner.getPruningPhase()).isEqualByComparingTo(PruningPhase.IDLE);
      generateBlockchainData(numBlockInCycle, accountsPerBlock);
      assertThat(pruner.getPruningPhase()).isEqualByComparingTo(PruningPhase.IDLE);

      // Collect the nodes we expect to keep
      final Set<Bytes> expectedNodes = new HashSet<>();
      for (int i = fullyMarkedBlockNum; i <= blockchain.getChainHeadBlockNumber(); i++) {
        final Hash stateRoot = blockchain.getBlockHeader(i).get().getStateRoot();
        collectWorldStateNodes(stateRoot, expectedNodes);
      }

      if (accountsPerBlock != 0) {
        assertThat(hashValueStore.size())
            .isGreaterThanOrEqualTo(expectedNodes.size()); // Sanity check
      }

      // Assert that blocks from mark point onward are still accessible
      for (int i = fullyMarkedBlockNum; i <= blockchain.getChainHeadBlockNumber(); i++) {
        final BlockHeader blockHeader = blockchain.getBlockHeader(i).get();
        final Hash stateRoot = blockHeader.getStateRoot();
        assertThat(worldStateArchive.get(stateRoot, blockHeader.getHash())).isPresent();
        final WorldState markedState =
            worldStateArchive.get(stateRoot, blockHeader.getHash()).get();
        // Traverse accounts and make sure all are accessible
        final int expectedAccounts = accountsPerBlock * i;
        final long accounts =
            markedState.streamAccounts(Bytes32.ZERO, expectedAccounts * 2).count();
        assertThat(accounts).isEqualTo(expectedAccounts);
        // Traverse storage to ensure that all storage is accessible
        markedState
            .streamAccounts(Bytes32.ZERO, expectedAccounts * 2)
            .forEach(a -> a.storageEntriesFrom(Bytes32.ZERO, 1000));
      }

      // All other state roots should have been removed
      for (int i = 0; i < fullyMarkedBlockNum; i++) {
        final BlockHeader curHeader = blockchain.getBlockHeader(i).get();
        if (!curHeader.getStateRoot().equals(Hash.EMPTY_TRIE_HASH)) {
          assertThat(worldStateArchive.get(curHeader.getStateRoot(), curHeader.getHash()))
              .isEmpty();
        }
      }

      // Check that storage contains only the values we expect
      assertThat(hashValueStore.size()).isEqualTo(expectedNodes.size());
      assertThat(hashValueStore.values())
          .containsExactlyInAnyOrderElementsOf(
              expectedNodes.stream().map(Bytes::toArrayUnsafe).collect(Collectors.toSet()));
    }

    pruner.stop();
  }

  private void generateBlockchainData(final int numBlocks, final int numAccounts) {
    Block parentBlock = blockchain.getChainHeadBlock();
    for (int i = 0; i < numBlocks; i++) {
      final BlockHeader parentHeader = parentBlock.getHeader();
      final Hash parentHash = parentBlock.getHash();
      final MutableWorldState worldState =
          worldStateArchive.getMutable(parentHeader.getStateRoot(), parentHash).get();
      gen.createRandomContractAccountsWithNonEmptyStorage(worldState, numAccounts);
      final Hash stateRoot = worldState.rootHash();

      final Block block =
          gen.block(
              BlockOptions.create()
                  .setStateRoot(stateRoot)
                  .setBlockNumber(parentHeader.getNumber() + 1L)
                  .setParentHash(parentHash));
      final List<TransactionReceipt> receipts = gen.receipts(block);
      blockchain.appendBlock(block, receipts);
      parentBlock = block;
    }
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

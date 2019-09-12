/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator.BlockOptions;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.Test;

public class DefaultBlockchainTest {

  @Test
  public void initializeNew() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    assertBlockDataIsStored(blockchain, genesisBlock, Collections.emptyList());
    assertBlockIsHead(blockchain, genesisBlock);
    assertTotalDifficultiesAreConsistent(blockchain, genesisBlock);
    assertThat(blockchain.getForks()).isEmpty();
  }

  @Test
  public void initializeExisting() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    // Write to kv store
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    createMutableBlockchain(kvStore, genesisBlock);

    // Initialize a new blockchain store with kvStore that already contains data
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    assertBlockDataIsStored(blockchain, genesisBlock, Collections.emptyList());
    assertBlockIsHead(blockchain, genesisBlock);
    assertTotalDifficultiesAreConsistent(blockchain, genesisBlock);
    assertThat(blockchain.getForks()).isEmpty();
  }

  @Test
  public void initializeExistingWithWrongGenesisBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    // Write to kv store
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    createMutableBlockchain(kvStore, genesisBlock);

    // Initialize a new blockchain store with same kvStore, but different genesis block
    assertThatThrownBy(() -> createMutableBlockchain(kvStore, gen.genesisBlock()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Supplied genesis block does not match stored chain data.\n"
                + "Please specify a different data directory with --data-path or specify the original genesis file with --genesis-file.");
  }

  @Test
  public void initializeReadOnly_withGenesisBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();

    // Write genesis block to storage
    createMutableBlockchain(kvStore, genesisBlock);

    // Create read only chain
    final Blockchain blockchain = createBlockchain(kvStore);

    assertBlockDataIsStored(blockchain, genesisBlock, Collections.emptyList());
    assertBlockIsHead(blockchain, genesisBlock);
    assertTotalDifficultiesAreConsistent(blockchain, genesisBlock);
  }

  @Test
  public void initializeReadOnly_withSmallChain() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final List<Block> blocks = gen.blockSequence(10);
    final List<List<TransactionReceipt>> blockReceipts = new ArrayList<>(blocks.size());
    blockReceipts.add(Collections.emptyList());

    // Write small chain to storage
    final MutableBlockchain mutableBlockchain = createMutableBlockchain(kvStore, blocks.get(0));
    for (int i = 1; i < blocks.size(); i++) {
      final Block block = blocks.get(i);
      final List<TransactionReceipt> receipts = gen.receipts(block);
      blockReceipts.add(receipts);
      mutableBlockchain.appendBlock(block, receipts);
    }

    // Create read only chain
    final Blockchain blockchain = createBlockchain(kvStore);

    for (int i = 0; i < blocks.size(); i++) {
      assertBlockDataIsStored(blockchain, blocks.get(i), blockReceipts.get(i));
    }
    final Block lastBlock = blocks.get(blocks.size() - 1);
    assertBlockIsHead(blockchain, lastBlock);
    assertTotalDifficultiesAreConsistent(blockchain, lastBlock);
  }

  @Test
  public void initializeReadOnly_emptyStorage() {
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();

    assertThatThrownBy(() -> createBlockchain(kvStore))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot create Blockchain from empty storage");
  }

  @Test
  public void appendBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    final BlockOptions options =
        new BlockOptions().setBlockNumber(1L).setParentHash(genesisBlock.getHash());
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);
    blockchain.appendBlock(newBlock, receipts);

    assertBlockIsHead(blockchain, newBlock);
    assertTotalDifficultiesAreConsistent(blockchain, newBlock);
    assertThat(blockchain.getForks()).isEmpty();
  }

  @Test(expected = IllegalArgumentException.class)
  public void appendUnconnectedBlock() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    final BlockOptions options = new BlockOptions().setBlockNumber(1L).setParentHash(Hash.ZERO);
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);
    blockchain.appendBlock(newBlock, receipts);
  }

  @Test(expected = IllegalArgumentException.class)
  public void appendBlockWithMismatchedReceipts() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    final BlockOptions options =
        new BlockOptions().setBlockNumber(1L).setParentHash(genesisBlock.getHash());
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);
    receipts.add(gen.receipt());
    blockchain.appendBlock(newBlock, receipts);
  }

  @Test
  public void createSmallChain() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final List<Block> chain = gen.blockSequence(3);
    final List<List<TransactionReceipt>> blockReceipts =
        chain.stream().map(gen::receipts).collect(Collectors.toList());

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, chain.get(0));
    for (int i = 1; i < chain.size(); i++) {
      blockchain.appendBlock(chain.get(i), blockReceipts.get(i));
    }

    for (int i = 1; i < chain.size(); i++) {
      assertBlockDataIsStored(blockchain, chain.get(i), blockReceipts.get(i));
    }

    final Block head = chain.get(chain.size() - 1);
    assertBlockIsHead(blockchain, head);
    assertTotalDifficultiesAreConsistent(blockchain, head);
    assertThat(blockchain.getForks()).isEmpty();
  }

  @Test
  public void appendBlockWithReorgToChainAtEqualHeight() {
    final BlockDataGenerator gen = new BlockDataGenerator(1);

    // Setup an initial blockchain
    final int chainLength = 3;
    final List<Block> chain = gen.blockSequence(chainLength);
    final List<List<TransactionReceipt>> blockReceipts =
        chain.stream().map(gen::receipts).collect(Collectors.toList());
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, chain.get(0));
    for (int i = 1; i < chain.size(); i++) {
      blockchain.appendBlock(chain.get(i), blockReceipts.get(i));
    }
    assertThat(blockchain.getForks()).isEmpty();
    final Block originalHead = chain.get(chainLength - 1);

    // Create parallel fork of length 1
    final int forkBlock = 2;
    final int commonAncestor = 1;
    final BlockOptions options =
        new BlockOptions()
            .setParentHash(chain.get(commonAncestor).getHash())
            .setBlockNumber(forkBlock)
            .setDifficulty(chain.get(forkBlock).getHeader().getDifficulty().plus(10L));
    final Block fork = gen.block(options);
    final List<TransactionReceipt> forkReceipts = gen.receipts(fork);
    final List<Block> reorgedChain = new ArrayList<>(chain.subList(0, forkBlock));
    reorgedChain.add(fork);
    final List<List<TransactionReceipt>> reorgedReceipts =
        new ArrayList<>(blockReceipts.subList(0, forkBlock));
    reorgedReceipts.add(forkReceipts);

    // Add fork
    blockchain.appendBlock(fork, forkReceipts);

    // Check chain has reorganized
    for (int i = 0; i < reorgedChain.size(); i++) {
      assertBlockDataIsStored(blockchain, reorgedChain.get(i), reorgedReceipts.get(i));
    }
    // Check old transactions have been removed
    for (final Transaction tx : originalHead.getBody().getTransactions()) {
      assertThat(blockchain.getTransactionByHash(tx.hash())).isNotPresent();
    }

    assertBlockIsHead(blockchain, fork);
    assertTotalDifficultiesAreConsistent(blockchain, fork);
    // Old chain head should now be tracked as a fork.
    final Set<Hash> forks = blockchain.getForks();
    assertThat(forks.size()).isEqualTo(1);
    assertThat(forks.stream().anyMatch(f -> f.equals(originalHead.getHash()))).isTrue();
    // Old chain should not be on canonical chain.
    for (int i = commonAncestor + 1; i < chainLength; i++) {
      assertThat(blockchain.blockIsOnCanonicalChain(chain.get(i).getHash())).isFalse();
    }
  }

  @Test
  public void appendBlockWithReorgToShorterChain() {
    final BlockDataGenerator gen = new BlockDataGenerator(2);

    // Setup an initial blockchain
    final int originalChainLength = 4;
    final List<Block> chain = gen.blockSequence(originalChainLength);
    final List<List<TransactionReceipt>> blockReceipts =
        chain.stream().map(gen::receipts).collect(Collectors.toList());
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, chain.get(0));
    for (int i = 1; i < chain.size(); i++) {
      blockchain.appendBlock(chain.get(i), blockReceipts.get(i));
    }
    final Block originalHead = chain.get(originalChainLength - 1);

    // Create parallel fork of length 2 from 3 blocks back
    final List<Block> forkBlocks = new ArrayList<>();
    final int forkStart = 1;
    final int commonAncestor = 0;
    // Generate first block
    BlockOptions options =
        new BlockOptions()
            .setParentHash(chain.get(commonAncestor).getHash())
            .setBlockNumber(forkStart)
            .setDifficulty(chain.get(forkStart).getHeader().getDifficulty().minus(5L));
    forkBlocks.add(gen.block(options));
    // Generate second block
    final UInt256 remainingDifficultyToOutpace =
        chain
            .get(forkStart + 1)
            .getHeader()
            .getDifficulty()
            .plus(chain.get(forkStart + 2).getHeader().getDifficulty());
    options =
        new BlockOptions()
            .setParentHash(forkBlocks.get(0).getHash())
            .setBlockNumber(forkStart + 1)
            .setDifficulty(remainingDifficultyToOutpace.plus(10L));
    forkBlocks.add(gen.block(options));
    // Generate corresponding receipts
    final List<List<TransactionReceipt>> forkReceipts =
        forkBlocks.stream().map(gen::receipts).collect(Collectors.toList());

    // Collect fork data
    final List<Block> reorgedChain = new ArrayList<>(chain.subList(0, forkStart));
    reorgedChain.addAll(forkBlocks);
    final List<List<TransactionReceipt>> reorgedReceipts =
        new ArrayList<>(blockReceipts.subList(0, forkStart));
    reorgedReceipts.addAll(forkReceipts);

    // Add first block in fork, which should not cause a reorg
    blockchain.appendBlock(forkBlocks.get(0), forkReceipts.get(0));
    // Check chain has not reorganized
    for (int i = 0; i < chain.size(); i++) {
      assertBlockDataIsStored(blockchain, chain.get(i), blockReceipts.get(i));
    }
    assertBlockIsHead(blockchain, originalHead);
    assertTotalDifficultiesAreConsistent(blockchain, originalHead);
    // Check transactions were not indexed
    for (final Transaction tx : forkBlocks.get(0).getBody().getTransactions()) {
      assertThat(blockchain.getTransactionByHash(tx.hash())).isNotPresent();
    }
    // Appended block should be tracked as a fork
    assertThat(blockchain.blockIsOnCanonicalChain(forkBlocks.get(0).getHash())).isFalse();
    Set<Hash> forks = blockchain.getForks();
    assertThat(forks.size()).isEqualTo(1);
    assertThat(forks.stream().anyMatch(f -> f.equals(forkBlocks.get(0).getHash()))).isTrue();

    // Add second block in fork, which should cause a reorg
    blockchain.appendBlock(forkBlocks.get(1), forkReceipts.get(1));
    // Check chain has reorganized
    for (int i = 0; i < reorgedChain.size(); i++) {
      assertBlockDataIsStored(blockchain, reorgedChain.get(i), reorgedReceipts.get(i));
    }
    assertBlockIsHead(blockchain, forkBlocks.get(1));
    assertTotalDifficultiesAreConsistent(blockchain, forkBlocks.get(1));
    // Check old transactions have been removed
    final List<Transaction> removedTransactions = new ArrayList<>();
    for (int i = forkStart; i < originalChainLength; i++) {
      removedTransactions.addAll(chain.get(i).getBody().getTransactions());
    }
    for (final Transaction tx : removedTransactions) {
      assertThat(blockchain.getTransactionByHash(tx.hash())).isNotPresent();
    }

    // Check that blockNumber index for previous chain head has been removed
    assertThat(blockchain.getBlockHashByNumber(originalChainLength - 1)).isNotPresent();
    // Old chain head should now be tracked as a fork.
    forks = blockchain.getForks();
    assertThat(forks.size()).isEqualTo(1);
    assertThat(forks.stream().anyMatch(f -> f.equals(originalHead.getHash()))).isTrue();
    // Old chain should not be on canonical chain.
    for (int i = commonAncestor + 1; i < originalChainLength; i++) {
      assertThat(blockchain.blockIsOnCanonicalChain(chain.get(i).getHash())).isFalse();
    }
  }

  @Test
  public void appendBlockWithReorgToLongerChain() {
    final BlockDataGenerator gen = new BlockDataGenerator(2);

    // Setup an initial blockchain
    final int originalChainLength = 4;
    final List<Block> chain = gen.blockSequence(originalChainLength);
    final List<List<TransactionReceipt>> blockReceipts =
        chain.stream().map(gen::receipts).collect(Collectors.toList());
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, chain.get(0));
    for (int i = 1; i < chain.size(); i++) {
      blockchain.appendBlock(chain.get(i), blockReceipts.get(i));
    }
    final Block originalHead = chain.get(originalChainLength - 1);

    // Create parallel fork of length 2 from 3 blocks back
    final List<Block> forkBlocks = new ArrayList<>();
    final int forkStart = 3;
    final int commonAncestor = 2;
    // Generate first block
    BlockOptions options =
        new BlockOptions()
            .setParentHash(chain.get(commonAncestor).getHash())
            .setBlockNumber(forkStart)
            .setDifficulty(chain.get(forkStart).getHeader().getDifficulty().minus(5L));
    forkBlocks.add(gen.block(options));
    // Generate second block
    options =
        new BlockOptions()
            .setParentHash(forkBlocks.get(0).getHash())
            .setBlockNumber(forkStart + 1)
            .setDifficulty(UInt256.of(10L));
    forkBlocks.add(gen.block(options));
    // Generate corresponding receipts
    final List<List<TransactionReceipt>> forkReceipts =
        forkBlocks.stream().map(gen::receipts).collect(Collectors.toList());

    // Collect fork data
    final List<Block> reorgedChain = new ArrayList<>(chain.subList(0, forkStart));
    reorgedChain.addAll(forkBlocks);
    final List<List<TransactionReceipt>> reorgedReceipts =
        new ArrayList<>(blockReceipts.subList(0, forkStart));
    reorgedReceipts.addAll(forkReceipts);

    // Add first block in fork, which should not cause a reorg
    blockchain.appendBlock(forkBlocks.get(0), forkReceipts.get(0));
    // Check chain has not reorganized
    for (int i = 0; i < chain.size(); i++) {
      assertBlockDataIsStored(blockchain, chain.get(i), blockReceipts.get(i));
    }
    assertBlockIsHead(blockchain, originalHead);
    assertTotalDifficultiesAreConsistent(blockchain, originalHead);
    // Check transactions were not indexed
    for (final Transaction tx : forkBlocks.get(0).getBody().getTransactions()) {
      assertThat(blockchain.getTransactionByHash(tx.hash())).isNotPresent();
    }
    // Appended block should be tracked as a fork
    assertThat(blockchain.blockIsOnCanonicalChain(forkBlocks.get(0).getHash())).isFalse();
    Set<Hash> forks = blockchain.getForks();
    assertThat(forks.size()).isEqualTo(1);
    assertThat(forks.stream().anyMatch(f -> f.equals(forkBlocks.get(0).getHash()))).isTrue();

    // Add second block in fork, which should cause a reorg
    blockchain.appendBlock(forkBlocks.get(1), forkReceipts.get(1));
    // Check chain has reorganized
    for (int i = 0; i < reorgedChain.size(); i++) {
      assertBlockDataIsStored(blockchain, reorgedChain.get(i), reorgedReceipts.get(i));
    }
    assertBlockIsHead(blockchain, forkBlocks.get(1));
    assertTotalDifficultiesAreConsistent(blockchain, forkBlocks.get(1));
    // Check old transactions have been removed
    final List<Transaction> removedTransactions = new ArrayList<>();
    for (int i = forkStart; i < originalChainLength; i++) {
      removedTransactions.addAll(chain.get(i).getBody().getTransactions());
    }
    for (final Transaction tx : removedTransactions) {
      assertThat(blockchain.getTransactionByHash(tx.hash())).isNotPresent();
    }
    // Old chain head should now be tracked as a fork.
    forks = blockchain.getForks();
    assertThat(forks.size()).isEqualTo(1);
    assertThat(forks.stream().anyMatch(f -> f.equals(originalHead.getHash()))).isTrue();
    // Old chain should not be on canonical chain.
    for (int i = commonAncestor + 1; i < originalChainLength; i++) {
      assertThat(blockchain.blockIsOnCanonicalChain(chain.get(i).getHash())).isFalse();
    }
  }

  @Test
  public void reorgWithOverlappingTransactions() {
    final BlockDataGenerator gen = new BlockDataGenerator(1);

    // Setup an initial blockchain
    final int chainLength = 3;
    final List<Block> chain = gen.blockSequence(chainLength);
    final List<List<TransactionReceipt>> blockReceipts =
        chain.stream().map(gen::receipts).collect(Collectors.toList());
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, chain.get(0));
    for (int i = 1; i < chain.size(); i++) {
      blockchain.appendBlock(chain.get(i), blockReceipts.get(i));
    }
    final Transaction overlappingTx = chain.get(chainLength - 1).getBody().getTransactions().get(0);

    // Create parallel fork of length 1
    final int forkBlock = 2;
    final int commonAncestor = 1;
    final BlockOptions options =
        new BlockOptions()
            .setParentHash(chain.get(commonAncestor).getHash())
            .setBlockNumber(forkBlock)
            .setDifficulty(chain.get(forkBlock).getHeader().getDifficulty().plus(10L))
            .addTransaction(overlappingTx)
            .addTransaction(gen.transaction());
    final Block fork = gen.block(options);
    final List<TransactionReceipt> forkReceipts = gen.receipts(fork);
    final List<Block> reorgedChain = new ArrayList<>(chain.subList(0, forkBlock));
    reorgedChain.add(fork);
    final List<List<TransactionReceipt>> reorgedReceipts =
        new ArrayList<>(blockReceipts.subList(0, forkBlock));
    reorgedReceipts.add(forkReceipts);

    // Add fork
    blockchain.appendBlock(fork, forkReceipts);

    // Check chain has reorganized
    for (int i = 0; i < reorgedChain.size(); i++) {
      assertBlockDataIsStored(blockchain, reorgedChain.get(i), reorgedReceipts.get(i));
    }

    // Check old transactions have been removed
    for (final Transaction tx : chain.get(chainLength - 1).getBody().getTransactions()) {
      final Optional<Transaction> actualTransaction = blockchain.getTransactionByHash(tx.hash());
      if (tx.equals(overlappingTx)) {
        assertThat(actualTransaction).isPresent();
      } else {
        assertThat(actualTransaction).isNotPresent();
      }
    }
  }

  @Test
  public void rewindChain() {
    final BlockDataGenerator gen = new BlockDataGenerator(2);

    // Setup an initial blockchain
    final int originalChainLength = 4;
    final List<Block> chain = gen.blockSequence(originalChainLength);
    final List<List<TransactionReceipt>> blockReceipts =
        chain.stream().map(gen::receipts).collect(Collectors.toList());
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, chain.get(0));
    for (int i = 1; i < chain.size(); i++) {
      blockchain.appendBlock(chain.get(i), blockReceipts.get(i));
    }
    final Block originalHead = blockchain.getChainHeadBlock();
    final Block targetHead =
        blockchain.getBlockByHash(originalHead.getHeader().getParentHash()).get();

    // rewind it by 1 block
    blockchain.rewindToBlock(targetHead.getHeader().getNumber());

    // Check chain has the expected blocks
    for (int i = 0; i < chain.size() - 1; i++) {
      assertBlockDataIsStored(blockchain, chain.get(i), blockReceipts.get(i));
    }
    assertBlockIsHead(blockchain, targetHead);

    // Check transactions were not indexed
    for (final Transaction tx : originalHead.getBody().getTransactions()) {
      assertThat(blockchain.getTransactionByHash(tx.hash())).isNotPresent();
    }

    // Check that blockNumber index for previous chain head has been removed
    assertThat(blockchain.getBlockHashByNumber(originalHead.getHeader().getNumber()))
        .isNotPresent();
    // Old chain head should not be tracked.
    assertThat(blockchain.blockIsOnCanonicalChain(originalHead.getHash())).isFalse();
  }

  @Test
  public void appendBlockForFork() {
    final BlockDataGenerator gen = new BlockDataGenerator(2);

    // Setup an initial blockchain
    final int originalChainLength = 4;
    final List<Block> chain = gen.blockSequence(originalChainLength);
    final List<List<TransactionReceipt>> blockReceipts =
        chain.stream().map(gen::receipts).collect(Collectors.toList());
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, chain.get(0));
    for (int i = 1; i < chain.size(); i++) {
      blockchain.appendBlock(chain.get(i), blockReceipts.get(i));
    }
    final Block originalHead = chain.get(originalChainLength - 1);

    // Create fork of length 2
    final List<Block> forkBlocks = new ArrayList<>();
    final int forkStart = 2;
    final int commonAncestor = 1;
    // Generate first block
    BlockOptions options =
        new BlockOptions()
            .setParentHash(chain.get(commonAncestor).getHash())
            .setBlockNumber(forkStart)
            .setDifficulty(chain.get(forkStart).getHeader().getDifficulty().minus(5L));
    forkBlocks.add(gen.block(options));
    // Generate second block
    options =
        new BlockOptions()
            .setParentHash(forkBlocks.get(0).getHash())
            .setBlockNumber(forkStart + 1)
            .setDifficulty(chain.get(forkStart + 1).getHeader().getDifficulty().minus(5L));
    forkBlocks.add(gen.block(options));
    // Generate corresponding receipts
    final List<List<TransactionReceipt>> forkReceipts =
        forkBlocks.stream().map(gen::receipts).collect(Collectors.toList());

    // Add fork blocks, which should not cause a reorg
    for (int i = 0; i < forkBlocks.size(); i++) {
      final Block forkBlock = forkBlocks.get(i);
      blockchain.appendBlock(forkBlock, forkReceipts.get(i));
      // Check chain has not reorganized
      for (int j = 0; j < chain.size(); j++) {
        assertBlockDataIsStored(blockchain, chain.get(j), blockReceipts.get(j));
      }
      assertBlockIsHead(blockchain, originalHead);
      assertTotalDifficultiesAreConsistent(blockchain, originalHead);
      // Check transactions were not indexed
      for (final Transaction tx : forkBlock.getBody().getTransactions()) {
        assertThat(blockchain.getTransactionByHash(tx.hash())).isNotPresent();
      }
      // Appended block should be tracked as a fork
      assertThat(blockchain.blockIsOnCanonicalChain(forkBlock.getHash())).isFalse();
      final Set<Hash> forks = blockchain.getForks();
      assertThat(forks.size()).isEqualTo(1);
      final Optional<Hash> trackedFork =
          forks.stream().filter(f -> f.equals(forkBlock.getHash())).findAny();
      assertThat(trackedFork).isPresent();
    }

    // Add another independent fork
    options =
        new BlockOptions()
            .setParentHash(chain.get(commonAncestor).getHash())
            .setBlockNumber(forkStart)
            .setDifficulty(chain.get(forkStart).getHeader().getDifficulty().minus(5L));
    final Block secondFork = gen.block(options);
    blockchain.appendBlock(secondFork, gen.receipts(secondFork));

    // We should now be tracking 2 forks
    assertThat(blockchain.blockIsOnCanonicalChain(secondFork.getHash())).isFalse();
    final Set<Hash> forks = blockchain.getForks();
    assertThat(forks.size()).isEqualTo(2);
    final Optional<Hash> trackedFork =
        forks.stream().filter(f -> f.equals(secondFork.getHash())).findAny();
    assertThat(trackedFork).isPresent();

    // Head should not have changed
    assertBlockIsHead(blockchain, originalHead);
  }

  @Test
  public void blockAddedObserver_removeNonexistentObserver() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    assertThat(blockchain.removeObserver(7)).isFalse();
  }

  @Test
  public void blockAddedObserver_addRemoveSingle() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    final long observerId = blockchain.observeBlockAdded((block, chain) -> {});
    assertThat(blockchain.observerCount()).isEqualTo(1);

    assertThat(blockchain.removeObserver(observerId)).isTrue();
    assertThat(blockchain.observerCount()).isEqualTo(0);
  }

  @Test(expected = NullPointerException.class)
  public void blockAddedObserver_nullObserver() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    blockchain.observeBlockAdded(null);
  }

  @Test
  public void blockAddedObserver_addRemoveMultiple() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    final long observerId1 = blockchain.observeBlockAdded((block, chain) -> {});
    assertThat(blockchain.observerCount()).isEqualTo(1);

    final long observerId2 = blockchain.observeBlockAdded((block, chain) -> {});
    assertThat(blockchain.observerCount()).isEqualTo(2);

    final long observerId3 = blockchain.observeBlockAdded((block, chain) -> {});
    assertThat(blockchain.observerCount()).isEqualTo(3);

    assertThat(blockchain.removeObserver(observerId1)).isTrue();
    assertThat(blockchain.observerCount()).isEqualTo(2);

    assertThat(blockchain.removeObserver(observerId2)).isTrue();
    assertThat(blockchain.observerCount()).isEqualTo(1);

    assertThat(blockchain.removeObserver(observerId3)).isTrue();
    assertThat(blockchain.observerCount()).isEqualTo(0);
  }

  @Test
  public void blockAddedObserver_invokedSingle() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    final BlockOptions options =
        new BlockOptions().setBlockNumber(1L).setParentHash(genesisBlock.getHash());
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);

    final AtomicBoolean observerInvoked = new AtomicBoolean(false);
    blockchain.observeBlockAdded((block, chain) -> observerInvoked.set(true));

    blockchain.appendBlock(newBlock, receipts);

    assertThat(observerInvoked.get()).isTrue();
  }

  @Test
  public void blockAddedObserver_invokedMultiple() {
    final BlockDataGenerator gen = new BlockDataGenerator();

    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final Block genesisBlock = gen.genesisBlock();
    final DefaultBlockchain blockchain = createMutableBlockchain(kvStore, genesisBlock);

    final BlockOptions options =
        new BlockOptions().setBlockNumber(1L).setParentHash(genesisBlock.getHash());
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);

    final AtomicBoolean observer1Invoked = new AtomicBoolean(false);
    blockchain.observeBlockAdded((block, chain) -> observer1Invoked.set(true));

    final AtomicBoolean observer2Invoked = new AtomicBoolean(false);
    blockchain.observeBlockAdded((block, chain) -> observer2Invoked.set(true));

    final AtomicBoolean observer3Invoked = new AtomicBoolean(false);
    blockchain.observeBlockAdded((block, chain) -> observer3Invoked.set(true));

    blockchain.appendBlock(newBlock, receipts);

    assertThat(observer1Invoked.get()).isTrue();
    assertThat(observer2Invoked.get()).isTrue();
    assertThat(observer3Invoked.get()).isTrue();
  }

  /*
   * Check that block header, block body, block number, transaction locations, and receipts for this
   * block are all stored.
   */
  private void assertBlockDataIsStored(
      final Blockchain blockchain, final Block block, final List<TransactionReceipt> receipts) {
    final Hash hash = block.getHash();
    assertEquals(hash, blockchain.getBlockHashByNumber(block.getHeader().getNumber()).get());
    assertEquals(block.getHeader(), blockchain.getBlockHeader(block.getHeader().getNumber()).get());
    assertEquals(block.getHeader(), blockchain.getBlockHeader(hash).get());
    assertEquals(block.getBody(), blockchain.getBlockBody(hash).get());
    assertThat(blockchain.blockIsOnCanonicalChain(block.getHash())).isTrue();

    final List<Transaction> txs = block.getBody().getTransactions();
    for (int i = 0; i < txs.size(); i++) {
      final Transaction expected = txs.get(i);
      final Transaction actual = blockchain.getTransactionByHash(expected.hash()).get();
      assertEquals(expected, actual);
    }
    final List<TransactionReceipt> actualReceipts = blockchain.getTxReceipts(hash).get();
    assertEquals(receipts, actualReceipts);
  }

  private void assertBlockIsHead(final Blockchain blockchain, final Block head) {
    assertEquals(head.getHash(), blockchain.getChainHeadHash());
    assertEquals(head.getHeader().getNumber(), blockchain.getChainHeadBlockNumber());
    assertEquals(head.getHash(), blockchain.getChainHead().getHash());
  }

  private void assertTotalDifficultiesAreConsistent(final Blockchain blockchain, final Block head) {
    // Check that total difficulties are summed correctly
    long num = BlockHeader.GENESIS_BLOCK_NUMBER;
    UInt256 td = UInt256.of(0);
    while (num <= head.getHeader().getNumber()) {
      final Hash curHash = blockchain.getBlockHashByNumber(num).get();
      final BlockHeader curHead = blockchain.getBlockHeader(curHash).get();
      td = td.plus(curHead.getDifficulty());
      assertEquals(td, blockchain.getTotalDifficultyByHash(curHash).get());

      num += 1;
    }

    // Check reported chainhead td
    assertEquals(td, blockchain.getChainHead().getTotalDifficulty());
  }

  private BlockchainStorage createStorage(final KeyValueStorage kvStore) {
    return new KeyValueStoragePrefixedKeyBlockchainStorage(
        kvStore, new MainnetBlockHeaderFunctions());
  }

  private DefaultBlockchain createMutableBlockchain(
      final KeyValueStorage kvStore, final Block genesisBlock) {
    return (DefaultBlockchain)
        DefaultBlockchain.createMutable(
            genesisBlock, createStorage(kvStore), new NoOpMetricsSystem());
  }

  private Blockchain createBlockchain(final KeyValueStorage kvStore) {
    return DefaultBlockchain.create(createStorage(kvStore), new NoOpMetricsSystem());
  }
}

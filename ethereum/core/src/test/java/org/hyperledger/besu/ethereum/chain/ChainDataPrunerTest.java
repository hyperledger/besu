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
package org.hyperledger.besu.ethereum.chain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ChainDataPrunerTest {

  @Test
  public void singleChainPruning() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.ALL, 512, 512, 512, 0, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Generate & Import 1000 blocks with BAL
    gen.blockSequence(genesisBlock, 1000)
        .forEach(
            blk -> {
              // Create and store BAL for each block
              final BlockAccessList bal = gen.blockAccessList();
              final BlockchainStorage.Updater updater = blockchainStorage.updater();
              updater.putBlockAccessList(blk.getHash(), bal);
              updater.commit();

              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              if (number <= 512) {
                // No prune happened
                assertThat(blockchain.getBlockHeader(1)).isPresent();
              } else {
                // Prune number - 512 only
                assertThat(blockchain.getBlockHeader(number - 512)).isEmpty();
                assertThat(blockchain.getBlockHeader(number - 511)).isPresent();
              }
            });
  }

  @Test
  public void forkPruning() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.ALL, 512, 512, 512, 0, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    List<Block> canonicalChain = gen.blockSequence(genesisBlock, 1000);
    List<Block> forkChain = gen.blockSequence(genesisBlock, 16);

    for (Block blk : forkChain) {
      blockchain.storeBlock(blk, gen.receipts(blk));
    }

    for (int i = 0; i < 512; i++) {
      Block blk = canonicalChain.get(i);
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // No prune happened
    assertThat(blockchain.getBlockByHash(canonicalChain.get(0).getHash())).isPresent();
    assertThat(blockchain.getBlockByHash(forkChain.get(0).getHash())).isPresent();

    for (int i = 512; i < 527; i++) {
      final int index = i;
      Block blk = canonicalChain.get(i);
      blockchain.appendBlock(blk, gen.receipts(blk));

      // Prune block on canonical chain and fork for i - 512 only
      assertThat(blockchain.getBlockByHash(canonicalChain.get(index - 512).getHash())).isEmpty();
      assertThat(blockchain.getBlockByHash(canonicalChain.get(i - 511).getHash())).isPresent();
      assertThat(blockchain.getBlockByHash(forkChain.get(index - 512).getHash())).isEmpty();
      assertThat(blockchain.getBlockByHash(forkChain.get(i - 511).getHash())).isPresent();
    }
  }

  @Test
  public void balOnlyPruning() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.BAL,
                Long.MAX_VALUE, // never prune blocks
                512,
                Long.MAX_VALUE,
                0,
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    gen.blockSequence(genesisBlock, 1000)
        .forEach(
            blk -> {
              final BlockAccessList bal = gen.blockAccessList();
              final BlockchainStorage.Updater updater = blockchainStorage.updater();
              updater.putBlockAccessList(blk.getHash(), bal);
              updater.commit();

              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              // Chain data should ALWAYS be present
              assertThat(blockchain.getBlockHeader(1)).isPresent();
              blockchain
                  .getBlockHeader(1)
                  .ifPresent(
                      header -> {
                        assertThat(blockchainStorage.getBlockBody(header.getBlockHash()))
                            .isPresent();
                        assertThat(blockchainStorage.getTransactionReceipts(header.getBlockHash()))
                            .isPresent();
                      });

              if (number > 512) {
                blockchain
                    .getBlockHeader(number - 512)
                    .ifPresent(
                        oldHeader -> {
                          assertThat(blockchainStorage.getBlockBody(oldHeader.getBlockHash()))
                              .isPresent();
                          assertThat(
                                  blockchainStorage.getTransactionReceipts(
                                      oldHeader.getBlockHash()))
                              .isPresent();
                          assertThat(blockchainStorage.getBlockAccessList(oldHeader.getBlockHash()))
                              .isEmpty();
                        });

                blockchain
                    .getBlockHeader(number - 511)
                    .ifPresent(
                        recentHeader -> {
                          assertThat(
                                  blockchainStorage.getBlockAccessList(recentHeader.getBlockHash()))
                              .isPresent();
                        });
              }
            });
  }

  @Test
  public void pruningWithFrequency() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.ALL, 256, 256, 256, 100, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Generate 400 blocks
    List<Block> blocks = gen.blockSequence(genesisBlock, 400);
    for (Block blk : blocks) {
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 400:
    // - We want to keep 256 blocks (blocks 145-400)
    // - blockPruningMark = 400 - 256 = 144
    //
    // Timeline:
    // Block 1-256: No pruning (retention = 256)
    // Block 257-355: Should prune but frequency not reached
    //                blocksToBePruned = (257-256) - 0 = 1 < 100
    // Block 356: First batch prunes blocks 0-100
    //            blockPruningMark = 356 - 256 = 100
    //            blocksToBePruned = 100 - 0 = 100
    // Block 400: blockPruningMark = 400 - 256 = 144
    //            blocksToBePruned = 144 - 100 = 44 < 100
    //            Only blocks 0-100 are pruned, blocks 101-144 wait for next batch

    // Blocks 1-100: Should be pruned (first batch at block 356)
    for (int i = 1; i <= 100; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should be pruned (first batch)", i)
          .isEmpty();
    }

    // Blocks 101-144: Should NOT be pruned yet (waiting for second batch at block 456)
    for (int i = 101; i <= 144; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (second batch needs block 456)", i)
          .isPresent();
    }

    // Blocks 145-400: Should exist (within retention window of 256)
    for (int i = 145; i <= 400; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (within retention)", i)
          .isPresent();
    }
  }

  @Test
  public void balRetentionLowerThanBlockRetentionInBalMode() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.BAL,
                Long.MAX_VALUE, // NEVER prune blocks
                256, // prune BALs after 256
                Long.MAX_VALUE,
                0,
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Generate 600 blocks with BAL
    gen.blockSequence(genesisBlock, 600)
        .forEach(
            blk -> {
              final BlockAccessList bal = gen.blockAccessList();
              final BlockchainStorage.Updater updater = blockchainStorage.updater();
              updater.putBlockAccessList(blk.getHash(), bal);
              updater.commit();

              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              // ALL blocks should always exist (never pruned in BAL mode)
              assertThat(blockchain.getBlockHeader(1)).isPresent();
              assertThat(blockchain.getBlockHeader(number)).isPresent();

              if (number > 256) {
                // BAL should be pruned for old blocks
                blockchain
                    .getBlockHeader(number - 256)
                    .ifPresent(
                        oldHeader -> {
                          // Block still exists
                          assertThat(blockchainStorage.getBlockBody(oldHeader.getBlockHash()))
                              .isPresent();
                          assertThat(
                                  blockchainStorage.getTransactionReceipts(
                                      oldHeader.getBlockHash()))
                              .isPresent();

                          // But BAL is pruned
                          assertThat(blockchainStorage.getBlockAccessList(oldHeader.getBlockHash()))
                              .isEmpty();
                        });

                // Recent blocks should have BAL
                blockchain
                    .getBlockHeader(number - 255)
                    .ifPresent(
                        recentHeader -> {
                          assertThat(
                                  blockchainStorage.getBlockAccessList(recentHeader.getBlockHash()))
                              .isPresent();
                        });
              }
            });
  }

  @Test
  public void equalBalAndBlockRetention() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.ALL, 512, 512, 512, 0, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    gen.blockSequence(genesisBlock, 1000)
        .forEach(
            blk -> {
              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              if (number > 512) {
                assertThat(blockchain.getBlockHeader(number - 512)).isEmpty();
                assertThat(blockchain.getBlockHeader(number - 511)).isPresent();
              }
            });
  }

  @Test
  public void noPruningWhenBelowRetentionThreshold() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.ALL, 1000, 1000, 1000, 0, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    gen.blockSequence(genesisBlock, 500)
        .forEach(
            blk -> {
              blockchain.appendBlock(blk, gen.receipts(blk));
              assertThat(blockchain.getBlockHeader(1)).isPresent();
              assertThat(blockchain.getBlockHeader(blk.getHeader().getNumber())).isPresent();
            });
  }

  @Test
  public void balPruningWithDifferentFrequency() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.BAL,
                Long.MAX_VALUE, // chainPruningBlocksRetained - never prune blocks
                256,
                Long.MAX_VALUE,
                100, // prune every 100 blocks
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    List<Block> blocks = gen.blockSequence(genesisBlock, 400);
    for (Block blk : blocks) {
      final BlockAccessList bal = gen.blockAccessList();
      final BlockchainStorage.Updater updater = blockchainStorage.updater();
      updater.putBlockAccessList(blk.getHash(), bal);
      updater.commit();
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 400:
    // - balPruningMark = 400 - 256 = 144
    // - First pruning happened at block 356 (when we had 100 blocks to prune: 0-100)
    // - Second pruning would happen at block 456 (when we have another 100: 101-200)
    // - So at block 400, only blocks 0-100 have been pruned

    // Blocks 0-100: BALs should be pruned (first batch)
    for (int i = 1; i <= 100; i++) {
      final Block block = blocks.get(i - 1);
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should be pruned (first batch at block 356)", i)
          .isEmpty();
    }

    // Blocks 101-400: BALs should still exist (not enough accumulated to trigger next pruning)
    for (int i = 101; i <= 400; i++) {
      final Block block = blocks.get(i - 1);
      assertThat(blockchain.getBlockHeader(i)).as("Block %d should exist", i).isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (next pruning at block 456)", i)
          .isPresent();
    }
  }

  @Test
  public void balPruningWithTwoBatches() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            0,
            ChainDataPruner.Mode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.BAL,
                Long.MAX_VALUE, // never prune blocks
                256, // keep 256 BALs
                Long.MAX_VALUE,
                100, // prune every 100 blocks
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Generate 500 blocks to trigger two pruning batches
    List<Block> blocks = gen.blockSequence(genesisBlock, 500);
    for (Block blk : blocks) {
      final BlockAccessList bal = gen.blockAccessList();
      final BlockchainStorage.Updater updater = blockchainStorage.updater();
      updater.putBlockAccessList(blk.getHash(), bal);
      updater.commit();
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 500:
    // - We want to keep 256 BALs (blocks 245-500)
    // - balPruningMark = 500 - 256 = 244
    //
    // Timeline of pruning:
    // Block 356: First batch prunes blocks 0-100 (100 blocks accumulated)
    //            balPruningMark = 356 - 256 = 100
    //            blocksToBePruned = 100 - 0 = 100
    //
    // Block 456: Second batch prunes blocks 101-200 (100 more blocks accumulated)
    //            balPruningMark = 456 - 256 = 200
    //            blocksToBePruned = 200 - 100 = 100
    //
    // Block 500: Third batch would need block 556 (not reached yet)
    //            balPruningMark = 500 - 256 = 244
    //            blocksToBePruned = 244 - 200 = 44  (< 100)

    // First batch (blocks 1-100): BALs should be pruned
    for (int i = 1; i <= 100; i++) {
      final Block block = blocks.get(i - 1);
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should be pruned (first batch at block 356)", i)
          .isEmpty();
    }

    // Second batch (blocks 101-200): BALs should be pruned
    for (int i = 101; i <= 200; i++) {
      final Block block = blocks.get(i - 1);
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should be pruned (second batch at block 456)", i)
          .isEmpty();
    }

    // Blocks 201-244: Should be pruned eventually but frequency not reached yet
    for (int i = 201; i <= 244; i++) {
      final Block block = blocks.get(i - 1);
      assertThat(blockchain.getBlockHeader(i)).as("Block %d should exist", i).isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (third batch needs block 556)", i)
          .isPresent();
    }

    // Blocks 245-500: Should be kept (within retention window)
    for (int i = 245; i <= 500; i++) {
      final Block block = blocks.get(i - 1);
      assertThat(blockchain.getBlockHeader(i)).as("Block %d should exist", i).isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (within retention of 256 blocks)", i)
          .isPresent();
    }
  }

  @Test
  public void testPreMergePruningAction() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    gen.blockSequence(genesisBlock, 20)
        .forEach((block) -> blockchain.appendBlock(block, gen.receipts(block)));
    final int mergeBlock = 11;
    final int pruningQuantity = 6;
    // ok, chain now has 20 blocks, including the genesis block
    Assertions.assertEquals(20, blockchain.getChainHeadBlockNumber());

    // set up the pruner to prune blocks to 1 to 10 in batches of 6
    ChainDataPruner pruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            mergeBlock,
            ChainDataPruner.Mode.PRE_MERGE_PRUNING,
            new ChainPrunerConfiguration(
                ChainPrunerConfiguration.ChainPruningMode.ALL, 0, 0, 0, 0, pruningQuantity),
            new BlockingExecutor());

    BlockAddedEvent blockAddedEvent = Mockito.mock(BlockAddedEvent.class);
    Mockito.when(blockAddedEvent.isNewCanonicalHead()).thenReturn(true);

    // On the first prune, we're expecting blocks 1 to 6 to be removed, the full pruning batch size
    pruner.onBlockAdded(blockAddedEvent);

    checkBlocks(blockchain, 1, pruningQuantity, Optional::isEmpty);
    checkBlocks(
        blockchain, pruningQuantity + 1, blockchain.getChainHeadBlockNumber(), Optional::isPresent);

    // On the second prune, we're expecting blocks 7 to 10 to be removed, limited by the merge block
    // supplied to the pruner
    pruner.onBlockAdded(blockAddedEvent);

    checkBlocks(blockchain, 1, mergeBlock - 1, Optional::isEmpty);
    checkBlocks(blockchain, mergeBlock, blockchain.getChainHeadBlockNumber(), Optional::isPresent);
  }

  private void checkBlocks(
      final Blockchain blockchain,
      final long start,
      final long end,
      final Predicate<Optional<Block>> test) {
    for (long prunedBlockNumber = start; prunedBlockNumber <= end; prunedBlockNumber++) {
      blockchain
          .getBlockHeader(prunedBlockNumber)
          .ifPresentOrElse(
              (blockHeader) ->
                  Assertions.assertTrue(
                      test.test(blockchain.getBlockByHash(blockHeader.getBlockHash()))),
              () -> Assertions.fail("Failed to find header expected to exist"));
    }
  }

  protected static class BlockingExecutor extends AbstractExecutorService {
    @Override
    public void shutdown() {}

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return true;
    }

    @Override
    public boolean isTerminated() {
      return true;
    }

    @Override
    public boolean awaitTermination(final long timeout, final @NotNull TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(final @NotNull Runnable command) {
      command.run();
    }
  }
}

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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL, 512, 512, 512, 0, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Configure generator to create blocks with BAL automatically
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    // Generate & Import 1000 blocks with BAL
    gen.blockSequenceWithAccessList(genesisBlock, 1000)
        .forEach(
            blockWithBal -> {
              final Block blk = blockWithBal.getBlock();

              // Store the generated BAL in blockchain storage
              blockWithBal
                  .getBlockAccessList()
                  .ifPresent(
                      bal -> {
                        assertThat(blk.getHeader().getBalHash()).isPresent();

                        final BlockchainStorage.Updater updater = blockchainStorage.updater();
                        updater.putBlockAccessList(blk.getHash(), bal);
                        updater.commit();
                      });

              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              // Verify balHash is set in the header
              assertThat(blk.getHeader().getBalHash()).isPresent();

              // Genesis block (block 0) is always kept
              assertThat(blockchain.getBlockHeader(0)).isPresent();

              if (number <= 512) {
                // No pruning has occurred yet
                assertThat(blockchain.getBlockHeader(1)).isPresent();
              } else {
                // Prune block number - 512 only
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL, 512, 512, 512, 0, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    List<BlockDataGenerator.BlockWithAccessList> canonicalChain =
        gen.blockSequenceWithAccessList(genesisBlock, 1000);
    List<BlockDataGenerator.BlockWithAccessList> forkChain =
        gen.blockSequenceWithAccessList(genesisBlock, 16);

    // Store fork blocks with their BAL
    for (BlockDataGenerator.BlockWithAccessList blockWithBal : forkChain) {
      final Block blk = blockWithBal.getBlock();
      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });
      blockchain.storeBlock(blk, gen.receipts(blk));
    }

    // Import first 512 blocks of canonical chain
    for (int i = 0; i < 512; i++) {
      BlockDataGenerator.BlockWithAccessList blockWithBal = canonicalChain.get(i);
      final Block blk = blockWithBal.getBlock();
      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // No pruning has occurred yet
    assertThat(blockchain.getBlockHeader(0)).isPresent();
    assertThat(blockchain.getBlockHeader(canonicalChain.get(0).getBlock().getHash())).isPresent();
    assertThat(blockchain.getBlockHeader(forkChain.get(0).getBlock().getHash())).isPresent();

    // Continue importing canonical chain from block 512 to 527
    for (int i = 512; i < 527; i++) {
      BlockDataGenerator.BlockWithAccessList blockWithBal = canonicalChain.get(i);
      final Block blk = blockWithBal.getBlock();
      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });
      blockchain.appendBlock(blk, gen.receipts(blk));

      // Genesis is always kept
      assertThat(blockchain.getBlockHeader(0)).isPresent();

      if (i > 512) {
        // Prune block on canonical chain and fork for i - 512 only
        assertThat(blockchain.getBlockHeader(canonicalChain.get(i - 512).getBlock().getHash()))
            .isEmpty();
        assertThat(blockchain.getBlockHeader(forkChain.get(i - 512).getBlock().getHash()))
            .isEmpty();
      }

      assertThat(blockchain.getBlockHeader(canonicalChain.get(i - 511).getBlock().getHash()))
          .isPresent();
      assertThat(blockchain.getBlockHeader(forkChain.get(i - 511).getBlock().getHash()))
          .isPresent();
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
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

    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    gen.blockSequenceWithAccessList(genesisBlock, 1000)
        .forEach(
            blockWithBal -> {
              final Block blk = blockWithBal.getBlock();

              // Store the generated BAL
              blockWithBal
                  .getBlockAccessList()
                  .ifPresent(
                      bal -> {
                        assertThat(blk.getHeader().getBalHash()).isPresent();

                        final BlockchainStorage.Updater updater = blockchainStorage.updater();
                        updater.putBlockAccessList(blk.getHash(), bal);
                        updater.commit();
                      });

              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              // Genesis is always kept
              assertThat(blockchain.getBlockHeader(0)).isPresent();
              // Chain data should ALWAYS be present in BAL mode
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
                assertThat(blockchain.getBlockHeader(number - 512)).isPresent();
                blockchain
                    .getBlockHeader(number - 512)
                    .ifPresent(
                        oldHeader -> {
                          // Block data still exists
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
                assertThat(blockchain.getBlockHeader(number - 511)).isPresent();
                blockchain
                    .getBlockHeader(number - 511)
                    .ifPresent(
                        recentHeader -> {
                          // Recent blocks should have BAL
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL, 256, 256, 256, 100, 0),
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
    // - Genesis (block 0) is ALWAYS kept, pruning starts from block 1
    //
    // Timeline:
    // Block 0 (genesis): ALWAYS kept
    // Block 1-256: No pruning (retention = 256)
    // Block 257-356: < 100 accumulated
    // Block 357: First batch prunes blocks 1-101
    // Block 400: blockPruningMark = 144
    //            blocksToBePruned = 144 - 101 = 43 < 100
    //            Only blocks 1-101 are pruned, blocks 102-144 wait for next batch

    // Genesis (block 0) is ALWAYS kept
    assertThat(blockchain.getBlockHeader(0)).as("Genesis block should always be kept").isPresent();

    // Blocks 1-101: Should be pruned (first batch at block 357)
    for (int i = 1; i <= 101; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should be pruned (first batch at block 357)", i)
          .isEmpty();
    }

    // Blocks 102-144: Should NOT be pruned yet (waiting for second batch at block 457)
    for (int i = 102; i <= 144; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (second batch needs block 457)", i)
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
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

    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    // Generate 600 blocks with BAL
    gen.blockSequenceWithAccessList(genesisBlock, 600)
        .forEach(
            blockWithBal -> {
              final Block blk = blockWithBal.getBlock();

              // Store the generated BAL
              blockWithBal
                  .getBlockAccessList()
                  .ifPresent(
                      bal -> {
                        assertThat(blk.getHeader().getBalHash()).isPresent();

                        final BlockchainStorage.Updater updater = blockchainStorage.updater();
                        updater.putBlockAccessList(blk.getHash(), bal);
                        updater.commit();
                      });

              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              // Verify balHash is set
              assertThat(blk.getHeader().getBalHash()).isPresent();

              // Genesis (block 0) is ALWAYS kept
              assertThat(blockchain.getBlockHeader(0))
                  .as("Genesis block should always be kept")
                  .isPresent();
              // ALL blocks should always exist (never pruned in BAL mode)
              assertThat(blockchain.getBlockHeader(1)).isPresent();
              assertThat(blockchain.getBlockHeader(number)).isPresent();

              if (number > 256) {
                // BAL should be pruned for old blocks
                assertThat(blockchain.getBlockHeader(number - 256)).isPresent();
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
                assertThat(blockchain.getBlockHeader(number - 255)).isPresent();
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL, 512, 512, 512, 0, 0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);
    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    gen.blockSequence(genesisBlock, 1000)
        .forEach(
            blk -> {
              blockchain.appendBlock(blk, gen.receipts(blk));
              long number = blk.getHeader().getNumber();

              // Genesis (block 0) is ALWAYS kept
              assertThat(blockchain.getBlockHeader(0))
                  .as("Genesis block should always be kept")
                  .isPresent();
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL, 1000, 1000, 1000, 0, 0),
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
                Long.MAX_VALUE,
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

    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    // Add BAL for genesis block
    final BlockAccessList genesisBal = gen.blockAccessList();
    final BlockchainStorage.Updater genesisUpdater = blockchainStorage.updater();
    genesisUpdater.putBlockAccessList(genesisBlock.getHash(), genesisBal);
    genesisUpdater.commit();

    List<BlockDataGenerator.BlockWithAccessList> blocks =
        gen.blockSequenceWithAccessList(genesisBlock, 400);
    for (BlockDataGenerator.BlockWithAccessList blockWithBal : blocks) {
      final Block blk = blockWithBal.getBlock();

      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                assertThat(blk.getHeader().getBalHash()).isPresent();

                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });

      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 400:
    // - balPruningMark = 400 - 256 = 144
    // - Genesis (block 0) BAL is ALWAYS kept, pruning starts from block 1
    // - storedBalPruningMark starts at 1 (after genesis)
    // - First pruning at block 357: balPruningMark = 101
    //   blocksToBePruned = 101 - 1 = 100
    //   Prunes BALs for blocks 1-101
    // - At block 400: balPruningMark = 144
    //   blocksToBePruned = 144 - 101 = 43 < 100
    //   Only BALs 1-101 are pruned, BALs 102-144 wait for next batch

    // Genesis (block 0): Block and BAL should ALWAYS exist
    assertThat(blockchain.getBlockHeader(0))
        .as("Genesis block should exist (BAL mode never prunes blocks)")
        .isPresent();
    assertThat(blockchainStorage.getBlockAccessList(genesisBlock.getHash()))
        .as("Genesis BAL should always be kept")
        .isPresent();

    // Blocks 1-101: BALs should be pruned (first batch at block 357)
    for (int i = 1; i <= 101; i++) {
      final Block block = blocks.get(i - 1).getBlock();
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should be pruned (first batch at block 357)", i)
          .isEmpty();
    }

    // Blocks 102-400: BALs should still exist (not enough accumulated to trigger next pruning)
    for (int i = 102; i <= 400; i++) {
      final Block block = blocks.get(i - 1).getBlock();
      assertThat(blockchain.getBlockHeader(i)).as("Block %d should exist", i).isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (next pruning at block 457)", i)
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
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

    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    // Add BAL for genesis block
    final BlockAccessList genesisBal = gen.blockAccessList();
    final BlockchainStorage.Updater genesisUpdater = blockchainStorage.updater();
    genesisUpdater.putBlockAccessList(genesisBlock.getHash(), genesisBal);
    genesisUpdater.commit();

    // Generate 500 blocks to trigger two pruning batches
    List<BlockDataGenerator.BlockWithAccessList> blocks =
        gen.blockSequenceWithAccessList(genesisBlock, 500);
    for (BlockDataGenerator.BlockWithAccessList blockWithBal : blocks) {
      final Block blk = blockWithBal.getBlock();

      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                assertThat(blk.getHeader().getBalHash()).isPresent();

                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });

      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 500:
    // - We want to keep 256 BALs (blocks 245-500)
    // - balPruningMark = 500 - 256 = 244
    // - Genesis (block 0) BAL is ALWAYS kept, pruning starts from block 1
    //
    // Timeline of pruning:
    // Block 357: First batch prunes BALs 1-101 (100 BALs accumulated)
    //            balPruningMark = 357 - 256 = 101
    //            blocksToBePruned = 101 - 1 = 100
    //
    // Block 457: Second batch prunes BALs 102-201 (100 more BALs accumulated)
    //            balPruningMark = 457 - 256 = 201
    //            blocksToBePruned = 201 - 101 = 100
    //
    // Block 500: Third batch would need block 557 (not reached yet)
    //            balPruningMark = 500 - 256 = 244
    //            blocksToBePruned = 244 - 201 = 43 < 100

    // Genesis (block 0): Block and BAL should ALWAYS exist
    assertThat(blockchain.getBlockHeader(0))
        .as("Genesis block should exist (BAL mode never prunes blocks)")
        .isPresent();
    assertThat(blockchainStorage.getBlockAccessList(genesisBlock.getHash()))
        .as("Genesis BAL should always be kept")
        .isPresent();

    // First batch (blocks 1-101): BALs should be pruned
    for (int i = 1; i <= 101; i++) {
      final Block block = blocks.get(i - 1).getBlock();
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should be pruned (first batch at block 357)", i)
          .isEmpty();
    }

    // Second batch (blocks 102-201): BALs should be pruned
    for (int i = 102; i <= 201; i++) {
      final Block block = blocks.get(i - 1).getBlock();
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should be pruned (second batch at block 457)", i)
          .isEmpty();
    }

    // Blocks 202-244: Should be pruned eventually but frequency not reached yet
    for (int i = 202; i <= 244; i++) {
      final Block block = blocks.get(i - 1).getBlock();
      assertThat(blockchain.getBlockHeader(i)).as("Block %d should exist", i).isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (third batch needs block 557)", i)
          .isPresent();
    }

    // Blocks 245-500: Should be kept (within retention window)
    for (int i = 245; i <= 500; i++) {
      final Block block = blocks.get(i - 1).getBlock();
      assertThat(blockchain.getBlockHeader(i)).as("Block %d should exist", i).isPresent();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (within retention of 256 blocks)", i)
          .isPresent();
    }
  }

  @Test
  public void forkBlocksRemovedInBalOnlyMode() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final InMemoryKeyValueStorage storage = new InMemoryKeyValueStorage();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            storage,
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final InMemoryKeyValueStorage prunerKvStorage = new InMemoryKeyValueStorage();
    final ChainDataPrunerStorage prunerStorage = new ChainDataPrunerStorage(prunerKvStorage);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            prunerStorage,
            0,
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
                Long.MAX_VALUE, // never prune blocks
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

    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    // Create canonical chain and fork
    List<BlockDataGenerator.BlockWithAccessList> canonicalChain =
        gen.blockSequenceWithAccessList(genesisBlock, 300);
    List<BlockDataGenerator.BlockWithAccessList> forkChain =
        gen.blockSequenceWithAccessList(genesisBlock, 16);

    // Store fork blocks
    for (BlockDataGenerator.BlockWithAccessList blockWithBal : forkChain) {
      final Block blk = blockWithBal.getBlock();
      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });
      blockchain.storeBlock(blk, gen.receipts(blk));
    }

    // Import canonical chain
    for (BlockDataGenerator.BlockWithAccessList blockWithBal : canonicalChain) {
      final Block blk = blockWithBal.getBlock();
      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 300, balPruningMark = 300 - 256 = 44
    // Both canonical and fork blocks should have:
    // - Blocks still present (BAL mode never prunes blocks)
    // - BALs pruned for blocks <= 44
    // - Fork blocks metadata removed for blocks <= 44

    // Verify fork blocks 1-16 (all should be pruned since 16 < 44)
    for (int i = 1; i <= 16; i++) {
      final Block forkBlock = forkChain.get(i - 1).getBlock();
      final Block canonicalBlock = canonicalChain.get(i - 1).getBlock();

      // Blocks should still exist (BAL mode doesn't prune blocks)
      assertThat(blockchain.getBlockByHash(forkBlock.getHash()))
          .as("Fork block %d should still exist in BAL mode", i)
          .isPresent();
      assertThat(blockchain.getBlockByHash(canonicalBlock.getHash()))
          .as("Canonical block %d should still exist", i)
          .isPresent();

      // BALs should be pruned (since i <= 44)
      assertThat(blockchainStorage.getBlockAccessList(forkBlock.getHash()))
          .as("Fork block %d BAL should be pruned", i)
          .isEmpty();
      assertThat(blockchainStorage.getBlockAccessList(canonicalBlock.getHash()))
          .as("Canonical block %d BAL should be pruned", i)
          .isEmpty();

      // Fork blocks metadata should be removed
      assertThat(prunerStorage.getForkBlocks(i))
          .as("Fork blocks metadata for block %d should be removed in BAL mode", i)
          .isEmpty();
    }

    // Verify blocks 45-256 still have BALs and fork blocks metadata
    for (int i = 45; i <= 256; i++) {
      final Block canonicalBlock = canonicalChain.get(i - 1).getBlock();
      assertThat(blockchainStorage.getBlockAccessList(canonicalBlock.getHash()))
          .as("Canonical block %d BAL should exist (within retention)", i)
          .isPresent();
      assertThat(prunerStorage.getForkBlocks(i))
          .as("Fork blocks metadata for block %d should be present in BAL mode", i)
          .isNotEmpty();
    }
  }

  @Test
  public void forkBlocksRemovedOnlyWithChainDataInAllMode() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final InMemoryKeyValueStorage storage = new InMemoryKeyValueStorage();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            storage,
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);
    final InMemoryKeyValueStorage prunerKvStorage = new InMemoryKeyValueStorage();
    final ChainDataPrunerStorage prunerStorage = new ChainDataPrunerStorage(prunerKvStorage);
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            prunerStorage,
            0,
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL,
                400, // prune blocks after 400
                200, // prune BALs after 200
                200,
                0,
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Configure generator with BAL generation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());

    // Create canonical chain and fork
    List<BlockDataGenerator.BlockWithAccessList> canonicalChain =
        gen.blockSequenceWithAccessList(genesisBlock, 500);
    List<BlockDataGenerator.BlockWithAccessList> forkChain =
        gen.blockSequenceWithAccessList(genesisBlock, 150);

    // Store fork blocks
    for (BlockDataGenerator.BlockWithAccessList blockWithBal : forkChain) {
      final Block blk = blockWithBal.getBlock();
      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });
      blockchain.storeBlock(blk, gen.receipts(blk));
    }

    // Import canonical chain
    for (BlockDataGenerator.BlockWithAccessList blockWithBal : canonicalChain) {
      final Block blk = blockWithBal.getBlock();
      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 500:
    // blockPruningMark = 500 - 400 = 100
    // balPruningMark = 500 - 200 = 300

    // Verify blocks 1-100: Everything pruned including fork blocks
    for (int i = 1; i <= 100; i++) {
      final Block canonicalBlock = canonicalChain.get(i - 1).getBlock();

      // Chain data should be pruned
      assertThat(blockchain.getBlockByHash(canonicalBlock.getHash()))
          .as("Canonical block %d should be pruned", i)
          .isEmpty();

      // Fork blocks metadata should be removed (blocks are pruned)
      assertThat(prunerStorage.getForkBlocks(i))
          .as("Fork blocks metadata for block %d should be removed (blocks pruned)", i)
          .isEmpty();
    }

    // Verify blocks 101-150: Blocks exist, BALs pruned, but fork blocks metadata still present
    for (int i = 101; i <= 150; i++) {
      final Block forkBlock = forkChain.get(i - 1).getBlock();
      final Block canonicalBlock = canonicalChain.get(i - 1).getBlock();

      // Blocks should still exist (101-150 < blockPruningMark + retention)
      assertThat(blockchain.getBlockByHash(canonicalBlock.getHash()))
          .as("Canonical block %d should exist (within block retention)", i)
          .isPresent();
      assertThat(blockchain.getBlockByHash(forkBlock.getHash()))
          .as("Fork block %d should exist (within block retention)", i)
          .isPresent();

      // BALs should be pruned (101-150 <= balPruningMark = 300)
      assertThat(blockchainStorage.getBlockAccessList(canonicalBlock.getHash()))
          .as("Canonical block %d BAL should be pruned", i)
          .isEmpty();
      assertThat(blockchainStorage.getBlockAccessList(forkBlock.getHash()))
          .as("Fork block %d BAL should be pruned", i)
          .isEmpty();

      // Fork blocks metadata should STILL exist (blocks not pruned yet)
      assertThat(prunerStorage.getForkBlocks(i))
          .as("Fork blocks metadata for block %d should exist (blocks not pruned yet)", i)
          .isNotEmpty();
    }

    // Verify blocks 301-500: Everything should exist
    for (int i = 301; i <= 500; i++) {
      final Block canonicalBlock = canonicalChain.get(i - 1).getBlock();
      assertThat(blockchain.getBlockByHash(canonicalBlock.getHash()))
          .as("Canonical block %d should exist", i)
          .isPresent();
      assertThat(blockchainStorage.getBlockAccessList(canonicalBlock.getHash()))
          .as("Canonical block %d BAL should exist", i)
          .isPresent();
    }
  }

  @Test
  public void pruningBeforeBalActivation() {
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
                Long.MAX_VALUE,
                256,
                Long.MAX_VALUE,
                0,
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Disable BAL generation for pre-activation
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withoutGeneratedBlockAccessList());

    // Generate 300 blocks WITHOUT BAL (pre-activation)
    List<Block> blocks = gen.blockSequence(genesisBlock, 300);
    for (Block blk : blocks) {
      // No BAL added - simulating pre-BAL activation
      blockchain.appendBlock(blk, gen.receipts(blk));

      // Verify no balHash is set
      assertThat(blk.getHeader().getBalHash()).isEmpty();
    }

    // All blocks should exist (BAL mode never prunes blocks)
    for (int i = 0; i <= 300; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
    }
  }

  @Test
  public void balPruningTransitionFromNoBalToBalActivated() {
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
                Long.MAX_VALUE,
                100, // Keep 100 BALs
                Long.MAX_VALUE,
                0,
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Phase 1: Generate 200 blocks WITHOUT BAL (simulating pre-activation)
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withoutGeneratedBlockAccessList());
    List<Block> preBalBlocks = gen.blockSequence(genesisBlock, 200);
    for (Block blk : preBalBlocks) {
      blockchain.appendBlock(blk, gen.receipts(blk));
      assertThat(blk.getHeader().getBalHash()).isEmpty();
    }

    // Phase 2: BAL activation at block 201 - generate 200 more blocks WITH BAL
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());
    List<BlockDataGenerator.BlockWithAccessList> postBalBlocks =
        gen.blockSequenceWithAccessList(preBalBlocks.get(199), 200);

    for (BlockDataGenerator.BlockWithAccessList blockWithBal : postBalBlocks) {
      final Block blk = blockWithBal.getBlock();

      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                assertThat(blk.getHeader().getBalHash()).isPresent();

                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });

      blockchain.appendBlock(blk, gen.receipts(blk));

      // Verify balHash is set for post-activation blocks
      assertThat(blk.getHeader().getBalHash()).isPresent();
    }

    // At block 400:
    // - We want to keep 100 BALs (blocks 301-400)
    // - Blocks 1-200 never had BAL (should be skipped)
    // - Blocks 201-300 had BAL but should be pruned

    // All blocks should still exist (BAL mode never prunes blocks)
    for (int i = 0; i <= 400; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (BAL mode never prunes blocks)", i)
          .isPresent();
    }

    // Blocks 1-200: No BAL to prune (never existed)
    for (int i = 1; i <= 200; i++) {
      final Block block = preBalBlocks.get(i - 1);
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("Block %d never had BAL", i)
          .isEmpty();
    }

    // Blocks 201-300: BAL should be pruned (outside retention window)
    for (int i = 201; i <= 300; i++) {
      final Block block = postBalBlocks.get(i - 201).getBlock();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should be pruned (outside retention)", i)
          .isEmpty();
    }

    // Blocks 301-400: BAL should exist (within retention window)
    for (int i = 301; i <= 400; i++) {
      final Block block = postBalBlocks.get(i - 201).getBlock();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (within retention)", i)
          .isPresent();
    }
  }

  @Test
  public void allModePruningBeforeBalActivation() {
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL,
                100, // Keep 100 blocks
                100, // Keep 100 BALs
                100,
                0,
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Generate 200 blocks WITHOUT BAL (pre-activation)
    List<Block> blocks = gen.blockSequence(genesisBlock, 200);
    for (Block blk : blocks) {
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // Genesis should exist
    assertThat(blockchain.getBlockHeader(0)).isPresent();

    // Blocks 1-100: Should be pruned (outside retention)
    for (int i = 1; i <= 100; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should be pruned (outside retention)", i)
          .isEmpty();
    }

    // Blocks 101-200: Should exist (within retention)
    for (int i = 101; i <= 200; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (within retention)", i)
          .isPresent();
    }
  }

  @Test
  public void allModePruningTransitionFromNoBalToBalActivated() {
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
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL,
                50, // Keep 50 blocks
                100, // Keep 100 BALs (more than blocks)
                50,
                0,
                0),
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Phase 1: Generate 100 blocks WITHOUT BAL
    List<Block> preBalBlocks = gen.blockSequence(genesisBlock, 100);
    for (Block blk : preBalBlocks) {
      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // Phase 2: BAL activation - generate 100 more blocks WITH BAL
    gen.setBlockOptionsSupplier(
        () -> BlockDataGenerator.BlockOptions.create().withGeneratedBlockAccessList());
    List<BlockDataGenerator.BlockWithAccessList> postBalBlocks =
        gen.blockSequenceWithAccessList(preBalBlocks.get(99), 100);

    for (BlockDataGenerator.BlockWithAccessList blockWithBal : postBalBlocks) {
      final Block blk = blockWithBal.getBlock();

      blockWithBal
          .getBlockAccessList()
          .ifPresent(
              bal -> {
                assertThat(blk.getHeader().getBalHash()).isPresent();

                final BlockchainStorage.Updater updater = blockchainStorage.updater();
                updater.putBlockAccessList(blk.getHash(), bal);
                updater.commit();
              });

      blockchain.appendBlock(blk, gen.receipts(blk));
    }

    // At block 200:
    // - Keep blocks 151-200 (50 blocks retained)
    // - Keep BALs from blocks 101-200 (100 BALs retained)
    // - Blocks 1-150 should be pruned
    // - Blocks 101-150 pruned as blocks but BAL never existed

    // Genesis always kept
    assertThat(blockchain.getBlockHeader(0)).isPresent();

    // Blocks 1-150: Should be pruned
    for (int i = 1; i <= 150; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should be pruned (outside retention)", i)
          .isEmpty();
    }

    // Blocks 151-200: Should exist
    for (int i = 151; i <= 200; i++) {
      assertThat(blockchain.getBlockHeader(i))
          .as("Block %d should exist (within retention)", i)
          .isPresent();
    }

    // Blocks 101-150: Were pruned as blocks, so no BAL check possible

    // Blocks 151-200: BAL should exist (within retention)
    for (int i = 151; i <= 200; i++) {
      final Block block = postBalBlocks.get(i - 101).getBlock();
      assertThat(blockchainStorage.getBlockAccessList(block.getHash()))
          .as("BAL for block %d should exist (within retention)", i)
          .isPresent();
    }
  }

  @Test
  public void balPruningMarkAdvancesAutomaticallyWhenNoBalPresent() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(),
            new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
            new MainnetBlockHeaderFunctions(),
            false);

    final ChainDataPrunerStorage prunerStorage =
        new ChainDataPrunerStorage(new InMemoryKeyValueStorage());

    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            prunerStorage,
            0,
            ChainDataPruner.PruningMode.CHAIN_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.BAL,
                Long.MAX_VALUE,
                256,
                Long.MAX_VALUE,
                100,
                0),
            new BlockingExecutor());

    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Add blocks without BAL - marker should advance automatically
    List<Block> blocks = gen.blockSequence(genesisBlock, 500);
    for (int i = 0; i < 500; i++) {
      Block blk = blocks.get(i);
      blockchain.appendBlock(blk, gen.receipts(blk));

      // Verify BAL pruning mark advances with each block added
      assertThat(prunerStorage.getBalPruningMark())
          .as("BAL pruning mark should advance to block %d", i + 1)
          .isPresent()
          .hasValue((long) i + 1);
    }

    // All blocks should still exist
    for (int i = 0; i <= 500; i++) {
      assertThat(blockchain.getBlockHeader(i)).isPresent();
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
    // Chain now has 20 blocks, including the genesis block
    Assertions.assertEquals(20, blockchain.getChainHeadBlockNumber());

    // Set up the pruner to prune blocks 1 to 10 in batches of 6
    ChainDataPruner pruner =
        new ChainDataPruner(
            blockchainStorage,
            () -> {},
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            mergeBlock,
            ChainDataPruner.PruningMode.PRE_MERGE_PRUNING,
            new ChainPrunerConfiguration(
                ChainDataPruner.ChainPruningStrategy.ALL, 0, 0, 0, 0, pruningQuantity),
            new BlockingExecutor());

    BlockAddedEvent blockAddedEvent = Mockito.mock(BlockAddedEvent.class);
    Mockito.when(blockAddedEvent.isNewCanonicalHead()).thenReturn(true);

    // On the first prune, we're expecting blocks 1 to 6 to be removed (full pruning batch size)
    pruner.onBlockAdded(blockAddedEvent);

    checkBlocks(blockchain, 1, pruningQuantity, Optional::isEmpty);
    checkBlocks(
        blockchain, pruningQuantity + 1, blockchain.getChainHeadBlockNumber(), Optional::isPresent);

    // On the second prune, we're expecting blocks 7 to 10 to be removed (limited by merge block)
    pruner.onBlockAdded(blockAddedEvent);

    checkBlocks(blockchain, 1, mergeBlock - 1, Optional::isEmpty);
    checkBlocks(blockchain, mergeBlock, blockchain.getChainHeadBlockNumber(), Optional::isPresent);
  }

  /**
   * Helper method to check if blocks in a range satisfy a given predicate.
   *
   * @param blockchain the blockchain to query
   * @param start the starting block number (inclusive)
   * @param end the ending block number (inclusive)
   * @param test the predicate to test each block against
   */
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

  /** Blocking executor for testing purposes. Executes tasks synchronously in the calling thread. */
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

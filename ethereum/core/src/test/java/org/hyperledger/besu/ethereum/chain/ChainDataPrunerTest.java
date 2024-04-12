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
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

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
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            512,
            0,
            // completed
            new BlockingExecutor());
    Block genesisBlock = gen.genesisBlock();
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock, blockchainStorage, new NoOpMetricsSystem(), 0);
    blockchain.observeBlockAdded(chainDataPruner);

    // Generate & Import 1000 blocks
    gen.blockSequence(genesisBlock, 1000)
        .forEach(
            blk -> {
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
            new ChainDataPrunerStorage(new InMemoryKeyValueStorage()),
            512,
            0,
            // completed
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
      assertThat(blockchain.getBlockByHash(canonicalChain.get(index - 512).getHash())).isEmpty();
      assertThat(blockchain.getBlockByHash(forkChain.get(i - 511).getHash())).isPresent();
    }
  }

  protected static class BlockingExecutor extends AbstractExecutorService {
    @Override
    public void shutdown() {}

    @Nonnull
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
    public boolean awaitTermination(final long timeout, final @Nonnull TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(final @Nonnull Runnable command) {
      command.run();
    }
  }
}

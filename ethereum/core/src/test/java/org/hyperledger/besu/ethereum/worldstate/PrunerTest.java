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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.testutil.MockExecutorService;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrunerTest {

  private final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();

  private final BlockDataGenerator gen = new BlockDataGenerator();

  @Mock private MarkSweepPruner markSweepPruner;
  private final ExecutorService mockExecutorService = new MockExecutorService();

  private final Block genesisBlock = gen.genesisBlock();

  @Test
  public void shouldMarkCorrectBlockAndSweep() throws ExecutionException, InterruptedException {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem, 0);

    final Pruner pruner =
        new Pruner(markSweepPruner, blockchain, new PrunerConfiguration(0, 1), mockExecutorService);
    pruner.start();

    final Block block1 = appendBlockWithParent(blockchain, genesisBlock);
    appendBlockWithParent(blockchain, block1);
    appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());

    verify(markSweepPruner).mark(block1.getHeader().getStateRoot());
    verify(markSweepPruner).sweepBefore(1);
    pruner.stop();
  }

  @Test
  public void shouldOnlySweepAfterBlockConfirmationPeriodAndRetentionPeriodEnds() {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem, 0);

    final Pruner pruner =
        new Pruner(markSweepPruner, blockchain, new PrunerConfiguration(1, 2), mockExecutorService);
    pruner.start();

    final Hash markBlockStateRootHash =
        appendBlockWithParent(blockchain, genesisBlock).getHeader().getStateRoot();
    verify(markSweepPruner, never()).mark(markBlockStateRootHash);
    verify(markSweepPruner, never()).sweepBefore(anyLong());

    appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());
    verify(markSweepPruner).mark(markBlockStateRootHash);
    verify(markSweepPruner, never()).sweepBefore(anyLong());

    appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());
    verify(markSweepPruner).sweepBefore(1);
    pruner.stop();
  }

  @Test
  public void abortsPruningWhenFullyMarkedBlockNoLongerOnCanonicalChain() {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem, 0);

    // start pruner so it can start handling block added events
    final Pruner pruner =
        new Pruner(markSweepPruner, blockchain, new PrunerConfiguration(0, 1), mockExecutorService);
    pruner.start();

    /*
     Set up pre-marking state:
      O <---- marking of this block's parent will begin when this block is added
      |
      |  O <- this is a fork as of now (non-canonical)
      O  | <- this is the initially canonical block that will be marked
       \/
       O <--- the common ancestor when the reorg happens
    */
    final Block initiallyCanonicalBlock = appendBlockWithParent(blockchain, genesisBlock);
    appendBlockWithParent(blockchain, initiallyCanonicalBlock);
    final Block forkBlock = appendBlockWithParent(blockchain, genesisBlock);
    /*
      Cause reorg:
     Set up pre-marking state:
      O
      |  O <---- this block causes a reorg; this branch becomes canonical
      |  O <---- which means that state here is referring to nodes from the common ancestor block,
      O  | <- because this was block at which marking began
       \/
       O
    */
    appendBlockWithParent(blockchain, forkBlock);
    verify(markSweepPruner).mark(initiallyCanonicalBlock.getHeader().getStateRoot());
    verify(markSweepPruner, never()).sweepBefore(anyLong());
    pruner.stop();
  }

  @Test
  public void shouldRejectInvalidArguments() {
    final Blockchain mockchain = mock(Blockchain.class);
    assertThatThrownBy(
            () ->
                new Pruner(
                    markSweepPruner,
                    mockchain,
                    new PrunerConfiguration(-1, -2),
                    mockExecutorService))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Pruner(
                    markSweepPruner,
                    mockchain,
                    new PrunerConfiguration(10, 8),
                    mockExecutorService))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Pruner(
                    markSweepPruner,
                    mockchain,
                    new PrunerConfiguration(10, 10),
                    mockExecutorService))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCleanUpPruningStrategyOnShutdown() throws InterruptedException {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem, 0);

    final Pruner pruner =
        new Pruner(markSweepPruner, blockchain, new PrunerConfiguration(0, 1), mockExecutorService);
    pruner.start();
    pruner.stop();
    verify(markSweepPruner).cleanup();
  }

  private Block appendBlockWithParent(final MutableBlockchain blockchain, final Block parent) {
    BlockOptions options =
        new BlockOptions()
            .setBlockNumber(parent.getHeader().getNumber() + 1)
            .setParentHash(parent.getHash());
    final Block newBlock = gen.block(options);
    final List<TransactionReceipt> receipts = gen.receipts(newBlock);
    blockchain.appendBlock(newBlock, receipts);
    return newBlock;
  }
}

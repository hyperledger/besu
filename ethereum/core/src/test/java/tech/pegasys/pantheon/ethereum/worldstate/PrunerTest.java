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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.BlockchainStorage;
import tech.pegasys.pantheon.ethereum.chain.DefaultBlockchain;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator.BlockOptions;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.testutil.MockExecutorService;

import java.util.List;
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
  public void shouldMarkCorrectBlockAndSweep() throws InterruptedException {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem);

    final Pruner pruner = new Pruner(markSweepPruner, blockchain, mockExecutorService, 0, 0);
    pruner.start();

    final Block block1 = appendBlockWithParent(blockchain, genesisBlock);
    appendBlockWithParent(blockchain, block1);
    appendBlockWithParent(blockchain, blockchain.getChainHeadBlock());

    verify(markSweepPruner).mark(block1.getHeader().getStateRoot());
    verify(markSweepPruner).sweepBefore(1);
    pruner.stop();
  }

  @Test
  public void shouldOnlySweepAfterTransientForkPeriodAndRetentionPeriodEnds()
      throws InterruptedException {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem);

    final Pruner pruner = new Pruner(markSweepPruner, blockchain, mockExecutorService, 1, 2);
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
  public void abortsPruningWhenFullyMarkedBlockNoLongerOnCanonicalChain()
      throws InterruptedException {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem);

    // start pruner so it can start handling block added events
    final Pruner pruner = new Pruner(markSweepPruner, blockchain, mockExecutorService, 0, 1);
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
    assertThatThrownBy(() -> new Pruner(markSweepPruner, mockchain, mockExecutorService, -1, -2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCleanUpPruningStrategyOnShutdown() throws InterruptedException {
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final MutableBlockchain blockchain =
        DefaultBlockchain.createMutable(genesisBlock, blockchainStorage, metricsSystem);

    final Pruner pruner = new Pruner(markSweepPruner, blockchain, mockExecutorService, 0, 0);
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

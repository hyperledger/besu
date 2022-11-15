package org.hyperledger.besu.ethereum.chain;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ChainDataPrunerTest {

  @Test
  public void singleChainPruning() {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final BlockchainStorage blockchainStorage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(blockchainStorage, new InMemoryKeyValueStorage(), 512);
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
            new InMemoryKeyValueStorage(), new MainnetBlockHeaderFunctions());
    final ChainDataPruner chainDataPruner =
        new ChainDataPruner(blockchainStorage, new InMemoryKeyValueStorage(), 512);
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
      Block blk = canonicalChain.get(i);
      blockchain.appendBlock(blk, gen.receipts(blk));
      // Prune block on canonical chain and fork for i - 512 only
      assertThat(blockchain.getBlockByHash(canonicalChain.get(i - 512).getHash())).isEmpty();
      assertThat(blockchain.getBlockByHash(canonicalChain.get(i - 511).getHash())).isPresent();
      assertThat(blockchain.getBlockByHash(forkChain.get(i - 512).getHash())).isEmpty();
      assertThat(blockchain.getBlockByHash(forkChain.get(i - 511).getHash())).isPresent();
    }
  }
}

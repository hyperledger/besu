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
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.GasLimitCalculator;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryStorageProvider;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.MiningParametersTestBuilder;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.PrecompiledContract;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.testutil.TestClock;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.hyperledger.besu.util.uint.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivacyTest {

  private static final int MAX_OPEN_FILES = 1024;
  private static final long CACHE_CAPACITY = 8388608;
  private static final int MAX_BACKGROUND_COMPACTIONS = 4;
  private static final int BACKGROUND_THREAD_COUNT = 4;

  private static final Integer ADDRESS = 9;
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void reorg() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    final Path dbDir = dataDir.resolve("database");
    final PrivacyParameters privacyParameters =
        new PrivacyParameters.Builder()
            .setPrivacyAddress(ADDRESS)
            .setEnabled(true)
            .setStorageProvider(createKeyValueStorageProvider(dataDir, dbDir))
            .build();

    final BesuController besuController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.development())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .privacyParameters(privacyParameters)
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .targetGasLimit(GasLimitCalculator.DEFAULT)
            .build();

    // Setup an initial blockchain
    final DefaultBlockchain blockchain =
        (DefaultBlockchain) besuController.getProtocolContext().getBlockchain();
    final List<Block> blocksToAdd =
        generateBlocks(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(1)
                .setParentHash(
                    besuController.getProtocolContext().getBlockchain().getGenesisBlock().getHash())
                .addTransaction()
                .addOmmers()
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
                .setGasUsed(0)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0x6553b1838b937f8f883600763505785cc227e9d99fa948f98566f0467f10e1af")));
    for (int i = 0; i < blocksToAdd.size(); i++) {
      if (!besuController
          .getProtocolSchedule()
          .getByBlockNumber(blockchain.getChainHeadBlockNumber())
          .getBlockImporter()
          .importBlock(
              besuController.getProtocolContext(), blocksToAdd.get(i), HeaderValidationMode.NONE)) {
        break;
      }
    }
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);
    // Create parallel fork of length 1
    final int forkBlock = 1;
    final BlockDataGenerator.BlockOptions options =
            new BlockDataGenerator.BlockOptions()
                    .setBlockNumber(forkBlock)
                    .setParentHash(
                            besuController.getProtocolContext().getBlockchain().getGenesisBlock().getHash())
                    .addTransaction()
                    .addOmmers()
                    .setDifficulty(besuController.getProtocolContext().getBlockchain().getChainHeadHeader().getDifficulty().plus(10L))
                    .setReceiptsRoot(
                            Hash.fromHexString(
                                    "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
                    .setGasUsed(0)
                    .setLogsBloom(LogsBloomFilter.empty())
                    .setStateRoot(
                            Hash.fromHexString(
                                    "0x6553b1838b937f8f883600763505785cc227e9d99fa948f98566f0467f10e1af"));
    final Block fork = generateBlocks(options).get(0);

    besuController
            .getProtocolSchedule()
            .getByBlockNumber(blockchain.getChainHeadBlockNumber())
            .getBlockImporter()
            .importBlock(
                    besuController.getProtocolContext(), fork, HeaderValidationMode.NONE);

    // Check chain has reorganized
    for (int i = 0; i < 1; i++) {
      assertBlockDataIsStored(blockchain, besuController.getProtocolContext().getBlockchain().getBlockByNumber(i).get());
    }
  // Check old transactions have been removed
    for (final Transaction tx : blocksToAdd.get(0).getBody().getTransactions()) {
      assertThat(blockchain.getTransactionByHash(tx.getHash())).isNotPresent();
    }

    assertBlockIsHead(blockchain, fork);
    assertTotalDifficultiesAreConsistent(blockchain, fork);
// Old chain head should now be tracked as a fork.
    final Set<Hash> forks = blockchain.getForks();
    assertThat(forks.size()).isEqualTo(1);
    assertThat(forks.stream().anyMatch(f -> f.equals(blocksToAdd.get(0).getHash()))).isTrue();
// Old chain should not be on canonical chain.
    for (int i = 0 + 1; i < 0; i++) {
      assertThat(blockchain.blockIsOnCanonicalChain(besuController.getProtocolContext().getBlockchain().getBlockByNumber(i).get().getHash())).isFalse();
    }

  }

  private List<Block> generateBlocks(final BlockDataGenerator.BlockOptions... blockOptions) {
    final List<Block> seq = new ArrayList<>(blockOptions.length);
    final BlockDataGenerator gen = new BlockDataGenerator();

    for (final BlockDataGenerator.BlockOptions blockOption : blockOptions) {
      final Block next = gen.block(blockOption);
      seq.add(next);
    }

    return seq;
  }

  private void assertBlockDataIsStored(
          final Blockchain blockchain, final Block block) {
    final Hash hash = block.getHash();
    assertThat(blockchain.getBlockHashByNumber(block.getHeader().getNumber()).get())
            .isEqualTo(hash);
    assertThat(blockchain.getBlockHeader(block.getHeader().getNumber()).get())
            .isEqualTo(block.getHeader());
    assertThat(blockchain.getBlockHeader(hash).get()).isEqualTo(block.getHeader());
    assertThat(blockchain.getBlockBody(hash).get()).isEqualTo(block.getBody());
    assertThat(blockchain.blockIsOnCanonicalChain(block.getHash())).isTrue();

    final List<Transaction> txs = block.getBody().getTransactions();
    for (int i = 0; i < txs.size(); i++) {
      final Transaction expected = txs.get(i);
      final Transaction actual = blockchain.getTransactionByHash(expected.getHash()).get();
      assertThat(actual).isEqualTo(expected);
    }
  }

  private void assertBlockIsHead(final Blockchain blockchain, final Block head) {
    assertThat(blockchain.getChainHeadHash()).isEqualTo(head.getHash());
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(head.getHeader().getNumber());
    assertThat(blockchain.getChainHead().getHash()).isEqualTo(head.getHash());
  }

  private void assertTotalDifficultiesAreConsistent(final Blockchain blockchain, final Block head) {
    // Check that total difficulties are summed correctly
    long num = BlockHeader.GENESIS_BLOCK_NUMBER;
    UInt256 td = UInt256.of(0);
    while (num <= head.getHeader().getNumber()) {
      final Hash curHash = blockchain.getBlockHashByNumber(num).get();
      final BlockHeader curHead = blockchain.getBlockHeader(curHash).get();
      td = td.plus(curHead.getDifficulty());
      assertThat(blockchain.getTotalDifficultyByHash(curHash).get()).isEqualTo(td);

      num += 1;
    }

    // Check reported chainhead td
    assertThat(blockchain.getChainHead().getTotalDifficulty()).isEqualTo(td);
  }

  @Test
  public void privacyPrecompiled() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    final Path dbDir = dataDir.resolve("database");
    final PrivacyParameters privacyParameters =
        new PrivacyParameters.Builder()
            .setPrivacyAddress(ADDRESS)
            .setEnabled(true)
            .setStorageProvider(createKeyValueStorageProvider(dataDir, dbDir))
            .build();
    final BesuController<?> besuController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .privacyParameters(privacyParameters)
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .targetGasLimit(GasLimitCalculator.DEFAULT)
            .build();

    final Address privacyContractAddress = Address.privacyPrecompiled(ADDRESS);
    final PrecompiledContract precompiledContract =
        besuController
            .getProtocolSchedule()
            .getByBlockNumber(1)
            .getPrecompileContractRegistry()
            .get(privacyContractAddress, Account.DEFAULT_VERSION);

    assertThat(precompiledContract.getName()).isEqualTo("Privacy");
  }

  private PrivacyStorageProvider createKeyValueStorageProvider(
      final Path dataDir, final Path dbDir) {
    return new PrivacyKeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValuePrivacyStorageFactory(
                new RocksDBKeyValueStorageFactory(
                    () ->
                        new RocksDBFactoryConfiguration(
                            MAX_OPEN_FILES,
                            MAX_BACKGROUND_COMPACTIONS,
                            BACKGROUND_THREAD_COUNT,
                            CACHE_CAPACITY),
                    Arrays.asList(KeyValueSegmentIdentifier.values()),
                    RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS)))
        .withCommonConfiguration(new BesuConfigurationImpl(dataDir, dbDir))
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }
}

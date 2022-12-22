/*
 * Copyright Hyperledger Besu contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;

import org.hyperledger.besu.config.GenesisAllocation;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockCreator;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractIsolationTests {
  protected BonsaiWorldStateArchive archive;
  protected BonsaiWorldStateKeyValueStorage bonsaiWorldStateStorage;
  protected ProtocolContext protocolContext;
  final Function<String, KeyPair> asKeyPair =
      key ->
          SignatureAlgorithmFactory.getInstance()
              .createKeyPair(SECPPrivateKey.create(Bytes32.fromHexString(key), "ECDSA"));
  protected final ProtocolSchedule protocolSchedule =
      MainnetProtocolSchedule.fromConfig(GenesisConfigFile.development().getConfigOptions());
  protected final GenesisState genesisState =
      GenesisState.fromConfig(GenesisConfigFile.development(), protocolSchedule);
  protected final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
  protected final AbstractPendingTransactionsSorter sorter =
      new GasPricePendingTransactionsSorter(
          ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(100).build(),
          Clock.systemUTC(),
          new NoOpMetricsSystem(),
          blockchain::getChainHeadHeader);

  protected final List<GenesisAllocation> accounts =
      GenesisConfigFile.development()
          .streamAllocations()
          .filter(ga -> ga.getPrivateKey().isPresent())
          .collect(Collectors.toList());

  KeyPair sender1 = asKeyPair.apply(accounts.get(0).getPrivateKey().get());

  @Rule public final TemporaryFolder tempData = new TemporaryFolder();

  protected boolean shouldUseSnapshots() {
    // override for layered worldstate
    return true;
  }

  @Before
  public void createStorage() {
    bonsaiWorldStateStorage =
        (BonsaiWorldStateKeyValueStorage)
            createKeyValueStorageProvider().createWorldStateStorage(DataStorageFormat.BONSAI);
    archive =
        new BonsaiWorldStateArchive(
            bonsaiWorldStateStorage,
            blockchain,
            Optional.of(16L),
            shouldUseSnapshots(),
            new CachedMerkleTrieLoader(new NoOpMetricsSystem()));
    var ws = archive.getMutable();
    genesisState.writeStateTo(ws);
    ws.persist(blockchain.getChainHeadHeader());
    protocolContext = new ProtocolContext(blockchain, archive, null);
  }

  // storage provider which uses a temporary directory based rocksdb
  protected StorageProvider createKeyValueStorageProvider() {
    try {
      tempData.create();
      return new KeyValueStorageProviderBuilder()
          .withStorageFactory(
              new RocksDBKeyValueStorageFactory(
                  () ->
                      new RocksDBFactoryConfiguration(
                          1024 /* MAX_OPEN_FILES*/,
                          4 /*MAX_BACKGROUND_COMPACTIONS*/,
                          4 /*BACKGROUND_THREAD_COUNT*/,
                          8388608 /*CACHE_CAPACITY*/,
                          false),
                  Arrays.asList(KeyValueSegmentIdentifier.values()),
                  2,
                  RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS))
          .withCommonConfiguration(
              new BesuConfiguration() {

                @Override
                public Path getStoragePath() {
                  return new File(tempData.getRoot().toString() + File.pathSeparator + "database")
                      .toPath();
                }

                @Override
                public Path getDataPath() {
                  return tempData.getRoot().toPath();
                }

                @Override
                public int getDatabaseVersion() {
                  return 2;
                }
              })
          .withMetricsSystem(new NoOpMetricsSystem())
          .build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static class TestBlockCreator extends AbstractBlockCreator {
    private TestBlockCreator(
        final Address coinbase,
        final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
        final Supplier<Optional<Long>> targetGasLimitSupplier,
        final ExtraDataCalculator extraDataCalculator,
        final AbstractPendingTransactionsSorter pendingTransactions,
        final ProtocolContext protocolContext,
        final ProtocolSchedule protocolSchedule,
        final Wei minTransactionGasPrice,
        final Double minBlockOccupancyRatio,
        final BlockHeader parentHeader) {
      super(
          coinbase,
          miningBeneficiaryCalculator,
          targetGasLimitSupplier,
          extraDataCalculator,
          pendingTransactions,
          protocolContext,
          protocolSchedule,
          minTransactionGasPrice,
          minBlockOccupancyRatio,
          parentHeader);
    }

    static TestBlockCreator forHeader(
        final BlockHeader parentHeader,
        final ProtocolContext protocolContext,
        final ProtocolSchedule protocolSchedule,
        final AbstractPendingTransactionsSorter sorter) {
      return new TestBlockCreator(
          Address.ZERO,
          __ -> Address.ZERO,
          () -> Optional.of(30_000_000L),
          __ -> Bytes.fromHexString("deadbeef"),
          sorter,
          protocolContext,
          protocolSchedule,
          Wei.of(1L),
          0d,
          parentHeader);
    }

    @Override
    protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
      return BlockHeaderBuilder.create()
          .difficulty(Difficulty.ZERO)
          .mixHash(Hash.ZERO)
          .populateFrom(sealableBlockHeader)
          .nonce(0L)
          .blockHeaderFunctions(blockHeaderFunctions)
          .buildBlockHeader();
    }
  }

  protected Transaction burnTransaction(final KeyPair sender, final Long nonce, final Address to) {
    return new TransactionTestFixture()
        .sender(Address.extract(Hash.hash(sender.getPublicKey().getEncodedBytes())))
        .to(Optional.of(to))
        .value(Wei.of(1_000_000_000_000_000_000L))
        .gasLimit(21_000L)
        .nonce(nonce)
        .createTransaction(sender);
  }

  protected Block forTransactions(final List<Transaction> transactions) {
    return forTransactions(transactions, blockchain.getChainHeadHeader());
  }

  protected Block forTransactions(
      final List<Transaction> transactions, final BlockHeader forHeader) {
    return TestBlockCreator.forHeader(forHeader, protocolContext, protocolSchedule, sorter)
        .createBlock(transactions, Collections.emptyList(), System.currentTimeMillis())
        .getBlock();
  }

  protected BlockProcessingResult executeBlock(final MutableWorldState ws, final Block block) {
    var res =
        protocolSchedule
            .getByBlockNumber(0)
            .getBlockProcessor()
            .processBlock(blockchain, ws, block);
    blockchain.appendBlock(block, res.getReceipts());
    return res;
  }
}

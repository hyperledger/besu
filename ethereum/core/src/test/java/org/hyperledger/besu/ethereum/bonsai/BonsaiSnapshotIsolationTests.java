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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createInMemoryBlockchain;

import org.hyperledger.besu.config.GenesisAllocation;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockCreator;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.util.number.Percentage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BonsaiSnapshotIsolationTests {

  private BonsaiWorldStateArchive archive;
  private ProtocolContext protocolContext;
  final Function<String, KeyPair> asKeyPair =
      key ->
          SignatureAlgorithmFactory.getInstance()
              .createKeyPair(SECPPrivateKey.create(Bytes32.fromHexString(key), "ECDSA"));
  final Function<GenesisAllocation, Address> extractAddress =
      ga -> Address.fromHexString(ga.getAddress());

  private final ProtocolSchedule protocolSchedule =
      MainnetProtocolSchedule.fromConfig(GenesisConfigFile.development().getConfigOptions());
  private final GenesisState genesisState =
      GenesisState.fromConfig(GenesisConfigFile.development(), protocolSchedule);
  private final MutableBlockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
  private final AbstractPendingTransactionsSorter sorter =
      new GasPricePendingTransactionsSorter(
          ImmutableTransactionPoolConfiguration.builder()
              .txPoolMaxSize(100)
              .build(),
          Clock.systemUTC(),
          new NoOpMetricsSystem(),
          blockchain::getChainHeadHeader);

  private final List<GenesisAllocation> accounts =
      GenesisConfigFile.development()
          .streamAllocations()
          .filter(ga -> ga.getPrivateKey().isPresent())
          .collect(Collectors.toList());

  KeyPair sender1 = asKeyPair.apply(accounts.get(0).getPrivateKey().get());

  @Rule
  public final TemporaryFolder tempData = new TemporaryFolder();

  @Before
  public void createStorage() {
//    final InMemoryKeyValueStorageProvider provider = new InMemoryKeyValueStorageProvider();
    archive = new BonsaiWorldStateArchive(createKeyValueStorageProvider(), blockchain);
    var ws = archive.getMutable();
    genesisState.writeStateTo(ws);
    ws.persist(blockchain.getChainHeadHeader());
    protocolContext = new ProtocolContext(blockchain, archive, null);
  }

  @Test
  public void testIsolatedFromHead_behindHead() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    // assert we can find the correct path if we are some number of blocks behind head
    var isolated = archive.getMutableSnapshot(genesisState.getBlock().getHash());

    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(archive.getMutable(), firstBlock);

    var isolated2 = archive.getMutableSnapshot(firstBlock.getHash());
    var secondBlock = forTransactions(List.of(burnTransaction(sender1, 1L, testAddress)));
    var res2 = executeBlock(archive.getMutable(), secondBlock);

    assertThat(res.isSuccessful()).isTrue();
    assertThat(res2.isSuccessful()).isTrue();

    assertThat(archive.getMutable().get(testAddress)).isNotNull();
    assertThat(archive.getMutable().get(testAddress).getBalance())
      .isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(isolated.get().get(testAddress)).isNull();
    assertThat(isolated2.get().get(testAddress)).isNotNull();
    assertThat(isolated2.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));

    // persist trielogs
    isolated.get().persist(firstBlock.getHeader());
    isolated2.get().persist(secondBlock.getHeader());

    //todo: check trielog layer for correctness
  }

  @Test
  public void testIsolatedFromHead_pastHead() {
    // assert we can find the correct path if our state is n blocks ahead of head
    Address testAddress = Address.fromHexString("0xdeadbeef");

    var res =
        executeBlock(
            archive.getMutable(),
            forTransactions(List.of(burnTransaction(sender1, 0L, testAddress))));

    var block2 = forTransactions(List.of(burnTransaction(sender1, 1L, testAddress)));
    var res2 = executeBlock(archive.getMutable(), block2);
    var isolated2 = archive.getMutableSnapshot(block2.getHash());

    var block3 = forTransactions(List.of(burnTransaction(sender1, 2L, testAddress)));
    var res3 = executeBlock(archive.getMutable(), block3);
    var isolated3 = archive.getMutableSnapshot(block3.getHash());

    assertThat(res.isSuccessful()).isTrue();
    assertThat(res2.isSuccessful()).isTrue();
    assertThat(res3.isSuccessful()).isTrue();

    blockchain.rewindToBlock(1L);

    // we should be 1 blocks ahead of head
    assertThat(isolated2.get().get(testAddress)).isNotNull();
    assertThat(isolated2.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(2_000_000_000_000_000_000L));

    // we should be 2 blocks ahead of head
    assertThat(isolated3.get().get(testAddress)).isNotNull();
    assertThat(isolated3.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(3_000_000_000_000_000_000L));

    // persist trielog layer:
    isolated2.get().persist(block2.getHeader());
    isolated3.get().persist(block3.getHeader());

    //todo: check trieloglayer for correctness
  }

  /**
   * this is an initial negative test case. we should expect this to fail with a mutable and
   * non-persisting copy of the persisted state.
   */
  @Test
  @Ignore("this is expected to fail without getting an isolated mutable copy")
  public void testCopyNonIsolation() {
    var tx1 = burnTransaction(sender1, 0L, Address.ZERO);
    Block oneTx = forTransactions(List.of(tx1));

    MutableWorldState firstWorldState = archive.getMutable();
    var res = executeBlock(firstWorldState, oneTx);

    assertThat(res.isSuccessful()).isTrue();
    // get a copy of this worldstate after it has persisted, then save the account val
    var isolated = archive.getMutable(oneTx.getHeader().getStateRoot(), oneTx.getHash(), false);
    Address beforeAddress = extractAddress.apply(accounts.get(1));
    var before = isolated.get().get(beforeAddress);

    // build and execute another block
    var tx2 = burnTransaction(sender1, 1L, beforeAddress);

    Block oneMoreTx = forTransactions(List.of(tx2));

    var res2 =
        executeBlock(archive.getMutable(oneTx.getHeader().getNumber(), true).get(), oneMoreTx);
    assertThat(res2.isSuccessful()).isTrue();

    // compare the cached account value to the current account value from the mutable worldstate
    var after = isolated.get().get(beforeAddress);
    assertThat(after.getBalance()).isNotEqualTo(before.getBalance());
  }

  private Transaction burnTransaction(final KeyPair sender, final Long nonce, final Address to) {
    return new TransactionTestFixture()
        .sender(Address.extract(Hash.hash(sender.getPublicKey().getEncodedBytes())))
        .to(Optional.of(to))
        .value(Wei.of(1_000_000_000_000_000_000L))
        .gasLimit(21_000L)
        .nonce(nonce)
        .createTransaction(sender);
  }

  private Block forTransactions(final List<Transaction> transactions) {
    return TestBlockCreator.forHeader(
            blockchain.getChainHeadHeader(), protocolContext, protocolSchedule, sorter)
        .createBlock(transactions, Collections.emptyList(), System.currentTimeMillis());
  }

  private BlockProcessor.Result executeBlock(final MutableWorldState ws, final Block block) {
    var res =
        protocolSchedule
            .getByBlockNumber(0)
            .getBlockProcessor()
            .processBlock(blockchain, ws, block);
    blockchain.appendBlock(block, res.getReceipts());
    return res;
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

  // storage provider which uses a temporary directory based rocksdb
  private StorageProvider createKeyValueStorageProvider() {
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
                          8388608 /*CACHE_CAPACITY*/),
                  Arrays.asList(KeyValueSegmentIdentifier.values()),
                  2,
                  RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS))
          .withCommonConfiguration(
              new BesuConfiguration() {

                @Override
                public Path getStoragePath() {
                  return new File(tempData.getRoot().toString() + File.pathSeparator + "database").toPath();
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
}

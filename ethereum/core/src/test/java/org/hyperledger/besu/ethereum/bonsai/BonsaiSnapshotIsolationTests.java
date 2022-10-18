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
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
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
          ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(100).build(),
          Clock.systemUTC(),
          new NoOpMetricsSystem(),
          blockchain::getChainHeadHeader);

  private final List<GenesisAllocation> accounts =
      GenesisConfigFile.development()
          .streamAllocations()
          .filter(ga -> ga.getPrivateKey().isPresent())
          .collect(Collectors.toList());

  KeyPair sender1 = asKeyPair.apply(accounts.get(0).getPrivateKey().get());

  @Rule public final TemporaryFolder tempData = new TemporaryFolder();

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
    // assert we can mutate head without mutating the isolated snapshot
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
    assertThat(isolated.get().rootHash())
        .isEqualTo(genesisState.getBlock().getHeader().getStateRoot());

    assertThat(isolated2.get().get(testAddress)).isNotNull();
    assertThat(isolated2.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolated2.get().rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());
  }

  @Test
  public void testIsolatedSnapshotMutation() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    // assert we can correctly execute a block on a mutable snapshot without mutating head
    var isolated = archive.getMutableSnapshot(genesisState.getBlock().getHash());

    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(isolated.get(), firstBlock);

    assertThat(res.isSuccessful()).isTrue();
    assertThat(isolated.get().get(testAddress)).isNotNull();
    assertThat(isolated.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolated.get().rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());

    // persist the isolated worldstate as trielog only:
    isolated.get().persist(firstBlock.getHeader());

    // assert we have not modified the head worldstate:
    assertThat(archive.getMutable().get(testAddress)).isNull();

    // roll the persisted world state to the new trie log from the persisted snapshot
    var ws = archive.getMutable(firstBlock.getHeader().getNumber(), true);
    assertThat(ws).isPresent();
    assertThat(ws.get().get(testAddress)).isNotNull();
    assertThat(ws.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(ws.get().rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());
  }

  @Test
  public void testSnapshotCloneIsolation() {
    Address testAddress = Address.fromHexString("0xdeadbeef");
    Address altTestAddress = Address.fromHexString("0xd1ffbeef");

    // create a snapshot worldstate, and then clone it:
    var isolated = archive.getMutableSnapshot(genesisState.getBlock().getHash()).get();
    var isolatedClone = isolated.copy();

    // execute a block with a single transaction on the first snapshot:
    var firstBlock = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(isolated, firstBlock);

    assertThat(res.isSuccessful()).isTrue();
    Runnable checkIsolatedState =
        () -> {
          assertThat(isolated.rootHash()).isEqualTo(firstBlock.getHeader().getStateRoot());
          assertThat(isolated.get(testAddress)).isNotNull();
          assertThat(isolated.get(altTestAddress)).isNull();
          assertThat(isolated.get(testAddress).getBalance())
              .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
        };
    checkIsolatedState.run();

    // assert clone is isolated and unmodified:
    assertThat(isolatedClone.get(testAddress)).isNull();
    assertThat(isolatedClone.rootHash())
        .isEqualTo(genesisState.getBlock().getHeader().getStateRoot());

    // assert clone isolated block execution
    var cloneForkBlock =
        forTransactions(
            List.of(burnTransaction(sender1, 0L, altTestAddress)),
            genesisState.getBlock().getHeader());
    var altRes = executeBlock(isolatedClone, cloneForkBlock);

    assertThat(altRes.isSuccessful()).isTrue();
    assertThat(isolatedClone.rootHash()).isEqualTo(cloneForkBlock.getHeader().getStateRoot());
    assertThat(isolatedClone.get(altTestAddress)).isNotNull();
    assertThat(isolatedClone.get(testAddress)).isNull();
    assertThat(isolatedClone.get(altTestAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolatedClone.rootHash()).isEqualTo(cloneForkBlock.getHeader().getStateRoot());

    // re-check isolated state remains unchanged:
    checkIsolatedState.run();

    // assert that the actual persisted worldstate remains unchanged:
    var persistedWorldState = archive.getMutable();
    assertThat(persistedWorldState.rootHash())
        .isEqualTo(genesisState.getBlock().getHeader().getStateRoot());
    assertThat(persistedWorldState.get(testAddress)).isNull();
    assertThat(persistedWorldState.get(altTestAddress)).isNull();

    // assert that trieloglayers exist for both of the isolated states:
    var firstBlockTrieLog = archive.getTrieLogManager().getTrieLogLayer(firstBlock.getHash());
    assertThat(firstBlockTrieLog).isNotEmpty();
    assertThat(firstBlockTrieLog.get().getAccount(testAddress)).isNotEmpty();
    assertThat(firstBlockTrieLog.get().getAccount(altTestAddress)).isEmpty();
    var cloneForkTrieLog = archive.getTrieLogManager().getTrieLogLayer(cloneForkBlock.getHash());
    assertThat(cloneForkTrieLog.get().getAccount(testAddress)).isEmpty();
    assertThat(cloneForkTrieLog.get().getAccount(altTestAddress)).isNotEmpty();
  }

  @Test
  public void assertSnapshotDoesNotClose() {
    // TODO: add unit test to assert snapshot does not close on clone if parent tx is closed

  }

  @Test
  public void testSnapshotRollToTrieLogBlockHash() {
    // assert we can roll a snapshot to a specific worldstate without mutating head
    Address testAddress = Address.fromHexString("0xdeadbeef");

    var block1 = forTransactions(List.of(burnTransaction(sender1, 0L, testAddress)));
    var res = executeBlock(archive.getMutable(), block1);

    var block2 = forTransactions(List.of(burnTransaction(sender1, 1L, testAddress)));
    var res2 = executeBlock(archive.getMutable(), block2);

    var block3 = forTransactions(List.of(burnTransaction(sender1, 2L, testAddress)));
    var res3 = executeBlock(archive.getMutable(), block3);

    assertThat(res.isSuccessful()).isTrue();
    assertThat(res2.isSuccessful()).isTrue();
    assertThat(res3.isSuccessful()).isTrue();

    // roll chain and worldstate to block 2
    blockchain.rewindToBlock(2L);
    var block1State = archive.getMutable(2L, true);

    // BonsaiPersistedWorldState should be at block 2
    assertThat(block1State.get().get(testAddress)).isNotNull();
    assertThat(block1State.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(2_000_000_000_000_000_000L));
    assertThat(block1State.get().rootHash()).isEqualTo(block2.getHeader().getStateRoot());

    var isolatedRollForward = archive.getMutableSnapshot(block3.getHash());

    // we should be at block 3, one block ahead of BonsaiPersistatedWorldState
    assertThat(isolatedRollForward.get().get(testAddress)).isNotNull();
    assertThat(isolatedRollForward.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(3_000_000_000_000_000_000L));
    assertThat(isolatedRollForward.get().rootHash()).isEqualTo(block3.getHeader().getStateRoot());

    // we should be at block 1, one block behind BonsaiPersistatedWorldState
    var isolatedRollBack = archive.getMutableSnapshot(block1.getHash());
    assertThat(isolatedRollBack.get().get(testAddress)).isNotNull();
    assertThat(isolatedRollBack.get().get(testAddress).getBalance())
        .isEqualTo(Wei.of(1_000_000_000_000_000_000L));
    assertThat(isolatedRollBack.get().rootHash()).isEqualTo(block1.getHeader().getStateRoot());
  }

  @Test
  public void assertCloseDisposesOfStateWithoutCommitting() {
    Address testAddress = Address.fromHexString("0xdeadbeef");

    var head = archive.getMutable();

    try (var shouldCloseSnapshot =
        archive.getMutableSnapshot(genesisState.getBlock().getHash()).get()) {

      var tx1 = burnTransaction(sender1, 0L, testAddress);
      Block oneTx = forTransactions(List.of(tx1));

      var res = executeBlock(shouldCloseSnapshot, oneTx);
      assertThat(res.isSuccessful()).isTrue();
      shouldCloseSnapshot.persist(oneTx.getHeader());

      assertThat(shouldCloseSnapshot.get(testAddress)).isNotNull();
      assertThat(shouldCloseSnapshot.get(testAddress).getBalance())
          .isEqualTo(Wei.of(1_000_000_000_000_000_000L));

    } catch (Exception e) {
      // just a cheap way to close the snapshot worldstate and transactions
    }

    assertThat(head.get(testAddress)).isNull();
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
    return forTransactions(transactions, blockchain.getChainHeadHeader());
  }

  private Block forTransactions(final List<Transaction> transactions, final BlockHeader forHeader) {
    return TestBlockCreator.forHeader(forHeader, protocolContext, protocolSchedule, sorter)
        .createBlock(transactions, Collections.emptyList(), System.currentTimeMillis())
        .getBlock();
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
}

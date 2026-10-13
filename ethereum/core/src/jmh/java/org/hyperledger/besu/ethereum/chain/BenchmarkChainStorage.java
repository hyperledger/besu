/*
 * Copyright contributors to Besu.
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

import static java.util.Collections.emptyList;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogsBloomFilter;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBConfigurationBuilder;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.OptimisticRocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedKeyValueStorageAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** On-disk RocksDB opened the way Besu opens it, plus a chain of mainnet-shaped headers. */
public final class BenchmarkChainStorage {

  /** Where the measured data lives when the benchmark runs. */
  public enum StorageState {
    /** Just written: still in the RocksDB memtable, like the newest blocks on a live node. */
    MEMTABLE,
    /** Database closed and reopened: WAL is flushed to SST files and reads go through them. */
    SST
  }

  private BenchmarkChainStorage() {}

  static OptimisticRocksDBColumnarKeyValueStorage open(final Path dir) {
    return new OptimisticRocksDBColumnarKeyValueStorage(
        new RocksDBConfigurationBuilder().databaseDir(dir).build(),
        List.of(
            KeyValueSegmentIdentifier.DEFAULT,
            KeyValueSegmentIdentifier.BLOCKCHAIN,
            KeyValueSegmentIdentifier.VARIABLES,
            KeyValueSegmentIdentifier.TRIE_LOG_STORAGE),
        emptyList(),
        new NoOpMetricsSystem(),
        RocksDBMetricsFactory.PUBLIC_ROCKS_DB_METRICS);
  }

  static KeyValueStorage segment(
      final OptimisticRocksDBColumnarKeyValueStorage db, final KeyValueSegmentIdentifier id) {
    return new SegmentedKeyValueStorageAdapter(id, db);
  }

  static MutableBlockchain blockchain(
      final OptimisticRocksDBColumnarKeyValueStorage db,
      final Block genesis,
      final int headerCacheSize) {
    final KeyValueStoragePrefixedKeyBlockchainStorage storage =
        new KeyValueStoragePrefixedKeyBlockchainStorage(
            segment(db, KeyValueSegmentIdentifier.BLOCKCHAIN),
            new VariablesKeyValueStorage(segment(db, KeyValueSegmentIdentifier.VARIABLES)),
            new MainnetBlockHeaderFunctions(),
            false);
    // Same factory BesuControllerBuilder uses; headerCacheSize is --cache-last-block-headers.
    return DefaultBlockchain.createMutable(
        genesis, storage, new NoOpMetricsSystem(), 0, null, 0, headerCacheSize);
  }

  static Block genesis() {
    return new Block(header(new Random(0), 0, Hash.ZERO), new BlockBody(emptyList(), emptyList()));
  }

  /** Appends {@code length} blocks through DefaultBlockchain.appendBlock, as block import does. */
  static void populate(
      final OptimisticRocksDBColumnarKeyValueStorage db, final Block genesis, final int length) {
    final MutableBlockchain chain = blockchain(db, genesis, 0);
    final Random random = new Random(42);
    for (long n = 1; n <= length; n++) {
      chain.appendBlock(
          new Block(
              header(random, n, chain.getChainHeadHash()), new BlockBody(emptyList(), emptyList())),
          emptyList());
    }
  }

  /** Every post-Prague header field populated, dense bloom, 32-byte extraData. */
  private static BlockHeader header(final Random random, final long number, final Hash parent) {
    return new BlockHeaderTestFixture()
        .parentHash(parent)
        .number(number)
        .difficulty(Difficulty.ONE)
        .coinbase(Address.wrap(Bytes.random(20, random)))
        .stateRoot(Hash.wrap(Bytes32.random(random)))
        .transactionsRoot(Hash.wrap(Bytes32.random(random)))
        .receiptsRoot(Hash.wrap(Bytes32.random(random)))
        .logsBloom(new LogsBloomFilter(Bytes.random(256, random)))
        .gasLimit(36_000_000)
        .gasUsed(15_000_000 + random.nextInt(10_000_000))
        .timestamp(1_700_000_000L + number * 12)
        .extraData(Bytes.random(32, random))
        .prevRandao(Bytes32.random(random))
        .baseFeePerGas(Wei.of(1_000_000_000L + random.nextInt(1_000_000_000)))
        .withdrawalsRoot(Hash.wrap(Bytes32.random(random)))
        .blobGasUsed(393_216L)
        .excessBlobGas(BlobGas.of(random.nextInt(10_000_000)))
        .parentBeaconBlockRoot(Optional.of(Bytes32.random(random)))
        .requestsHash(Hash.wrap(Bytes32.random(random)))
        .buildHeader();
  }

  /**
   * Prints the SST and blob files on disk so the log shows which storage state each fork measured.
   * BLOCKCHAIN and TRIE_LOG_STORAGE store values of 100+ bytes in blob files, not inline in SSTs.
   */
  static void describe(final Path dir, final StorageState state) throws IOException {
    System.out.printf(
        "[storage=%s] sst: %s, blob: %s%n", state, filesWith(dir, ".sst"), filesWith(dir, ".blob"));
  }

  private static String filesWith(final Path dir, final String suffix) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      final List<Path> matching = files.filter(p -> p.toString().endsWith(suffix)).toList();
      long bytes = 0;
      for (final Path file : matching) {
        bytes += Files.size(file);
      }
      return matching.size() + " files / " + bytes + " bytes";
    }
  }
}

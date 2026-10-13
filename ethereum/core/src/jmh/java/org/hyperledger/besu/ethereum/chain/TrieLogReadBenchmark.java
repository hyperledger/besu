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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.chain.BenchmarkChainStorage.StorageState;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.trielog.BonsaiTrieLogFactory;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogLayer;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.OptimisticRocksDBColumnarKeyValueStorage;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Cost of one TrieLogManager.getTrieLogLayer call (read bytes from TRIE_LOG_STORAGE, then
 * deserialize), the per-block work every walk strategy has to do. Trie-log sizes are synthetic.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class TrieLogReadBenchmark {

  /** Distinct trie logs read round-robin so one cached entry isn't measured over and over. */
  private static final int LAYERS = 64;

  public enum Size {
    SMALL(50, 100, 0),
    MEDIUM(300, 1_000, 1),
    LARGE(1_000, 4_000, 3);

    final int accounts;
    final int storageSlots;
    final int contractDeploys;

    Size(final int accounts, final int storageSlots, final int contractDeploys) {
      this.accounts = accounts;
      this.storageSlots = storageSlots;
      this.contractDeploys = contractDeploys;
    }
  }

  @Param({"SMALL", "MEDIUM", "LARGE"})
  public Size size;

  @Param({"MEMTABLE", "SST"})
  public StorageState storage;

  private Path dir;
  private OptimisticRocksDBColumnarKeyValueStorage db;
  private KeyValueStorage trieLogStorage;
  private final BonsaiTrieLogFactory factory = new BonsaiTrieLogFactory();
  private final byte[][] keys = new byte[LAYERS][];
  private int next;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    dir = Files.createTempDirectory("trie-log-read");
    db = BenchmarkChainStorage.open(dir);
    trieLogStorage = BenchmarkChainStorage.segment(db, KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);

    final Random random = new Random(7);
    long totalBytes = 0;
    final var tx = trieLogStorage.startTransaction();
    for (int i = 0; i < LAYERS; i++) {
      final Hash blockHash = Hash.wrap(Bytes32.random(random));
      final byte[] serialized = factory.serialize(layer(random, blockHash, i));
      keys[i] = blockHash.getBytes().toArrayUnsafe();
      tx.put(keys[i], serialized);
      totalBytes += serialized.length;
    }
    tx.commit();

    if (storage == StorageState.SST) {
      db.close();
      db = BenchmarkChainStorage.open(dir);
      trieLogStorage =
          BenchmarkChainStorage.segment(db, KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
    }
    BenchmarkChainStorage.describe(dir, storage);
    System.out.printf(
        "[size=%s] average serialized trie log: %d bytes%n", size, totalBytes / LAYERS);
  }

  @TearDown(Level.Trial)
  public void tearDown() throws IOException {
    db.close();
    MoreFiles.deleteRecursively(dir, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  /** What getTrieLogLayer does: storage read + BonsaiTrieLogFactory.deserialize. */
  @Benchmark
  public TrieLog readAndDeserialize() {
    return factory.deserialize(trieLogStorage.get(nextKey()).orElseThrow());
  }

  /** Storage read alone, to split the cost between RocksDB and deserialization. */
  @Benchmark
  public Optional<byte[]> readBytesOnly() {
    return trieLogStorage.get(nextKey());
  }

  private byte[] nextKey() {
    next = (next + 1) % LAYERS;
    return keys[next];
  }

  private TrieLogLayer layer(final Random random, final Hash blockHash, final long number) {
    final TrieLogLayer layer = new TrieLogLayer().setBlockHash(blockHash).setBlockNumber(number);
    final Address[] addresses = new Address[size.accounts];
    for (int a = 0; a < size.accounts; a++) {
      addresses[a] = Address.wrap(Bytes.random(20, random));
      layer.addAccountChange(addresses[a], account(random), account(random));
    }
    // Storage writes concentrate on a fifth of the touched accounts (the contracts).
    final int contracts = Math.max(1, size.accounts / 5);
    for (int s = 0; s < size.storageSlots; s++) {
      layer.addStorageChange(
          addresses[random.nextInt(contracts)],
          new StorageSlotKey(UInt256.fromBytes(Bytes32.random(random))),
          random.nextInt(10) < 3 ? UInt256.ZERO : slotValue(random),
          slotValue(random));
    }
    for (int c = 0; c < size.contractDeploys; c++) {
      layer.addCodeChange(addresses[c], null, Bytes.random(8_000, random), blockHash);
    }
    return layer;
  }

  /** Half small counters/balances, half full 32-byte words (hashes, packed structs). */
  private static UInt256 slotValue(final Random random) {
    return random.nextBoolean()
        ? UInt256.valueOf(random.nextInt(1 << 20))
        : UInt256.fromBytes(Bytes32.random(random));
  }

  private static PmtStateTrieAccountValue account(final Random random) {
    return new PmtStateTrieAccountValue(
        random.nextInt(10_000),
        Wei.of(random.nextLong() & Long.MAX_VALUE),
        Hash.wrap(Bytes32.random(random)),
        Hash.wrap(Bytes32.random(random)));
  }
}

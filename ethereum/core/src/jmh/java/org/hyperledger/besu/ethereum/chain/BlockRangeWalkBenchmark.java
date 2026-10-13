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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BenchmarkChainStorage.StorageState;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.plugin.services.storage.rocksdb.segmented.OptimisticRocksDBColumnarKeyValueStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
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
 * Cost of finding the block hashes in (start, end] for debug_getModifiedAccounts*: the current
 * parent-hash walk vs. walking the canonical number index, with and without the header cache. Each
 * method returns the hashes so nothing is dead-code eliminated.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class BlockRangeWalkBenchmark {

  private static final int CHAIN_LENGTH = 4096;
  private static final int HEADER_CACHE_SIZE = 1024;

  @Param({"MEMTABLE", "SST"})
  public StorageState storage;

  /** 1 = single-block form; 512 = DEFAULT_MAX_LAYERS_TO_LOAD, the largest allowed range. */
  @Param({"1", "512"})
  public int range;

  private Path dir;
  private OptimisticRocksDBColumnarKeyValueStorage db;
  private Blockchain noHeaderCache;
  private Blockchain headerCache;
  private BlockHeader endHeader;
  private long startNumber;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    dir = Files.createTempDirectory("block-range-walk");
    db = BenchmarkChainStorage.open(dir);
    final Block genesis = BenchmarkChainStorage.genesis();
    BenchmarkChainStorage.populate(db, genesis, CHAIN_LENGTH);
    if (storage == StorageState.SST) {
      db.close();
      db = BenchmarkChainStorage.open(dir);
    }
    BenchmarkChainStorage.describe(dir, storage);

    noHeaderCache = BenchmarkChainStorage.blockchain(db, genesis, 0);
    headerCache = BenchmarkChainStorage.blockchain(db, genesis, HEADER_CACHE_SIZE);
    endHeader = noHeaderCache.getChainHeadHeader();
    startNumber = endHeader.getNumber() - range;
    System.out.printf("header RLP size: %d bytes%n", RLP.encode(endHeader::writeTo).size());

    // Block import puts each new header in the cache; reading the range once does the same here.
    parentWalk(headerCache);
  }

  @TearDown(Level.Trial)
  public void tearDown() throws IOException {
    db.close();
    MoreFiles.deleteRecursively(dir, RecursiveDeleteOption.ALLOW_INSECURE);
  }

  /** Current code (AbstractDebugGetModifiedAccounts pass 1), default node: no header cache. */
  @Benchmark
  public List<Hash> parentWalk_noHeaderCache() {
    return parentWalk(noHeaderCache);
  }

  /** Current code with --cache-last-block-headers / --cache-last-blocks set. */
  @Benchmark
  public List<Hash> parentWalk_headerCache() {
    return parentWalk(headerCache);
  }

  /** Canonical index only (getBlockHashByNumber): never touches the header cache. */
  @Benchmark
  public List<Hash> hashByNumber() {
    final List<Hash> hashes = new ArrayList<>();
    for (long n = endHeader.getNumber(); n > startNumber; n--) {
      hashes.add(noHeaderCache.getBlockHashByNumber(n).orElseThrow());
    }
    return hashes;
  }

  /** Reviewer's snippet, default node: getBlockHeader(n) = index read + header read. */
  @Benchmark
  public List<Hash> headerByNumber_noHeaderCache() {
    return headerByNumber(noHeaderCache);
  }

  /** Reviewer's snippet with the header cache: index read + cached header. */
  @Benchmark
  public List<Hash> headerByNumber_headerCache() {
    return headerByNumber(headerCache);
  }

  private List<Hash> parentWalk(final Blockchain blockchain) {
    final List<Hash> hashes = new ArrayList<>();
    BlockHeader header = endHeader;
    while (header.getNumber() > startNumber) {
      hashes.add(header.getBlockHash());
      header = blockchain.getBlockHeader(header.getParentHash()).orElseThrow();
    }
    hashes.add(header.getBlockHash()); // the ancestry check compares this against start
    return hashes;
  }

  private List<Hash> headerByNumber(final Blockchain blockchain) {
    final List<Hash> hashes = new ArrayList<>();
    for (long n = endHeader.getNumber(); n > startNumber; n--) {
      hashes.add(blockchain.getBlockHeader(n).orElseThrow().getBlockHash());
    }
    return hashes;
  }
}

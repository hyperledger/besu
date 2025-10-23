/// *
// * Copyright contributors to Hyperledger Besu.
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except
// * in compliance with the License. You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software distributed under the
// License
// * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
// express
// * or implied. See the License for the specific language governing permissions and limitations
// under
// * the License.
// *
// * SPDX-License-Identifier: Apache-2.0
// */
// package org.hyperledger.besu.ethereum.eth.sync.fastsync;
//
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.mockito.ArgumentMatchers.anyLong;
// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.when;
//
// import org.hyperledger.besu.datatypes.Hash;
// import org.hyperledger.besu.ethereum.chain.Blockchain;
// import org.hyperledger.besu.ethereum.eth.manager.EthContext;
// import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
// import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
// import org.hyperledger.besu.plugin.services.MetricsSystem;
//
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.List;
// import java.util.Optional;
// import java.util.OptionalLong;
// import java.util.Set;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.TimeUnit;
//
// import org.apache.tuweni.bytes.Bytes;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Disabled;
// import org.junit.jupiter.api.Test;
//
/// **
// * Unit tests for BackwardHeaderSource.
// *
// * <p>NOTE: These tests are currently DISABLED because BackwardHeaderSource's constructor makes a
// * synchronous network call (GetHeadersFromPeerByHashTask.forSingleHash().run().join()) which is
// * difficult to mock in unit tests. The static method call and the .join() in the constructor make
// * it impossible to properly mock without PowerMock or significant refactoring.
// *
// * <p>TODO: Consider one of these solutions: 1. Add a test-friendly constructor that accepts
// * pivotBlockNumber directly 2. Convert these to integration tests with a real EthContext 3.
// * Refactor to inject a HeaderFetcher dependency instead of calling static methods
// */
// @Disabled("BackwardHeaderSource constructor makes network calls - needs refactoring for unit
// tests")
// class BackwardHeaderSourceTest {
//
//  private EthContext ethContext;
//  private ProtocolSchedule protocolSchedule;
//  private MetricsSystem metricsSystem;
//  private Blockchain blockchain;
//  private FastSyncState fastSyncState;
//
//  @BeforeEach
//  void setUp() {
//    ethContext = mock(EthContext.class);
//    protocolSchedule = mock(ProtocolSchedule.class);
//    metricsSystem = new NoOpMetricsSystem();
//    blockchain = mock(Blockchain.class);
//    fastSyncState = mock(FastSyncState.class);
//
//    // Default behavior: no blocks in database, no resume state
//    when(blockchain.getBlockHeader(anyLong())).thenReturn(Optional.empty());
//
// when(fastSyncState.getLowestContiguousBlockHeaderDownloaded()).thenReturn(OptionalLong.empty());
//  }
//
//  private BackwardHeaderSource createSource(final int batchSize) {
//    // NOTE: This will fail because the constructor tries to fetch the pivot block from the
// network
//    return new BackwardHeaderSource(
//            batchSize,
//        ethContext,
//        protocolSchedule,
//        metricsSystem,
//        blockchain,
//        fastSyncState);
//  }
//
//  @Test
//  void shouldGenerateDescendingBlockNumbers() {
//    // Given: source from block 100, batch size 5
//    final BackwardHeaderSource source = createSource(100, 5);
//
//    // When: calling next() multiple times
//    final Long first = source.next();
//    final Long second = source.next();
//    final Long third = source.next();
//
//    // Then: returns 100, 95, 90
//    assertThat(first).isEqualTo(100);
//    assertThat(second).isEqualTo(95);
//    assertThat(third).isEqualTo(90);
//  }
//
//  @Test
//  void shouldReturnNullWhenExhausted() {
//    // Given: source from block 10, batch size 10
//    final BackwardHeaderSource source = createSource(10, 10);
//
//    // When: calling next() twice
//    final Long first = source.next();
//    final Long second = source.next();
//
//    // Then: first call returns 10, second returns 0
//    assertThat(first).isEqualTo(10);
//    assertThat(second).isEqualTo(0);
//  }
//
//  @Test
//  void shouldHandleSingleBatch() {
//    // Given: source from block 10, batch size 10
//    final BackwardHeaderSource source = createSource(10, 10);
//
//    // When: calling next()
//    final Long result = source.next();
//
//    // Then: returns 10
//    assertThat(result).isEqualTo(10);
//    assertThat(source.next()).isEqualTo(0);
//  }
//
//  @Test
//  void shouldReportHasNextCorrectly() {
//    // Given: source from block 10, batch size 5
//    final BackwardHeaderSource source = createSource(10, 5);
//
//    // When: checking hasNext() before and after exhaustion
//    assertThat(source.hasNext()).isTrue();
//
//    source.next(); // 10
//    assertThat(source.hasNext()).isTrue();
//
//    source.next(); // 5
//    assertThat(source.hasNext()).isTrue();
//
//    source.next(); // 0
//    assertThat(source.hasNext()).isFalse();
//
//    // Then: returns true before, false after
//    assertThat(source.next()).isNull();
//  }
//
//  @Test
//  void shouldHandlePartialLastBatch() {
//    // Given: source from block 10, batch size 7
//    final BackwardHeaderSource source = createSource(10, 7);
//
//    // When: calling next() twice
//    final Long first = source.next();
//    final Long second = source.next();
//
//    // Then: returns 10, then 3 (not negative)
//    assertThat(first).isEqualTo(10);
//    assertThat(second).isEqualTo(3);
//    assertThat(source.next()).isNull();
//  }
//
//  @Test
//  void shouldBeThreadSafe() throws InterruptedException {
//    // Given: source shared between threads
//    final BackwardHeaderSource source = createSource(1000, 10);
//    final int threadCount = 10;
//    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
//    final Set<Long> allResults = Collections.newSetFromMap(new ConcurrentHashMap<>());
//    final CountDownLatch latch = new CountDownLatch(threadCount);
//
//    // When: multiple threads call next() concurrently
//    for (int i = 0; i < threadCount; i++) {
//      executor.submit(
//          () -> {
//            try {
//              Long result;
//              while ((result = source.next()) != null) {
//                allResults.add(result);
//              }
//            } finally {
//              latch.countDown();
//            }
//          });
//    }
//
//    // Then: each thread gets unique block numbers without duplicates
//    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
//    executor.shutdown();
//
//    // Verify all expected block numbers were generated (1000, 990, 980, ..., 10, 0)
//    assertThat(allResults).hasSize(101); // 0, 10, 20, ..., 1000
//    assertThat(allResults).contains(1000L, 990L, 500L, 10L, 0L);
//  }
//
//  @Test
//  void shouldHandleExactlyDivisibleRange() {
//    // Given: source where range is exactly divisible by batch size
//    final BackwardHeaderSource source = createSource(100, 10);
//
//    // When: exhausting the source
//    final List<Long> results = new ArrayList<>();
//    Long result;
//    while ((result = source.next()) != null) {
//      results.add(result);
//    }
//
//    // Then: returns exactly 11 values (100, 90, 80, ..., 10, 0)
//    assertThat(results).containsExactly(100L, 90L, 80L, 70L, 60L, 50L, 40L, 30L, 20L, 10L, 0L);
//  }
//
//  @Test
//  void shouldHandleDescendingToZero() {
//    // Given: source starting at 50
//    final BackwardHeaderSource source = createSource(50, 5);
//
//    // When: exhausting the source
//    final List<Long> results = new ArrayList<>();
//    Long result;
//    while ((result = source.next()) != null) {
//      results.add(result);
//    }
//
//    // Then: goes down to 0
//    assertThat(results).containsExactly(50L, 45L, 40L, 35L, 30L, 25L, 20L, 15L, 10L, 5L, 0L);
//  }
//
//  @Test
//  void shouldHandleStartBlockEqualToBatchSize() {
//    // Given: start block equals batch size
//    final BackwardHeaderSource source = createSource(10, 5);
//
//    // When: calling next() multiple times
//    final Long first = source.next();
//    final Long second = source.next();
//    final Long third = source.next();
//
//    // Then: returns 10, 5, 0, then null
//    assertThat(first).isEqualTo(10);
//    assertThat(second).isEqualTo(5);
//    assertThat(third).isEqualTo(0);
//  }
//
//  @Test
//  void shouldHandleStartBlockSmallerThanBatchSize() {
//    // Given: start block smaller than batch size
//    final BackwardHeaderSource source = createSource(3, 5);
//
//    // When: calling next()
//    final Long first = source.next();
//    final Long second = source.next();
//
//    // Then: returns start block, then null
//    assertThat(first).isEqualTo(3);
//    assertThat(second).isNull();
//  }
// }

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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link ExecutionStats}. */
public class ExecutionStatsTest {

  private ExecutionStats stats;

  @BeforeEach
  void setUp() {
    stats = new ExecutionStats();
  }

  // =============================================
  // STATE READ/WRITE COUNTERS
  // =============================================

  @Test
  void shouldIncrementAccountReads() {
    stats.incrementAccountReads();
    stats.incrementAccountReads();
    assertThat(stats.getAccountReads()).isEqualTo(2);
  }

  @Test
  void shouldIncrementStorageReads() {
    stats.incrementStorageReads();
    assertThat(stats.getStorageReads()).isEqualTo(1);
  }

  @Test
  void shouldIncrementCodeReads() {
    stats.incrementCodeReads();
    stats.incrementCodeReads();
    stats.incrementCodeReads();
    assertThat(stats.getCodeReads()).isEqualTo(3);
  }

  @Test
  void shouldTrackCodeBytesRead() {
    stats.addCodeBytesRead(1024);
    stats.addCodeBytesRead(512);
    assertThat(stats.getCodeBytesRead()).isEqualTo(1536);
  }

  @Test
  void shouldIncrementAccountWrites() {
    stats.incrementAccountWrites();
    assertThat(stats.getAccountWrites()).isEqualTo(1);
  }

  @Test
  void shouldIncrementStorageWrites() {
    stats.incrementStorageWrites();
    stats.incrementStorageWrites();
    assertThat(stats.getStorageWrites()).isEqualTo(2);
  }

  // =============================================
  // CACHE STATISTICS
  // =============================================

  @Test
  void shouldSetCacheStats() {
    stats.setCacheStats(100, 20, 500, 100, 50, 5);

    assertThat(stats.getAccountCacheHits()).isEqualTo(100);
    assertThat(stats.getAccountCacheMisses()).isEqualTo(20);
    assertThat(stats.getStorageCacheHits()).isEqualTo(500);
    assertThat(stats.getStorageCacheMisses()).isEqualTo(100);
    assertThat(stats.getCodeCacheHits()).isEqualTo(50);
    assertThat(stats.getCodeCacheMisses()).isEqualTo(5);
  }

  @Test
  void shouldIncrementCacheCountersIndividually() {
    stats.incrementAccountCacheHits();
    stats.incrementAccountCacheHits();
    stats.incrementAccountCacheMisses();
    stats.incrementStorageCacheHits();
    stats.incrementStorageCacheMisses();
    stats.incrementCodeCacheHits();
    stats.incrementCodeCacheMisses();

    assertThat(stats.getAccountCacheHits()).isEqualTo(2);
    assertThat(stats.getAccountCacheMisses()).isEqualTo(1);
    assertThat(stats.getStorageCacheHits()).isEqualTo(1);
    assertThat(stats.getStorageCacheMisses()).isEqualTo(1);
    assertThat(stats.getCodeCacheHits()).isEqualTo(1);
    assertThat(stats.getCodeCacheMisses()).isEqualTo(1);
  }

  // =============================================
  // EVM OPERATION COUNTERS
  // =============================================

  @Test
  void shouldIncrementSloadCount() {
    stats.incrementSloadCount();
    stats.incrementSloadCount();
    assertThat(stats.getSloadCount()).isEqualTo(2);
  }

  @Test
  void shouldIncrementSstoreCount() {
    stats.incrementSstoreCount();
    assertThat(stats.getSstoreCount()).isEqualTo(1);
  }

  @Test
  void shouldIncrementCallCount() {
    stats.incrementCallCount();
    stats.incrementCallCount();
    stats.incrementCallCount();
    assertThat(stats.getCallCount()).isEqualTo(3);
  }

  @Test
  void shouldIncrementCreateCount() {
    stats.incrementCreateCount();
    assertThat(stats.getCreateCount()).isEqualTo(1);
  }

  // =============================================
  // TIMING
  // =============================================

  @Test
  void shouldMeasureExecutionTime() throws InterruptedException {
    stats.startExecution();
    Thread.sleep(10); // Small delay
    stats.endExecution();

    assertThat(stats.getExecutionTimeMs()).isGreaterThanOrEqualTo(0);
    assertThat(stats.getExecutionTimeNanos()).isGreaterThan(0);
  }

  @Test
  void shouldCalculateTotalTime() {
    stats.addValidationTime(1_000_000); // 1ms
    stats.addCommitTime(2_000_000); // 2ms

    assertThat(stats.getValidationTimeMs()).isEqualTo(1);
    assertThat(stats.getCommitTimeMs()).isEqualTo(2);
  }

  // =============================================
  // GAS AND THROUGHPUT
  // =============================================

  @Test
  void shouldTrackGasUsed() {
    stats.addGasUsed(21000);
    stats.addGasUsed(42000);
    assertThat(stats.getGasUsed()).isEqualTo(63000);
  }

  @Test
  void shouldTrackTransactionCount() {
    stats.incrementTransactionCount();
    stats.incrementTransactionCount();
    assertThat(stats.getTransactionCount()).isEqualTo(2);
  }

  @Test
  void shouldReturnZeroMgasWhenNoExecutionTime() {
    stats.addGasUsed(30_000_000);
    assertThat(stats.getMgasPerSecond()).isEqualTo(0.0);
  }

  // =============================================
  // UNIQUE TRACKING
  // =============================================

  @Test
  void shouldTrackUniqueAccountsTouched() {
    Address addr1 = Address.fromHexString("0x0000000000000000000000000000000000000001");
    Address addr2 = Address.fromHexString("0x0000000000000000000000000000000000000002");

    stats.recordAccountTouched(addr1);
    stats.recordAccountTouched(addr1); // Duplicate
    stats.recordAccountTouched(addr2);

    assertThat(stats.getUniqueAccountsTouched()).isEqualTo(2);
  }

  @Test
  void shouldTrackUniqueStorageSlots() {
    Address addr = Address.fromHexString("0x0000000000000000000000000000000000000001");

    stats.recordStorageSlotAccessed(addr, UInt256.valueOf(1));
    stats.recordStorageSlotAccessed(addr, UInt256.valueOf(1)); // Duplicate
    stats.recordStorageSlotAccessed(addr, UInt256.valueOf(2));

    assertThat(stats.getUniqueStorageSlots()).isEqualTo(2);
  }

  @Test
  void shouldTrackUniqueContractsExecuted() {
    Address addr1 = Address.fromHexString("0x0000000000000000000000000000000000000001");
    Address addr2 = Address.fromHexString("0x0000000000000000000000000000000000000002");

    stats.recordContractExecuted(addr1);
    stats.recordContractExecuted(addr1); // Duplicate
    stats.recordContractExecuted(addr2);

    assertThat(stats.getUniqueContractsExecuted()).isEqualTo(2);
  }

  // =============================================
  // JSON OUTPUT - STRUCTURE
  // =============================================

  @Test
  void shouldGenerateValidSlowBlockJsonWithAllFields() {
    // Set all metrics
    stats.addGasUsed(21_000_000);
    stats.incrementTransactionCount();
    stats.incrementTransactionCount();

    // State reads
    stats.incrementAccountReads();
    stats.incrementStorageReads();
    stats.incrementCodeReads();
    stats.addCodeBytesRead(1024);

    // State writes
    stats.incrementAccountWrites();
    stats.incrementStorageWrites();

    // Cache stats
    stats.setCacheStats(100, 20, 500, 100, 50, 5);

    // EVM operations
    stats.incrementSloadCount();
    stats.incrementSstoreCount();
    stats.incrementCallCount();
    stats.incrementCreateCount();

    String json = stats.toSlowBlockJson(12345L, "0xabcd1234");

    // Verify top-level structure
    assertThat(json).contains("\"level\":\"warn\"");
    assertThat(json).contains("\"msg\":\"Slow block\"");

    // Verify block section
    assertThat(json).contains("\"block\":");
    assertThat(json).contains("\"number\":12345");
    assertThat(json).contains("\"hash\":\"0xabcd1234\"");
    assertThat(json).contains("\"gas_used\":21000000");
    assertThat(json).contains("\"tx_count\":2");

    // Verify timing section
    assertThat(json).contains("\"timing\":");
    assertThat(json).contains("\"execution_ms\":");
    assertThat(json).contains("\"total_ms\":");

    // Verify throughput section
    assertThat(json).contains("\"throughput\":");
    assertThat(json).contains("\"mgas_per_sec\":");

    // Verify state_reads section
    assertThat(json).contains("\"state_reads\":");
    assertThat(json).contains("\"accounts\":1");
    assertThat(json).contains("\"storage_slots\":1");

    // Verify state_writes section
    assertThat(json).contains("\"state_writes\":");

    // Verify cache section with nested structure
    assertThat(json).contains("\"cache\":");
    assertThat(json).contains("\"account\":{\"hits\":100,\"misses\":20");
    assertThat(json).contains("\"storage\":{\"hits\":500,\"misses\":100");
    assertThat(json).contains("\"code\":{\"hits\":50,\"misses\":5");

    // Verify EVM section
    assertThat(json).contains("\"evm\":");
    assertThat(json).contains("\"sload\":1");
    assertThat(json).contains("\"sstore\":1");
    assertThat(json).contains("\"calls\":1");
    assertThat(json).contains("\"creates\":1");
  }

  // =============================================
  // JSON OUTPUT - CACHE HIT RATES
  // =============================================

  @Test
  void shouldFormatHitRateWithTwoDecimals() {
    // 100 hits, 20 misses = 100/120 = 83.33%
    stats.setCacheStats(100, 20, 0, 0, 0, 0);

    String json = stats.toSlowBlockJson(1L, "0x1234");

    assertThat(json).contains("\"hit_rate\":83.33");
  }

  @Test
  void shouldReturnZeroHitRateWhenNoAccesses() {
    stats.setCacheStats(0, 0, 0, 0, 0, 0);

    String json = stats.toSlowBlockJson(1L, "0x1234");

    assertThat(json).contains("\"hit_rate\":0.00");
  }

  @Test
  void shouldReturn100HitRateWhenAllHits() {
    stats.setCacheStats(100, 0, 0, 0, 0, 0);

    String json = stats.toSlowBlockJson(1L, "0x1234");

    assertThat(json).contains("\"hit_rate\":100.00");
  }

  // =============================================
  // JSON OUTPUT - UNIQUE TRACKING
  // =============================================

  @Test
  void shouldIncludeUniqueTrackingInJson() {
    Address addr = Address.fromHexString("0x0000000000000000000000000000000000000001");
    stats.recordAccountTouched(addr);
    stats.recordStorageSlotAccessed(addr, UInt256.valueOf(1));
    stats.recordContractExecuted(addr);

    String json = stats.toSlowBlockJson(1L, "0x1234");

    assertThat(json).contains("\"unique\":");
    assertThat(json).contains("\"accounts\":1");
    assertThat(json).contains("\"contracts\":1");
  }

  // =============================================
  // RESET
  // =============================================

  @Test
  void shouldResetAllCounters() {
    // Set various stats
    stats.incrementAccountReads();
    stats.incrementStorageReads();
    stats.incrementCodeReads();
    stats.incrementAccountWrites();
    stats.incrementStorageWrites();
    stats.setCacheStats(100, 20, 500, 100, 50, 5);
    stats.incrementSloadCount();
    stats.incrementSstoreCount();
    stats.incrementCallCount();
    stats.incrementCreateCount();
    stats.addGasUsed(21000);
    stats.incrementTransactionCount();

    stats.reset();

    assertThat(stats.getAccountReads()).isEqualTo(0);
    assertThat(stats.getStorageReads()).isEqualTo(0);
    assertThat(stats.getCodeReads()).isEqualTo(0);
    assertThat(stats.getAccountWrites()).isEqualTo(0);
    assertThat(stats.getStorageWrites()).isEqualTo(0);
    assertThat(stats.getAccountCacheHits()).isEqualTo(0);
    assertThat(stats.getSloadCount()).isEqualTo(0);
    assertThat(stats.getSstoreCount()).isEqualTo(0);
    assertThat(stats.getCallCount()).isEqualTo(0);
    assertThat(stats.getCreateCount()).isEqualTo(0);
    assertThat(stats.getGasUsed()).isEqualTo(0);
    assertThat(stats.getTransactionCount()).isEqualTo(0);
  }

  // =============================================
  // SLOW BLOCK THRESHOLD
  // =============================================

  @Test
  void shouldIdentifySlowBlock() {
    assertThat(stats.isSlowBlock(1000)).isFalse(); // 0ms < 1000ms
  }
}

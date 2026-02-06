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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tracer that logs slow blocks using execution metrics from ExecutionMetricsTracer.
 *
 * <p>This tracer does not collect metrics itself - it relies on ExecutionMetricsTracer being
 * present in the tracer composition to provide execution statistics. Blocks exceeding the
 * configured threshold are logged in a standardized JSON format.
 *
 * <p>The tracer uses a dedicated "SlowBlock" logger, allowing operators to route slow block output
 * to a separate file/sink via logback configuration.
 *
 * <p>This tracer is designed to be used with TracerAggregator to compose it with
 * ExecutionMetricsTracer.
 */
public class SlowBlockTracer implements BlockAwareOperationTracer {

  private static final Logger SLOW_BLOCK_LOG = LoggerFactory.getLogger("SlowBlock");
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final long slowBlockThresholdMs;
  private final BlockAwareOperationTracer delegate;

  /**
   * Creates a new SlowBlockTracer with no delegate.
   *
   * @param slowBlockThresholdMs the threshold in milliseconds beyond which blocks are logged.
   *     Negative values disable logging, zero logs all blocks.
   */
  public SlowBlockTracer(final long slowBlockThresholdMs) {
    this(slowBlockThresholdMs, BlockAwareOperationTracer.NO_TRACING);
  }

  /**
   * Creates a new SlowBlockTracer that wraps another tracer.
   *
   * @param slowBlockThresholdMs the threshold in milliseconds beyond which blocks are logged.
   *     Negative values disable logging, zero logs all blocks.
   * @param delegate the tracer to delegate calls to
   */
  public SlowBlockTracer(
      final long slowBlockThresholdMs, final BlockAwareOperationTracer delegate) {
    this.slowBlockThresholdMs = slowBlockThresholdMs;
    this.delegate = delegate;
  }

  /**
   * Returns whether slow block tracing is enabled.
   *
   * @return true if threshold is non-negative
   */
  public boolean isEnabled() {
    return slowBlockThresholdMs >= 0;
  }

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final BlockHeader blockHeader,
      final BlockBody blockBody,
      final Address miningBeneficiary) {
    // Delegate all tracing - ExecutionMetricsTracer will handle metrics collection
    delegate.traceStartBlock(worldView, blockHeader, blockBody, miningBeneficiary);
  }

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final ProcessableBlockHeader processableBlockHeader,
      final Address miningBeneficiary) {
    // Delegate all tracing - ExecutionMetricsTracer will handle metrics collection
    delegate.traceStartBlock(worldView, processableBlockHeader, miningBeneficiary);
  }

  @Override
  public void traceEndTransaction(
      final WorldView worldView,
      final Transaction tx,
      final boolean status,
      final Bytes output,
      final List<Log> logs,
      final long gasUsed,
      final Set<Address> selfDestructs,
      final long timeNs) {
    // Delegate all tracing - ExecutionMetricsTracer will handle metrics collection
    delegate.traceEndTransaction(
        worldView, tx, status, output, logs, gasUsed, selfDestructs, timeNs);
  }

  @Override
  public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
    // Delegate first to ensure ExecutionMetricsTracer completes its work
    delegate.traceEndBlock(blockHeader, blockBody);

    // Check for slow blocks using ExecutionStats from thread-local storage
    if (isEnabled()) {
      final ExecutionStats executionStats = ExecutionStatsHolder.get();
      if (executionStats != null && executionStats.isSlowBlock(slowBlockThresholdMs)) {
        logSlowBlock(blockHeader, executionStats);
      }
    }
  }

  /**
   * Logs slow block execution statistics in JSON format for performance monitoring. Follows the
   * cross-client execution metrics specification.
   *
   * @param blockHeader the block header
   * @param stats the execution statistics
   */
  private void logSlowBlock(final BlockHeader blockHeader, final ExecutionStats stats) {
    try {
      final ObjectNode json = JSON_MAPPER.createObjectNode();
      json.put("level", "warn");
      json.put("msg", "Slow block");

      final ObjectNode blockNode = json.putObject("block");
      blockNode.put("number", blockHeader.getNumber());
      blockNode.put("hash", blockHeader.getBlockHash().toHexString());
      blockNode.put("gas_used", stats.getGasUsed());
      blockNode.put("tx_count", stats.getTransactionCount());

      final ObjectNode timingNode = json.putObject("timing");
      timingNode.put("execution_ms", stats.getExecutionTimeMs());
      timingNode.put("state_read_ms", stats.getStateReadTimeMs());
      timingNode.put("state_hash_ms", stats.getStateHashTimeMs());
      timingNode.put("commit_ms", stats.getCommitTimeMs());
      timingNode.put("total_ms", stats.getTotalTimeMs());

      final ObjectNode throughputNode = json.putObject("throughput");
      throughputNode.put("mgas_per_sec", stats.getMgasPerSecond());

      final ObjectNode stateReadsNode = json.putObject("state_reads");
      stateReadsNode.put("accounts", stats.getAccountReads());
      stateReadsNode.put("storage_slots", stats.getStorageReads());
      stateReadsNode.put("code", stats.getCodeReads());
      stateReadsNode.put("code_bytes", stats.getCodeBytesRead());

      final ObjectNode stateWritesNode = json.putObject("state_writes");
      stateWritesNode.put("accounts", stats.getAccountWrites());
      stateWritesNode.put("storage_slots", stats.getStorageWrites());
      stateWritesNode.put("code", stats.getCodeWrites());
      stateWritesNode.put("code_bytes", stats.getCodeBytesWritten());
      stateWritesNode.put("eip7702_delegations_set", stats.getEip7702DelegationsSet());
      stateWritesNode.put("eip7702_delegations_cleared", stats.getEip7702DelegationsCleared());

      final ObjectNode cacheNode = json.putObject("cache");

      final ObjectNode accountCacheNode = cacheNode.putObject("account");
      accountCacheNode.put("hits", stats.getAccountCacheHits());
      accountCacheNode.put("misses", stats.getAccountCacheMisses());
      accountCacheNode.put(
          "hit_rate", calculateHitRate(stats.getAccountCacheHits(), stats.getAccountCacheMisses()));

      final ObjectNode storageCacheNode = cacheNode.putObject("storage");
      storageCacheNode.put("hits", stats.getStorageCacheHits());
      storageCacheNode.put("misses", stats.getStorageCacheMisses());
      storageCacheNode.put(
          "hit_rate", calculateHitRate(stats.getStorageCacheHits(), stats.getStorageCacheMisses()));

      final ObjectNode codeCacheNode = cacheNode.putObject("code");
      codeCacheNode.put("hits", stats.getCodeCacheHits());
      codeCacheNode.put("misses", stats.getCodeCacheMisses());
      codeCacheNode.put(
          "hit_rate", calculateHitRate(stats.getCodeCacheHits(), stats.getCodeCacheMisses()));

      final ObjectNode uniqueNode = json.putObject("unique");
      uniqueNode.put("accounts", stats.getUniqueAccountsTouched());
      uniqueNode.put("storage_slots", stats.getUniqueStorageSlots());
      uniqueNode.put("contracts", stats.getUniqueContractsExecuted());

      final ObjectNode evmNode = json.putObject("evm");
      evmNode.put("sload", stats.getSloadCount());
      evmNode.put("sstore", stats.getSstoreCount());
      evmNode.put("calls", stats.getCallCount());
      evmNode.put("creates", stats.getCreateCount());

      SLOW_BLOCK_LOG.warn(JSON_MAPPER.writeValueAsString(json));
    } catch (JsonProcessingException e) {
      // Fallback to simple log
      SLOW_BLOCK_LOG.warn(
          "Slow block number={} hash={} exec={}ms gas={} mgas/s={:.2f} txs={}",
          blockHeader.getNumber(),
          blockHeader.getBlockHash().toHexString(),
          stats.getExecutionTimeMs(),
          stats.getGasUsed(),
          stats.getMgasPerSecond(),
          stats.getTransactionCount());
    }
  }

  /**
   * Calculates the cache hit rate as a percentage.
   *
   * @param hits the number of cache hits
   * @param misses the number of cache misses
   * @return the hit rate as a percentage (0-100)
   */
  private static double calculateHitRate(final long hits, final long misses) {
    final long total = hits + misses;
    if (total > 0) {
      return (hits * 100.0) / total;
    }
    return 0.0;
  }
}

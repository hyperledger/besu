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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.tracing.ExecutionMetricsTracer;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tracer that collects execution metrics and logs slow blocks.
 *
 * <p>This tracer implements the cross-client execution metrics specification, collecting detailed
 * statistics about block execution including timing, state access patterns, cache performance, and
 * EVM operation counts. Blocks exceeding the configured threshold are logged in a standardized JSON
 * format.
 *
 * <p>The tracer uses a dedicated "SlowBlock" logger, allowing operators to route slow block output
 * to a separate file/sink via logback configuration.
 *
 * <p>This tracer supports composition - it can wrap another BlockAwareOperationTracer and delegate
 * all calls to it while adding slow block metrics collection.
 */
public class SlowBlockTracer implements BlockAwareOperationTracer {

  private static final Logger SLOW_BLOCK_LOG = LoggerFactory.getLogger("SlowBlock");
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  private final long slowBlockThresholdMs;
  private final BlockAwareOperationTracer delegate;
  private ExecutionStats executionStats;
  private ExecutionMetricsTracer metricsTracer;

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
    // Delegate first
    delegate.traceStartBlock(worldView, blockHeader, blockBody, miningBeneficiary);

    if (!isEnabled()) {
      return;
    }
    executionStats = new ExecutionStats();
    executionStats.startExecution();
    ExecutionStatsHolder.set(executionStats);

    // Create ExecutionMetricsTracer for this block
    metricsTracer = new ExecutionMetricsTracer();
  }

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final ProcessableBlockHeader processableBlockHeader,
      final Address miningBeneficiary) {
    // Delegate first
    delegate.traceStartBlock(worldView, processableBlockHeader, miningBeneficiary);

    // Block building - same initialization
    if (!isEnabled()) {
      return;
    }
    executionStats = new ExecutionStats();
    executionStats.startExecution();
    ExecutionStatsHolder.set(executionStats);

    // Create ExecutionMetricsTracer for this block
    metricsTracer = new ExecutionMetricsTracer();
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
    // Delegate first
    delegate.traceEndTransaction(
        worldView, tx, status, output, logs, gasUsed, selfDestructs, timeNs);

    if (!isEnabled() || executionStats == null) {
      return;
    }
    executionStats.incrementTransactionCount();
    executionStats.addGasUsed(gasUsed);
  }

  @Override
  public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
    // Our metrics collection first (before delegate which may do cleanup)
    if (isEnabled() && executionStats != null) {
      try {
        // Collect EVM operation counters from ExecutionMetricsTracer
        if (metricsTracer != null) {
          executionStats.collectMetricsFromTracer(metricsTracer);
        }
        // End execution timing
        executionStats.endExecution();

        // Log if slow
        if (executionStats.isSlowBlock(slowBlockThresholdMs)) {
          logSlowBlock(blockHeader, executionStats);
        }
      } finally {
        // Clean up thread-local state
        ExecutionStatsHolder.clear();
        executionStats = null;
        metricsTracer = null;
      }
    }

    // Delegate after our cleanup
    delegate.traceEndBlock(blockHeader, blockBody);
  }

  /**
   * Gets the current execution stats, if available.
   *
   * @return the current ExecutionStats or null if not in a block
   */
  public ExecutionStats getExecutionStats() {
    return executionStats;
  }

  /**
   * Gets the current execution metrics tracer, if available.
   *
   * @return the current ExecutionMetricsTracer or null if not in a block
   */
  public ExecutionMetricsTracer getExecutionMetricsTracer() {
    return metricsTracer;
  }

  @Override
  public void tracePreExecution(final MessageFrame frame) {
    // Delegate to the wrapped tracer first
    delegate.tracePreExecution(frame);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.tracePreExecution(frame);
    }
  }

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    // Delegate to the wrapped tracer first
    delegate.tracePostExecution(frame, operationResult);

    // Delegate to ExecutionMetricsTracer if available for EVM operation counting
    if (metricsTracer != null) {
      metricsTracer.tracePostExecution(frame, operationResult);
    }
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    // Delegate to the wrapped tracer first
    delegate.tracePrecompileCall(frame, gasRequirement, output);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.tracePrecompileCall(frame, gasRequirement, output);
    }
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    // Delegate to the wrapped tracer first
    delegate.traceAccountCreationResult(frame, haltReason);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.traceAccountCreationResult(frame, haltReason);
    }
  }

  @Override
  public void tracePrepareTransaction(final WorldView worldView, final Transaction transaction) {
    // Delegate to the wrapped tracer first
    delegate.tracePrepareTransaction(worldView, transaction);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.tracePrepareTransaction(worldView, transaction);
    }
  }

  @Override
  public void traceStartTransaction(final WorldView worldView, final Transaction transaction) {
    // Delegate to the wrapped tracer first
    delegate.traceStartTransaction(worldView, transaction);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.traceStartTransaction(worldView, transaction);
    }
  }

  @Override
  public void traceBeforeRewardTransaction(
      final WorldView worldView, final Transaction tx, final Wei miningReward) {
    // Delegate to the wrapped tracer first
    delegate.traceBeforeRewardTransaction(worldView, tx, miningReward);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.traceBeforeRewardTransaction(worldView, tx, miningReward);
    }
  }

  @Override
  public void traceContextEnter(final MessageFrame frame) {
    // Delegate to the wrapped tracer first
    delegate.traceContextEnter(frame);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.traceContextEnter(frame);
    }
  }

  @Override
  public void traceContextReEnter(final MessageFrame frame) {
    // Delegate to the wrapped tracer first
    delegate.traceContextReEnter(frame);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.traceContextReEnter(frame);
    }
  }

  @Override
  public void traceContextExit(final MessageFrame frame) {
    // Delegate to the wrapped tracer first
    delegate.traceContextExit(frame);

    // Delegate to ExecutionMetricsTracer if available
    if (metricsTracer != null) {
      metricsTracer.traceContextExit(frame);
    }
  }

  @Override
  public boolean isExtendedTracing() {
    // Return true if either the delegate supports extended tracing or we need metrics tracking
    return delegate.isExtendedTracing() || (metricsTracer != null);
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

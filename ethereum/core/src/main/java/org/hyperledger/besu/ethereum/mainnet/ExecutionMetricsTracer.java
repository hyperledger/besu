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
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EvmOperationCounters;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.List;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;

/**
 * A tracer that collects execution metrics following the cross-client execution metrics
 * specification.
 *
 * <p>This tracer collects detailed statistics about block execution including timing, state access
 * patterns, cache performance, and EVM operation counts. The collected metrics are made available
 * via thread-local storage for other components to access.
 *
 * <p>This tracer is designed to be composed with other tracers using TracerAggregator.
 */
public class ExecutionMetricsTracer implements BlockAwareOperationTracer {

  private ExecutionStats executionStats;

  /** Creates a new ExecutionMetricsTracer. */
  public ExecutionMetricsTracer() {}

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final BlockHeader blockHeader,
      final BlockBody blockBody,
      final Address miningBeneficiary) {
    executionStats = new ExecutionStats();
    executionStats.startExecution();
    ExecutionStatsHolder.set(executionStats);
    EvmOperationCounters.reset();
  }

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final ProcessableBlockHeader processableBlockHeader,
      final Address miningBeneficiary) {
    // Block building - same initialization
    executionStats = new ExecutionStats();
    executionStats.startExecution();
    ExecutionStatsHolder.set(executionStats);
    EvmOperationCounters.reset();
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
    if (executionStats != null) {
      executionStats.incrementTransactionCount();
      executionStats.addGasUsed(gasUsed);
    }
  }

  @Override
  public void traceEndBlock(final BlockHeader blockHeader, final BlockBody blockBody) {
    if (executionStats != null) {
      try {
        // Collect EVM operation counters
        executionStats.collectEvmCounters();
        // End execution timing
        executionStats.endExecution();
      } finally {
        // Clean up thread-local state
        ExecutionStatsHolder.clear();
        EvmOperationCounters.clear();
        executionStats = null;
      }
    }
  }

  @Override
  public void traceBeforeRewardTransaction(
      final WorldView worldView, final Transaction tx, final Wei miningReward) {
    // No metrics collection needed for reward transaction
  }

  /**
   * Gets the current execution stats, if available.
   *
   * @return the current ExecutionStats or null if not in a block
   */
  public ExecutionStats getExecutionStats() {
    return executionStats;
  }
}
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
import org.hyperledger.besu.datatypes.Log;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.List;
import java.util.Optional;
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
public class ExecutionMetricsTracer implements BlockAwareOperationTracer, OperationTracer {

  private ExecutionStats executionStats;
  private final org.hyperledger.besu.evm.tracing.ExecutionMetricsTracer evmMetricsTracer;

  /** Creates a new ExecutionMetricsTracer. */
  public ExecutionMetricsTracer() {
    this.evmMetricsTracer = new org.hyperledger.besu.evm.tracing.ExecutionMetricsTracer();
  }

  @Override
  public void traceStartBlock(
      final WorldView worldView,
      final BlockHeader blockHeader,
      final BlockBody blockBody,
      final Address miningBeneficiary) {
    executionStats = new ExecutionStats();
    executionStats.startExecution();
    ExecutionStatsHolder.set(executionStats);
    evmMetricsTracer.reset();
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
    evmMetricsTracer.reset();
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
        // Collect EVM operation counters from tracer instead of static counters
        collectEvmMetricsFromTracer();
        // End execution timing
        executionStats.endExecution();
      } finally {
        // Clean up thread-local state
        ExecutionStatsHolder.clear();
        executionStats = null;
      }
    }
  }

  /** Collect EVM metrics from the internal ExecutionMetricsTracer. */
  private void collectEvmMetricsFromTracer() {
    final var metrics = evmMetricsTracer.getMetrics();

    // Set EVM operation counts
    executionStats.setSloadCount(metrics.getSloadCount());
    executionStats.setSstoreCount(metrics.getSstoreCount());
    executionStats.setCallCount(metrics.getCallCount());
    executionStats.setCreateCount(metrics.getCreateCount());

    // Set state access counts
    executionStats.setAccountReads(metrics.getAccountReads());
    executionStats.setStorageReads(metrics.getStorageReads());
    executionStats.setCodeReads(metrics.getCodeReads());
    executionStats.setCodeBytesRead(metrics.getCodeBytesRead());
    executionStats.setAccountWrites(metrics.getAccountWrites());
    executionStats.setStorageWrites(metrics.getStorageWrites());
    executionStats.setCodeWrites(metrics.getCodeWrites());
    executionStats.setCodeBytesWritten(metrics.getCodeBytesWritten());

    // Set EIP-7702 delegation counts
    executionStats.setEip7702DelegationsSet(metrics.getEip7702DelegationsSet());
    executionStats.setEip7702DelegationsCleared(metrics.getEip7702DelegationsCleared());
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

  // OperationTracer methods - delegate to internal EVM metrics tracer

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    evmMetricsTracer.tracePostExecution(frame, operationResult);
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    evmMetricsTracer.traceAccountCreationResult(frame, haltReason);
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    evmMetricsTracer.tracePrecompileCall(frame, gasRequirement, output);
  }
}

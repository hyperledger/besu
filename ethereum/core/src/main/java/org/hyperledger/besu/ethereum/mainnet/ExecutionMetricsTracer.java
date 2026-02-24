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
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
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
 * patterns, cache performance, and EVM operation counts. The collected metrics are stored in an
 * {@link ExecutionStats} instance and made available via {@link ExecutionStatsHolder}.
 *
 * <p>This tracer is designed to be composed with other tracers using TracerAggregator.
 */
public class ExecutionMetricsTracer implements BlockAwareOperationTracer, OperationTracer {

  private ExecutionStats executionStats;
  private final org.hyperledger.besu.evm.tracing.EVMExecutionMetricsTracer evmMetricsTracer;

  /** Creates a new ExecutionMetricsTracer. */
  public ExecutionMetricsTracer() {
    this.evmMetricsTracer = new org.hyperledger.besu.evm.tracing.EVMExecutionMetricsTracer();
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
    setCollectorOnWorldState(worldView);
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
    setCollectorOnWorldState(worldView);
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
      // Collect EVM operation counters from tracer instead of static counters
      collectEvmMetricsFromTracer();
      // End execution timing
      executionStats.endExecution();
      // NOTE: We do NOT clear ExecutionStatsHolder here because other tracers (like
      // SlowBlockTracer)
      // may need to access it. The thread-local will be cleared automatically when the thread ends,
      // or can be cleared by the last tracer that needs it.
    }
  }

  /**
   * Collect EVM metrics from the internal ExecutionMetricsTracer.
   *
   * <p>Only EVM operation counts (SLOAD, SSTORE, CALL, CREATE) are collected here. State-layer
   * metrics (account/storage/code reads and writes, cache stats) flow directly through the {@link
   * org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.StateMetricsCollector} set on the
   * world state.
   */
  private void collectEvmMetricsFromTracer() {
    final var metrics = evmMetricsTracer.getMetrics();

    // Set EVM operation counts (tracked via tracePostExecution in the EVM tracer)
    executionStats.setSloadCount(metrics.getSloadCount());
    executionStats.setSstoreCount(metrics.getSstoreCount());
    executionStats.setCallCount(metrics.getCallCount());
    executionStats.setCreateCount(metrics.getCreateCount());
  }

  /** Sets the StateMetricsCollector on the world state if it is a PathBasedWorldState. */
  private void setCollectorOnWorldState(final WorldView worldView) {
    if (worldView instanceof PathBasedWorldState pws) {
      pws.setStateMetricsCollector(executionStats);
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

  /**
   * Merges metrics from a parallel transaction's ExecutionMetricsTracer into this block-level
   * tracer. This is called when parallel execution succeeded without conflicts.
   *
   * @param parallelTracer the parallel transaction's ExecutionMetricsTracer to merge from
   */
  public void mergeFrom(
      final org.hyperledger.besu.evm.tracing.EVMExecutionMetricsTracer parallelTracer) {
    if (parallelTracer != null && evmMetricsTracer != null) {
      evmMetricsTracer.mergeFrom(parallelTracer);
    }
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

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
package org.hyperledger.besu.evm.tracing;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/**
 * An OperationTracer that collects execution metrics for EVM operations.
 *
 * <p>This tracer follows the standard Besu tracer pattern and is designed to be passed through
 * method parameters like DebugOperationTracer. When instantiated, it tracks all available metrics:
 *
 * <ul>
 *   <li>EVM operation counts (SLOAD, SSTORE, CALL, CREATE)
 *   <li>State access operations (account/storage/code reads/writes)
 *   <li>EIP-7702 delegation operations
 * </ul>
 *
 * <p>The decision of whether to collect metrics is made at tracer instantiation time, not during
 * execution. If metrics collection is not desired, use {@link OperationTracer#NO_TRACING} instead
 * of instantiating this tracer.
 *
 * <p>The metrics collected by this tracer can be aggregated across parallel transaction executions
 * and provide detailed insights into block execution performance.
 */
public class EVMExecutionMetricsTracer implements OperationTracer {

  /**
   * Container for EVM operation metrics.
   *
   * <p>This class holds only EVM opcode-level counters (SLOAD, SSTORE, CALL, CREATE). State-layer
   * metrics (account/storage/code reads and writes, cache stats) are collected via the {@code
   * StateMetricsCollector} threaded through the world state object graph.
   */
  public static final class ExecutionMetrics {
    // EVM operation counters
    private int sloadCount;
    private int sstoreCount;
    private int callCount;
    private int createCount;

    /** Creates a new ExecutionMetrics instance with all counters initialized to zero. */
    public ExecutionMetrics() {
      // All primitive int fields are automatically initialized to 0
    }

    /** Reset all counters to zero. */
    public void reset() {
      sloadCount = 0;
      sstoreCount = 0;
      callCount = 0;
      createCount = 0;
    }

    /**
     * Merge metrics from another ExecutionMetrics instance into this one.
     *
     * @param other the metrics to merge
     */
    public void merge(final ExecutionMetrics other) {
      this.sloadCount += other.sloadCount;
      this.sstoreCount += other.sstoreCount;
      this.callCount += other.callCount;
      this.createCount += other.createCount;
    }

    /**
     * Returns the number of SLOAD operations executed.
     *
     * @return the number of SLOAD operations executed
     */
    public int getSloadCount() {
      return sloadCount;
    }

    /**
     * Returns the number of SSTORE operations executed.
     *
     * @return the number of SSTORE operations executed
     */
    public int getSstoreCount() {
      return sstoreCount;
    }

    /**
     * Returns the number of CALL operations executed.
     *
     * @return the number of CALL operations executed
     */
    public int getCallCount() {
      return callCount;
    }

    /**
     * Returns the number of CREATE operations executed.
     *
     * @return the number of CREATE operations executed
     */
    public int getCreateCount() {
      return createCount;
    }
  }

  private final ExecutionMetrics metrics = new ExecutionMetrics();

  /** Create a new EVMExecutionMetricsTracer that tracks all available metrics. */
  public EVMExecutionMetricsTracer() {
    // This tracer tracks all available execution metrics when instantiated
  }

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    // Track EVM operation based on the operation being executed
    final var operation = frame.getCurrentOperation();
    if (operation != null) {
      final String opcodeName = operation.getName();

      switch (opcodeName) {
        case "SLOAD":
          metrics.sloadCount++;
          break;
        case "SSTORE":
          metrics.sstoreCount++;
          break;
        case "CALL":
        case "CALLCODE":
        case "DELEGATECALL":
        case "STATICCALL":
          metrics.callCount++;
          break;
        case "CREATE":
        case "CREATE2":
          metrics.createCount++;
          break;
        default:
          // No tracking needed for other operations
          break;
      }
    }
  }

  @Override
  public void traceAccountCreationResult(
      final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
    // Creation operations are already tracked in tracePostExecution
  }

  @Override
  public void tracePrecompileCall(
      final MessageFrame frame, final long gasRequirement, final Bytes output) {
    // Precompile calls can be considered as special CALL operations
    // But we may want to track them separately if needed
  }

  /**
   * Get the current execution metrics.
   *
   * @return the execution metrics
   */
  public ExecutionMetrics getMetrics() {
    return metrics;
  }

  /** Reset all metrics to zero. */
  public void reset() {
    metrics.reset();
  }

  /**
   * Create a copy of the current metrics.
   *
   * @return a new ExecutionMetrics instance with copied values
   */
  public ExecutionMetrics copyMetrics() {
    final ExecutionMetrics copy = new ExecutionMetrics();
    copy.merge(metrics);
    return copy;
  }

  /**
   * Merge metrics from another ExecutionMetricsTracer into this tracer. This method is used during
   * parallel execution consolidation to combine metrics from background execution with the main
   * block tracer.
   *
   * @param other the EVMExecutionMetricsTracer to merge metrics from
   */
  public void mergeFrom(final EVMExecutionMetricsTracer other) {
    this.metrics.merge(other.metrics);
  }
}

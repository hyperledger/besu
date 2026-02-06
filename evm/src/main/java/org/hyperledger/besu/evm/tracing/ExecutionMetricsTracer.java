/*
 * Copyright contributors to Hyperledger Besu.
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
 * method parameters like DebugOperationTracer. It tracks:
 *
 * <ul>
 *   <li>EVM operation counts (SLOAD, SSTORE, CALL, CREATE)
 *   <li>State access operations (account/storage/code reads/writes)
 *   <li>EIP-7702 delegation operations
 * </ul>
 *
 * <p>The metrics collected by this tracer can be aggregated across parallel transaction executions
 * and provide detailed insights into block execution performance.
 */
public class ExecutionMetricsTracer implements OperationTracer {

  /** Configuration for what metrics to collect. */
  public static class ExecutionMetricsConfig {
    private final boolean trackEvmOperations;
    private final boolean trackStateAccess;
    private final boolean trackEip7702Operations;

    /**
     * Create a new ExecutionMetricsConfig with the specified tracking options.
     *
     * @param trackEvmOperations whether to track EVM operation counts
     * @param trackStateAccess whether to track state access operations
     * @param trackEip7702Operations whether to track EIP-7702 delegation operations
     */
    public ExecutionMetricsConfig(
        final boolean trackEvmOperations,
        final boolean trackStateAccess,
        final boolean trackEip7702Operations) {
      this.trackEvmOperations = trackEvmOperations;
      this.trackStateAccess = trackStateAccess;
      this.trackEip7702Operations = trackEip7702Operations;
    }

    /**
     * Default configuration that tracks all metrics.
     *
     * @return a new ExecutionMetricsConfig that tracks all available metrics
     */
    public static ExecutionMetricsConfig createDefault() {
      return new ExecutionMetricsConfig(true, true, true);
    }

    /**
     * Check if EVM operation tracking is enabled.
     *
     * @return true if EVM operation tracking is enabled
     */
    public boolean isTrackEvmOperations() {
      return trackEvmOperations;
    }

    /**
     * Check if state access tracking is enabled.
     *
     * @return true if state access tracking is enabled
     */
    public boolean isTrackStateAccess() {
      return trackStateAccess;
    }

    /**
     * Check if EIP-7702 delegation tracking is enabled.
     *
     * @return true if EIP-7702 delegation tracking is enabled
     */
    public boolean isTrackEip7702Operations() {
      return trackEip7702Operations;
    }
  }

  /**
   * Container for execution metrics.
   *
   * <p>This class holds all collected execution metrics and provides getters for accessing the
   * various counters.
   */
  public static final class ExecutionMetrics {
    // EVM operation counters
    private int sloadCount;
    private int sstoreCount;
    private int callCount;
    private int createCount;

    // State read/write counters
    private int accountReads;
    private int storageReads;
    private int codeReads;
    private int codeBytesRead;
    private int accountWrites;
    private int storageWrites;
    private int codeWrites;
    private int codeBytesWritten;

    // EIP-7702 delegation counters
    private int eip7702DelegationsSet;
    private int eip7702DelegationsCleared;

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
      accountReads = 0;
      storageReads = 0;
      codeReads = 0;
      codeBytesRead = 0;
      accountWrites = 0;
      storageWrites = 0;
      codeWrites = 0;
      codeBytesWritten = 0;
      eip7702DelegationsSet = 0;
      eip7702DelegationsCleared = 0;
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
      this.accountReads += other.accountReads;
      this.storageReads += other.storageReads;
      this.codeReads += other.codeReads;
      this.codeBytesRead += other.codeBytesRead;
      this.accountWrites += other.accountWrites;
      this.storageWrites += other.storageWrites;
      this.codeWrites += other.codeWrites;
      this.codeBytesWritten += other.codeBytesWritten;
      this.eip7702DelegationsSet += other.eip7702DelegationsSet;
      this.eip7702DelegationsCleared += other.eip7702DelegationsCleared;
    }

    // Getters for all metrics
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

    /**
     * Returns the number of account read operations.
     *
     * @return the number of account read operations
     */
    public int getAccountReads() {
      return accountReads;
    }

    /**
     * Returns the number of storage read operations.
     *
     * @return the number of storage read operations
     */
    public int getStorageReads() {
      return storageReads;
    }

    /**
     * Returns the number of code read operations.
     *
     * @return the number of code read operations
     */
    public int getCodeReads() {
      return codeReads;
    }

    /**
     * Returns the total number of code bytes read.
     *
     * @return the total number of code bytes read
     */
    public int getCodeBytesRead() {
      return codeBytesRead;
    }

    /**
     * Returns the number of account write operations.
     *
     * @return the number of account write operations
     */
    public int getAccountWrites() {
      return accountWrites;
    }

    /**
     * Returns the number of storage write operations.
     *
     * @return the number of storage write operations
     */
    public int getStorageWrites() {
      return storageWrites;
    }

    /**
     * Returns the number of code write operations.
     *
     * @return the number of code write operations
     */
    public int getCodeWrites() {
      return codeWrites;
    }

    /**
     * Returns the total number of code bytes written.
     *
     * @return the total number of code bytes written
     */
    public int getCodeBytesWritten() {
      return codeBytesWritten;
    }

    /**
     * Returns the number of EIP-7702 delegations set.
     *
     * @return the number of EIP-7702 delegations set
     */
    public int getEip7702DelegationsSet() {
      return eip7702DelegationsSet;
    }

    /**
     * Returns the number of EIP-7702 delegations cleared.
     *
     * @return the number of EIP-7702 delegations cleared
     */
    public int getEip7702DelegationsCleared() {
      return eip7702DelegationsCleared;
    }
  }

  private final ExecutionMetricsConfig config;
  private final ExecutionMetrics metrics = new ExecutionMetrics();

  /** Create a new ExecutionMetricsTracer with the default configuration. */
  public ExecutionMetricsTracer() {
    this(ExecutionMetricsConfig.createDefault());
  }

  /**
   * Create a new ExecutionMetricsTracer with the specified configuration.
   *
   * @param config the configuration specifying which metrics to collect
   */
  public ExecutionMetricsTracer(final ExecutionMetricsConfig config) {
    this.config = config;
  }

  @Override
  public void tracePostExecution(final MessageFrame frame, final OperationResult operationResult) {
    if (!config.isTrackEvmOperations()) {
      return;
    }

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

  /** Track account read operation. Called directly from state operations. */
  public void onAccountRead() {
    if (config.isTrackStateAccess()) {
      metrics.accountReads++;
    }
  }

  /** Track storage read operation. Called directly from state operations. */
  public void onStorageRead() {
    if (config.isTrackStateAccess()) {
      metrics.storageReads++;
    }
  }

  /** Track code read operation. Called directly from state operations. */
  public void onCodeRead() {
    if (config.isTrackStateAccess()) {
      metrics.codeReads++;
    }
  }

  /**
   * Track code bytes read. Called directly from state operations.
   *
   * @param bytes the number of bytes read
   */
  public void onCodeBytesRead(final int bytes) {
    if (config.isTrackStateAccess()) {
      metrics.codeBytesRead += bytes;
    }
  }

  /** Track account write operation. Called directly from state operations. */
  public void onAccountWrite() {
    if (config.isTrackStateAccess()) {
      metrics.accountWrites++;
    }
  }

  /** Track storage write operation. Called directly from state operations. */
  public void onStorageWrite() {
    if (config.isTrackStateAccess()) {
      metrics.storageWrites++;
    }
  }

  /** Track code write operation. Called directly from state operations. */
  public void onCodeWrite() {
    if (config.isTrackStateAccess()) {
      metrics.codeWrites++;
    }
  }

  /**
   * Track code bytes written. Called directly from state operations.
   *
   * @param bytes the number of bytes written
   */
  public void onCodeBytesWritten(final int bytes) {
    if (config.isTrackStateAccess()) {
      metrics.codeBytesWritten += bytes;
    }
  }

  /** Track EIP-7702 delegation set operation. Called directly from delegation service. */
  public void onEip7702DelegationSet() {
    if (config.isTrackEip7702Operations()) {
      metrics.eip7702DelegationsSet++;
    }
  }

  /** Track EIP-7702 delegation cleared operation. Called directly from delegation service. */
  public void onEip7702DelegationCleared() {
    if (config.isTrackEip7702Operations()) {
      metrics.eip7702DelegationsCleared++;
    }
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
   * @param other the ExecutionMetricsTracer to merge metrics from
   */
  public void mergeFrom(final ExecutionMetricsTracer other) {
    this.metrics.merge(other.metrics);
  }
}

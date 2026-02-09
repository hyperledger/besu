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
public class ExecutionMetricsTracer implements OperationTracer {

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
    public int getSloadCount() {
      return sloadCount;
    }

    public int getSstoreCount() {
      return sstoreCount;
    }

    public int getCallCount() {
      return callCount;
    }

    public int getCreateCount() {
      return createCount;
    }

    public int getAccountReads() {
      return accountReads;
    }

    public int getStorageReads() {
      return storageReads;
    }

    public int getCodeReads() {
      return codeReads;
    }

    public int getCodeBytesRead() {
      return codeBytesRead;
    }

    public int getAccountWrites() {
      return accountWrites;
    }

    public int getStorageWrites() {
      return storageWrites;
    }

    public int getCodeWrites() {
      return codeWrites;
    }

    public int getCodeBytesWritten() {
      return codeBytesWritten;
    }

    public int getEip7702DelegationsSet() {
      return eip7702DelegationsSet;
    }

    public int getEip7702DelegationsCleared() {
      return eip7702DelegationsCleared;
    }
  }

  private final ExecutionMetrics metrics = new ExecutionMetrics();

  /** Create a new ExecutionMetricsTracer that tracks all available metrics. */
  public ExecutionMetricsTracer() {
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

  /** Track account read operation. Called directly from state operations. */
  public void onAccountRead() {
    metrics.accountReads++;
  }

  /** Track storage read operation. Called directly from state operations. */
  public void onStorageRead() {
    metrics.storageReads++;
  }

  /** Track code read operation. Called directly from state operations. */
  public void onCodeRead() {
    metrics.codeReads++;
  }

  /**
   * Track code bytes read. Called directly from state operations.
   *
   * @param bytes the number of bytes read
   */
  public void onCodeBytesRead(final int bytes) {
    metrics.codeBytesRead += bytes;
  }

  /** Track account write operation. Called directly from state operations. */
  public void onAccountWrite() {
    metrics.accountWrites++;
  }

  /** Track storage write operation. Called directly from state operations. */
  public void onStorageWrite() {
    metrics.storageWrites++;
  }

  /** Track code write operation. Called directly from state operations. */
  public void onCodeWrite() {
    metrics.codeWrites++;
  }

  /**
   * Track code bytes written. Called directly from state operations.
   *
   * @param bytes the number of bytes written
   */
  public void onCodeBytesWritten(final int bytes) {
    metrics.codeBytesWritten += bytes;
  }

  /** Track EIP-7702 delegation set operation. Called directly from delegation service. */
  public void onEip7702DelegationSet() {
    metrics.eip7702DelegationsSet++;
  }

  /** Track EIP-7702 delegation cleared operation. Called directly from delegation service. */
  public void onEip7702DelegationCleared() {
    metrics.eip7702DelegationsCleared++;
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

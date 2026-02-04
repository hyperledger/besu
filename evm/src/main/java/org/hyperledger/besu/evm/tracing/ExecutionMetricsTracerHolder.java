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

import java.util.Optional;

/**
 * Thread-local holder for ExecutionMetricsTracer to enable metrics collection during EVM execution.
 *
 * <p>This class provides a mechanism to pass ExecutionMetricsTracer through the execution stack 
 * without modifying method signatures. EVM operations and world state operations can access the 
 * current tracer to record execution metrics.
 * 
 * <p>This replaces the direct static counter calls in {@link org.hyperledger.besu.evm.EvmOperationCounters}
 * with a tracer-based approach that supports parallel transaction execution.
 */
public final class ExecutionMetricsTracerHolder {

  private static final ThreadLocal<ExecutionMetricsTracer> CURRENT = new ThreadLocal<>();

  private ExecutionMetricsTracerHolder() {
    // Utility class
  }

  /**
   * Sets the current ExecutionMetricsTracer for this thread.
   *
   * @param tracer the ExecutionMetricsTracer instance for the current execution context
   */
  public static void set(final ExecutionMetricsTracer tracer) {
    CURRENT.set(tracer);
  }

  /**
   * Gets the current ExecutionMetricsTracer for this thread.
   *
   * @return the current ExecutionMetricsTracer, or null if not set
   */
  public static ExecutionMetricsTracer get() {
    return CURRENT.get();
  }

  /**
   * Gets the current ExecutionMetricsTracer as an Optional.
   *
   * @return Optional containing the current ExecutionMetricsTracer, or empty if not set
   */
  public static Optional<ExecutionMetricsTracer> getOptional() {
    return Optional.ofNullable(CURRENT.get());
  }

  /** Clears the current ExecutionMetricsTracer for this thread. Should be called after execution. */
  public static void clear() {
    CURRENT.remove();
  }

  /**
   * Convenience method to track SLOAD operation if a tracer is present.
   */
  public static void trackSload() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      // SLOAD tracking is handled in tracePostExecution
      // This is a no-op since EVM operations are tracked via the OperationTracer interface
    }
  }

  /**
   * Convenience method to track SSTORE operation if a tracer is present.
   */
  public static void trackSstore() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      // SSTORE tracking is handled in tracePostExecution
      // This is a no-op since EVM operations are tracked via the OperationTracer interface
    }
  }

  /**
   * Convenience method to track CALL operation if a tracer is present.
   */
  public static void trackCall() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      // CALL tracking is handled in tracePostExecution
      // This is a no-op since EVM operations are tracked via the OperationTracer interface
    }
  }

  /**
   * Convenience method to track CREATE operation if a tracer is present.
   */
  public static void trackCreate() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      // CREATE tracking is handled in tracePostExecution
      // This is a no-op since EVM operations are tracked via the OperationTracer interface
    }
  }

  /**
   * Convenience method to track account read if a tracer is present.
   */
  public static void trackAccountRead() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackAccountRead();
    }
  }

  /**
   * Convenience method to track storage read if a tracer is present.
   */
  public static void trackStorageRead() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackStorageRead();
    }
  }

  /**
   * Convenience method to track code read if a tracer is present.
   */
  public static void trackCodeRead() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackCodeRead();
    }
  }

  /**
   * Convenience method to track code bytes read if a tracer is present.
   * 
   * @param bytes the number of bytes read
   */
  public static void trackCodeBytesRead(final int bytes) {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackCodeBytesRead(bytes);
    }
  }

  /**
   * Convenience method to track account write if a tracer is present.
   */
  public static void trackAccountWrite() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackAccountWrite();
    }
  }

  /**
   * Convenience method to track storage write if a tracer is present.
   */
  public static void trackStorageWrite() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackStorageWrite();
    }
  }

  /**
   * Convenience method to track code write if a tracer is present.
   */
  public static void trackCodeWrite() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackCodeWrite();
    }
  }

  /**
   * Convenience method to track code bytes written if a tracer is present.
   * 
   * @param bytes the number of bytes written
   */
  public static void trackCodeBytesWritten(final int bytes) {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackCodeBytesWritten(bytes);
    }
  }

  /**
   * Convenience method to track EIP-7702 delegation set if a tracer is present.
   */
  public static void trackEip7702DelegationSet() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackEip7702DelegationSet();
    }
  }

  /**
   * Convenience method to track EIP-7702 delegation cleared if a tracer is present.
   */
  public static void trackEip7702DelegationCleared() {
    final ExecutionMetricsTracer tracer = get();
    if (tracer != null) {
      tracer.trackEip7702DelegationCleared();
    }
  }
}
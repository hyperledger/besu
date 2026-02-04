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

import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;

/**
 * Helper utility class for accessing ExecutionMetricsTracer from MessageFrame context.
 *
 * <p>This provides a standard way for state operations to access the ExecutionMetricsTracer that is
 * passed through the transaction execution context via MessageFrame context variables.
 */
public final class ExecutionMetricsTracerHelper {

  private static final String EXECUTION_METRICS_TRACER_KEY = "executionMetricsTracer";

  private ExecutionMetricsTracerHelper() {
    // Utility class
  }

  /**
   * Extract ExecutionMetricsTracer from MessageFrame context variables.
   *
   * @param frame the MessageFrame to extract from
   * @return the ExecutionMetricsTracer if available
   */
  public static Optional<ExecutionMetricsTracer> extractFromMessageFrame(final MessageFrame frame) {
    if (frame == null) {
      return Optional.empty();
    }

    if (!frame.hasContextVariable(EXECUTION_METRICS_TRACER_KEY)) {
      return Optional.empty();
    }

    return Optional.ofNullable(frame.getContextVariable(EXECUTION_METRICS_TRACER_KEY));
  }

  /**
   * Track account read operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onAccountRead(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onAccountRead);
  }

  /**
   * Track storage read operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onStorageRead(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onStorageRead);
  }

  /**
   * Track code read operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onCodeRead(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onCodeRead);
  }

  /**
   * Track code bytes read if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   * @param bytes the number of bytes read
   */
  public static void onCodeBytesRead(final MessageFrame frame, final int bytes) {
    extractFromMessageFrame(frame).ifPresent(tracer -> tracer.onCodeBytesRead(bytes));
  }

  /**
   * Track account write operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onAccountWrite(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onAccountWrite);
  }

  /**
   * Track storage write operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onStorageWrite(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onStorageWrite);
  }

  /**
   * Track code write operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onCodeWrite(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onCodeWrite);
  }

  /**
   * Track code bytes written if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   * @param bytes the number of bytes written
   */
  public static void onCodeBytesWritten(final MessageFrame frame, final int bytes) {
    extractFromMessageFrame(frame).ifPresent(tracer -> tracer.onCodeBytesWritten(bytes));
  }

  /**
   * Track EIP-7702 delegation set operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onEip7702DelegationSet(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onEip7702DelegationSet);
  }

  /**
   * Track EIP-7702 delegation cleared operation if ExecutionMetricsTracer is available.
   *
   * @param frame the MessageFrame context
   */
  public static void onEip7702DelegationCleared(final MessageFrame frame) {
    extractFromMessageFrame(frame).ifPresent(ExecutionMetricsTracer::onEip7702DelegationCleared);
  }

  /**
   * Get the context key used to store ExecutionMetricsTracer in context variables.
   *
   * @return the context key
   */
  public static String getContextKey() {
    return EXECUTION_METRICS_TRACER_KEY;
  }
}

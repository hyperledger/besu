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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace.OpcodeCategory.isCreateOp;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.math.BigInteger;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates gas usage for call tracing operations.
 *
 * <p>This class encapsulates all gas-related calculations needed to produce Geth-compatible call
 * traces. It handles the complex gas accounting for different operation types including regular
 * calls, precompiles, creates, and self-destructs.
 *
 * <p>Key calculations include:
 *
 * <ul>
 *   <li>Gas provided to child calls (63/64 rule per EIP-150)
 *   <li>Gas consumed by operations
 *   <li>Code deposit costs for contract creation
 *   <li>Precompile gas calculations
 * </ul>
 */
public final class CallTracerGasCalculator {
  private static final Logger LOG = LoggerFactory.getLogger(CallTracerGasCalculator.class);

  // Gas calculation constants
  /** Warm storage access cost per EIP-2929 */
  private static final long WARM_ACCESS_GAS = 100L;

  /** Cost per byte for storing contract code */
  private static final long CODE_DEPOSIT_GAS_PER_BYTE = 200L;

  /** Default SELFDESTRUCT operation cost */
  private static final long DEFAULT_SELFDESTRUCT_COST = 5000L;

  /**
   * EIP-150 gas stipend divisor. Child calls receive at most 63/64 of available gas. This is
   * calculated as: gas - (gas / 64)
   */
  private static final long GAS_CALL_STIPEND_DIVISOR = 64L;

  private CallTracerGasCalculator() {
    // Utility class - prevent instantiation
  }

  /**
   * Calculates the gas provided to a child call.
   *
   * <p>This implements the EIP-150 rule where child calls receive at most 63/64 of available gas.
   * The calculation considers:
   *
   * <ul>
   *   <li>If the call entered (nextTrace depth > frame depth), use the callee's starting gas
   *   <li>For precompiles, return 0 (handled separately)
   *   <li>For soft failures, use the gas that would have been provided
   *   <li>Fallback to 63/64 calculation if no direct information available
   * </ul>
   *
   * @param frame the trace frame of the call operation
   * @param nextTrace the next trace frame (callee's first frame), may be null
   * @return the amount of gas provided to the child call
   */
  public static long computeGasProvided(final TraceFrame frame, final TraceFrame nextTrace) {
    boolean hasCalleeStart = nextTrace != null && nextTrace.getDepth() == frame.getDepth() + 1;

    if (hasCalleeStart) {
      return Math.max(0L, nextTrace.getGasRemaining());
    }

    // Special case for precompiles - gas is calculated differently
    if (frame.isPrecompile()) {
      return 0L;
    }

    // For non-entered calls (soft failures), use the gas that would have been
    // provided to the child, which is now directly available in the TraceFrame
    if (frame.getGasAvailableForChildCall().isPresent()) {
      return frame.getGasAvailableForChildCall().getAsLong();
    }

    // Fallback calculation if gasAvailableForChildCall is not available
    // (for older traces or other edge cases)
    return calculateFallbackChildGas(frame);
  }

  /**
   * Calculates gas for precompile calls using Geth-style calculation.
   *
   * <p>The calculation follows Geth's approach:
   *
   * <ol>
   *   <li>Start with gas remaining after operation
   *   <li>Subtract warm access cost if applicable
   *   <li>Apply 63/64 rule
   * </ol>
   *
   * @param entryFrame the trace frame containing precompile information
   * @return the gas allocated for the precompile execution
   */
  public static long calculatePrecompileGas(final TraceFrame entryFrame) {
    final long post = Math.max(0L, entryFrame.getGasRemaining());
    final long base = post > WARM_ACCESS_GAS ? post - WARM_ACCESS_GAS : 0L;
    return base - (base / GAS_CALL_STIPEND_DIVISOR);
  }

  /**
   * Calculates the gas used by a precompile call.
   *
   * @param entryFrame the trace frame containing precompile information
   * @param hasFailed whether the precompile execution failed
   * @param gasAllocated the gas that was allocated for the precompile
   * @return the gas consumed by the precompile
   */
  public static long calculatePrecompileGasUsed(
      final TraceFrame entryFrame, final boolean hasFailed, final long gasAllocated) {

    if (hasFailed) {
      // Failed precompiles consume all allocated gas
      return gasAllocated;
    }

    return entryFrame.getPrecompiledGasCost().orElseGet(() -> entryFrame.getGasCost().orElse(0L));
  }

  /**
   * Calculates the gas used by a child call that returned normally (RETURN, REVERT, STOP).
   *
   * <p>This method handles:
   *
   * <ul>
   *   <li>Root transaction gas accounting
   *   <li>SELFDESTRUCT special case (adds operation cost)
   *   <li>Code deposit costs for successful CREATE operations
   * </ul>
   *
   * @param gasProvided the gas that was provided to the child call
   * @param callType the type of the call (e.g., "CREATE", "CALL")
   * @param entryFrame the frame when the call was initiated
   * @param exitFrame the frame when the call returned
   * @return the gas consumed by the child call
   */
  public static long calculateGasUsed(
      final BigInteger gasProvided,
      final String callType,
      final TraceFrame entryFrame,
      final TraceFrame exitFrame) {

    // Root transaction special case
    if (exitFrame.getDepth() == 0) {
      return Math.max(
          0, entryFrame.getGasRemaining() - exitFrame.getGasRemaining() - exitFrame.getGasRefund());
    }

    long gasProvidedLong = gasProvided.longValue();
    long baseGasUsed = calculateBaseGasUsed(exitFrame, entryFrame, gasProvidedLong);

    // Add code deposit cost for successful CREATE
    if (shouldAddCodeDepositCost(callType, exitFrame)) {
      baseGasUsed += exitFrame.getOutputData().size() * CODE_DEPOSIT_GAS_PER_BYTE;
    }

    return baseGasUsed;
  }

  /**
   * Calculates the gas used when a call fails implicitly (exceptional halt without explicit
   * return).
   *
   * @param lastFrame the last frame executed in the failed context
   * @param gasProvided the gas that was provided to the call
   * @return the gas consumed
   */
  public static long calculateImplicitReturnGasUsed(
      final TraceFrame lastFrame, final BigInteger gasProvided) {

    long gasProvidedLong = gasProvided.longValue();

    // All gas consumed for insufficient gas errors
    if (isInsufficientGasError(lastFrame)) {
      return gasProvidedLong;
    }

    // Special handling for SELFDESTRUCT
    if (OpcodeCategory.isSelfDestructOp(lastFrame.getOpcode())) {
      long selfDestructCost = lastFrame.getGasCost().orElse(DEFAULT_SELFDESTRUCT_COST);
      return gasProvidedLong - lastFrame.getGasRemaining() + selfDestructCost;
    }

    // Normal calculation: gas provided minus gas remaining
    return Math.max(0, gasProvidedLong - lastFrame.getGasRemaining());
  }

  /**
   * Calculates gas used for a hard failure (exceptional halt that prevents call entry).
   *
   * @param frame the frame where the failure occurred
   * @return the gas consumed, typically 0 unless it's an INSUFFICIENT_GAS error
   */
  public static long calculateHardFailureGasUsed(final TraceFrame frame) {
    if (isInsufficientGasError(frame)) {
      return frame.getGasCost().orElse(0L);
    }
    return 0L;
  }

  /**
   * Checks if a frame has an insufficient gas error.
   *
   * @param frame the trace frame to check
   * @return true if the frame has an INSUFFICIENT_GAS exceptional halt reason
   */
  public static boolean isInsufficientGasError(final TraceFrame frame) {
    return frame
        .getExceptionalHaltReason()
        .filter(r -> Objects.equals(r, ExceptionalHaltReason.INSUFFICIENT_GAS))
        .isPresent();
  }

  /**
   * Determines if code deposit cost should be added for a CREATE operation.
   *
   * @param callType the type of call
   * @param exitFrame the frame when the call returned
   * @return true if code deposit cost should be added
   */
  static boolean shouldAddCodeDepositCost(final String callType, final TraceFrame exitFrame) {
    return isCreateOp(callType)
        && "RETURN".equals(exitFrame.getOpcode()) // Must be successful return, not REVERT
        && exitFrame.getOutputData() != null
        && !exitFrame.getOutputData().isEmpty()
        && exitFrame.getExceptionalHaltReason().isEmpty();
  }

  private static long calculateBaseGasUsed(
      final TraceFrame exitFrame, final TraceFrame entryFrame, final long gasProvided) {

    // SELFDESTRUCT adds its operation cost to the gas used
    if (OpcodeCategory.isSelfDestructOp(exitFrame.getOpcode())) {
      long selfDestructCost = exitFrame.getGasCost().orElse(DEFAULT_SELFDESTRUCT_COST);
      return gasProvided - exitFrame.getGasRemaining() + selfDestructCost;
    }

    // Normal case: gas provided minus gas remaining
    if (exitFrame.getGasRemaining() >= 0) {
      return gasProvided - exitFrame.getGasRemaining();
    }

    // Fallback for edge cases (shouldn't normally happen)
    if (entryFrame.getPrecompiledGasCost().isPresent()) {
      return entryFrame.getPrecompiledGasCost().getAsLong();
    }

    return entryFrame.getGasCost().orElse(0L);
  }

  private static long calculateFallbackChildGas(final TraceFrame frame) {
    long gasAfter = frame.getGasRemainingPostExecution();
    long childGas = gasAfter - (gasAfter / GAS_CALL_STIPEND_DIVISOR);

    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Fallback gas calculation: gasAfter={} (0x{}), calculated childGas={} (0x{})",
          gasAfter,
          Long.toHexString(gasAfter),
          childGas,
          Long.toHexString(childGas));
    }

    return Math.max(0L, childGas);
  }
}

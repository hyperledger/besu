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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace.OpcodeCategory;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;

/**
 * Converts Ethereum transaction traces into 4byte tracer format.
 *
 * <p>This class transforms transaction traces to collect function selectors (the first 4 bytes of
 * call data) from all internal calls made during transaction execution, along with the size of the
 * remaining call data (excluding the 4-byte selector). This matches Geth's 4byteTracer output
 * format.
 *
 * <p>The converter processes only CALL-type operations that successfully enter:
 *
 * <ul>
 *   <li>CALL
 *   <li>CALLCODE
 *   <li>DELEGATECALL
 *   <li>STATICCALL
 * </ul>
 *
 * <p>CREATE and CREATE2 operations are excluded, as are calls to precompiled contracts and calls
 * that fail to enter.
 *
 * <p>For each qualifying call operation, it extracts the first 4 bytes of the input data (the
 * function selector) and combines it with the size of the remaining call data (size - 4) to create
 * a key in the format "0x[4-byte-selector]-[remaining-size]". The result is a map of these keys to
 * their occurrence counts.
 *
 * @see <a href="https://github.com/ethereum/go-ethereum/blob/master/eth/tracers/native/4byte.go">
 *     Geth 4byteTracer Implementation</a>
 */
public final class FourByteTracerResultConverter {

  private static final int FUNCTION_SELECTOR_LENGTH = 4;

  private FourByteTracerResultConverter() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts a transaction trace to a 4byte tracer result.
   *
   * @param transactionTrace The transaction trace to convert
   * @param protocolSpec Protocol Spec instance related to the transaction's block
   * @return A 4byte tracer result containing function selector counts
   * @throws NullPointerException if transactionTrace is null
   */
  public static FourByteTracerResult convert(
      final TransactionTrace transactionTrace, final ProtocolSpec protocolSpec) {
    checkNotNull(
        transactionTrace, "FourByteTracerResultConverter requires a non-null TransactionTrace");
    checkNotNull(protocolSpec, "FourByteTracerResultConverter requires a non-null ProtocolSpec");

    // Sort keys alphabetically to match Geth's JSON encoding behavior
    final Map<String, Integer> selectorCounts = new TreeMap<>();

    processInitialTransaction(transactionTrace, protocolSpec, selectorCounts);

    // Process all trace frames for internal calls only (not the initial transaction)
    if (transactionTrace.getTraceFrames() != null) {
      processTraceFrames(transactionTrace.getTraceFrames(), selectorCounts);
    }

    return new FourByteTracerResult(selectorCounts);
  }

  /**
   * Processes the initial transaction to extract its function selector.
   *
   * <p>This matches Geth's behavior where OnEnter fires for the transaction's entry into the
   * contract, not just for internal calls.
   *
   * @param transactionTrace the transaction trace
   * @param protocolSpec Protocol Spec instance to obtain precompile registry
   * @param selectorCounts the map to update with selector counts
   */
  private static void processInitialTransaction(
      final TransactionTrace transactionTrace,
      final ProtocolSpec protocolSpec,
      final Map<String, Integer> selectorCounts) {

    final Transaction tx = transactionTrace.getTransaction();

    // Skip scenarios
    if (tx.isContractCreation()
        || tx.getTo().isEmpty()
        || isTargetPrecompiledContract(protocolSpec, tx.getTo().get())) {
      return;
    }

    final Bytes inputData = tx.getPayload();

    // Only process if we have at least 4 bytes for the function selector
    if (inputData != null && inputData.size() >= FUNCTION_SELECTOR_LENGTH) {
      final String key = createKey(inputData);
      selectorCounts.merge(key, 1, Integer::sum);
    }
  }

  private static boolean isTargetPrecompiledContract(
      final ProtocolSpec protocolSpec, final Address toAddress) {
    return protocolSpec.getPrecompileContractRegistry().get(toAddress) != null;
  }

  /**
   * Processes all trace frames to extract function selectors from CALL operations.
   *
   * <p>This method only processes CALL, CALLCODE, DELEGATECALL, and STATICCALL operations that
   * successfully enter a new execution scope. CREATE and CREATE2 operations are ignored to match
   * Geth's behavior.
   *
   * @param frames the list of trace frames to process
   * @param selectorCounts the map to update with selector counts
   */
  private static void processTraceFrames(
      final List<TraceFrame> frames, final Map<String, Integer> selectorCounts) {

    for (int i = 0; i < frames.size(); i++) {
      final TraceFrame frame = frames.get(i);
      final String opcode = frame.getOpcode();

      // Only process CALL-type operations (not CREATE/CREATE2)
      if (OpcodeCategory.isCallOp(opcode)) {
        final TraceFrame nextTrace = (i < frames.size() - 1) ? frames.get(i + 1) : null;
        processCall(frame, nextTrace, selectorCounts);
      }
    }
  }

  /**
   * Processes a single CALL operation to extract its function selector.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Checks if the call actually entered (depth increased in next frame)
   *   <li>Skips calls to precompiled contracts
   *   <li>Extracts the input data from the entered call
   *   <li>Skips calls with less than 4 bytes of input (no function selector)
   *   <li>Creates a key in the format "0x[selector]-[size-4]" and increments its count
   * </ul>
   *
   * <p>This matches Geth's OnEnter hook behavior which only fires when a call successfully enters a
   * new execution scope.
   *
   * @param frame the current trace frame (the CALL instruction)
   * @param nextTrace the next trace frame (first frame inside the call, may be null)
   * @param selectorCounts the map to update with selector counts
   */
  private static void processCall(
      final TraceFrame frame,
      final TraceFrame nextTrace,
      final Map<String, Integer> selectorCounts) {

    // Skip precompiled contracts
    if (frame.isPrecompile()) {
      return;
    }

    // Check if call entered (depth increased) - matches Geth's OnEnter behavior
    final boolean calleeEntered = nextTrace != null && nextTrace.getDepth() > frame.getDepth();
    if (!calleeEntered) {
      return;
    }

    // Get the input data from the entered call (nextTrace has the actual input data)
    final Bytes inputData = nextTrace.getInputData();

    // Only process if we have at least 4 bytes for the function selector
    if (inputData != null && inputData.size() >= FUNCTION_SELECTOR_LENGTH) {
      final String key = createKey(inputData);
      selectorCounts.merge(key, 1, Integer::sum);
    }
  }

  /**
   * Creates a key in the format "0x[4-byte-selector]-[size-4]" from the input data.
   *
   * <p>The size portion represents the length of the input data minus the 4-byte selector, matching
   * Geth's implementation where the size is calculated as `len(input) - 4`.
   *
   * @param inputData the input data containing the function selector
   * @return the formatted key (e.g., "0x27dc297e-128")
   */
  private static String createKey(final Bytes inputData) {
    final Bytes selector = inputData.slice(0, FUNCTION_SELECTOR_LENGTH);
    final int remainingSize = inputData.size() - FUNCTION_SELECTOR_LENGTH;
    return selector.toHexString() + "-" + remainingSize;
  }
}

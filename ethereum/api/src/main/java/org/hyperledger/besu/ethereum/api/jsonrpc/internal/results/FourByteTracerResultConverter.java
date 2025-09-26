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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts Ethereum transaction traces into 4byte tracer format.
 *
 * <p>The 4byteTracer collects the function selectors of every function executed in the lifetime of
 * a transaction, along with the size of the supplied call data. The result is a map where the keys
 * are SELECTOR-PARAMETERDATASIZE and the values are number of occurrences of this key.
 *
 * <p>Function selectors are the first 4 bytes of the Keccak-256 hash of function signatures, which
 * are used to identify which function to call in Solidity contracts.
 */
public class FourByteTracerResultConverter {
  private static final Logger LOG = LoggerFactory.getLogger(FourByteTracerResultConverter.class);


  /**
   * Converts a transaction trace to a 4byte tracer result.
   *
   * @param transactionTrace The transaction trace to convert
   * @return A 4byte tracer result containing function selectors and their occurrence counts
   * @throws NullPointerException if transactionTrace or its components are null
   */
  public static FourByteTracerResult convert(final TransactionTrace transactionTrace) {
    checkNotNull(
        transactionTrace, "FourByteTracerResultConverter requires a non-null TransactionTrace");
    checkNotNull(
        transactionTrace.getTransaction(),
        "FourByteTracerResultConverter requires non-null Transaction");
    checkNotNull(
        transactionTrace.getResult(),
        "FourByteTracerResultConverter requires non-null Result");

    final Map<String, Integer> selectorCounts = new HashMap<>();
    final Transaction transaction = transactionTrace.getTransaction();
    final List<TraceFrame> traceFrames = transactionTrace.getTraceFrames();

    // Process the initial transaction call data only for message-call transactions
    if (transaction.getTo().isPresent()) {
      processCallData(transaction.getPayload(), selectorCounts);
    }

    // Process all trace frames for additional function calls
    if (traceFrames != null) {
      LOG.trace("Processing {} trace frames for 4byte tracer", traceFrames.size());
      for (final TraceFrame frame : traceFrames) {
        if (shouldProcessFrame(frame)) {
          final Bytes inputData = frame.getInputData();
          if (inputData != null) {
            processCallData(inputData, selectorCounts);
          }
        }
      }
    }

    LOG.trace("4byte tracer found {} unique function selectors", selectorCounts.size());
    return new FourByteTracerResult(selectorCounts);
  }

  /**
   * Processes call data to extract function selector and update counts.
   *
   * Stores the 4-byte function selector along with the size
   * of the call data minus the 4-byte selector (i.e., the actual parameter data size).
   *
   * @param callData The call data to process
   * @param selectorCounts The map to update with selector counts
   */
  private static void processCallData(final Bytes callData, final Map<String, Integer> selectorCounts) {
    if (callData == null || callData.size() < 4) {
      // Not enough data for a function selector
      return;
    }

    // Extract the first 4 bytes as the function selector
    final Bytes selector = callData.slice(0, 4);
    final String selectorHex = selector.toHexString();
    
    // Use len(input)-4 (parameter data size, excluding selector)
    final int parameterDataSize = callData.size() - 4;
    
    // Create the key in the format "selector-parameterdatasize" 
    final String key = selectorHex + "-" + parameterDataSize;
    
    // Update the count
    selectorCounts.put(key, selectorCounts.getOrDefault(key, 0) + 1);
    
    LOG.trace("4byte tracer: selector {} with parameter data size {} (key: {})", 
              selectorHex, parameterDataSize, key);
  }

  /**
   * Determines if a trace frame should be processed for 4byte analysis.
   * 
   * Only process CALL, CALLCODE, DELEGATECALL, and STATICCALL operations,
   * excluding CREATE/CREATE2 and precompiled contracts.
   *
   * @param frame The trace frame to check
   * @return true if the frame should be processed, false otherwise
   */
  private static boolean shouldProcessFrame(final TraceFrame frame) {
    final String opcode = frame.getOpcode();
    
    // Only process specific call operations 
    if (!isRelevantCallOperation(opcode)) {
      return false;
    }
    
    // Skip precompiled contracts (portable across forks/chains)
    if (frame.isPrecompile()) {
      LOG.trace("Skipping precompiled contract call");
      return false;
    }
    
    // Ensure we have enough input data for a function selector
    final Bytes inputData = frame.getInputData();
    return inputData != null && inputData.size() >= 4;
  }

  /**
   * Checks if the given opcode represents a relevant call operation for 4byte tracing.
   * 
   * Only CALL, CALLCODE, DELEGATECALL, and STATICCALL
   * are relevant. CREATE and CREATE2 are explicitly excluded.
   *
   * @param opcode The opcode to check
   * @return true if the opcode is a relevant call operation, false otherwise
   */
  private static boolean isRelevantCallOperation(final String opcode) {
    return "CALL".equals(opcode) ||
           "CALLCODE".equals(opcode) ||
           "DELEGATECALL".equals(opcode) ||
           "STATICCALL".equals(opcode);
    // Note: CREATE and CREATE2 are intentionally excluded 
  }
}


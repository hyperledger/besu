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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugStructLoggerTracerResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugTraceTransactionResult;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonGetter;

/**
 * Factory for creating transaction steps for various tracers.
 *
 * <p>This factory provides methods to create functions that process a {@link TransactionTrace} and
 * return a {@link DebugTraceTransactionResult} with the appropriate tracer result based on the
 * specified tracer type. Both synchronous and asynchronous processing options are available through
 * the {@code create} and {@code createAsync} methods respectively.
 */
public class DebugTraceTransactionStepFactory {

  /**
   * Creates a function that processes a {@link TransactionTrace} and returns a {@link
   * DebugTraceTransactionResult} with the appropriate tracer result based on the specified tracer
   * type.
   *
   * @param tracerType the type of tracer to use for processing the transaction trace
   * @return a function that processes a {@link TransactionTrace} and returns a {@link
   *     DebugTraceTransactionResult} with the appropriate tracer result
   */
  public static Function<TransactionTrace, DebugTraceTransactionResult> create(
      final String tracerType) {
    // Normalize null values to empty string
    final String normalizedTracerType = tracerType == null ? "" : tracerType;
    return switch (normalizedTracerType) {
      case "" ->
          transactionTrace -> {
            // default - struct/opcode logger tracer
            var result = new DebugStructLoggerTracerResult(transactionTrace);
            return new DebugTraceTransactionResult(transactionTrace, result);
          };
      case "callTracer" ->
          transactionTrace -> {
            // TODO: Implement callTracer logic and wire it here
            var result = new NotYetImplemented();
            return new DebugTraceTransactionResult(transactionTrace, result);
          };
      case "flatCallTracer" ->
          transactionTrace -> {
            // TODO: Implement flatCallTracer logic and wire it here
            var result = new NotYetImplemented();
            return new DebugTraceTransactionResult(transactionTrace, result);
          };
      case "prestateTracer" ->
          transactionTrace -> {
            // TODO: Implement prestateTracer logic and wire it here
            var result = new NotYetImplemented();
            return new DebugTraceTransactionResult(transactionTrace, result);
          };
      // Possible feature: Load via "plugin" system, and then raise an error if not found
      default ->
          transactionTrace ->
              new DebugTraceTransactionResult(transactionTrace, new NotYetImplemented());
    };
  }

  /**
   * Creates an asynchronous function that processes a {@link TransactionTrace} and returns a {@link
   * DebugTraceTransactionResult} with the appropriate tracer result based on the specified tracer
   * type.
   *
   * @param tracerType the type of tracer to use for processing the transaction trace
   * @return an asynchronous function that processes a {@link TransactionTrace} and returns a {@link
   *     DebugTraceTransactionResult} with the appropriate tracer result
   */
  public static Function<TransactionTrace, CompletableFuture<DebugTraceTransactionResult>>
      createAsync(final String tracerType) {
    return transactionTrace ->
        CompletableFuture.supplyAsync(() -> create(tracerType).apply(transactionTrace));
  }

  public static class NotYetImplemented {
    @JsonGetter("error")
    public String getError() {
      return "Not Yet Implemented";
    }
  }
}

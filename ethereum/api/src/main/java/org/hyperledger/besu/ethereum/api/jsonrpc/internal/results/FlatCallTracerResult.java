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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.debug.TraceFrame;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonGetter;

public class FlatCallTracerResult {
  // CALL, CALLCODE, DELEGATECALL, STATICCALL
  private static final Set<Integer> CALL_OPCODES = Set.of(0xF1, 0xF2, 0xF4, 0xFA);

  // CREATE, CREATE2
  private static final Set<Integer> CREATE_OPCODES = Set.of(0xF0, 0xF5);

  private final List<Trace> callTraces;

  private FlatCallTracerResult(final List<Trace> callTraces) {
    this.callTraces = callTraces;
  }

  @JsonGetter
  public List<Trace> getCalls() {
    return callTraces;
  }

  public static FlatCallTracerResult from(final TransactionTrace transactionTrace) {
    return new FlatCallTracerResult(generateFlatCallTrace(transactionTrace));
  }

  private static List<Trace> generateFlatCallTrace(final TransactionTrace transactionTrace) {
    final List<TraceFrame> onlyCallTraces =
        transactionTrace.getTraceFrames().stream()
            .filter(
                traceFrame ->
                    isCall(traceFrame.getOpcodeNumber()) || isCreate(traceFrame.getOpcodeNumber()))
            .toList();
    final TransactionTrace filteredTransactionTrace =
        new TransactionTrace(
            transactionTrace.getTransaction(),
            transactionTrace.getResult(),
            onlyCallTraces,
            transactionTrace.getBlock());

    return FlatTraceGenerator.generateFromTransactionTraceAndBlock(
            null, filteredTransactionTrace, transactionTrace.getBlock().orElse(null))
        .toList();
  }

  private static boolean isCall(final int opcode) {
    return CALL_OPCODES.contains(opcode);
  }

  private static boolean isCreate(final int opcode) {
    return CREATE_OPCODES.contains(opcode);
  }
}

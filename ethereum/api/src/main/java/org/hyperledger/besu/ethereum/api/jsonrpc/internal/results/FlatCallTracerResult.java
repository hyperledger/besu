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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonGetter;

public class FlatCallTracerResult {
  // CALL, CALLCODE, DELEGATECALL, STATICCALL
  private static final Set<String> CALL_OPCODES =
      Set.of("call", "callcode", "delegatecall", "staticcall");

  // CREATE, CREATE2
  private static final Set<String> CREATE_OPCODES = Set.of("create", "create2");

  private final List<FlatTrace> callTraces;

  private FlatCallTracerResult(final List<FlatTrace> callTraces) {
    this.callTraces = callTraces;
  }

  @JsonGetter
  public List<FlatTrace> getCalls() {
    return callTraces;
  }

  public static FlatCallTracerResult from(final TransactionTrace transactionTrace) {
    return new FlatCallTracerResult(generateFlatCallTrace(transactionTrace));
  }

  private static List<FlatTrace> generateFlatCallTrace(final TransactionTrace transactionTrace) {
    final Stream<FlatTrace> allFlatTraces =
        FlatTraceGenerator.generateFromTransactionTraceAndBlock(
                null, transactionTrace, transactionTrace.getBlock().orElse(null))
            .map(FlatTrace.class::cast);

    return allFlatTraces
        .filter(
            trace ->
                isCall(trace.getAction().getCallType())
                    || isCreate(trace.getAction().getCallType()))
        .toList();
  }

  private static boolean isCall(final String callType) {
    return CALL_OPCODES.contains(callType);
  }

  private static boolean isCreate(final String callType) {
    return CREATE_OPCODES.contains(callType);
  }
}

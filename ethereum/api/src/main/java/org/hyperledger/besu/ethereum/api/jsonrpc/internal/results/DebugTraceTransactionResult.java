/*
 * Copyright ConsenSys AG.
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
import org.hyperledger.besu.ethereum.debug.TraceFrame;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"gas", "failed", "returnValue", "structLogs"})
public class DebugTraceTransactionResult {

  private final List<StructLog> structLogs;
  private final String returnValue;
  private final long gas;
  private final boolean failed;

  public DebugTraceTransactionResult(final TransactionTrace transactionTrace) {
    gas = transactionTrace.getGas();
    returnValue = transactionTrace.getResult().getOutput().toString().substring(2);
    structLogs =
        transactionTrace.getTraceFrames().stream()
            .map(DebugTraceTransactionResult::createStructLog)
            .collect(Collectors.toList());
    failed = !transactionTrace.getResult().isSuccessful();
  }

  public static Collection<DebugTraceTransactionResult> of(
      final Collection<TransactionTrace> traces) {
    return traces.stream().map(DebugTraceTransactionResult::new).collect(Collectors.toList());
  }

  private static StructLog createStructLog(final TraceFrame frame) {
    return frame
        .getExceptionalHaltReason()
        .map(__ -> (StructLog) new StructLogWithError(frame))
        .orElse(new StructLog(frame));
  }

  @JsonGetter(value = "structLogs")
  public List<StructLog> getStructLogs() {
    return structLogs;
  }

  @JsonGetter(value = "returnValue")
  public String getReturnValue() {
    return returnValue;
  }

  @JsonGetter(value = "gas")
  public long getGas() {
    return gas;
  }

  @JsonGetter(value = "failed")
  public boolean failed() {
    return failed;
  }
}

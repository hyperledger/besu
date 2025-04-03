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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.StructLog.toCompactHex;

@JsonPropertyOrder({
  "type",
  "from",
  "to",
  "value",
  "gas",
  "gasUsed",
  "input",
  "output",
  "error",
  "revertReason"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DebugCallTracerResult implements DebugTracerResult {
  private String type;
  private String from;
  private String to;
  private String value;
  private String gas;
  private String gasUsed;
  private String input;
  private String output;
  private String error;
  private String revertReason;

  public DebugCallTracerResult(final TransactionTrace transactionTrace) {
    transactionTrace.getTraceFrames().stream().findFirst().ifPresent(traceFrame -> {
        type = traceFrame.getOpcode();
        revertReason  = traceFrame.getRevertReason().map(bytes -> toCompactHex(bytes, true)).orElse(null);

    });
  }

  @JsonGetter("type")
  public String getType() {
    return type;
  }

  @JsonGetter("from")
  public String getFrom() {
    return from;
  }

  @JsonGetter("to")
  public String getTo() {
    return to;
  }

  @JsonGetter("value")
  public String getValue() {
    return value;
  }

  @JsonGetter("gas")
  public String getGas() {
    return gas;
  }

  @JsonGetter("gasUsed")
  public String getGasUsed() {
    return gasUsed;
  }

  @JsonGetter("input")
  public String getInput() {
    return input;
  }

  @JsonGetter("output")
  public String getOutput() {
    return output;
  }

  @JsonGetter("error")
  public String getError() {
    return error;
  }

  @JsonGetter("revertReason")
  public String getRevertReason() {
    return revertReason;
  }
}

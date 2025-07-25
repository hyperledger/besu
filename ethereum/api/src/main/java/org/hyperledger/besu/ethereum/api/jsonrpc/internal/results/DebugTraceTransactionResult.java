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

import java.util.Collection;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"txHash", "result"})
public class DebugTraceTransactionResult {

  private final String txHash;
  private final Object result;

  public DebugTraceTransactionResult(final TransactionTrace transactionTrace, final Object result) {
    this.txHash = transactionTrace.getTransaction().getHash().toHexString();
    this.result = result;
  }

  public static Collection<DebugTraceTransactionResult> of(
      final Collection<TransactionTrace> traces,
      final Function<TransactionTrace, Object> constructor) {
    return traces.stream()
        .map(trace -> new DebugTraceTransactionResult(trace, constructor.apply(trace)))
        .toList();
  }

  @JsonGetter(value = "txHash")
  public String getTxHash() {
    return txHash;
  }

  @JsonGetter(value = "result")
  public Object getResult() {
    return result;
  }
}

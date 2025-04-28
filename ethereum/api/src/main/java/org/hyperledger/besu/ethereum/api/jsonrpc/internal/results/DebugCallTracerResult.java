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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.evm.operation.ReturnOperation;
import org.hyperledger.besu.evm.operation.RevertOperation;

import java.util.Optional;
import java.util.OptionalLong;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

@JsonPropertyOrder({
  "from",
  "gas",
  "gasUsed",
  "to",
  "input",
  "output",
  "error",
  "revertReason",
  "value",
  "type"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DebugCallTracerResult implements DebugTracerResult {
  private final String type;
  private final String from;
  private final String to;
  private final String value;
  private String gas;
  private String gasUsed;
  private final String input;
  private String output;
  private String error;
  private String revertReason;

  public DebugCallTracerResult(final TransactionTrace transactionTrace) {
    final Transaction tx = transactionTrace.getTransaction();
    final Optional<String> smartContractCode =
        tx.getInit().map(__ -> transactionTrace.getResult().getOutput().toString());
    final Optional<String> smartContractAddress =
        smartContractCode.map(
            __ -> Address.contractAddress(tx.getSender(), tx.getNonce()).toHexString());
    final Optional<Bytes> revertReason = transactionTrace.getResult().getRevertReason();

    // set to, input and type fields if not a smart contract
    if (tx.getTo().isPresent()) {
      final Bytes payload = tx.getPayload();
      this.to = tx.getTo().map(Bytes::toHexString).orElse(null);
      this.type = "CALL";
      this.input = payload == null ? "0x" : payload.toHexString();
      this.from = tx.getSender().toHexString();
      this.value = tx.getValue().toShortHexString();

      // check and set revert error and reason
      if (!transactionTrace.getTraceFrames().isEmpty()
          && hasRevertInSubCall(transactionTrace, transactionTrace.getTraceFrames().getFirst())) {
        this.error = "execution reverted";
      }
      revertReason.ifPresent(r -> this.revertReason = r.toHexString());

    } else {
      this.type = "CREATE";
      this.to = smartContractAddress.orElse(null);
      // set input from init field for smart contract deployment
      this.input = tx.getInit().map(Bytes::toHexString).orElse(null);
      this.from = tx.getSender().toHexString();
      this.value = tx.getValue().toShortHexString();
    }

    // TODO: Other Types???

    // set gasUsed
    if (!transactionTrace.getTraceFrames().isEmpty()) {
      final OptionalLong precompiledGasCost =
          transactionTrace.getTraceFrames().getFirst().getPrecompiledGasCost();
      if (precompiledGasCost.isPresent()) {
        this.gasUsed = "0x" + Long.toHexString(precompiledGasCost.getAsLong());
      }
    }

    // TODO: Handle TraceFrames

  } // end of constructor

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

  private static boolean hasRevertInSubCall(
      final TransactionTrace transactionTrace, final TraceFrame callFrame) {
    for (int i = 0; i < transactionTrace.getTraceFrames().size(); i++) {
      if (i + 1 < transactionTrace.getTraceFrames().size()) {
        final TraceFrame next = transactionTrace.getTraceFrames().get(i + 1);
        if (next.getDepth() == callFrame.getDepth()) {
          if (next.getOpcodeNumber() == RevertOperation.OPCODE) {
            return true;
          } else if (next.getOpcodeNumber() == ReturnOperation.OPCODE) {
            return false;
          }
        }
      }
    }
    return false;
  }
}

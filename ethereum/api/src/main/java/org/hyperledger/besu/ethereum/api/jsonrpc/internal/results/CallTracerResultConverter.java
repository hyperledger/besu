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

import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallTracerResultConverter {
  private static final Logger LOG = LoggerFactory.getLogger("CallTracerConverter");

  public static CallTracerResult convert(final TransactionTrace transactionTrace) {
    if (transactionTrace == null) {
      throw new IllegalArgumentException("TransactionTrace cannot be null");
    }

    if (transactionTrace.getTransaction() == null || transactionTrace.getResult() == null) {
      throw new IllegalArgumentException("TransactionTrace must have valid transaction and result");
    }

    if (transactionTrace.getTraceFrames() == null || transactionTrace.getTraceFrames().isEmpty()) {
      LOG.warn("No Trace Frames, Calling createRootCallFromTransaction()");
      return createRootCallFromTransaction(transactionTrace);
    }

    LOG.warn("Trace Frames, Calling buildCallHierarchyFromFrames()");
    return buildCallHierarchyFromFrames(transactionTrace);
  }

  private static CallTracerResult buildCallHierarchyFromFrames(final TransactionTrace trace) {
    List<TraceFrame> frames = trace.getTraceFrames();
    Transaction tx = trace.getTransaction();

    Deque<CallTracerResult.Builder> stack = new ArrayDeque<>();

    CallTracerResult.Builder root =
        CallTracerResult.builder()
            .type(tx.isContractCreation() ? "CREATE" : "CALL")
            .from(tx.getSender().toHexString())
            .to(
                tx.isContractCreation()
                    ? tx.contractAddress().map(Address::toHexString).orElse(null)
                    : tx.getTo().map(Address::toHexString).orElse(null))
            .value(tx.getValue().toShortHexString())
            .gas(tx.getGasLimit())
            .input(tx.getPayload().toHexString());

    stack.push(root);

    for (int i = 0; i < frames.size(); i++) {
      TraceFrame frame = frames.get(i);
      String opcode = frame.getOpcode();

      if (!("CALL".equals(opcode)
          || "CALLCODE".equals(opcode)
          || "DELEGATECALL".equals(opcode)
          || "STATICCALL".equals(opcode)
          || "CREATE".equals(opcode)
          || "CREATE2".equals(opcode))) {
        continue;
      }

      int callDepth = frame.getDepth();
      long gasBefore = frame.getGasRemaining();

      TraceFrame exitFrame = null;
      for (int j = i + 1; j < frames.size(); j++) {
        TraceFrame maybeExit = frames.get(j);
        if (maybeExit.getDepth() < callDepth) {
          break;
        }
        if (maybeExit.getDepth() == callDepth
            && ("RETURN".equals(maybeExit.getOpcode())
                || "STOP".equals(maybeExit.getOpcode())
                || "REVERT".equals(maybeExit.getOpcode()))) {
          exitFrame = maybeExit;
          break;
        }
      }

      long gasAfter = (exitFrame != null) ? exitFrame.getGasRemainingPostExecution() : 0L;
      long gasUsed = Math.max(0, gasBefore - gasAfter);

      String toAddress = resolveToAddress(frame, stack);

      CallTracerResult.Builder parent = stack.peek();
      CallTracerResult parentResult = (parent != null) ? parent.build() : null;
      String fromAddress = (parentResult != null) ? parentResult.getTo() : null;

      CallTracerResult.Builder builder =
          CallTracerResult.builder()
              .type(opcode)
              .from(fromAddress)
              .to(toAddress)
              .value("STATICCALL".equals(opcode) ? "0x0" : frame.getValue().toShortHexString())
              .gas(gasBefore)
              .gasUsed(gasUsed)
              .input(frame.getInputData().toHexString())
              .output(exitFrame != null ? exitFrame.getOutputData().toHexString() : "0x");

      if (exitFrame != null) {
        if (exitFrame.getExceptionalHaltReason().isPresent()) {
          builder.error("execution reverted");
        }
        exitFrame.getRevertReason().ifPresent(reason -> builder.revertReason(reason.toHexString()));
      }

      stack.push(builder);
    }

    while (stack.size() > 1) {
      CallTracerResult.Builder child = stack.pop();
      stack.peek().addCall(child.build());
    }

    TransactionProcessingResult result = trace.getResult();
    root.gasUsed(trace.getGas());
    if (!result.isSuccessful()) {
      root.error("execution reverted");
      result.getRevertReason().ifPresent(reason -> root.revertReason(reason.toHexString()));
    }

    return root.build();
  }

  private static String resolveToAddress(
      final TraceFrame frame, final Deque<CallTracerResult.Builder> stack) {
    String opcode = frame.getOpcode();
    switch (opcode) {
      case "DELEGATECALL":
        CallTracerResult.Builder parent = stack.peek();
        CallTracerResult parentResult = (parent != null) ? parent.build() : null;
        return (parentResult != null) ? parentResult.getTo() : null;
      case "CREATE":
      case "CREATE2":
        return null;
      case "CALL":
      case "STATICCALL":
      case "CALLCODE":
        return frame
            .getStack()
            .filter(s -> s.length > 1)
            .map(s -> toAddress(s[1]).toHexString())
            .orElse(null);
      default:
        return null;
    }
  }

  private static CallTracerResult createRootCallFromTransaction(final TransactionTrace trace) {
    Transaction tx = trace.getTransaction();
    TransactionProcessingResult result = trace.getResult();
    CallTracerResult.Builder rootBuilder =
        CallTracerResult.builder()
            .type(tx.isContractCreation() ? "CREATE" : "CALL")
            .from(tx.getSender().toHexString())
            .to(
                tx.isContractCreation()
                    ? tx.contractAddress().map(Address::toHexString).orElse(null)
                    : tx.getTo().map(Address::toHexString).orElse(null))
            .value(tx.getValue().toShortHexString())
            .gas(tx.getGasLimit())
            .gasUsed(trace.getGas())
            .input(tx.getPayload().toHexString())
            .output("0x");

    if (!result.isSuccessful()) {
      rootBuilder.error("execution reverted");
      result.getRevertReason().ifPresent(reason -> rootBuilder.revertReason(reason.toHexString()));
    }

    return rootBuilder.build();
  }
}

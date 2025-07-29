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

import org.apache.tuweni.bytes.Bytes;
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
    final List<TraceFrame> frames = trace.getTraceFrames();
    final Transaction tx = trace.getTransaction();

    final Deque<CallTracerResult.Builder> stack = new ArrayDeque<>();

    final CallTracerResult.Builder root =
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
      final TraceFrame frame = frames.get(i);
      final String opcode = frame.getOpcode();

      if (!("CALL".equals(opcode)
          || "CALLCODE".equals(opcode)
          || "DELEGATECALL".equals(opcode)
          || "STATICCALL".equals(opcode)
          || "CREATE".equals(opcode)
          || "CREATE2".equals(opcode))) {
        continue;
      }

      final int callDepth = frame.getDepth();
      final long gasBefore = frame.getGasRemaining();

      TraceFrame exitFrame = null;
      for (int j = i + 1; j < frames.size(); j++) {
        final TraceFrame maybeExit = frames.get(j);
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

      final long gasUsed;
      if (exitFrame != null && exitFrame.getGasRemainingPostExecution() >= 0) {
        gasUsed = gasBefore - exitFrame.getGasRemainingPostExecution();
      } else if (frame.getPrecompiledGasCost().isPresent()) {
        gasUsed = frame.getPrecompiledGasCost().getAsLong();
        LOG.warn("Used precompiledGasCost = {} for opcode {}", gasUsed, opcode);
      } else if (frame.getGasCost().isPresent()) {
        gasUsed = frame.getGasCost().getAsLong();
        LOG.warn("Used gasCost = {} as fallback for opcode {}", gasUsed, opcode);
      } else {
        gasUsed = 0;
        LOG.warn("Unable to determine gasUsed for opcode {} at depth {}", opcode, callDepth);
      }

      final String toAddress = resolveToAddress(frame, opcode);

      final CallTracerResult.Builder parent = stack.peek();
      final CallTracerResult parentResult = (parent != null) ? parent.build() : null;
      final String fromAddress = (parentResult != null) ? parentResult.getTo() : null;

      final Bytes inputData;
      if (i + 1 < frames.size() && frames.get(i + 1).getDepth() == callDepth + 1) {
        inputData = frames.get(i + 1).getInputData();
      } else {
        inputData = frame.getInputData();
      }

      final CallTracerResult.Builder builder =
          CallTracerResult.builder()
              .type(opcode)
              .from(fromAddress)
              .to(toAddress)
              .value("STATICCALL".equals(opcode) ? "0x0" : frame.getValue().toShortHexString())
              .gas(gasBefore)
              .gasUsed(gasUsed)
              .input(inputData.toHexString());

      if (exitFrame != null) {
        if (exitFrame.getOutputData() != null && !exitFrame.getOutputData().isEmpty()) {
          builder.output(exitFrame.getOutputData().toHexString());
        }
        if (exitFrame.getExceptionalHaltReason().isPresent()) {
          builder.error("execution reverted");
        }
        exitFrame.getRevertReason().ifPresent(reason -> builder.revertReason(reason.toHexString()));
      }

      stack.push(builder);
    }

    while (stack.size() > 1) {
      final CallTracerResult.Builder child = stack.pop();
      final CallTracerResult.Builder parent = stack.peek();
      if (parent != null) {
        parent.addCall(child.build());
      }
    }

    final TransactionProcessingResult result = trace.getResult();
    final long totalGasUsed = trace.getTransaction().getGasLimit() - result.getGasRemaining();
    root.gasUsed(totalGasUsed);
    if (!result.isSuccessful()) {
      root.error("execution reverted");
      result.getRevertReason().ifPresent(reason -> root.revertReason(reason.toHexString()));
    }
    if (result.getOutput() != null && !result.getOutput().isEmpty()) {
      root.output(result.getOutput().toHexString());
    }

    return root.build();
  }

  private static String resolveToAddress(final TraceFrame frame, final String opcode) {
    return switch (opcode) {
      case "DELEGATECALL" -> frame.getRecipient().toHexString();
      case "CREATE", "CREATE2" -> null;
      case "CALL", "STATICCALL", "CALLCODE" ->
          frame
              .getStack()
              .filter(s -> s.length > 1)
              .map(s -> toAddress(s[s.length - 2]).toHexString())
              .orElse(null);
      default -> null;
    };
  }

  private static CallTracerResult createRootCallFromTransaction(final TransactionTrace trace) {
    final Transaction tx = trace.getTransaction();
    final TransactionProcessingResult result = trace.getResult();
    final CallTracerResult.Builder rootBuilder =
        CallTracerResult.builder()
            .type(tx.isContractCreation() ? "CREATE" : "CALL")
            .from(tx.getSender().toHexString())
            .to(
                tx.isContractCreation()
                    ? tx.contractAddress().map(Address::toHexString).orElse(null)
                    : tx.getTo().map(Address::toHexString).orElse(null))
            .value(tx.getValue().toShortHexString())
            .gas(tx.getGasLimit())
            .gasUsed(tx.getGasLimit() - result.getGasRemaining())
            .input(tx.getPayload().toHexString());

    if (result.getOutput() != null && !result.getOutput().isEmpty()) {
      rootBuilder.output(result.getOutput().toHexString());
    }

    if (!result.isSuccessful()) {
      rootBuilder.error("execution reverted");
      result.getRevertReason().ifPresent(reason -> rootBuilder.revertReason(reason.toHexString()));
    }

    return rootBuilder.build();
  }
}

/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.privateTracing;

import static org.hyperledger.besu.evm.internal.Words.toAddress;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor.PrivateTransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TracingUtils;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.Action;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.Result;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.ExecutedPrivateTransaction;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Atomics;
import org.apache.tuweni.bytes.Bytes;

public class PrivateTraceGenerator {

  private static final String ZERO_ADDRESS_STRING = Address.ZERO.toHexString();
  private static final int EIP_150_DIVISOR = 64;

  public static Stream<Trace> generateFromTransactionTrace(
      final ProtocolSchedule protocolSchedule,
      final PrivateTransactionTrace transactionTrace,
      final Block block,
      final AtomicInteger traceCounter,
      final Consumer<PrivateFlatTrace.Builder> consumer) {

    final PrivateFlatTrace.Builder firstFlatTraceBuilder =
        PrivateFlatTrace.freshBuilder(transactionTrace);

    final ExecutedPrivateTransaction tx = transactionTrace.getPrivateTransaction();

    final Optional<String> smartContractCode =
        tx.getInit().map(__ -> transactionTrace.getResult().getOutput().toString());
    final Optional<String> smartContractAddress =
        smartContractCode.map(
            __ -> Address.contractAddress(tx.getSender(), tx.getNonce()).toHexString());
    final Optional<Bytes> revertReason = transactionTrace.getResult().getRevertReason();

    // set code field in result node
    smartContractCode.ifPresent(firstFlatTraceBuilder.getResultBuilder()::code);
    revertReason.ifPresent(r -> firstFlatTraceBuilder.revertReason(r.toHexString()));

    // set init field if transaction is a smart contract deployment
    tx.getInit().map(Bytes::toHexString).ifPresent(firstFlatTraceBuilder.getActionBuilder()::init);

    // set to, input and callType fields if not a smart contract
    if (tx.getTo().isPresent()) {
      final Bytes payload = tx.getPayload();
      firstFlatTraceBuilder
          .getActionBuilder()
          .to(tx.getTo().map(Bytes::toHexString).orElse(null))
          .callType("call")
          .input(payload == null ? "0x" : payload.toHexString());

      if (!transactionTrace.getTraceFrames().isEmpty()
          && hasRevertInSubCall(transactionTrace, transactionTrace.getTraceFrames().get(0))) {
        firstFlatTraceBuilder.error(Optional.of("Reverted"));
      }

    } else {
      firstFlatTraceBuilder
          .type("create")
          .getResultBuilder()
          .address(smartContractAddress.orElse(null));
    }

    if (!transactionTrace.getTraceFrames().isEmpty()) {
      final OptionalLong precompiledGasCost =
          transactionTrace.getTraceFrames().get(0).getPrecompiledGasCost();
      if (precompiledGasCost.isPresent()) {
        firstFlatTraceBuilder
            .getResultBuilder()
            .gasUsed("0x" + Long.toHexString(precompiledGasCost.getAsLong()));
      }
    }

    final List<PrivateFlatTrace.Builder> flatTraces = new ArrayList<>();

    // stack of previous contexts
    final Deque<PrivateFlatTrace.Context> tracesContexts = new ArrayDeque<>();

    // add the first transactionTrace context to the queue of transactionTrace contexts
    PrivateFlatTrace.Context currentContext = new PrivateFlatTrace.Context(firstFlatTraceBuilder);
    tracesContexts.addLast(currentContext);
    flatTraces.add(currentContext.getBuilder());
    // declare the first transactionTrace context as the previous transactionTrace context
    long cumulativeGasCost = 0;

    final Iterator<TraceFrame> iter = transactionTrace.getTraceFrames().iterator();
    Optional<TraceFrame> nextTraceFrame =
        iter.hasNext() ? Optional.of(iter.next()) : Optional.empty();
    while (nextTraceFrame.isPresent()) {
      final TraceFrame traceFrame = nextTraceFrame.get();
      nextTraceFrame = iter.hasNext() ? Optional.of(iter.next()) : Optional.empty();
      cumulativeGasCost +=
          traceFrame.getGasCost().orElse(0L) + traceFrame.getPrecompiledGasCost().orElse(0L);

      final String opcodeString = traceFrame.getOpcode();
      if ("CALL".equals(opcodeString)
          || "CALLCODE".equals(opcodeString)
          || "DELEGATECALL".equals(opcodeString)
          || "STATICCALL".equals(opcodeString)) {

        currentContext =
            handleCall(
                transactionTrace,
                traceFrame,
                nextTraceFrame,
                flatTraces,
                cumulativeGasCost,
                tracesContexts,
                opcodeString.toLowerCase(Locale.US));

      } else if ("CALLDATALOAD".equals(opcodeString)) {
        currentContext = handleCallDataLoad(currentContext, traceFrame);
      } else if ("RETURN".equals(opcodeString) || "STOP".equals(opcodeString)) {
        currentContext =
            handleReturn(
                protocolSchedule,
                transactionTrace,
                block,
                traceFrame,
                tracesContexts,
                currentContext);
      } else if ("SELFDESTRUCT".equals(opcodeString)) {
        if (traceFrame.getExceptionalHaltReason().isPresent()) {
          currentContext =
              handleCall(
                  transactionTrace,
                  traceFrame,
                  nextTraceFrame,
                  flatTraces,
                  cumulativeGasCost,
                  tracesContexts,
                  opcodeString.toLowerCase(Locale.US));
        } else {
          currentContext =
              handleSelfDestruct(traceFrame, tracesContexts, currentContext, flatTraces);
        }
      } else if (("CREATE".equals(opcodeString) || "CREATE2".equals(opcodeString))
          && (traceFrame.getExceptionalHaltReason().isEmpty() || traceFrame.getDepth() == 0)) {
        currentContext =
            handleCreateOperation(
                traceFrame,
                nextTraceFrame,
                flatTraces,
                cumulativeGasCost,
                tracesContexts,
                smartContractAddress);
      } else if ("REVERT".equals(opcodeString)) {
        currentContext = handleRevert(tracesContexts, currentContext);
      }

      if (traceFrame.getExceptionalHaltReason().isPresent()) {
        currentContext = handleHalt(flatTraces, tracesContexts, currentContext, traceFrame);
      }

      if (currentContext == null) {
        break;
      }
    }

    return flatTraces.stream().peek(consumer).map(PrivateFlatTrace.Builder::build);
  }

  public static Stream<Trace> generateFromTransactionTraceAndBlock(
      final ProtocolSchedule protocolSchedule,
      final PrivateTransactionTrace transactionTrace,
      final Block block) {
    return generateFromTransactionTrace(
        protocolSchedule,
        transactionTrace,
        block,
        new AtomicInteger(),
        builder ->
            addAdditionalTransactionInformationToFlatTrace(builder, transactionTrace, block));
  }

  private static long computeGasUsed(
      final Deque<PrivateFlatTrace.Context> tracesContexts,
      final PrivateFlatTrace.Context currentContext,
      final PrivateTransactionTrace transactionTrace,
      final TraceFrame traceFrame) {

    final long gasRemainingBeforeProcessed;
    final long gasRemainingAfterProcessed;
    long gasRefund = 0;
    if (tracesContexts.size() == 1) {
      gasRemainingBeforeProcessed = transactionTrace.getTraceFrames().get(0).getGasRemaining();
      gasRemainingAfterProcessed = transactionTrace.getResult().getGasRemaining();
      if (gasRemainingAfterProcessed > traceFrame.getGasRemaining()) {
        gasRefund = gasRemainingAfterProcessed - traceFrame.getGasRemaining();
      } else {
        gasRefund = traceFrame.getGasRefund();
      }
    } else {
      final Action.Builder actionBuilder = currentContext.getBuilder().getActionBuilder();
      gasRemainingBeforeProcessed = Long.decode(actionBuilder.getGas());
      gasRemainingAfterProcessed = traceFrame.getGasRemaining();
    }
    return gasRemainingBeforeProcessed - gasRemainingAfterProcessed + gasRefund;
  }

  private static long computeGas(
      final TraceFrame traceFrame, final Optional<TraceFrame> nextTraceFrame) {
    if (traceFrame.getGasCost().isPresent()) {
      final long gasNeeded = traceFrame.getGasCost().getAsLong();
      final long currentGas = traceFrame.getGasRemaining();
      if (currentGas >= gasNeeded) {
        final long gasRemaining = currentGas - gasNeeded;
        return gasRemaining - Math.floorDiv(gasRemaining, EIP_150_DIVISOR);
      }
    }
    return nextTraceFrame.map(TraceFrame::getGasRemaining).orElse(0L);
  }

  private static String calculateCallingAddress(final PrivateFlatTrace.Context lastContext) {
    final PrivateFlatTrace.Builder lastContextBuilder = lastContext.getBuilder();
    final Action.Builder lastActionBuilder = lastContextBuilder.getActionBuilder();
    if (lastActionBuilder.getCallType() == null) {
      if ("create".equals(lastContextBuilder.getType())) {
        return lastContextBuilder.getResultBuilder().getAddress();
      } else {
        return ZERO_ADDRESS_STRING;
      }
    }

    switch (lastActionBuilder.getCallType()) {
      case "call":
      case "staticcall":
        return lastActionBuilder.getTo();
      case "delegatecall":
      case "callcode":
        return lastActionBuilder.getFrom();
      case "create":
      case "create2":
        return lastContextBuilder.getResultBuilder().getAddress();
      default:
        return ZERO_ADDRESS_STRING;
    }
  }

  private static List<Integer> calculateTraceAddress(
      final Deque<PrivateFlatTrace.Context> contexts) {
    return contexts.stream()
        .map(context -> context.getBuilder().getSubtraces())
        .collect(Collectors.toList());
  }

  private static List<Integer> calculateSelfDescructAddress(
      final Deque<PrivateFlatTrace.Context> contexts) {
    return Streams.concat(
            contexts.stream()
                .map(context -> context.getBuilder().getSubtraces())) // , Stream.of(0))
        .collect(Collectors.toList());
  }

  private static String getActionAddress(
      final Action.Builder callingAction, final String recipient) {
    if (callingAction.getCallType() != null) {
      return callingAction.getCallType().equals("call")
          ? callingAction.getTo()
          : callingAction.getFrom();
    }
    return firstNonNull("", recipient, callingAction.getFrom(), callingAction.getTo());
  }

  private static String firstNonNull(final String defaultValue, final String... values) {
    for (final String value : values) {
      if (value != null) {
        return value;
      }
    }
    return defaultValue;
  }

  private static PrivateFlatTrace.Context handleCreateOperation(
      final TraceFrame traceFrame,
      final Optional<TraceFrame> nextTraceFrame,
      final List<PrivateFlatTrace.Builder> flatTraces,
      final long cumulativeGasCost,
      final Deque<PrivateFlatTrace.Context> tracesContexts,
      final Optional<String> smartContractAddress) {
    final PrivateFlatTrace.Context lastContext = tracesContexts.peekLast();

    final String callingAddress = calculateCallingAddress(lastContext);

    final PrivateFlatTrace.Builder subTraceBuilder =
        PrivateFlatTrace.builder()
            .type("create")
            .traceAddress(calculateTraceAddress(tracesContexts))
            .resultBuilder(Result.builder());

    final Action.Builder subTraceActionBuilder =
        Action.builder()
            .from(smartContractAddress.orElse(callingAddress))
            .gas("0x" + Long.toHexString(computeGas(traceFrame, nextTraceFrame)))
            .value(Quantity.create(nextTraceFrame.map(TraceFrame::getValue).orElse(Wei.ZERO)));

    traceFrame
        .getMaybeCode()
        .map(Code::getBytes)
        .map(Bytes::toHexString)
        .ifPresent(subTraceActionBuilder::init);

    final PrivateFlatTrace.Context currentContext =
        new PrivateFlatTrace.Context(subTraceBuilder.actionBuilder(subTraceActionBuilder));

    currentContext
        .getBuilder()
        .getResultBuilder()
        .address(nextTraceFrame.map(TraceFrame::getRecipient).orElse(Address.ZERO).toHexString());
    currentContext.setCreateOp(true);
    currentContext.decGasUsed(cumulativeGasCost);
    tracesContexts.addLast(currentContext);
    flatTraces.add(currentContext.getBuilder());
    return currentContext;
  }

  private static PrivateFlatTrace.Context handleHalt(
      final List<PrivateFlatTrace.Builder> flatTraces,
      final Deque<PrivateFlatTrace.Context> tracesContexts,
      final PrivateFlatTrace.Context currentContext,
      final TraceFrame traceFrame) {
    final PrivateFlatTrace.Builder traceFrameBuilder;
    if (currentContext == null) {
      traceFrameBuilder = flatTraces.get(flatTraces.size() - 1);
    } else {
      traceFrameBuilder = currentContext.getBuilder();
    }
    traceFrameBuilder.error(
        traceFrame.getExceptionalHaltReason().map(ExceptionalHaltReason::getDescription));
    if (currentContext != null) {
      final Action.Builder actionBuilder = traceFrameBuilder.getActionBuilder();
      actionBuilder.value(Quantity.create(traceFrame.getValue()));
      tracesContexts.removeLast();
      final PrivateFlatTrace.Context nextContext = tracesContexts.peekLast();
      if (nextContext != null) {
        nextContext.getBuilder().incSubTraces();
      }
      return nextContext;
    }
    return currentContext;
  }

  private static PrivateFlatTrace.Context handleRevert(
      final Deque<PrivateFlatTrace.Context> tracesContexts,
      final PrivateFlatTrace.Context currentContext) {
    currentContext.getBuilder().error(Optional.of("Reverted"));
    tracesContexts.removeLast();
    final PrivateFlatTrace.Context nextContext = tracesContexts.peekLast();
    if (nextContext != null) {
      nextContext.getBuilder().incSubTraces();
    }
    return nextContext;
  }

  private static PrivateFlatTrace.Context handleSelfDestruct(
      final TraceFrame traceFrame,
      final Deque<PrivateFlatTrace.Context> tracesContexts,
      final PrivateFlatTrace.Context currentContext,
      final List<PrivateFlatTrace.Builder> flatTraces) {

    final Action.Builder actionBuilder = currentContext.getBuilder().getActionBuilder();
    final long gasUsed =
        Long.decode(actionBuilder.getGas())
            - traceFrame.getGasRemaining()
            + (traceFrame.getGasCost().orElse(0L));

    currentContext.setGasUsed(gasUsed);

    final Bytes[] stack = traceFrame.getStack().orElseThrow();
    final Address refundAddress = toAddress(stack[stack.length - 1]);
    final PrivateFlatTrace.Builder subTraceBuilder =
        PrivateFlatTrace.builder()
            .type("suicide")
            .traceAddress(calculateSelfDescructAddress(tracesContexts));

    final AtomicReference<Wei> weiBalance = Atomics.newReference(Wei.ZERO);
    traceFrame
        .getMaybeRefunds()
        .ifPresent(refunds -> weiBalance.set(refunds.getOrDefault(refundAddress, Wei.ZERO)));

    final Action.Builder callingAction = tracesContexts.peekLast().getBuilder().getActionBuilder();
    final String actionAddress =
        getActionAddress(callingAction, traceFrame.getRecipient().toHexString());
    final Action.Builder subTraceActionBuilder =
        Action.builder()
            .address(actionAddress)
            .refundAddress(refundAddress.toString())
            .balance(TracingUtils.weiAsHex(weiBalance.get()));

    flatTraces.add(
        new PrivateFlatTrace.Context(subTraceBuilder.actionBuilder(subTraceActionBuilder))
            .getBuilder());
    final PrivateFlatTrace.Context lastContext = tracesContexts.removeLast();
    lastContext.getBuilder().incSubTraces();
    final PrivateFlatTrace.Context nextContext = tracesContexts.peekLast();
    if (nextContext != null) {
      nextContext.getBuilder().incSubTraces();
    }
    return nextContext;
  }

  private static PrivateFlatTrace.Context handleReturn(
      final ProtocolSchedule protocolSchedule,
      final PrivateTransactionTrace transactionTrace,
      final Block block,
      final TraceFrame traceFrame,
      final Deque<PrivateFlatTrace.Context> tracesContexts,
      final PrivateFlatTrace.Context currentContext) {

    final PrivateFlatTrace.Builder traceFrameBuilder = currentContext.getBuilder();
    final Result.Builder resultBuilder = traceFrameBuilder.getResultBuilder();
    final Action.Builder actionBuilder = traceFrameBuilder.getActionBuilder();
    actionBuilder.value(Quantity.create(traceFrame.getValue()));

    currentContext.setGasUsed(
        computeGasUsed(tracesContexts, currentContext, transactionTrace, traceFrame));

    if ("STOP".equals(traceFrame.getOpcode()) && resultBuilder.isGasUsedEmpty()) {
      final long callStipend =
          protocolSchedule
              .getByBlockHeader(block.getHeader())
              .getGasCalculator()
              .getAdditionalCallStipend();
      tracesContexts.stream()
          .filter(
              context ->
                  !tracesContexts.getFirst().equals(context)
                      && !tracesContexts.getLast().equals(context))
          .forEach(context -> context.decGasUsed(callStipend));
    }

    final Bytes outputData = traceFrame.getOutputData();
    if (resultBuilder.getCode() == null) {
      resultBuilder.output(outputData.toHexString());
    }

    // set value for contract creation TXes, CREATE, and CREATE2
    if (actionBuilder.getCallType() == null && traceFrame.getMaybeCode().isPresent()) {
      actionBuilder.init(traceFrame.getMaybeCode().get().getBytes().toHexString());
      resultBuilder.code(outputData.toHexString());
      if (currentContext.isCreateOp()) {
        // this is from a CREATE/CREATE2, so add code deposit cost.
        currentContext.incGasUsed(outputData.size() * 200L);
      }
    }

    tracesContexts.removeLast();
    final PrivateFlatTrace.Context nextContext = tracesContexts.peekLast();
    if (nextContext != null) {
      nextContext.getBuilder().incSubTraces();
    }
    return nextContext;
  }

  private static PrivateFlatTrace.Context handleCallDataLoad(
      final PrivateFlatTrace.Context currentContext, final TraceFrame traceFrame) {
    if (!traceFrame.getValue().isZero()) {
      currentContext
          .getBuilder()
          .getActionBuilder()
          .value(traceFrame.getValue().toShortHexString());
    } else {
      currentContext.getBuilder().getActionBuilder().value("0x0");
    }
    return currentContext;
  }

  private static PrivateFlatTrace.Context handleCall(
      final PrivateTransactionTrace transactionTrace,
      final TraceFrame traceFrame,
      final Optional<TraceFrame> nextTraceFrame,
      final List<PrivateFlatTrace.Builder> flatTraces,
      final long cumulativeGasCost,
      final Deque<PrivateFlatTrace.Context> tracesContexts,
      final String opcodeString) {
    final Bytes[] stack = traceFrame.getStack().orElseThrow();
    final PrivateFlatTrace.Context lastContext = tracesContexts.peekLast();

    final String callingAddress = calculateCallingAddress(lastContext);

    if (traceFrame.getDepth() >= nextTraceFrame.map(TraceFrame::getDepth).orElse(0)) {
      // don't log calls to calls that don't execute, such as insufficient value and precompiles
      return tracesContexts.peekLast();
    }

    final PrivateFlatTrace.Builder subTraceBuilder =
        PrivateFlatTrace.builder()
            .traceAddress(calculateTraceAddress(tracesContexts))
            .resultBuilder(Result.builder());
    final Action.Builder subTraceActionBuilder =
        Action.builder()
            .from(callingAddress)
            .input(
                nextTraceFrame.map(TraceFrame::getInputData).map(Bytes::toHexString).orElse(null))
            .gas(
                "0x" + Long.toHexString(nextTraceFrame.map(TraceFrame::getGasRemaining).orElse(0L)))
            .callType(opcodeString.toLowerCase(Locale.US))
            .value(Quantity.create(traceFrame.getValue()));

    if (stack.length > 1) {
      subTraceActionBuilder.to(toAddress(stack[stack.length - 2]).toString());
    }

    nextTraceFrame.ifPresent(
        nextFrame -> {
          if (hasRevertInSubCall(transactionTrace, nextFrame)) {
            subTraceBuilder.error(Optional.of("Reverted"));
          }
        });

    final PrivateFlatTrace.Context currentContext =
        new PrivateFlatTrace.Context(subTraceBuilder.actionBuilder(subTraceActionBuilder));
    currentContext.decGasUsed(cumulativeGasCost);

    tracesContexts.addLast(currentContext);
    flatTraces.add(currentContext.getBuilder());
    return currentContext;
  }

  private static boolean hasRevertInSubCall(
      final PrivateTransactionTrace transactionTrace, final TraceFrame callFrame) {
    return transactionTrace.getTraceFrames().stream()
        .filter(traceFrame -> !traceFrame.equals(callFrame))
        .takeWhile(traceFrame -> !traceFrame.getOpcode().equals("RETURN"))
        .filter(traceFrame -> traceFrame.getOpcode().equals("REVERT"))
        .anyMatch(traceFrame -> traceFrame.getDepth() == callFrame.getDepth());
  }

  private static void addAdditionalTransactionInformationToFlatTrace(
      final PrivateFlatTrace.Builder builder,
      final PrivateTransactionTrace transactionTrace,
      final Block block) {
    // add block information (hash and number)
    builder.blockHash(block.getHash().toHexString()).blockNumber(block.getHeader().getNumber());
    // add transaction information (position and hash)
    builder.transactionHash(transactionTrace.getPrivateTransaction().getPmtHash().toHexString());

    addContractCreationMethodToTrace(transactionTrace, builder);
  }

  private static void addContractCreationMethodToTrace(
      final PrivateTransactionTrace transactionTrace, final PrivateFlatTrace.Builder builder) {
    // add creationMethod for create action
    Optional.ofNullable(builder.getType())
        .filter(type -> type.equals("create"))
        .ifPresent(
            __ ->
                builder
                    .getActionBuilder()
                    .creationMethod(
                        transactionTrace.getTraceFrames().stream()
                            .filter(frame -> "CREATE2".equals(frame.getOpcode()))
                            .findFirst()
                            .map(TraceFrame::getOpcode)
                            .orElse("CREATE")
                            .toLowerCase(Locale.US)));
  }
}

/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.tracing;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.debug.TraceFrame;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTrace;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Atomics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FlatTraceGenerator {

  public static final Logger LOG = LogManager.getLogger();

  /**
   * Generates a stream of {@link Trace} from the passed {@link TransactionTrace} data.
   *
   * @param transactionTrace the {@link TransactionTrace} to use
   * @param traceCounter the current trace counter value
   * @param gasCalculator the {@link GasCalculator} to use
   * @return a stream of generated traces {@link Trace}
   */
  public static Stream<Trace> generateFromTransactionTrace(
      final TransactionTrace transactionTrace,
      final AtomicInteger traceCounter,
      final GasCalculator gasCalculator) {
    final FlatTrace.Builder firstFlatTraceBuilder = FlatTrace.freshBuilder(transactionTrace);
    final String lastContractAddress =
        transactionTrace.getTransaction().getTo().orElse(Address.ZERO).getHexString();

    final Optional<String> smartContractCode =
        transactionTrace.getTransaction().getInit().isPresent()
            ? Optional.of(transactionTrace.getResult().getOutput().toString())
            : Optional.empty();
    final Optional<String> smartContractAddress =
        smartContractCode.isPresent()
            ? Optional.of(
                Address.contractAddress(
                        transactionTrace.getTransaction().getSender(),
                        transactionTrace.getTransaction().getNonce())
                    .getHexString())
            : Optional.empty();
    // set code field in result node
    smartContractCode.ifPresent(firstFlatTraceBuilder.getResultBuilder()::code);
    // set init field if transaction is a smart contract deployment
    transactionTrace
        .getTransaction()
        .getInit()
        .map(BytesValue::getHexString)
        .ifPresent(firstFlatTraceBuilder.getActionBuilder()::init);
    // set to, input and callType fields if not a smart contract
    transactionTrace
        .getTransaction()
        .getTo()
        .ifPresent(
            to ->
                firstFlatTraceBuilder
                    .getActionBuilder()
                    .to(to.toString())
                    .callType("call")
                    .input(
                        transactionTrace
                            .getTransaction()
                            .getData()
                            .orElse(
                                transactionTrace
                                    .getTransaction()
                                    .getInit()
                                    .orElse(BytesValue.EMPTY))
                            .getHexString()));
    // declare a queue of transactionTrace contexts
    final Deque<FlatTrace.Context> tracesContexts = new ArrayDeque<>();
    // add the first transactionTrace context to the queue of transactionTrace contexts
    tracesContexts.addLast(new FlatTrace.Context(firstFlatTraceBuilder));
    // declare the first transactionTrace context as the previous transactionTrace context
    final List<Integer> addressVector = new ArrayList<>();
    final AtomicLong cumulativeGasCost = new AtomicLong(0);
    final Gas transactionIntrinsicGasCost =
        gasCalculator.transactionIntrinsicGasCost(transactionTrace.getTransaction());
    LOG.debug(
        "Transaction intrinsic gas cost: {} - {}",
        transactionIntrinsicGasCost.toLong(),
        transactionIntrinsicGasCost.toHexString());
    int traceFrameIndex = 0;
    for (TraceFrame traceFrame : transactionTrace.getTraceFrames()) {
      cumulativeGasCost.addAndGet(traceFrame.getGasCost().orElse(Gas.ZERO).toLong());
      if ("CALL".equals(traceFrame.getOpcode())) {
        handleCall(
            transactionTrace,
            traceFrame,
            lastContractAddress,
            cumulativeGasCost,
            addressVector,
            tracesContexts,
            traceFrameIndex);
      } else if ("RETURN".equals(traceFrame.getOpcode()) || "STOP".equals(traceFrame.getOpcode())) {
        handleReturn(transactionTrace, traceFrame, smartContractAddress, tracesContexts);
      } else if ("SELFDESTRUCT".equals(traceFrame.getOpcode())) {
        handleSelfDestruct(
            traceFrame, lastContractAddress, cumulativeGasCost, addressVector, tracesContexts);
      }
      traceFrameIndex++;
    }
    final List<Trace> flatTraces = new ArrayList<>();
    tracesContexts.forEach(context -> flatTraces.add(context.getBuilder().build()));
    traceCounter.incrementAndGet();
    return flatTraces.stream();
  }

  private static void handleCall(
      final TransactionTrace transactionTrace,
      final TraceFrame traceFrame,
      final String lastContractAddress,
      final AtomicLong cumulativeGasCost,
      final List<Integer> addressVector,
      final Deque<FlatTrace.Context> tracesContexts,
      final int traceFrameIndex) {
    final Bytes32[] stack = traceFrame.getStack().orElseThrow();
    final Address contractCallAddress = toAddress(stack[stack.length - 2]);
    if (addressVector.size() <= traceFrame.getDepth()) {
      addressVector.add(0);
    } else {
      addressVector.set(traceFrame.getDepth(), addressVector.get(traceFrame.getDepth()) + 1);
    }
    final List<Integer> traceAddress = Lists.newCopyOnWriteArrayList(addressVector);
    final FlatTrace.Builder subTraceBuilder =
        FlatTrace.builder().traceAddress(traceAddress).resultBuilder(Result.builder());
    // get the next trace frame to set the gas field (gas remaining) of the sub trace
    final TraceFrame traceFrameAfterCall =
        transactionTrace.getTraceFrames().get(traceFrameIndex + 1);
    final Action.Builder subTraceActionBuilder =
        Action.createCallAction(
            transactionTrace.getTransaction(),
            lastContractAddress,
            contractCallAddress,
            traceFrame,
            traceFrameAfterCall.getGasRemaining());

    final long gasCost = cumulativeGasCost.longValue();
    // retrieve the previous transactionTrace context
    Optional.ofNullable(tracesContexts.peekLast())
        .ifPresent(
            previousContext -> {
              // increment sub traces counter of previous transactionTrace
              previousContext.getBuilder().incSubTraces();
              // set gas cost of previous transactionTrace
              previousContext
                  .getBuilder()
                  .getResultBuilder()
                  .gasUsed(Gas.of(gasCost).toHexString());
            });
    tracesContexts.addLast(
        new FlatTrace.Context(subTraceBuilder.actionBuilder(subTraceActionBuilder)).subTrace());
    cumulativeGasCost.set(0);
  }

  private static void handleReturn(
      final TransactionTrace transactionTrace,
      final TraceFrame traceFrame,
      final Optional<String> smartContractAddress,
      final Deque<FlatTrace.Context> tracesContexts) {
    final Deque<FlatTrace.Context> polledContexts = new ArrayDeque<>();
    FlatTrace.Context ctx;
    // find last non returned transactionTrace
    while ((ctx = tracesContexts.pollLast()) != null) {
      polledContexts.addFirst(ctx);
      // continue until finding a non returned context
      if (ctx.isReturned()) {
        continue;
      }
      final FlatTrace.Builder flatTraceBuilder = ctx.getBuilder();
      final Gas gasRemainingAtStartOfTrace =
          Gas.fromHexString(flatTraceBuilder.getActionBuilder().getGas());
      final Gas gasUsed = gasRemainingAtStartOfTrace.minus(traceFrame.getGasRemaining());
      final Result.Builder resultBuilder = flatTraceBuilder.getResultBuilder();
      final Gas finalGasUsed;
      if (ctx.isSubtrace()) {
        finalGasUsed = gasUsed;
      } else {
        finalGasUsed = computeGasUsed(transactionTrace, gasUsed);
      }

      // set gas used for the trace
      resultBuilder.gasUsed(finalGasUsed.toHexString());
      // set address and type to create if smart contract deployment
      smartContractAddress.ifPresentOrElse(
          address -> {
            resultBuilder.address(address);
            flatTraceBuilder.type("create");
          },
          // set output otherwise
          () ->
              resultBuilder.output(
                  traceFrame.getMemory().isPresent() && traceFrame.getMemory().get().length > 0
                      ? traceFrame.getMemory().get()[0].toString()
                      : "0x"));
      ctx.markAsReturned();
      break;
    }
    // reinsert polled contexts add the end of the queue
    polledContexts.forEach(tracesContexts::addLast);
  }

  private static void handleSelfDestruct(
      final TraceFrame traceFrame,
      final String lastContractAddress,
      final AtomicLong cumulativeGasCost,
      final List<Integer> addressVector,
      final Deque<FlatTrace.Context> tracesContexts) {
    final Bytes32[] stack = traceFrame.getStack().orElseThrow();
    final Address refundAddress = toAddress(stack[0]);
    if (addressVector.size() <= traceFrame.getDepth()) {
      addressVector.add(0);
    }
    final FlatTrace.Builder subTraceBuilder =
        FlatTrace.builder().type("suicide").traceAddress(addressVector);

    final AtomicReference<Wei> weiBalance = Atomics.newReference(Wei.ZERO);
    traceFrame
        .getMaybeRefunds()
        .ifPresent(refunds -> weiBalance.set(refunds.getOrDefault(refundAddress, Wei.ZERO)));

    final Action.Builder subTraceActionBuilder =
        Action.createSelfDestructAction(lastContractAddress, refundAddress, weiBalance.get());

    final long gasCost = cumulativeGasCost.longValue();
    // retrieve the previous transactionTrace context
    if (!tracesContexts.isEmpty()) {
      FlatTrace.Context previousContext = tracesContexts.peekLast();
      // increment sub traces counter of previous transactionTrace
      previousContext.getBuilder().incSubTraces();
      // set gas cost of previous transactionTrace
      previousContext.getBuilder().getResultBuilder().gasUsed(Gas.of(gasCost).toHexString());
    }
    tracesContexts.addLast(
        new FlatTrace.Context(subTraceBuilder.actionBuilder(subTraceActionBuilder)));
    cumulativeGasCost.set(0);
  }

  private static Gas computeGasUsed(
      final TransactionTrace transactionTrace, final Gas fallbackValue) {
    final long firstFrameGasRemaining =
        transactionTrace.getTraceFrames().get(0).getGasRemaining().toLong();
    final long gasRemainingAfterTransactionWasProcessed =
        transactionTrace.getResult().getGasRemaining();
    if (firstFrameGasRemaining > gasRemainingAfterTransactionWasProcessed) {
      return Gas.of(firstFrameGasRemaining - gasRemainingAfterTransactionWasProcessed);
    } else {
      return fallbackValue;
    }
  }

  private static Address toAddress(final Bytes32 value) {
    return Address.wrap(
        BytesValue.of(
            Arrays.copyOfRange(value.extractArray(), Bytes32.SIZE - Address.SIZE, Bytes32.SIZE)));
  }
}

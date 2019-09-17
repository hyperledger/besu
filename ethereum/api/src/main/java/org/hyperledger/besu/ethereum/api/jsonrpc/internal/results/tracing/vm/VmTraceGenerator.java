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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VmTraceGenerator {

  /**
   * Generate a stream of trace result objects.
   *
   * @param transactionTrace the transaction trace to use.
   * @return a representation of generated traces.
   */
  public static Stream<Trace> generateTraceStream(final TransactionTrace transactionTrace) {
    return Stream.of(generateTrace(transactionTrace));
  }

  /**
   * Generate trace representation from the specified transaction trace.
   *
   * @param transactionTrace the transaction trace to use.
   * @return a representation of the trace.
   */
  public static Trace generateTrace(final TransactionTrace transactionTrace) {
    final VmTrace rootVmTrace = new VmTrace();
    final Deque<VmTrace> parentTraces = new ArrayDeque<>();
    parentTraces.add(rootVmTrace);
    if (transactionTrace != null && !transactionTrace.getTraceFrames().isEmpty()) {
      transactionTrace
          .getTransaction()
          .getInit()
          .map(BytesValue::getHexString)
          .ifPresent(rootVmTrace::setCode);
      final AtomicInteger index = new AtomicInteger(0);
      transactionTrace
          .getTraceFrames()
          .forEach(traceFrame -> addFrame(index, transactionTrace, traceFrame, parentTraces));
    }
    return rootVmTrace;
  }

  /**
   * Add a trace frame to the VmTrace result object.
   *
   * @param index index of the current frame in the trace
   * @param transactionTrace the transaction trace
   * @param traceFrame the current trace frame
   * @param parentTraces the queue of parent traces
   */
  private static void addFrame(
      final AtomicInteger index,
      final TransactionTrace transactionTrace,
      final TraceFrame traceFrame,
      final Deque<VmTrace> parentTraces) {
    if ("STOP".equals(traceFrame.getOpcode())) {
      return;
    }

    VmTrace newSubTrace;
    VmTrace currentTrace = parentTraces.getLast();

    // set smart contract code
    currentTrace.setCode(traceFrame.getMaybeCode().orElse(new Code()).getBytes().getHexString());
    final int nextFrameIndex = index.get() + 1;
    // retrieve next frame if not last
    final Optional<TraceFrame> maybeNextFrame =
        transactionTrace.getTraceFrames().size() > nextFrameIndex
            ? Optional.of(transactionTrace.getTraceFrames().get(nextFrameIndex))
            : Optional.empty();
    final Op op = new Op();
    // set gas cost and program counter
    op.setCost(traceFrame.getGasCost().orElse(Gas.ZERO).toLong());
    op.setPc(traceFrame.getPc());

    final Ex ex = new Ex();
    // set gas remaining
    ex.setUsed(
        traceFrame.getGasRemaining().toLong() - traceFrame.getGasCost().orElse(Gas.ZERO).toLong());

    final boolean isPreviousFrameReturnOpCode =
        index.get() != 0
            ? Optional.ofNullable(transactionTrace.getTraceFrames().get(index.get() - 1))
                .map(frame -> "RETURN".equals(frame.getOpcode()))
                .orElse(false)
            : false;

    // set memory if memory has been changed by this operation
    if (traceFrame.isMemoryWritten() && !isPreviousFrameReturnOpCode) {
      maybeNextFrame
          .flatMap(TraceFrame::getMemory)
          .filter(memory -> memory.length > 0)
          .map(Trace::dumpAndTrimTrailingZeros)
          .map(Mem::new)
          .ifPresent(ex::setMem);
    }

    // set push from stack elements if some elements have been produced
    if (traceFrame.getStackItemsProduced() > 0 && maybeNextFrame.isPresent()) {
      final Bytes32[] stack = maybeNextFrame.get().getStack().orElseThrow();
      if (stack != null && stack.length > 0) {
        IntStream.range(0, traceFrame.getStackItemsProduced())
            .forEach(
                i -> {
                  final BytesValue value =
                      BytesValues.trimLeadingZeros(stack[stack.length - i - 1]);
                  ex.addPush(value.isEmpty() || value.isZero() ? "0x0" : value.toShortHexString());
                });
      }
    }

    if ("CALL".equals(traceFrame.getOpcode())) {
      maybeNextFrame.ifPresent(
          nextFrame -> op.setCost(nextFrame.getGasRemaining().toLong() + op.getCost()));
      newSubTrace = new VmTrace();
      parentTraces.addLast(newSubTrace);
      ex.addPush("0x1");
      findFrameAfterReturn(transactionTrace, traceFrame, index.get())
          .ifPresent(frame -> ex.setUsed(frame.getGasRemaining().toLong()));
      op.setSub(newSubTrace);
    }

    // set store from the stack
    if ("SSTORE".equals(traceFrame.getOpcode())) {
      handleSstore(traceFrame, ex);
    }

    if ("RETURN".equals(traceFrame.getOpcode())) {
      currentTrace = parentTraces.removeLast();
    }

    // add the Op representation to the list of traces
    op.setEx(ex);
    currentTrace.add(op);

    index.incrementAndGet();
  }

  /**
   * Find the frame after return to set the gas used of CALL frame.
   *
   * @param trace the root {@link TransactionTrace}
   * @param callFrame the CALL frame
   * @param callIndex the CALL frame index
   * @return an {@link Optional} of {@link TraceFrame} containing the frame after return if found.
   */
  private static Optional<TraceFrame> findFrameAfterReturn(
      final TransactionTrace trace, final TraceFrame callFrame, final int callIndex) {
    final int numberOfFrames = trace.getTraceFrames().size();
    for (int i = callIndex; i < numberOfFrames; i++) {
      final TraceFrame frame = trace.getTraceFrames().get(i);
      if ("RETURN".equals(frame.getOpcode()) && (i + 1) < numberOfFrames) {
        final TraceFrame next = trace.getTraceFrames().get(i + 1);
        if (next.getPc() == (callFrame.getPc() + 1) && next.getDepth() == callFrame.getDepth()) {
          return Optional.of(next);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Handle SSTORE specific opcode. Retrieve elements the stack (key and value).
   *
   * @param traceFrame the trace frame to use.
   * @param ex the Ex object to populate.
   */
  private static void handleSstore(final TraceFrame traceFrame, final Ex ex) {
    ex.setStore(
        traceFrame
            .getStack()
            .map(stack -> new Store(stack[1].toShortHexString(), stack[0].toShortHexString()))
            .orElseThrow());
  }
}

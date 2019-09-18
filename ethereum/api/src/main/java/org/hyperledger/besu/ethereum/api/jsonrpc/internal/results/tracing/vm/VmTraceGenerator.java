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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.TracingUtils;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.uint.UInt256;

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
  private static Trace generateTrace(final TransactionTrace transactionTrace) {
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
    final VmOperation vmOperation = new VmOperation();
    // set gas cost and program counter
    vmOperation.setCost(traceFrame.getGasCost().orElse(Gas.ZERO).toLong());
    vmOperation.setPc(traceFrame.getPc());

    final VmOperationExecutionReport vmOperationExecutionReport = new VmOperationExecutionReport();
    // set gas remaining
    vmOperationExecutionReport.setUsed(
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
          .map(TracingUtils::dumpMemoryAndTrimTrailingZeros)
          .map(Mem::new)
          .ifPresent(vmOperationExecutionReport::setMem);
    }

    // set push from stack elements if some elements have been produced
    if (traceFrame.getStackItemsProduced() > 0 && maybeNextFrame.isPresent()) {
      final Bytes32[] stack = maybeNextFrame.get().getStack().orElseThrow();
      if (stack.length > 0) {
        IntStream.range(0, traceFrame.getStackItemsProduced())
            .forEach(
                i -> {
                  final BytesValue value =
                      BytesValues.trimLeadingZeros(stack[stack.length - i - 1]);
                  vmOperationExecutionReport.addPush(
                      value.isEmpty() || value.isZero()
                          ? "0x0"
                          : UInt256.fromHexString(value.getHexString()).toShortHexString());
                });
      }
    }

    // check if next frame depth has increased i.e the current operation is a call
    if (maybeNextFrame.map(next -> next.isDeeperThan(traceFrame)).orElse(false)) {
      maybeNextFrame.ifPresent(
          nextFrame ->
              vmOperation.setCost(nextFrame.getGasRemaining().toLong() + vmOperation.getCost()));
      newSubTrace = new VmTrace();
      parentTraces.addLast(newSubTrace);
      // TODO: investigate how this hard coded value is necessary
      vmOperationExecutionReport.addPush("0x1");
      findLastFrameInCall(transactionTrace, traceFrame, index.get())
          .ifPresent(frame -> vmOperationExecutionReport.setUsed(frame.getGasRemaining().toLong()));
      vmOperation.setSub(newSubTrace);
    }

    // set store from the stack
    if ("SSTORE".equals(traceFrame.getOpcode())) {
      handleSstore(traceFrame, vmOperationExecutionReport);
    }

    // check if next frame depth has decreased i.e the current operation closes the parent trace
    if (maybeNextFrame.map(next -> next.isLessDeepThan(traceFrame)).orElse(false)) {
      currentTrace = parentTraces.removeLast();
    }

    // add the Op representation to the list of traces
    vmOperation.setVmOperationExecutionReport(vmOperationExecutionReport);
    currentTrace.add(vmOperation);

    index.incrementAndGet();
  }

  /**
   * Find the last frame in the call.
   *
   * @param trace the root {@link TransactionTrace}
   * @param callFrame the CALL frame
   * @param callIndex the CALL frame index
   * @return an {@link Optional} of {@link TraceFrame} containing the last frame in the call.
   */
  private static Optional<TraceFrame> findLastFrameInCall(
      final TransactionTrace trace, final TraceFrame callFrame, final int callIndex) {
    for (int i = callIndex; i < trace.getTraceFrames().size(); i++) {
      if (i + 1 < trace.getTraceFrames().size()) {
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
   * @param vmOperationExecutionReport the Ex object to populate.
   */
  private static void handleSstore(
      final TraceFrame traceFrame, final VmOperationExecutionReport vmOperationExecutionReport) {
    vmOperationExecutionReport.setStore(
        traceFrame
            .getStack()
            .map(
                stack ->
                    new Store(
                        UInt256.wrap(stack[1]).toShortHexString(),
                        UInt256.wrap(stack[0]).toShortHexString()))
            .orElseThrow());
  }
}

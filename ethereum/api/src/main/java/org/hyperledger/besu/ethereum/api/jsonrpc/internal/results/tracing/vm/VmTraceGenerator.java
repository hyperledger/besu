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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class VmTraceGenerator {

  private int currentIndex = 0;
  private VmTrace currentTrace;
  private TraceFrame currentTraceFrame;
  private final TransactionTrace transactionTrace;
  private final VmTrace rootVmTrace = new VmTrace();
  private final Deque<VmTrace> parentTraces = new ArrayDeque<>();

  public VmTraceGenerator(final TransactionTrace transactionTrace) {
    this.transactionTrace = transactionTrace;
  }

  /**
   * Generate a stream of trace result objects.
   *
   * @return a representation of generated traces.
   */
  public Stream<Trace> generateTraceStream() {
    return Stream.of(generateTrace());
  }

  /**
   * Generate trace representation from the specified transaction trace.
   *
   * @return a representation of the trace.
   */
  private Trace generateTrace() {
    parentTraces.add(rootVmTrace);
    if (transactionTrace != null && !transactionTrace.getTraceFrames().isEmpty()) {
      transactionTrace
          .getTransaction()
          .getInit()
          .map(Bytes::toHexString)
          .ifPresent(rootVmTrace::setCode);
      transactionTrace.getTraceFrames().forEach(this::addFrame);
    }
    return rootVmTrace;
  }

  /**
   * Add a trace frame to the VmTrace result object.
   *
   * @param frame the current trace frame
   */
  private void addFrame(final TraceFrame frame) {
    if (mustIgnore(frame)) {
      return;
    }
    initStep(frame);
    final VmOperation op = buildVmOperation();
    final VmOperationExecutionReport report = generateExecutionReport();
    generateTracingMemory(report);
    generateTracingPush(report);
    generateTracingStorage(report);
    handleDepthIncreased(op, report);
    handleDepthDecreased();
    completeStep(op, report);
  }

  private boolean mustIgnore(final TraceFrame frame) {
    if ("STOP".equals(frame.getOpcode()) && transactionTrace.getTraceFrames().size() == 1) {
      return true;
    } else if (!frame.getExceptionalHaltReasons().isEmpty()
        && !frame
            .getExceptionalHaltReasons()
            .contains(ExceptionalHaltReason.INVALID_JUMP_DESTINATION)) {
      return true;
    } else {
      return frame.isVirtualOperation();
    }
  }

  private void completeStep(final VmOperation op, final VmOperationExecutionReport report) {
    // add the operation representation to the list of traces
    op.setVmOperationExecutionReport(report);
    currentTrace.add(op);
    currentIndex++;
  }

  private void handleDepthIncreased(final VmOperation op, final VmOperationExecutionReport report) {
    // check if next frame depth has increased i.e the current operation is a call
    if (currentTraceFrame.depthHasIncreased()) {
      op.setCost(currentTraceFrame.getGasRemainingPostExecution().toLong() + op.getCost());
      final VmTrace newSubTrace = new VmTrace();
      parentTraces.addLast(newSubTrace);
      findLastFrameInCall(currentTraceFrame, currentIndex)
          .ifPresent(
              lastFrameInCall -> {
                report.setUsed(lastFrameInCall.getGasRemaining().toLong());
                lastFrameInCall
                    .getStack()
                    .filter(stack -> stack.length > 0)
                    .map(stack -> stack[stack.length - 1])
                    .map(last -> Quantity.create(UInt256.fromHexString(last.toHexString())))
                    .ifPresent(report::singlePush);
                if (!"DELEGATECALL".equals(currentTraceFrame.getOpcode())) {
                  lastFrameInCall
                      .getMemory()
                      .map(mem -> new Mem(mem[0].toHexString(), 0))
                      .ifPresent(report::setMem);
                }
              });

      op.setSub(newSubTrace);
    }
  }

  private void handleDepthDecreased() {
    // check if next frame depth has decreased i.e the current operation closes the parent trace
    if (currentTraceFrame.depthHasDecreased()) {
      currentTrace = parentTraces.removeLast();
    }
  }

  private VmOperation buildVmOperation() {
    final VmOperation op = new VmOperation();
    // set gas cost and program counter
    op.setCost(currentTraceFrame.getGasCost().orElse(Gas.ZERO).toLong());
    op.setPc(currentTraceFrame.getPc());
    return op;
  }

  private VmOperationExecutionReport generateExecutionReport() {
    final VmOperationExecutionReport report = new VmOperationExecutionReport();
    // set gas remaining
    report.setUsed(
        currentTraceFrame.getGasRemaining().toLong()
            - currentTraceFrame.getGasCost().orElse(Gas.ZERO).toLong());
    return report;
  }

  private void generateTracingMemory(final VmOperationExecutionReport report) {
    currentTraceFrame
        .getMaybeUpdatedMemory()
        .map(
            updatedMemory ->
                new Mem(
                    updatedMemory.getValue().toHexString(), updatedMemory.getOffset().intValue()))
        .ifPresent(report::setMem);
  }

  private void generateTracingPush(final VmOperationExecutionReport report) {
    // set push from stack elements if some elements have been produced
    if (currentTraceFrame.getStackItemsProduced() > 0) {
      currentTraceFrame
          .getStackPostExecution()
          .filter(stack -> stack.length > 0)
          .ifPresent(
              stack ->
                  IntStream.range(0, currentTraceFrame.getStackItemsProduced())
                      .mapToObj(i -> Bytes.wrap(stack[stack.length - i - 1]).trimLeadingZeros())
                      .map(value -> Quantity.create(UInt256.fromHexString(value.toHexString())))
                      .forEach(report::addPush));
    }
  }

  private void generateTracingStorage(final VmOperationExecutionReport report) {
    // set storage if updated
    updatedStorage(currentTraceFrame.getStoragePreExecution(), currentTraceFrame.getStorage())
        .map(
            storageEntry ->
                new Store(
                    storageEntry.key.toShortHexString(), storageEntry.value.toShortHexString()))
        .ifPresent(report::setStore);
  }

  /**
   * Set current trace from parents queue and retrieve next frame.
   *
   * @param frame the trace frame.
   */
  private void initStep(final TraceFrame frame) {
    this.currentTraceFrame = frame;
    currentTrace = parentTraces.getLast();
    // set smart contract code
    currentTrace.setCode(
        currentTraceFrame.getMaybeCode().orElse(new Code()).getBytes().toHexString());
  }

  /**
   * Find updated storage from 2 storage captures.
   *
   * @param firstCapture The first storage capture.
   * @param secondCapture The second storage capture.
   * @return an {@link Optional} wrapping the diff.
   */
  private Optional<StorageEntry> updatedStorage(
      final Optional<Map<UInt256, UInt256>> firstCapture,
      final Optional<Map<UInt256, UInt256>> secondCapture) {
    final Map<UInt256, UInt256> first = firstCapture.orElse(new HashMap<>());
    final Map<UInt256, UInt256> second = secondCapture.orElse(new HashMap<>());
    final MapDifference<UInt256, UInt256> diff = Maps.difference(first, second);
    final Map<UInt256, MapDifference.ValueDifference<UInt256>> entriesDiffering =
        diff.entriesDiffering();
    if (entriesDiffering.size() > 0) {
      final UInt256 firstDiffKey = entriesDiffering.keySet().iterator().next();
      final MapDifference.ValueDifference<UInt256> firstDiff = entriesDiffering.get(firstDiffKey);
      return Optional.of(new StorageEntry(firstDiffKey, firstDiff.rightValue()));
    }
    final Map<UInt256, UInt256> onlyOnRight = diff.entriesOnlyOnRight();
    if (onlyOnRight.size() > 0) {
      final UInt256 firstOnlyOnRightKey = onlyOnRight.keySet().iterator().next();
      return Optional.of(
          new StorageEntry(firstOnlyOnRightKey, onlyOnRight.get(firstOnlyOnRightKey)));
    }
    return Optional.empty();
  }

  /**
   * Find the last frame in the call.
   *
   * @param callFrame the CALL frame
   * @param callIndex the CALL frame index
   * @return an {@link Optional} of {@link TraceFrame} containing the last frame in the call.
   */
  private Optional<TraceFrame> findLastFrameInCall(
      final TraceFrame callFrame, final int callIndex) {
    for (int i = callIndex; i < transactionTrace.getTraceFrames().size(); i++) {
      if (i + 1 < transactionTrace.getTraceFrames().size()) {
        final TraceFrame next = transactionTrace.getTraceFrames().get(i + 1);
        if (next.getPc() == (callFrame.getPc() + 1) && next.getDepth() == callFrame.getDepth()) {
          return Optional.of(next);
        }
      }
    }
    return Optional.empty();
  }

  static class StorageEntry {
    private final UInt256 key;
    private final UInt256 value;

    StorageEntry(final UInt256 key, final UInt256 value) {
      this.key = key;
      this.value = value;
    }
  }
}

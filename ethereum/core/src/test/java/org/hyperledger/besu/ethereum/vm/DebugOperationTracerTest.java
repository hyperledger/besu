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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.tracing.OpCodeTracerConfigBuilder;
import org.hyperledger.besu.evm.tracing.OpCodeTracerConfigBuilder.OpCodeTracerConfig;
import org.hyperledger.besu.evm.tracing.TraceFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebugOperationTracerTest {

  private static final int DEPTH = 4;
  private static final long INITIAL_GAS = 1000L;
  private static final Bytes32 WORD_1 = Bytes32.fromHexString("0x" + "aa".repeat(32));
  private static final Bytes32 WORD_2 = Bytes32.fromHexString("0x" + "bb".repeat(32));

  @Mock private WorldUpdater worldUpdater;

  // @Mock private OperationTracer.ExecuteOperation executeOperationAction;

  private final Operation anOperation =
      new AbstractOperation(0x02, "MUL", 2, 1, null) {
        @Override
        public OperationResult execute(final MessageFrame frame, final EVM evm) {
          return new OperationResult(20L, null);
        }
      };

  private final CallOperation callOperation = new CallOperation(new CancunGasCalculator());

  @Test
  void shouldRecordProgramCounter() {
    final MessageFrame frame = validMessageFrame();
    frame.setPC(10);
    final TraceFrame traceFrame = traceFrame(frame);

    assertThat(traceFrame.getPc()).isEqualTo(10);
  }

  @Test
  void shouldRecordOpcode() {
    final MessageFrame frame = validMessageFrame();
    final TraceFrame traceFrame = traceFrame(frame);
    assertThat(traceFrame.getOpcode()).isEqualTo("MUL");
  }

  @Test
  void shouldRecordDepth() {
    final MessageFrame frame = validMessageFrame();
    // simulate 4 calls
    frame.getMessageFrameStack().add(frame);
    frame.getMessageFrameStack().add(frame);
    frame.getMessageFrameStack().add(frame);
    frame.getMessageFrameStack().add(frame);
    final TraceFrame traceFrame = traceFrame(frame);
    assertThat(traceFrame.getDepth()).isEqualTo(DEPTH);
  }

  @Test
  void shouldRecordRemainingGas() {
    final MessageFrame frame = validMessageFrame();
    //    final Gas currentGasCost = Gas.of(50);
    final TraceFrame traceFrame = traceFrame(frame);
    assertThat(traceFrame.getGasRemaining()).isEqualTo(INITIAL_GAS);
  }

  @Test
  void shouldRecordStackWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final UInt256 stackItem1 = UInt256.fromHexString("0x01");
    final UInt256 stackItem2 = UInt256.fromHexString("0x02");
    final UInt256 stackItem3 = UInt256.fromHexString("0x03");
    frame.pushStackItem(stackItem1);
    frame.pushStackItem(stackItem2);
    frame.pushStackItem(stackItem3);
    final TraceFrame traceFrame =
        traceFrame(
            frame,
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(true)
                .build(),
            false);
    assertThat(traceFrame.getStack()).isPresent();
    assertThat(traceFrame.getStack().get()).containsExactly(stackItem1, stackItem2, stackItem3);
  }

  @Test
  void shouldNotRecordStackWhenDisabled() {
    final TraceFrame traceFrame =
        traceFrame(
            validMessageFrame(),
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            false);
    assertThat(traceFrame.getStack()).isEmpty();
  }

  @Test
  void shouldRecordMemoryWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final Bytes32 word1 = Bytes32.fromHexString("0x01");
    final Bytes32 word2 = Bytes32.fromHexString("0x02");
    final Bytes32 word3 = Bytes32.fromHexString("0x03");
    frame.writeMemory(0, 32, word1);
    frame.writeMemory(32, 32, word2);
    frame.writeMemory(64, 32, word3);
    final TraceFrame traceFrame =
        traceFrame(
            frame,
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(true)
                .traceStack(false)
                .build(),
            false);
    assertThat(traceFrame.getMemory()).isPresent();
    assertThat(traceFrame.getMemory().get()).containsExactly(word1, word2, word3);
  }

  @Test
  void shouldNotRecordMemoryWhenDisabled() {
    final TraceFrame traceFrame =
        traceFrame(
            validMessageFrame(),
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            false);
    assertThat(traceFrame.getMemory()).isEmpty();
  }

  @Test
  void shouldRecordStorageWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);
    final TraceFrame traceFrame =
        traceFrame(
            frame,
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(true)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            false);
    assertThat(traceFrame.getStorage()).isPresent();
    assertThat(traceFrame.getStorage()).contains(updatedStorage);
  }

  @Test
  void shouldNotRecordStorageWhenDisabled() {
    final TraceFrame traceFrame =
        traceFrame(
            validMessageFrame(),
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            false);
    assertThat(traceFrame.getStorage()).isEmpty();
  }

  @Test
  void shouldNotAddGasWhenDisabled() {
    final TraceFrame traceFrame =
        traceFrame(
            validCallFrame(),
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            false);
    assertThat(traceFrame.getGasCost()).isEqualTo(OptionalLong.of(20));
  }

  @Test
  void shouldAddGasWhenEnabled() {
    final TraceFrame traceFrame =
        traceFrame(
            validCallFrame(),
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            true);
    assertThat(traceFrame.getGasCost()).isEqualTo(OptionalLong.of(1020L));
  }

  @Test
  void childGasFlagDoesNotMatterForNonCallOperations() {
    final TraceFrame flagDisabledTracer =
        traceFrame(
            validMessageFrame(),
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            false);
    final TraceFrame flagEnabledTracer =
        traceFrame(
            validMessageFrame(),
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(false)
                .traceMemory(false)
                .traceStack(false)
                .build(),
            true);

    assertThat(flagEnabledTracer.getGasCost()).isEqualTo(flagDisabledTracer.getGasCost());
  }

  @Test
  void shouldCaptureFrameWhenExceptionalHaltOccurs() {
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);

    final DebugOperationTracer tracer =
        new DebugOperationTracer(
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                .traceStorage(true)
                .traceMemory(true)
                .traceStack(true)
                .build(),
            false);
    tracer.tracePreExecution(frame);
    tracer.tracePostExecution(
        frame, new OperationResult(50L, ExceptionalHaltReason.INSUFFICIENT_GAS));

    final TraceFrame traceFrame = getOnlyTraceFrame(tracer);
    assertThat(traceFrame.getExceptionalHaltReason())
        .contains(ExceptionalHaltReason.INSUFFICIENT_GAS);
    assertThat(traceFrame.getStorage()).contains(updatedStorage);
  }

  @Test
  void shouldUseLastSnapshotInNonMemoryWriteOperation() {
    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.writeMemory(0L, 32, WORD_1);

    final DebugOperationTracer tracer = createDebugOperationTracerWithMemory();

    // Frame 0: non-memory-writing op — captures initial snapshot
    traceFrame(frame, tracer, anOperation);
    // Frame 1: another non-memory-writing op — should reuse last snapshot, not re-capture
    traceFrame(frame, tracer, anOperation);

    final List<TraceFrame> frames = tracer.getTraceFrames();
    assertThat(frames).hasSize(2);

    final Bytes[] frame0Memory = frames.get(0).getMemory().get();
    final Bytes[] frame1Memory = frames.get(1).getMemory().get();

    assertThat(frame1Memory)
        .as("For a non-memory-writing opcode, the last snapshot must be reused")
        .isSameAs(frame0Memory);
  }

  @Test
  void shouldTakeNewMemorySnapshotWhenMemorySizeGrowsWithoutExplicitWrite() {
    // Safety-net check: even with no explicit-update flag and the same depth, if memory
    // has grown between frames (e.g. expanded by a read touching a new page) the size guard
    // lastFrame.getMemory().map(m -> m.length).orElse(0) == frame.memoryWordSize()
    // must prevent stale snapshot reuse.

    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.writeMemory(0L, 32, WORD_1); // 1 word

    final DebugOperationTracer tracer = createDebugOperationTracerWithMemory();

    // Frame 0: 1 word in memory
    traceFrame(frame, tracer, anOperation);

    // Memory grows to 2 words without an explicit-update flag (simulates e.g. MLOAD
    // touching a new page, which expands memory but does not set the flag)
    frame.writeMemory(32L, 32, WORD_2); // now 2 words, no explicit-update flag
    traceFrame(frame, tracer, anOperation);

    final List<TraceFrame> frames = tracer.getTraceFrames();
    assertThat(frames).hasSize(2);

    final Bytes[] snapshot0 = frames.get(0).getMemory().get();
    final Bytes[] snapshot1 = frames.get(1).getMemory().get();

    assertThat(snapshot1)
        .as("When memory size grows, a fresh snapshot must be taken even without an explicit write")
        .isNotSameAs(snapshot0);
    assertThat(snapshot0).hasSize(1);
    assertThat(snapshot1).hasSize(2);
    assertThat(snapshot1[1]).isEqualTo(WORD_2);
  }

  @Test
  void shouldTakeNewMemorySnapshotAfterExplicitMemoryWrite() {
    final Operation memoryWritingOp =
        new AbstractOperation(0x52, "MSTORE", 2, 0, null) {
          @Override
          public OperationResult execute(final MessageFrame frame, final EVM evm) {
            // explicitMemoryUpdate=true simulates what MSTORE does in the real EVM
            frame.writeMemory(0L, 32, WORD_2, true);
            return new OperationResult(3L, null);
          }
        };

    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.writeMemory(0L, 32, WORD_1);

    final DebugOperationTracer tracer = createDebugOperationTracerWithMemory();

    // Frame 0: non-memory-writing op
    traceFrame(frame, tracer, anOperation);

    // Frame 1: memory-writing op
    frame.setCurrentOperation(memoryWritingOp);
    traceFrame(frame, tracer, memoryWritingOp);

    final List<TraceFrame> frames = tracer.getTraceFrames();
    assertThat(frames).hasSize(2);

    final Bytes[] before = frames.get(0).getMemory().get();
    final Bytes[] after = frames.get(1).getMemory().get();

    // A memory write must produce a distinct snapshot with updated content
    assertThat(after)
        .as("After a memory-writing opcode, a new memory snapshot must be taken")
        .isNotSameAs(before);
    assertThat(before[0]).isEqualTo(WORD_1);
    assertThat(after[0]).isEqualTo(WORD_2);
  }

  @Test
  void shouldCaptureMemoryDirectlyWhenLastFrameIsNull() {
    // captureMemory is invoked with lastFrame == null (first trace ever).
    // The early-return path (reuse lastFrame.getMemory()) must be skipped and
    // memory must be read fresh from the frame even when getMaybeUpdatedMemory() is empty.
    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.writeMemory(0L, 32, WORD_1); // no explicit-update flag → getMaybeUpdatedMemory() is empty

    final DebugOperationTracer tracer = createDebugOperationTracerWithMemory();
    traceFrame(frame, tracer, anOperation);

    final List<TraceFrame> frames = tracer.getTraceFrames();
    assertThat(frames).hasSize(1);
    assertThat(frames.get(0).getMemory()).isPresent();
    assertThat(frames.get(0).getMemory().get()).containsExactly(WORD_1);
  }

  @Test
  void shouldHandleCallReturnScenario() {
    // Full CALL/RETURN lifecycle: parent (depth 0) → child (depth 1) → parent (depth 0).
    //
    // Any depth change — whether entering a CALL (0→1) or returning from one (1→0) —
    // must trigger a fresh memory snapshot, because lastFrame.depth != frame.depth.
    // This prevents both cross-frame snapshot reuse and child-memory leaking into the parent.
    final DebugOperationTracer tracer = createDebugOperationTracerWithMemory();

    // --- Step 1: parent frame before CALL (depth 0) ---
    final MessageFrame parentFrame = validMessageFrameBuilder().build();
    parentFrame.writeMemory(0L, 32, WORD_1);
    parentFrame.setCurrentOperation(anOperation);

    traceFrame(parentFrame, tracer, anOperation);

    // --- Step 2: child frame during CALL (depth 1) ---
    // lastFrame.depth(0) != frame.depth(1) → fresh snapshot must be taken from the child
    final MessageFrame childFrame = validMessageFrameBuilder().build();
    childFrame.writeMemory(0L, 32, WORD_2);
    childFrame.getMessageFrameStack().add(childFrame); // raises depth to 1

    traceFrame(childFrame, tracer, anOperation);

    // --- Step 3: parent frame resumes after RETURN (depth 0) ---
    // getMaybeUpdatedMemory() is empty (reset() cleared it after step 1),
    // lastFrame.depth(1) != frame.depth(0) → fresh snapshot must be read from parentFrame
    traceFrame(parentFrame, tracer, anOperation);

    final List<TraceFrame> frames = tracer.getTraceFrames();
    assertThat(frames).hasSize(3);

    final Bytes[] parentSnapshot = frames.get(0).getMemory().get();
    final Bytes[] childSnapshot = frames.get(1).getMemory().get();
    final Bytes[] parentAfterReturn = frames.get(2).getMemory().get();

    // On CALL entry (depth 0→1): child must get its own fresh snapshot, not reuse parent's
    assertThat(childSnapshot)
        .as("On CALL entry, child must get a fresh snapshot — not reuse the parent's")
        .isNotSameAs(parentSnapshot);
    assertThat(childSnapshot[0]).isEqualTo(WORD_2);

    // After RETURN (depth 1→0): parent memory must be freshly captured, not the child's snapshot
    assertThat(parentAfterReturn)
        .as("After RETURN, parent memory must be freshly captured — not the child's snapshot")
        .isNotSameAs(childSnapshot);
    assertThat(parentAfterReturn[0])
        .as("After RETURN, parent memory must reflect the parent frame's actual content")
        .isEqualTo(WORD_1);
  }

  private TraceFrame traceFrame(final MessageFrame frame) {
    return traceFrame(
        frame,
        OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
            .traceStorage(false)
            .traceMemory(false)
            .traceStack(false)
            .build(),
        false);
  }

  private TraceFrame traceFrame(
      final MessageFrame frame,
      final OpCodeTracerConfig traceOptions,
      final boolean additionalCallGas) {
    final DebugOperationTracer tracer = new DebugOperationTracer(traceOptions, additionalCallGas);
    tracer.tracePreExecution(frame);
    OperationResult operationResult = anOperation.execute(frame, null);
    tracer.tracePostExecution(frame, operationResult);
    return getOnlyTraceFrame(tracer);
  }

  private void traceFrame(
          final MessageFrame frame,
          final DebugOperationTracer tracer,
          final Operation operation) {
    frame.setCurrentOperation(operation);
    tracer.tracePreExecution(frame);
    OperationResult operationResult = operation.execute(frame, null);
    tracer.tracePostExecution(frame, operationResult);
  }

  private MessageFrame validMessageFrame() {
    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.setCurrentOperation(anOperation);
    frame.setPC(10);
    return frame;
  }

  private MessageFrame validCallFrame() {
    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.setCurrentOperation(callOperation);
    frame.setPC(10);
    return frame;
  }

  private TraceFrame getOnlyTraceFrame(final DebugOperationTracer tracer) {
    Assertions.assertThat(tracer.getTraceFrames()).hasSize(1);
    return tracer.getTraceFrames().get(0);
  }

  private MessageFrameTestFixture validMessageFrameBuilder() {
    final BlockHeader blockHeader = new BlockHeaderTestFixture().number(1).buildHeader();
    final ReferenceTestBlockchain blockchain = new ReferenceTestBlockchain(blockHeader.getNumber());
    return new MessageFrameTestFixture()
        .initialGas(INITIAL_GAS)
        .worldUpdater(worldUpdater)
        .executionContextTestFixture(ExecutionContextTestFixture.create())
        .gasPrice(Wei.of(25))
        .blockHeader(blockHeader)
        .blockchain(blockchain);
  }

  private Map<UInt256, UInt256> setupStorageForCapture(final MessageFrame frame) {
    final MutableAccount account = mock(MutableAccount.class);
    when(worldUpdater.getAccount(frame.getRecipientAddress())).thenReturn(account);

    final Map<UInt256, UInt256> updatedStorage = new TreeMap<>();
    updatedStorage.put(UInt256.ZERO, UInt256.valueOf(233));
    updatedStorage.put(UInt256.ONE, UInt256.valueOf(2424));
    when(account.getUpdatedStorage()).thenReturn(updatedStorage);
    final Bytes32 word1 = Bytes32.fromHexString("0x01");
    final Bytes32 word2 = Bytes32.fromHexString("0x02");
    final Bytes32 word3 = Bytes32.fromHexString("0x03");
    frame.writeMemory(0, 32, word1);
    frame.writeMemory(32, 32, word2);
    frame.writeMemory(64, 32, word3);
    return updatedStorage;
  }

  private static DebugOperationTracer createDebugOperationTracerWithMemory() {
    final OpCodeTracerConfig config =
            OpCodeTracerConfigBuilder.createFrom(OpCodeTracerConfig.DEFAULT)
                    .traceMemory(true)
                    .traceStack(false)
                    .traceStorage(false)
                    .build();
    return new DebugOperationTracer(config, false);
  }
}

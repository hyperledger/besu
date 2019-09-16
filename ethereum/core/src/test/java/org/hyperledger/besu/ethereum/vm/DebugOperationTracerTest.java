/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DebugOperationTracerTest {

  private static final int DEPTH = 4;
  private static final Gas INITIAL_GAS = Gas.of(1000);

  @Mock private WorldUpdater worldUpdater;

  @Mock private OperationTracer.ExecuteOperation executeOperationAction;

  private final Operation anOperation =
      new AbstractOperation(0x02, "MUL", 2, 1, false, 1, null) {
        @Override
        public Gas cost(final MessageFrame frame) {
          return Gas.of(20);
        }

        @Override
        public void execute(final MessageFrame frame) {}
      };

  @Test
  public void shouldRecordProgramCounter() throws Exception {
    final MessageFrame frame = validMessageFrame();
    frame.setPC(10);
    final TraceFrame traceFrame = traceFrame(frame, Gas.of(50));

    assertThat(traceFrame.getPc()).isEqualTo(10);
  }

  @Test
  public void shouldRecordOpcode() throws Exception {
    final MessageFrame frame = validMessageFrame();
    final TraceFrame traceFrame = traceFrame(frame, Gas.of(50));
    assertThat(traceFrame.getOpcode()).isEqualTo("MUL");
  }

  @Test
  public void shouldRecordDepth() throws Exception {
    final MessageFrame frame = validMessageFrame();
    final TraceFrame traceFrame = traceFrame(frame, Gas.of(50));
    assertThat(traceFrame.getDepth()).isEqualTo(DEPTH);
  }

  @Test
  public void shouldRecordRemainingGas() throws Exception {
    final MessageFrame frame = validMessageFrame();
    final Gas currentGasCost = Gas.of(50);
    final TraceFrame traceFrame = traceFrame(frame, currentGasCost);
    assertThat(traceFrame.getGasRemaining()).isEqualTo(INITIAL_GAS);
  }

  @Test
  public void shouldRecordStackWhenEnabled() throws Exception {
    final MessageFrame frame = validMessageFrame();
    final Bytes32 stackItem1 = Bytes32.fromHexString("0x01");
    final Bytes32 stackItem2 = Bytes32.fromHexString("0x02");
    final Bytes32 stackItem3 = Bytes32.fromHexString("0x03");
    frame.pushStackItem(stackItem1);
    frame.pushStackItem(stackItem2);
    frame.pushStackItem(stackItem3);
    final TraceFrame traceFrame = traceFrame(frame, Gas.ZERO, new TraceOptions(false, false, true));
    assertThat(traceFrame.getStack()).isPresent();
    assertThat(traceFrame.getStack().get()).containsExactly(stackItem1, stackItem2, stackItem3);
  }

  @Test
  public void shouldNotRecordStackWhenDisabled() throws Exception {
    final TraceFrame traceFrame =
        traceFrame(validMessageFrame(), Gas.ZERO, new TraceOptions(false, false, false));
    assertThat(traceFrame.getStack()).isEmpty();
  }

  @Test
  public void shouldRecordMemoryWhenEnabled() throws Exception {
    final MessageFrame frame = validMessageFrame();
    final Bytes32 word1 = Bytes32.fromHexString("0x01");
    final Bytes32 word2 = Bytes32.fromHexString("0x02");
    final Bytes32 word3 = Bytes32.fromHexString("0x03");
    frame.writeMemory(UInt256.ZERO, UInt256.of(32), word1);
    frame.writeMemory(UInt256.of(32), UInt256.of(32), word2);
    frame.writeMemory(UInt256.of(64), UInt256.of(32), word3);
    final TraceFrame traceFrame = traceFrame(frame, Gas.ZERO, new TraceOptions(false, true, false));
    assertThat(traceFrame.getMemory()).isPresent();
    assertThat(traceFrame.getMemory().get()).containsExactly(word1, word2, word3);
  }

  @Test
  public void shouldNotRecordMemoryWhenDisabled() throws Exception {
    final TraceFrame traceFrame =
        traceFrame(validMessageFrame(), Gas.ZERO, new TraceOptions(false, false, false));
    assertThat(traceFrame.getMemory()).isEmpty();
  }

  @Test
  public void shouldRecordStorageWhenEnabled() throws Exception {
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);
    final TraceFrame traceFrame = traceFrame(frame, Gas.ZERO, new TraceOptions(true, false, false));
    assertThat(traceFrame.getStorage()).isPresent();
    assertThat(traceFrame.getStorage().get()).isEqualTo(updatedStorage);
  }

  @Test
  public void shouldNotRecordStorageWhenDisabled() throws Exception {
    final TraceFrame traceFrame =
        traceFrame(validMessageFrame(), Gas.ZERO, new TraceOptions(false, false, false));
    assertThat(traceFrame.getStorage()).isEmpty();
  }

  @Test
  public void shouldCaptureFrameWhenExceptionalHaltOccurs() throws Exception {
    final EnumSet<ExceptionalHaltReason> expectedHaltReasons =
        EnumSet.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
    final ExceptionalHaltException expectedException =
        new ExceptionalHaltException(expectedHaltReasons);
    doThrow(expectedException).when(executeOperationAction).execute();
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);

    final DebugOperationTracer tracer =
        new DebugOperationTracer(new TraceOptions(true, true, true));
    assertThatThrownBy(
            () -> tracer.traceExecution(frame, Optional.of(Gas.of(50)), executeOperationAction))
        .isSameAs(expectedException);

    final TraceFrame traceFrame = getOnlyTraceFrame(tracer);
    assertThat(traceFrame.getStorage()).contains(updatedStorage);
  }

  private TraceFrame traceFrame(final MessageFrame frame, final Gas currentGasCost)
      throws Exception {
    return traceFrame(frame, currentGasCost, new TraceOptions(false, false, false));
  }

  private TraceFrame traceFrame(
      final MessageFrame frame, final Gas currentGasCost, final TraceOptions traceOptions)
      throws Exception {
    final DebugOperationTracer tracer = new DebugOperationTracer(traceOptions);
    tracer.traceExecution(frame, Optional.of(currentGasCost), executeOperationAction);
    return getOnlyTraceFrame(tracer);
  }

  private MessageFrame validMessageFrame() {
    final MessageFrame frame = validMessageFrameBuilder().build();
    frame.setCurrentOperation(anOperation);
    frame.setPC(10);
    return frame;
  }

  private TraceFrame getOnlyTraceFrame(final DebugOperationTracer tracer) {
    Assertions.assertThat(tracer.getTraceFrames()).hasSize(1);
    return tracer.getTraceFrames().get(0);
  }

  private MessageFrameTestFixture validMessageFrameBuilder() {
    final BlockHeader blockHeader = new BlockHeaderTestFixture().number(1).buildHeader();
    final TestBlockchain blockchain = new TestBlockchain(blockHeader.getNumber());
    return new MessageFrameTestFixture()
        .initialGas(INITIAL_GAS)
        .worldState(worldUpdater)
        .gasPrice(Wei.of(25))
        .blockHeader(blockHeader)
        .blockchain(blockchain)
        .depth(DEPTH);
  }

  private Map<UInt256, UInt256> setupStorageForCapture(final MessageFrame frame) {
    final MutableAccount account = mock(MutableAccount.class);
    when(worldUpdater.getMutable(frame.getRecipientAddress())).thenReturn(account);

    final Map<UInt256, UInt256> updatedStorage = new TreeMap<>();
    updatedStorage.put(UInt256.ZERO, UInt256.of(233));
    updatedStorage.put(UInt256.ONE, UInt256.of(2424));
    when(account.getUpdatedStorage()).thenReturn(updatedStorage);
    final Bytes32 word1 = Bytes32.fromHexString("0x01");
    final Bytes32 word2 = Bytes32.fromHexString("0x02");
    final Bytes32 word3 = Bytes32.fromHexString("0x03");
    frame.writeMemory(UInt256.ZERO, UInt256.of(32), word1);
    frame.writeMemory(UInt256.of(32), UInt256.of(32), word2);
    frame.writeMemory(UInt256.of(64), UInt256.of(32), word3);
    return updatedStorage;
  }
}

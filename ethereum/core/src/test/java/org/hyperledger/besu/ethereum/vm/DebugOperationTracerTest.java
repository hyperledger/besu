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
import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DebugOperationTracerTest {

  private static final int DEPTH = 4;
  private static final long INITIAL_GAS = 1000L;

  @Mock private WorldUpdater worldUpdater;

  // @Mock private OperationTracer.ExecuteOperation executeOperationAction;

  private final Operation anOperation =
      new AbstractOperation(0x02, "MUL", 2, 1, 1, null) {
        @Override
        public OperationResult execute(final MessageFrame frame, final EVM evm) {
          return new OperationResult(OptionalLong.of(20L), Optional.empty());
        }
      };

  @Test
  public void shouldRecordProgramCounter() {
    final MessageFrame frame = validMessageFrame();
    frame.setPC(10);
    final TraceFrame traceFrame = traceFrame(frame);

    assertThat(traceFrame.getPc()).isEqualTo(10);
  }

  @Test
  public void shouldRecordOpcode() {
    final MessageFrame frame = validMessageFrame();
    final TraceFrame traceFrame = traceFrame(frame);
    assertThat(traceFrame.getOpcode()).isEqualTo("MUL");
  }

  @Test
  public void shouldRecordDepth() {
    final MessageFrame frame = validMessageFrame();
    final TraceFrame traceFrame = traceFrame(frame);
    assertThat(traceFrame.getDepth()).isEqualTo(DEPTH);
  }

  @Test
  public void shouldRecordRemainingGas() {
    final MessageFrame frame = validMessageFrame();
    //    final Gas currentGasCost = Gas.of(50);
    final TraceFrame traceFrame = traceFrame(frame);
    assertThat(traceFrame.getGasRemaining()).isEqualTo(INITIAL_GAS);
  }

  @Test
  public void shouldRecordStackWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final UInt256 stackItem1 = UInt256.fromHexString("0x01");
    final UInt256 stackItem2 = UInt256.fromHexString("0x02");
    final UInt256 stackItem3 = UInt256.fromHexString("0x03");
    frame.pushStackItem(stackItem1);
    frame.pushStackItem(stackItem2);
    frame.pushStackItem(stackItem3);
    final TraceFrame traceFrame = traceFrame(frame, new TraceOptions(false, false, true));
    assertThat(traceFrame.getStack()).isPresent();
    assertThat(traceFrame.getStack().get()).containsExactly(stackItem1, stackItem2, stackItem3);
  }

  @Test
  public void shouldNotRecordStackWhenDisabled() {
    final TraceFrame traceFrame =
        traceFrame(validMessageFrame(), new TraceOptions(false, false, false));
    assertThat(traceFrame.getStack()).isEmpty();
  }

  @Test
  public void shouldRecordMemoryWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final Bytes32 word1 = Bytes32.fromHexString("0x01");
    final Bytes32 word2 = Bytes32.fromHexString("0x02");
    final Bytes32 word3 = Bytes32.fromHexString("0x03");
    frame.writeMemory(0, 32, word1);
    frame.writeMemory(32, 32, word2);
    frame.writeMemory(64, 32, word3);
    final TraceFrame traceFrame = traceFrame(frame, new TraceOptions(false, true, false));
    assertThat(traceFrame.getMemory()).isPresent();
    assertThat(traceFrame.getMemory().get()).containsExactly(word1, word2, word3);
  }

  @Test
  public void shouldNotRecordMemoryWhenDisabled() {
    final TraceFrame traceFrame =
        traceFrame(validMessageFrame(), new TraceOptions(false, false, false));
    assertThat(traceFrame.getMemory()).isEmpty();
  }

  @Test
  public void shouldRecordStorageWhenEnabled() {
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);
    final TraceFrame traceFrame = traceFrame(frame, new TraceOptions(true, false, false));
    assertThat(traceFrame.getStorage()).isPresent();
    assertThat(traceFrame.getStorage().get()).isEqualTo(updatedStorage);
  }

  @Test
  public void shouldNotRecordStorageWhenDisabled() {
    final TraceFrame traceFrame =
        traceFrame(validMessageFrame(), new TraceOptions(false, false, false));
    assertThat(traceFrame.getStorage()).isEmpty();
  }

  @Test
  public void shouldCaptureFrameWhenExceptionalHaltOccurs() {
    final MessageFrame frame = validMessageFrame();
    final Map<UInt256, UInt256> updatedStorage = setupStorageForCapture(frame);

    final DebugOperationTracer tracer =
        new DebugOperationTracer(new TraceOptions(true, true, true));
    tracer.traceExecution(
        frame,
        () ->
            new OperationResult(
                OptionalLong.of(50L), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS)));

    final TraceFrame traceFrame = getOnlyTraceFrame(tracer);
    assertThat(traceFrame.getExceptionalHaltReason())
        .contains(ExceptionalHaltReason.INSUFFICIENT_GAS);
    assertThat(traceFrame.getStorage()).contains(updatedStorage);
  }

  private TraceFrame traceFrame(final MessageFrame frame) {
    return traceFrame(frame, new TraceOptions(false, false, false));
  }

  private TraceFrame traceFrame(final MessageFrame frame, final TraceOptions traceOptions) {
    final DebugOperationTracer tracer = new DebugOperationTracer(traceOptions);
    tracer.traceExecution(frame, () -> anOperation.execute(frame, null));
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
    final ReferenceTestBlockchain blockchain = new ReferenceTestBlockchain(blockHeader.getNumber());
    return new MessageFrameTestFixture()
        .initialGas(INITIAL_GAS)
        .worldUpdater(worldUpdater)
        .gasPrice(Wei.of(25))
        .blockHeader(blockHeader)
        .blockchain(blockchain)
        .depth(DEPTH);
  }

  private Map<UInt256, UInt256> setupStorageForCapture(final MessageFrame frame) {
    final WrappedEvmAccount account = mock(WrappedEvmAccount.class);
    final MutableAccount mutableAccount = mock(MutableAccount.class);
    when(account.getMutable()).thenReturn(mutableAccount);
    when(worldUpdater.getAccount(frame.getRecipientAddress())).thenReturn(account);

    final Map<UInt256, UInt256> updatedStorage = new TreeMap<>();
    updatedStorage.put(UInt256.ZERO, UInt256.valueOf(233));
    updatedStorage.put(UInt256.ONE, UInt256.valueOf(2424));
    when(mutableAccount.getUpdatedStorage()).thenReturn(updatedStorage);
    final Bytes32 word1 = Bytes32.fromHexString("0x01");
    final Bytes32 word2 = Bytes32.fromHexString("0x02");
    final Bytes32 word3 = Bytes32.fromHexString("0x03");
    frame.writeMemory(0, 32, word1);
    frame.writeMemory(32, 32, word2);
    frame.writeMemory(64, 32, word3);
    return updatedStorage;
  }
}

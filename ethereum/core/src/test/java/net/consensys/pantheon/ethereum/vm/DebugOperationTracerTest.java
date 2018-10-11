package net.consensys.pantheon.ethereum.vm;

import static net.consensys.pantheon.ethereum.core.AddressHelpers.calculateAddressWithRespectTo;
import static net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.MutableAccount;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.core.WorldUpdater;
import net.consensys.pantheon.ethereum.debug.TraceFrame;
import net.consensys.pantheon.ethereum.debug.TraceOptions;
import net.consensys.pantheon.ethereum.vm.OperationTracer.ExecuteOperation;
import net.consensys.pantheon.ethereum.vm.ehalt.ExceptionalHaltException;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DebugOperationTracerTest {

  private static final int DEPTH = 4;
  private static final Gas INITIAL_GAS = Gas.of(1000);

  @Mock private WorldUpdater worldUpdater;

  @Mock private ExecuteOperation executeOperationAction;

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
    final EnumSet<ExceptionalHaltReason> expectedHaltReasons = EnumSet.of(INSUFFICIENT_GAS);
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
    assertThat(tracer.getTraceFrames()).hasSize(1);
    return tracer.getTraceFrames().get(0);
  }

  private MessageFrame.Builder validMessageFrameBuilder() {
    return MessageFrame.builder()
        .type(MessageFrame.Type.MESSAGE_CALL)
        .messageFrameStack(new ArrayDeque<>())
        .blockchain(new TestBlockchain())
        .worldState(worldUpdater)
        .initialGas(INITIAL_GAS)
        .contract(calculateAddressWithRespectTo(Address.ID, 1))
        .address(calculateAddressWithRespectTo(Address.ID, 2))
        .originator(calculateAddressWithRespectTo(Address.ID, 3))
        .gasPrice(Wei.of(25))
        .inputData(BytesValue.EMPTY)
        .sender(calculateAddressWithRespectTo(Address.ID, 4))
        .value(Wei.of(30))
        .apparentValue(Wei.of(35))
        .code(new Code())
        .blockHeader(new BlockHeaderTestFixture().buildHeader())
        .depth(DEPTH)
        .completer(c -> {})
        .miningBeneficiary(AddressHelpers.ofValue(0));
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

package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.LogTopic;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.EnumSet;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

public class LogOperation extends AbstractOperation {

  private final int numTopics;

  public LogOperation(final int numTopics, final GasCalculator gasCalculator) {
    super(0xA0 + numTopics, "LOG" + numTopics, numTopics + 2, 0, false, 1, gasCalculator);
    this.numTopics = numTopics;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 dataOffset = frame.getStackItem(0).asUInt256();
    final UInt256 dataLength = frame.getStackItem(1).asUInt256();

    return gasCalculator().logOperationGasCost(frame, dataOffset, dataLength, numTopics);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Address address = frame.getRecipientAddress();

    final UInt256 dataLocation = frame.popStackItem().asUInt256();
    final UInt256 numBytes = frame.popStackItem().asUInt256();
    final BytesValue data = frame.readMemory(dataLocation, numBytes);

    final ImmutableList.Builder<LogTopic> builder =
        ImmutableList.builderWithExpectedSize(numTopics);
    for (int i = 0; i < numTopics; i++) {
      builder.add(LogTopic.of(frame.popStackItem()));
    }

    frame.addLog(new Log(address, data, builder.build()));
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    return frame.isStatic()
        ? Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE)
        : Optional.empty();
  }
}

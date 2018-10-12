package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.uint.UInt256Bytes;

public class TimestampOperation extends AbstractOperation {

  public TimestampOperation(final GasCalculator gasCalculator) {
    super(0x42, "TIMESTAMP", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final long timestamp = frame.getBlockHeader().getTimestamp();
    frame.pushStackItem(UInt256Bytes.of(timestamp));
  }
}

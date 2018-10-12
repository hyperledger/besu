package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256Bytes;

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

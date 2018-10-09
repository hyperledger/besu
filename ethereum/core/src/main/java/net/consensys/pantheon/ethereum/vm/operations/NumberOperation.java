package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256Bytes;

public class NumberOperation extends AbstractOperation {

  public NumberOperation(final GasCalculator gasCalculator) {
    super(0x43, "NUMBER", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final long number = frame.getBlockHeader().getNumber();
    frame.pushStackItem(UInt256Bytes.of(number));
  }
}

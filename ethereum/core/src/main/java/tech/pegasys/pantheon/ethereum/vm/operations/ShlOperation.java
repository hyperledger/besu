package net.consensys.pantheon.ethereum.vm.operations;

import static net.consensys.pantheon.util.uint.UInt256s.greaterThanOrEqualTo256;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.Bytes32s;
import net.consensys.pantheon.util.uint.UInt256;

public class ShlOperation extends AbstractOperation {

  public ShlOperation(final GasCalculator gasCalculator) {
    super(0x1b, "SHL", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 shiftAmount = frame.popStackItem().asUInt256();
    final Bytes32 value = frame.popStackItem();

    if (greaterThanOrEqualTo256(shiftAmount)) {
      frame.pushStackItem(Bytes32.ZERO);
    } else {
      frame.pushStackItem(Bytes32s.shiftLeft(value, shiftAmount.toInt()));
    }
  }
}

package tech.pegasys.pantheon.ethereum.vm.operations;

import static tech.pegasys.pantheon.util.uint.UInt256s.greaterThanOrEqualTo256;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.Bytes32s;
import tech.pegasys.pantheon.util.uint.UInt256;

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

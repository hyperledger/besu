package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.Int256;

public class SGtOperation extends AbstractOperation {

  public SGtOperation(final GasCalculator gasCalculator) {
    super(0x13, "SGT", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Int256 value0 = frame.popStackItem().asInt256();
    final Int256 value1 = frame.popStackItem().asInt256();

    final Bytes32 result = value0.compareTo(value1) > 0 ? Bytes32.TRUE : Bytes32.FALSE;

    frame.pushStackItem(result);
  }
}

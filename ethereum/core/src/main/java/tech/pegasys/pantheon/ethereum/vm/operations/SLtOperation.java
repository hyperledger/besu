package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.Int256;

public class SLtOperation extends AbstractOperation {

  public SLtOperation(final GasCalculator gasCalculator) {
    super(0x12, "SLT", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Int256 value0 = frame.popStackItem().asInt256();
    final Int256 value1 = frame.popStackItem().asInt256();

    final Bytes32 result = value0.compareTo(value1) < 0 ? Bytes32.TRUE : Bytes32.FALSE;

    frame.pushStackItem(result);
  }
}

package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.Int256;

public class SDivOperation extends AbstractOperation {

  public SDivOperation(final GasCalculator gasCalculator) {
    super(0x05, "SDIV", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Int256 value0 = frame.popStackItem().asInt256();
    final Int256 value1 = frame.popStackItem().asInt256();

    final Int256 result = value0.dividedBy(value1);

    frame.pushStackItem(result.getBytes());
  }
}

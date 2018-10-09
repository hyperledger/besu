package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256;

public class ModOperation extends AbstractOperation {

  public ModOperation(final GasCalculator gasCalculator) {
    super(0x06, "MOD", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 value0 = frame.popStackItem().asUInt256();
    final UInt256 value1 = frame.popStackItem().asUInt256();

    final UInt256 result = value0.mod(value1);

    frame.pushStackItem(result.getBytes());
  }
}

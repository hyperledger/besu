package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256;

public class ExpOperation extends AbstractOperation {

  public ExpOperation(final GasCalculator gasCalculator) {
    super(0x0A, "EXP", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 power = frame.getStackItem(1).asUInt256();

    final int numBytes = (power.bitLength() + 7) / 8;
    return gasCalculator().expOperationGasCost(numBytes);
    //    return FrontierGasCosts.EXP.plus(FrontierGasCosts.EXP_BYTE.times(numBytes));
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 value0 = frame.popStackItem().asUInt256();
    final UInt256 value1 = frame.popStackItem().asUInt256();

    final UInt256 result = value0.pow(value1);

    frame.pushStackItem(result.getBytes());
  }
}

package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.uint.UInt256;

public class MulOperation extends AbstractOperation {

  public MulOperation(final GasCalculator gasCalculator) {
    super(0x02, "MUL", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 value0 = frame.popStackItem().asUInt256();
    final UInt256 value1 = frame.popStackItem().asUInt256();

    final UInt256 result = value0.times(value1);

    frame.pushStackItem(result.getBytes());
  }
}

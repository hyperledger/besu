package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.uint.Int256;

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

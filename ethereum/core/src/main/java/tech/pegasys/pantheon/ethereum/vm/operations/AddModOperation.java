package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.uint.UInt256;

public class AddModOperation extends AbstractOperation {

  public AddModOperation(final GasCalculator gasCalculator) {
    super(0x08, "ADDMOD", 3, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getMidTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 value0 = frame.popStackItem().asUInt256();
    final UInt256 value1 = frame.popStackItem().asUInt256();
    final UInt256 value2 = frame.popStackItem().asUInt256();

    final UInt256 result = value0.plusModulo(value1, value2);

    frame.pushStackItem(result.getBytes());
  }
}

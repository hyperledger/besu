package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;

public class CallValueOperation extends AbstractOperation {

  public CallValueOperation(final GasCalculator gasCalculator) {
    super(0x34, "CALLVALUE", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Wei value = frame.getApparentValue();
    frame.pushStackItem(value.getBytes());
  }
}

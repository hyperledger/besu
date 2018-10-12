package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;

public class DupOperation extends AbstractOperation {

  private final int index;

  public DupOperation(final int index, final GasCalculator gasCalculator) {
    super(0x80 + index - 1, "DUP" + index, index, index + 1, false, 1, gasCalculator);
    this.index = index;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    frame.pushStackItem(frame.getStackItem(index - 1));
  }
}

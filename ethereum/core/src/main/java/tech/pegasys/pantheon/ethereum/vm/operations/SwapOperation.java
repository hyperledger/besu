package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;

public class SwapOperation extends AbstractOperation {

  private final int index;

  public SwapOperation(final int index, final GasCalculator gasCalculator) {
    super(0x90 + index - 1, "SWAP" + index, index + 1, index + 1, false, 1, gasCalculator);
    this.index = index;
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Bytes32 tmp = frame.getStackItem(0);
    frame.setStackItem(0, frame.getStackItem(index));
    frame.setStackItem(index, tmp);
  }
}

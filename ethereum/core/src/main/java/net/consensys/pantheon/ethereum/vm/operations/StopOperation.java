package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.BytesValue;

public class StopOperation extends AbstractOperation {

  public StopOperation(final GasCalculator gasCalculator) {
    super(0x00, "STOP", 0, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getZeroTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    frame.setState(MessageFrame.State.CODE_SUCCESS);
    frame.setOutputData(BytesValue.EMPTY);
  }
}

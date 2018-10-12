package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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

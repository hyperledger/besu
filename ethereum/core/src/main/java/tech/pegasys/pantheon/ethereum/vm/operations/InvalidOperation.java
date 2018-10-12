package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;

public class InvalidOperation extends AbstractOperation {

  public InvalidOperation(final GasCalculator gasCalculator) {
    super(0xFE, "INVALID", -1, -1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return null;
  }

  @Override
  public void execute(final MessageFrame frame) {
    frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
    frame.getExceptionalHaltReasons().add(ExceptionalHaltReason.INVALID_OPERATION);
  }
}

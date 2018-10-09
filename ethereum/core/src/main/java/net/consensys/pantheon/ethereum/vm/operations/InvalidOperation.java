package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;

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

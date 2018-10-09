package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;

public class JumpDestOperation extends AbstractOperation {

  public static final int OPCODE = 0x5B;

  public JumpDestOperation(final GasCalculator gasCalculator) {
    super(OPCODE, "JUMPDEST", 0, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getJumpDestOperationGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {}
}

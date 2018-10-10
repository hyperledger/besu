package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256;

public class DifficultyOperation extends AbstractOperation {

  public DifficultyOperation(final GasCalculator gasCalculator) {
    super(0x44, "DIFFICULTY", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 difficulty = frame.getBlockHeader().getDifficulty();
    frame.pushStackItem(difficulty.getBytes());
  }
}

package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

public class GasOperation extends AbstractOperation {

  public GasOperation(final GasCalculator gasCalculator) {
    super(0x5A, "GAS", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Gas gasRemaining = frame.getRemainingGas();
    final Bytes32 value = Bytes32.leftPad(BytesValue.of(gasRemaining.getBytes()));
    frame.pushStackItem(value);
  }
}

package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;

public class GasPriceOperation extends AbstractOperation {

  public GasPriceOperation(final GasCalculator gasCalculator) {
    super(0x3A, "GASPRICE", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Wei gasPrice = frame.getGasPrice();
    frame.pushStackItem(gasPrice.getBytes());
  }
}

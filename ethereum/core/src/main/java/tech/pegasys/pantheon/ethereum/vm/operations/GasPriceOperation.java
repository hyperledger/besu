package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;

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

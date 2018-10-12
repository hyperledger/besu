package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class GasLimitOperation extends AbstractOperation {

  public GasLimitOperation(final GasCalculator gasCalculator) {
    super(0x45, "GASLIMIT", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Gas gasLimit = Gas.of(frame.getBlockHeader().getGasLimit());
    final Bytes32 value = Bytes32.leftPad(BytesValue.of(gasLimit.getBytes()));
    frame.pushStackItem(value);
  }
}

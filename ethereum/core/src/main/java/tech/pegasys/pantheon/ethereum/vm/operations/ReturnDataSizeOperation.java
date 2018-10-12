package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256Bytes;

public class ReturnDataSizeOperation extends AbstractOperation {

  public ReturnDataSizeOperation(final GasCalculator gasCalculator) {
    super(0x3D, "RETURNDATASIZE", 0, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBaseTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final BytesValue returnData = frame.getReturnData();
    frame.pushStackItem(UInt256Bytes.of(returnData.size()));
  }
}

package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256Bytes;

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

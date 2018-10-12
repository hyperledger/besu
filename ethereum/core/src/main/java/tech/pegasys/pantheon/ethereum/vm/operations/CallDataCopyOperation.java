package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

public class CallDataCopyOperation extends AbstractOperation {

  public CallDataCopyOperation(final GasCalculator gasCalculator) {
    super(0x37, "CALLDATACOPY", 3, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = frame.getStackItem(0).asUInt256();
    final UInt256 length = frame.getStackItem(2).asUInt256();

    return gasCalculator().dataCopyOperationGasCost(frame, offset, length);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final BytesValue callData = frame.getInputData();

    final UInt256 memOffset = frame.popStackItem().asUInt256();
    final UInt256 sourceOffset = frame.popStackItem().asUInt256();
    final UInt256 numBytes = frame.popStackItem().asUInt256();

    frame.writeMemory(memOffset, sourceOffset, numBytes, callData);
  }
}

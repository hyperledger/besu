package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

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

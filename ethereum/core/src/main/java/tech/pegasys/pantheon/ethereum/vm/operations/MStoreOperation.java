package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.UInt256;

public class MStoreOperation extends AbstractOperation {

  public MStoreOperation(final GasCalculator gasCalculator) {
    super(0x52, "MSTORE", 2, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = frame.getStackItem(0).asUInt256();

    return gasCalculator().mStoreOperationGasCost(frame, offset);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 location = frame.popStackItem().asUInt256();
    final Bytes32 value = frame.popStackItem();

    frame.writeMemory(location, UInt256.U_32, value);
  }
}

package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256;

public class MStore8Operation extends AbstractOperation {

  public MStore8Operation(final GasCalculator gasCalculator) {
    super(0x53, "MSTORE8", 2, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = frame.getStackItem(0).asUInt256();

    return gasCalculator().mStore8OperationGasCost(frame, offset);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 location = frame.popStackItem().asUInt256();
    final Bytes32 value = frame.popStackItem();

    frame.writeMemory(location, value.get(Bytes32.SIZE - 1));
  }
}

package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256;

public class MLoadOperation extends AbstractOperation {

  public MLoadOperation(final GasCalculator gasCalculator) {
    super(0x51, "MLOAD", 1, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = frame.getStackItem(0).asUInt256();

    return gasCalculator().mLoadOperationGasCost(frame, offset);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 location = frame.popStackItem().asUInt256();

    final Bytes32 value = Bytes32.leftPad(frame.readMemory(location, UInt256.U_32));

    frame.pushStackItem(value);
  }
}

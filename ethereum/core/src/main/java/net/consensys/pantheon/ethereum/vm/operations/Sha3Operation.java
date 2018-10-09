package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

public class Sha3Operation extends AbstractOperation {

  public Sha3Operation(final GasCalculator gasCalculator) {
    super(0x20, "SHA3", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 offset = frame.getStackItem(0).asUInt256();
    final UInt256 length = frame.getStackItem(1).asUInt256();

    return gasCalculator().sha3OperationGasCost(frame, offset, length);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 from = frame.popStackItem().asUInt256();
    final UInt256 length = frame.popStackItem().asUInt256();

    final BytesValue bytes = frame.readMemory(from, length);
    frame.pushStackItem(Hash.hash(bytes));
  }
}

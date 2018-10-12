package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.UInt256;

public class IsZeroOperation extends AbstractOperation {

  public IsZeroOperation(final GasCalculator gasCalculator) {
    super(0x15, "ISZERO", 1, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 value = frame.popStackItem().asUInt256();

    frame.pushStackItem(value.isZero() ? Bytes32.TRUE : Bytes32.FALSE);
  }
}

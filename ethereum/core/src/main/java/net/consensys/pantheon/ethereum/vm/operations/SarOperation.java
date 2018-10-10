package net.consensys.pantheon.ethereum.vm.operations;

import static net.consensys.pantheon.util.uint.UInt256s.greaterThanOrEqualTo256;

import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.Bytes32s;
import net.consensys.pantheon.util.uint.UInt256;

public class SarOperation extends AbstractOperation {

  private static final Bytes32 ALL_BITS =
      Bytes32.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  public SarOperation(final GasCalculator gasCalculator) {
    super(0x1d, "SAR", 2, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getVeryLowTierGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 shiftAmount = frame.popStackItem().asUInt256();
    Bytes32 value = frame.popStackItem();

    final boolean negativeNumber = value.get(0) < 0;

    // short circuit result if we are shifting more than the width of the data.
    if (greaterThanOrEqualTo256(shiftAmount)) {
      final Bytes32 overflow = negativeNumber ? ALL_BITS : Bytes32.ZERO;
      frame.pushStackItem(overflow);
      return;
    }

    // first perform standard shift right.
    value = Bytes32s.shiftRight(value, shiftAmount.toInt());

    // if a negative number, carry through the sign.
    if (negativeNumber) {
      final Bytes32 significantBits = Bytes32s.shiftLeft(ALL_BITS, 256 - shiftAmount.toInt());
      value = Bytes32s.or(value, significantBits);
    }
    frame.pushStackItem(value);
  }
}

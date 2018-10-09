package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

public class Create2Operation extends AbstractCreateOperation {

  private static final BytesValue PREFIX = BytesValue.fromHexString("0xFF");

  public Create2Operation(final GasCalculator gasCalculator) {
    super(0xF5, "CREATE2", 4, 1, false, 1, gasCalculator);
  }

  @Override
  protected Address targetContractAddress(final MessageFrame frame) {
    final Address sender = frame.getSenderAddress();
    final UInt256 offset = frame.getStackItem(1).asUInt256();
    final UInt256 length = frame.getStackItem(2).asUInt256();
    final Bytes32 salt = frame.getStackItem(3);
    final BytesValue initCode = frame.readMemory(offset, length);
    final Hash hash = Hash.hash(PREFIX.concat(sender).concat(salt).concat(Hash.hash(initCode)));
    return Address.extract(hash);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().create2OperationGasCost(frame);
  }
}

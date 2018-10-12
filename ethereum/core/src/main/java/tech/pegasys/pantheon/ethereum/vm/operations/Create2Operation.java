package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

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

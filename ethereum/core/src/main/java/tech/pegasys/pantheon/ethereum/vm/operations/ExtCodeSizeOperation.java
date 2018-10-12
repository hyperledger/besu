package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.Words;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.UInt256Bytes;

public class ExtCodeSizeOperation extends AbstractOperation {

  public ExtCodeSizeOperation(final GasCalculator gasCalculator) {
    super(0x3B, "EXTCODESIZE", 1, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getExtCodeSizeOperationGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Address address = Words.toAddress(frame.popStackItem());
    final Account account = frame.getWorldState().get(address);
    frame.pushStackItem(account == null ? Bytes32.ZERO : UInt256Bytes.of(account.getCode().size()));
  }
}

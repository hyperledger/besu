package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.ethereum.vm.Words;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256Bytes;

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

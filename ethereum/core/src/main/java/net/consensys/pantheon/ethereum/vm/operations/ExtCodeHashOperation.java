package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.ethereum.vm.Words;
import net.consensys.pantheon.util.bytes.Bytes32;

public class ExtCodeHashOperation extends AbstractOperation {

  public ExtCodeHashOperation(final GasCalculator gasCalculator) {
    super(0x3F, "EXTCODEHASH", 1, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().extCodeHashOperationGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Address address = Words.toAddress(frame.popStackItem());
    final Account account = frame.getWorldState().get(address);
    if (account == null) {
      frame.pushStackItem(Bytes32.ZERO);
    } else {
      frame.pushStackItem(account.getCodeHash());
    }
  }
}

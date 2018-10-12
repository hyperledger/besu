package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.Words;
import tech.pegasys.pantheon.util.bytes.Bytes32;

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

package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256;

public class SLoadOperation extends AbstractOperation {

  public SLoadOperation(final GasCalculator gasCalculator) {
    super(0x54, "SLOAD", 1, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getSloadOperationGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 key = frame.popStackItem().asUInt256();

    final Account account = frame.getWorldState().get(frame.getRecipientAddress());
    assert account != null : "VM account should exists";

    frame.pushStackItem(account.getStorageValue(key).getBytes());
  }
}

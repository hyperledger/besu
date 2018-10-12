package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.util.uint.UInt256;

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

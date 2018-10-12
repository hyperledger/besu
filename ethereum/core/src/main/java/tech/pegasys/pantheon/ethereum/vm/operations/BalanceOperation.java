package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.Words;
import tech.pegasys.pantheon.util.bytes.Bytes32;

public class BalanceOperation extends AbstractOperation {

  public BalanceOperation(final GasCalculator gasCalculator) {
    super(0x31, "BALANCE", 1, 1, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    return gasCalculator().getBalanceOperationGasCost();
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Address accountAddress = Words.toAddress(frame.popStackItem());
    final Account account = frame.getWorldState().get(accountAddress);
    frame.pushStackItem(account == null ? Bytes32.ZERO : account.getBalance().getBytes());
  }
}

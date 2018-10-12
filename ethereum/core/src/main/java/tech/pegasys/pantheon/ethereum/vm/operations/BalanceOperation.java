package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.ethereum.vm.Words;
import net.consensys.pantheon.util.bytes.Bytes32;

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

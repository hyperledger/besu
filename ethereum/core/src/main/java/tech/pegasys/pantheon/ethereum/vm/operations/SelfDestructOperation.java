package tech.pegasys.pantheon.ethereum.vm.operations;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.vm.AbstractOperation;
import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;
import tech.pegasys.pantheon.ethereum.vm.GasCalculator;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.Words;

import java.util.EnumSet;
import java.util.Optional;

public class SelfDestructOperation extends AbstractOperation {

  public SelfDestructOperation(final GasCalculator gasCalculator) {
    super(0xFF, "SELFDESTRUCT", 1, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final Address recipientAddress = Words.toAddress(frame.getStackItem(0));

    final Account recipient = frame.getWorldState().get(recipientAddress);
    final Wei inheritance = frame.getWorldState().get(frame.getRecipientAddress()).getBalance();

    return gasCalculator().selfDestructOperationGasCost(recipient, inheritance);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final Address address = frame.getRecipientAddress();
    final MutableAccount account = frame.getWorldState().getMutable(address);

    frame.addSelfDestruct(address);

    final MutableAccount recipient =
        frame.getWorldState().getOrCreate(Words.toAddress(frame.popStackItem()));

    recipient.incrementBalance(account.getBalance());
    account.setBalance(Wei.ZERO);

    frame.setState(MessageFrame.State.CODE_SUCCESS);
  }

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    return frame.isStatic()
        ? Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE)
        : Optional.empty();
  }
}

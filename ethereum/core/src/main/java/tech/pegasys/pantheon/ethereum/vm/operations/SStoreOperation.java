package net.consensys.pantheon.ethereum.vm.operations;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.MutableAccount;
import net.consensys.pantheon.ethereum.vm.AbstractOperation;
import net.consensys.pantheon.ethereum.vm.EVM;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.ethereum.vm.GasCalculator;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.EnumSet;
import java.util.Optional;

public class SStoreOperation extends AbstractOperation {

  public SStoreOperation(final GasCalculator gasCalculator) {
    super(0x55, "SSTORE", 2, 0, false, 1, gasCalculator);
  }

  @Override
  public Gas cost(final MessageFrame frame) {
    final UInt256 key = frame.getStackItem(0).asUInt256();
    final UInt256 newValue = frame.getStackItem(1).asUInt256();

    final Account account = frame.getWorldState().get(frame.getRecipientAddress());
    // Setting storage value to non-zero from zero (i.e. nothing currently at this location) vs.
    // resetting an existing value.
    final UInt256 currentValue = account.getStorageValue(key);

    return gasCalculator()
        .calculateStorageCost(() -> getOriginalValue(frame, key), currentValue, newValue);
  }

  @Override
  public void execute(final MessageFrame frame) {
    final UInt256 key = frame.popStackItem().asUInt256();
    final UInt256 value = frame.popStackItem().asUInt256();

    final MutableAccount account = frame.getWorldState().getMutable(frame.getRecipientAddress());
    assert account != null : "VM account should exists";

    // Increment the refund counter.
    final UInt256 originalValue = getOriginalValue(frame, key);
    final UInt256 currentValue = account.getStorageValue(key);
    frame.incrementGasRefund(
        gasCalculator()
            .calculateStorageRefundAmount(() -> getOriginalValue(frame, key), currentValue, value));

    account.setStorageValue(key.copy(), value.copy());
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

  private UInt256 getOriginalValue(final MessageFrame frame, final UInt256 key) {
    final Account originalAccount =
        frame.getWorldState().getOriginalAccount(frame.getRecipientAddress());
    return originalAccount != null ? originalAccount.getStorageValue(key) : UInt256.ZERO;
  }
}

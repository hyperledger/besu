package net.consensys.pantheon.ethereum.vm.ehalt;

import net.consensys.pantheon.ethereum.vm.EVM;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.ethereum.vm.MessageFrame;
import net.consensys.pantheon.ethereum.vm.Operation;

import java.util.EnumSet;
import java.util.Optional;

public class StackUnderflowExceptionalHaltPredicate implements ExceptionalHaltPredicate {

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame, final EnumSet<ExceptionalHaltReason> prevReasons, final EVM evm) {
    final Operation op = frame.getCurrentOperation();
    final boolean condition = frame.stackSize() < op.getStackItemsConsumed();

    return condition
        ? Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS)
        : Optional.empty();
  }
}

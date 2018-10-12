package tech.pegasys.pantheon.ethereum.vm.ehalt;

import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;
import tech.pegasys.pantheon.ethereum.vm.Operation;

import java.util.EnumSet;
import java.util.Optional;

public class StackOverflowExceptionalHaltPredicate implements ExceptionalHaltPredicate {
  public static final int MAX_STACK_SIZE = 1024;

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame, final EnumSet<ExceptionalHaltReason> prevReasons, final EVM evm) {
    final Operation op = frame.getCurrentOperation();
    final boolean condition = frame.stackSize() + op.getStackSizeChange() > MAX_STACK_SIZE;
    return condition ? Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS) : Optional.empty();
  }
}

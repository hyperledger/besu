package net.consensys.pantheon.ethereum.vm.ehalt;

import net.consensys.pantheon.ethereum.vm.EVM;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.ethereum.vm.MessageFrame;

import java.util.EnumSet;
import java.util.Optional;

public class InsufficientGasExceptionalHaltPredicate implements ExceptionalHaltPredicate {

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame, final EnumSet<ExceptionalHaltReason> prevReasons, final EVM evm) {
    // Should not execute, as the cost function could depend on the stack items.
    if (prevReasons.contains(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS)) {
      return Optional.empty();
    }

    try {
      return Optional.ofNullable(frame.getCurrentOperation().cost(frame))
          .filter(cost -> frame.getRemainingGas().compareTo(cost) < 0)
          .map(cost -> ExceptionalHaltReason.INSUFFICIENT_GAS);
    } catch (final IllegalArgumentException e) {
      // TODO: Figure out a better way to handle gas overflows.
      if (e.getMessage().contains("Gas too large")) {
        return Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
      }

      // throw e;
      return Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
    }
  }
}

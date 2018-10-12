package tech.pegasys.pantheon.ethereum.vm.ehalt;

import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class ExceptionalHaltManager {

  private static final List<ExceptionalHaltPredicate> GLOBAL =
      Arrays.asList(
          new InvalidOperationExceptionalHaltPredicate(),
              new StackOverflowExceptionalHaltPredicate(),
          new StackUnderflowExceptionalHaltPredicate(),
              new InsufficientGasExceptionalHaltPredicate());

  public static EnumSet<ExceptionalHaltReason> evaluateAll(
      final MessageFrame frame, final EVM evm) {
    final EnumSet<ExceptionalHaltReason> answer = EnumSet.noneOf(ExceptionalHaltReason.class);
    for (final ExceptionalHaltPredicate predicate : GLOBAL) {
      predicate.exceptionalHaltCondition(frame, answer, evm).ifPresent(answer::add);
    }

    // TODO: Determine whether or not to short-circuit here.
    // Several operations (e.g. JUMP and JUMPI) have stack dependent checks.

    if (!answer.contains(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS)) {
      // Evaluate any operation specific halt conditions too.
      frame
          .getCurrentOperation()
          .exceptionalHaltCondition(frame, answer, evm)
          .ifPresent(answer::add);
    }

    return answer;
  }
}

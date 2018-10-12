package tech.pegasys.pantheon.ethereum.vm;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.vm.ehalt.ExceptionalHaltPredicate;

import java.util.EnumSet;
import java.util.Optional;

public interface Operation extends ExceptionalHaltPredicate {

  /**
   * @param frame The frame for execution of this operation.
   * @return The gas cost associated with executing this operation given the current {@link
   *     MessageFrame}.
   */
  Gas cost(MessageFrame frame);

  /**
   * Executes the logic behind this operation.
   *
   * @param frame The frame for execution of this operation.
   */
  void execute(MessageFrame frame);

  /**
   * Check if an exceptional halt condition should apply
   *
   * @param frame the current frame
   * @param previousReasons any existing exceptional halt conditions
   * @param evm the currently executing EVM
   * @return an {@link Optional} containing the {@link ExceptionalHaltReason} that applies or empty
   *     if no exceptional halt condition applies.
   */
  @Override
  default Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame,
      final EnumSet<ExceptionalHaltReason> previousReasons,
      final EVM evm) {
    return Optional.empty();
  }

  int getOpcode();

  String getName();

  int getStackItemsConsumed();

  int getStackItemsProduced();

  default int getStackSizeChange() {
    return getStackItemsProduced() - getStackItemsConsumed();
  }

  boolean getUpdatesProgramCounter();

  int getOpSize();
}

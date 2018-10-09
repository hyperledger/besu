package net.consensys.pantheon.ethereum.vm.ehalt;

import net.consensys.pantheon.ethereum.vm.EVM;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.ethereum.vm.MessageFrame;

import java.util.EnumSet;
import java.util.Optional;

public class InvalidOperationExceptionalHaltPredicate implements ExceptionalHaltPredicate {

  private static final int INVALID_OPCODE = 0xfe;

  @Override
  public Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      final MessageFrame frame, final EnumSet<ExceptionalHaltReason> prevReasons, final EVM evm) {
    return frame.getCurrentOperation().getOpcode() == INVALID_OPCODE
        ? Optional.of(ExceptionalHaltReason.INVALID_OPERATION)
        : Optional.empty();
  }
}

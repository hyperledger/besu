package net.consensys.pantheon.ethereum.vm.ehalt;

import net.consensys.pantheon.ethereum.vm.EVM;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.ethereum.vm.MessageFrame;

import java.util.EnumSet;
import java.util.Optional;

public interface ExceptionalHaltPredicate {

  Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      MessageFrame frame, EnumSet<ExceptionalHaltReason> previousReasons, EVM evm);
}

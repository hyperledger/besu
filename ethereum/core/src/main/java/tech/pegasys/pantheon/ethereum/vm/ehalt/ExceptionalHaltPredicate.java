package tech.pegasys.pantheon.ethereum.vm.ehalt;

import tech.pegasys.pantheon.ethereum.vm.EVM;
import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;
import tech.pegasys.pantheon.ethereum.vm.MessageFrame;

import java.util.EnumSet;
import java.util.Optional;

public interface ExceptionalHaltPredicate {

  Optional<ExceptionalHaltReason> exceptionalHaltCondition(
      MessageFrame frame, EnumSet<ExceptionalHaltReason> previousReasons, EVM evm);
}

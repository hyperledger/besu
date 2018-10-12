package tech.pegasys.pantheon.ethereum.vm.ehalt;

import tech.pegasys.pantheon.ethereum.vm.ExceptionalHaltReason;

import java.util.EnumSet;

/** An exception to signal that an exceptional halt has occurred. */
public class ExceptionalHaltException extends Exception {
  private final EnumSet<ExceptionalHaltReason> reasons;

  public ExceptionalHaltException(final EnumSet<ExceptionalHaltReason> reasons) {
    this.reasons = reasons;
  }

  @Override
  public String getMessage() {
    return "Exceptional halt condition(s) triggered: " + this.reasons;
  }

  public EnumSet<ExceptionalHaltReason> getReasons() {
    return reasons;
  }
}

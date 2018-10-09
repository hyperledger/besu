package net.consensys.pantheon.ethereum.jsonrpc.internal.results;

import net.consensys.pantheon.ethereum.debug.TraceFrame;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;

import com.fasterxml.jackson.annotation.JsonGetter;

public class StructLogWithError extends StructLog {

  private final String[] error;

  public StructLogWithError(final TraceFrame traceFrame) {
    super(traceFrame);
    error =
        traceFrame.getExceptionalHaltReasons().isEmpty()
            ? null
            : traceFrame
                .getExceptionalHaltReasons()
                .stream()
                .map(ExceptionalHaltReason::name)
                .toArray(String[]::new);
  }

  @JsonGetter("error")
  public String[] getError() {
    return error;
  }
}

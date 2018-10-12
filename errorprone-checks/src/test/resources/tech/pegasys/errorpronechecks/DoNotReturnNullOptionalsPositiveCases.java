package tech.pegasys.errorpronechecks;

import java.util.Optional;

public class DoNotReturnNullOptionalsPositiveCases {

  // BUG: Diagnostic contains: Do not return null optionals.
  public Optional<Long> returnsNull() {
    return null;
  }

  // BUG: Diagnostic contains: Do not return null optionals.
  public Optional<Long> sometimesReturnsNull(boolean random) {
    if (random) {

      return null;
    }
    return Optional.of(2L);
  }
}

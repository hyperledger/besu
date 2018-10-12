package net.consensys.errorpronechecks;

import java.security.Provider;
import java.security.SecureRandom;

public class DoNotCreateSecureRandomDirectlyPositiveCases {

  public void callsSecureRandomGetInstance() throws Exception {
    // BUG: Diagnostic contains:  Do not create SecureRandom directly.
    SecureRandom.getInstance("");

    // BUG: Diagnostic contains:  Do not create SecureRandom directly.
    SecureRandom.getInstance("", "");

    // BUG: Diagnostic contains:  Do not create SecureRandom directly.
    SecureRandom.getInstance("", new Provider("", 0, "") {});
  }

  public void invokesSecureRandomConstructor() throws Exception {
    // BUG: Diagnostic contains:  Do not create SecureRandom directly.
    new SecureRandom();

    // BUG: Diagnostic contains:  Do not create SecureRandom directly.
    new SecureRandom(new byte[] {});
  }
}

package net.consensys.errorpronechecks;

import java.security.Provider;
import java.security.SecureRandom;

public class DoNotCreateSecureRandomDirectlyNegativeCases {

  public void callsNonJRESecureRandomGetInstance() throws Exception {
    TestSecureRandom.getInstance("");
    TestSecureRandom.getInstance("", "");
    TestSecureRandom.getInstance("", new Provider("", 0, "") {});
  }

  public void invokesNonJRESecureRandomConstructor() throws Exception {
    new TestSecureRandom();
  }

  private class TestSecureRandom extends SecureRandom {}
}

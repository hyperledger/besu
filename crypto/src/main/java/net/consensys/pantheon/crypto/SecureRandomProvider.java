package net.consensys.pantheon.crypto;

import java.security.SecureRandom;

public class SecureRandomProvider {
  private static final SecureRandom publicSecureRandom = new PRNGSecureRandom();

  // Returns a shared instance of secure random intended to be used where the value is used publicly
  public static SecureRandom publicSecureRandom() {
    return publicSecureRandom;
  }

  public static SecureRandom createSecureRandom() {
    return new PRNGSecureRandom();
  }
}

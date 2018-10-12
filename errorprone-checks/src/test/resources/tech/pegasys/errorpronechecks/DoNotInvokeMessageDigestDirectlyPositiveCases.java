package tech.pegasys.errorpronechecks;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DoNotInvokeMessageDigestDirectlyPositiveCases {

  public void callsMessageDigestGetInstance() throws NoSuchAlgorithmException {
    // BUG: Diagnostic contains:  Do not invoke MessageDigest.getInstance directly.
    MessageDigest dig = MessageDigest.getInstance("SHA-256");
  }
}

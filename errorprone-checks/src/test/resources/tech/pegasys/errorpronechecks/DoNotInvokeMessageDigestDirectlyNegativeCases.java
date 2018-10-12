package tech.pegasys.errorpronechecks;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DoNotInvokeMessageDigestDirectlyNegativeCases {

  public void callsMessageDigestGetInstance() throws NoSuchAlgorithmException {
    MessageDigest dig = null;
  }
}

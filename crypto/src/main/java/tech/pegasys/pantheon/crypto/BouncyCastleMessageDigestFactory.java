package tech.pegasys.pantheon.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class BouncyCastleMessageDigestFactory {

  private static final BouncyCastleProvider securityProvider = new BouncyCastleProvider();

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  public static MessageDigest create(final String algorithm) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance(algorithm, securityProvider);
  }
}

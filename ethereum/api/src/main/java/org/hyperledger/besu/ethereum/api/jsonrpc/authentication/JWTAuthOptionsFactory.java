package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JWTAuthOptionsFactory {
  private static final Logger LOG = LogManager.getLogger();

  public JWTAuthOptions create(final File externalPublicKeyFile) {
    final Optional<String> externalJwtPublicKey =
        externalPublicKeyFile == null ? Optional.empty() : readPublicKey(externalPublicKeyFile);
    return makeJwtAuthOptions(externalJwtPublicKey);
  }

  private Optional<String> readPublicKey(final File authenticationPublicKeyFile) {
    try {
      return Optional.of(Files.readString(authenticationPublicKeyFile.toPath()));
    } catch (IOException e) {
      LOG.error("Authentication RPC public key could not be read", e);
      return Optional.empty();
    }
  }

  private JWTAuthOptions makeJwtAuthOptions(final Optional<String> publicKey) {
    if (publicKey.isEmpty()) {
      final KeyPair keypair = generateJwtKeyPair();
      return new JWTAuthOptions()
          .setPermissionsClaimKey("permissions")
          .addPubSecKey(
              new PubSecKeyOptions()
                  .setAlgorithm("RS256")
                  .setPublicKey(
                      Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded()))
                  .setSecretKey(
                      Base64.getEncoder().encodeToString(keypair.getPrivate().getEncoded())));
    } else {
      return new JWTAuthOptions()
          .setPermissionsClaimKey("permissions")
          .addPubSecKey(new PubSecKeyOptions().setAlgorithm("RS256").setPublicKey(publicKey.get()));
    }
  }

  private KeyPair generateJwtKeyPair() {
    final KeyPairGenerator keyGenerator;
    try {
      keyGenerator = KeyPairGenerator.getInstance("RSA");
      keyGenerator.initialize(1024);
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    return keyGenerator.generateKeyPair();
  }
}

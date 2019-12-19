package org.hyperledger.besu.ethereum.api.tls;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;

public class TlsStoreValidator {
  public static void validateConfiguration(
      TlsStoreConfiguration configuration, boolean isKeyStore) {
    final Optional<CertificateStoreType> certificateStoreType = configuration.getType();
    checkArgument(
        certificateStoreType.isPresent(), "Invalid TLS certificate store type is configured.");

    if (certificateStoreType.get().isPasswordRequired()) {
      checkArgument(configuration.getStorePath() != null, "TLS store path is not configured.");
      checkArgument(
          configuration.getStorePassword() != null, "TLS store password is not configured.");
    } else {
      checkArgument(
          configuration.getCertPath() != null, "TLS PEM certificate path is not configured.");
      if (isKeyStore) {
        checkArgument(
            configuration.getKeyPath() != null, "TLS PEM Private key path is not configured");
      }
    }
  }
}

package org.hyperledger.besu.ethereum.api.tls;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class TlsTrustStoreConfiguration {
    private final String type;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final String certPath;

    public TlsTrustStoreConfiguration(final String type, final String keyStorePath, final String keyStorePassword, final String certPath) {
        this.type = type;
         this.keyStorePath = keyStorePath;
         this.keyStorePassword = keyStorePassword;
         this.certPath = certPath;

        validate();
    }

    private void validate() throws IllegalArgumentException {
        final Optional<CertificateStoreType> certificateStoreType = CertificateStoreType.fromString(type);
        checkArgument(certificateStoreType.isPresent(),
                "Invalid TLS certificate store type is configured.");

        if (certificateStoreType.get().isPasswordRequired()) {
            checkArgument( keyStorePath != null, "TLS key/certificate store path is not configured.");
            checkArgument( keyStorePassword != null, "TLS key/certificate store password is not configured.");
        } else  {
            checkArgument( certPath != null, "TLS PEM certificate path is not configured.");
        }
    }

    public CertificateStoreType getType() {
        return CertificateStoreType.fromString(type).get();
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public String getCertPath() {
        return certPath;
    }
}

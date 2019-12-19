package org.hyperledger.besu.ethereum.api.tls;

import static com.google.common.base.Preconditions.checkArgument;

public class TlsKeyStoreConfiguration extends TlsTrustStoreConfiguration {
    private final String keyPath;

    public TlsKeyStoreConfiguration(final String type, final String keyStorePath, final String keyStorePassword, final String certPath, final String keyPath) {
        super(type, keyStorePath, keyStorePassword, certPath);
        this.keyPath = keyPath;
        checkArgument(keyPath != null, "TLS PEM Private key path is not configured");
    }

    public String getKeyPath() {
        return keyPath;
    }
}

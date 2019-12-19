package org.hyperledger.besu.ethereum.api.tls;

import java.util.Optional;

public class TlsStoreConfiguration {
  private final String type;
  private String storePath;
  private String storePassword;
  private String certPath;
  private String keyPath;

  public TlsStoreConfiguration(final String type) {
    this.type = type;
  }

  public Optional<CertificateStoreType> getType() {
    return CertificateStoreType.fromString(type);
  }

  public String getStorePath() {
    return storePath;
  }

  public void setStorePath(final String storePath) {
    this.storePath = storePath;
  }

  public String getStorePassword() {
    return storePassword;
  }

  public void setStorePassword(final String storePassword) {
    this.storePassword = storePassword;
  }

  public String getCertPath() {
    return certPath;
  }

  public void setCertPath(final String certPath) {
    this.certPath = certPath;
  }

  public String getKeyPath() {
    return keyPath;
  }

  public void setKeyPath(final String keyPath) {
    this.keyPath = keyPath;
  }
}

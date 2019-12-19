package org.hyperledger.besu.ethereum.api.tls;

/** Points to PKCS#12 format keystore which contains key/certificate */
public class TlsStoreConfiguration {
  private final String storePath;
  private final String storePassword;

  public TlsStoreConfiguration(final String storePath, final String storePassword) {
    this.storePath = storePath;
    this.storePassword = storePassword;
  }

  public String getStorePath() {
    return storePath;
  }

  public String getStorePassword() {
    return storePassword;
  }
}

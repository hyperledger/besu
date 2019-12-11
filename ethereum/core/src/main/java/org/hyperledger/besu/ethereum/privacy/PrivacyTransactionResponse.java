package org.hyperledger.besu.ethereum.privacy;

public class PrivacyTransactionResponse {
  private final String enclaveKey;
  private final String privacyGroup;

  public PrivacyTransactionResponse(final String enclaveKey, final String privacyGroup) {
    this.enclaveKey = enclaveKey;
    this.privacyGroup = privacyGroup;
  }

  public String getEnclaveKey() {
    return enclaveKey;
  }

  public String getPrivacyGroup() {
    return privacyGroup;
  }
}

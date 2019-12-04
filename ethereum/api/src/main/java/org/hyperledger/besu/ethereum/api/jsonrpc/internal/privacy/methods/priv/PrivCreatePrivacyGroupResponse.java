package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import java.util.Objects;

public class PrivCreatePrivacyGroupResponse {
  private final String privacyGroupId;
  private final String transactionHash;

  public PrivCreatePrivacyGroupResponse(final String privacyGroupId, final String transactionHash) {
    this.privacyGroupId = privacyGroupId;
    this.transactionHash = transactionHash;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  public String getTransactionHash() {
    return transactionHash;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PrivCreatePrivacyGroupResponse that = (PrivCreatePrivacyGroupResponse) o;
    return privacyGroupId.equals(that.privacyGroupId)
        && transactionHash.equals(that.transactionHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(privacyGroupId, transactionHash);
  }
}

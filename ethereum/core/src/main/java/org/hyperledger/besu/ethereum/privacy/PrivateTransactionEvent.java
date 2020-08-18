/*
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.ethereum.privacy;

public class PrivateTransactionEvent {
  private final String privacyGroupId;
  private final String enclavePublicKey;

  public PrivateTransactionEvent(final String privacyGroupId, final String enclavePublicKey) {
    this.privacyGroupId = privacyGroupId;
    this.enclavePublicKey = enclavePublicKey;
  }

  public String getPrivacyGroupId() {
    return privacyGroupId;
  }

  public String getEnclavePublicKey() {
    return enclavePublicKey;
  }
}

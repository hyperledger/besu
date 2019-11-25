/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.crosschain.core.keys;

import org.hyperledger.besu.crosschain.core.keys.generation.KeyGenFailureToCompleteReason;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/** Holds all of the information related to a round of key generation. */
public class BlsThresholdCredentials extends BlsThresholdPublicKeyImpl {
  private Map<BigInteger, BigInteger> mySecretShares;
  private BigInteger myNodeAddress;
  private Set<BigInteger> nodesStillActiveInKeyGeneration;
  private Map<BigInteger, KeyGenFailureToCompleteReason> nodesNoLongerInKeyGeneration;
  private KeyGenFailureToCompleteReason failureReason;
  private KeyStatus keyStatus;

  public BlsThresholdCredentials(
      final long keyVersion,
      final int threshold,
      final BlsPoint publicKey,
      final BigInteger blockchainId,
      final BlsThresholdCryptoSystem algorithm,
      final Map<BigInteger, BigInteger> mySecretShares,
      final BigInteger myNodeAddress,
      final Set<BigInteger> nodesStillActiveInKeyGeneration,
      final Map<BigInteger, KeyGenFailureToCompleteReason> nodesNoLongerInKeyGeneration,
      final KeyGenFailureToCompleteReason failureReason,
      final KeyStatus keyStatus) {
    super(publicKey, keyVersion, threshold, blockchainId, algorithm);
    this.mySecretShares = mySecretShares;
    this.myNodeAddress = myNodeAddress;
    this.nodesStillActiveInKeyGeneration = nodesStillActiveInKeyGeneration;
    this.nodesNoLongerInKeyGeneration = nodesNoLongerInKeyGeneration;
    this.failureReason = failureReason;
    this.keyStatus = keyStatus;
  }

  public Map<BigInteger, BigInteger> getMySecretShares() {
    return mySecretShares;
  }

  public BigInteger getMyNodeAddress() {
    return myNodeAddress;
  }

  public Set<BigInteger> getNodesCompletedKeyGeneration() {
    return nodesStillActiveInKeyGeneration;
  }

  public Map<BigInteger, KeyGenFailureToCompleteReason> getNodesDoppedOutOfKeyGeneration() {
    return nodesNoLongerInKeyGeneration;
  }

  public KeyGenFailureToCompleteReason getFailureReason() {
    return failureReason;
  }

  public KeyStatus getKeyStatus() {
    return this.keyStatus;
  }

  public static class Builder {
    private long keyVersion;
    private int threshold;
    private BlsPoint publicKey;
    private BigInteger blockchainId;
    private BlsThresholdCryptoSystem algorithm;
    private Map<BigInteger, BigInteger> mySecretShares;
    private BigInteger myNodeAddress;
    private Set<BigInteger> nodesStillActiveInKeyGeneration;
    private Map<BigInteger, KeyGenFailureToCompleteReason> nodesNoLongerInKeyGeneration;
    private KeyGenFailureToCompleteReason failureReason;
    private KeyStatus keyStatus;

    public Builder keyVersion(final long keyVersion) {
      this.keyVersion = keyVersion;
      return this;
    }

    public Builder threshold(final int threshold) {
      this.threshold = threshold;
      return this;
    }

    public Builder publicKey(final BlsPoint publicKey) {
      this.publicKey = publicKey;
      return this;
    }

    public Builder blockchainId(final BigInteger blockchainId) {
      this.blockchainId = blockchainId;
      return this;
    }

    public Builder algorithm(final BlsThresholdCryptoSystem algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    public Builder mySecretShares(final Map<BigInteger, BigInteger> mySecretShares) {
      this.mySecretShares = mySecretShares;
      return this;
    }

    public Builder myNodeAddress(final BigInteger myNodeAddress) {
      this.myNodeAddress = myNodeAddress;
      return this;
    }

    public Builder nodesStillActiveInKeyGeneration(
        final Set<BigInteger> nodesStillActiveInKeyGeneration) {
      this.nodesStillActiveInKeyGeneration = nodesStillActiveInKeyGeneration;
      return this;
    }

    public Builder nodesNoLongerInKeyGeneration(
        final Map<BigInteger, KeyGenFailureToCompleteReason> nodesNoLongerInKeyGeneration) {
      this.nodesNoLongerInKeyGeneration = nodesNoLongerInKeyGeneration;
      return this;
    }

    public Builder failureReason(final KeyGenFailureToCompleteReason failureReason) {
      this.failureReason = failureReason;
      return this;
    }

    public Builder keyStatus(final KeyStatus keyStatus) {
      this.keyStatus = keyStatus;
      return this;
    }

    public BlsThresholdCredentials build() {
      return new BlsThresholdCredentials(
          keyVersion,
          threshold,
          publicKey,
          blockchainId,
          algorithm,
          mySecretShares,
          myNodeAddress,
          nodesStillActiveInKeyGeneration,
          nodesNoLongerInKeyGeneration,
          failureReason,
          keyStatus);
    }
  }
}

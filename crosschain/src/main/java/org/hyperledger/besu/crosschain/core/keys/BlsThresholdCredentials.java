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

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

/**
 * Holds the Blockchain Public Key, the node's BLS Private Key Share, the threshold and key version.
 * The key version is incremented each time a key generation is started.
 */
public class BlsThresholdCredentials implements BlsThresholdPublicKeyWithThreshold {
  public static final long NO_PUBLIC_KEY_SET = 0;
  public static final int NO_PUBLIC_KEY_SETI = 0;
  public static final BytesValue NO_PUBLIC_KEY_SET_BV = BytesValue.EMPTY;

  private long keyVersion;
  private int threshold;
  private BlsPoint publicKey;
  private BigInteger blockchainId;
  private BlsThresholdCryptoSystem algorithm;

  public BlsThresholdCredentials(
      final BlsPoint publicKey, final long keyVersion, final int threshold) {
    this.keyVersion = keyVersion;
    this.threshold = threshold;
    this.publicKey = publicKey;
  }

  public static BlsThresholdCredentials emptyCredentials() {
    return new BlsThresholdCredentials(null, NO_PUBLIC_KEY_SET, NO_PUBLIC_KEY_SETI);
  }

  @Override
  public BlsPoint getPublicKey() {
    return this.publicKey;
  }

  @Override
  public long getKeyVersion() {
    return this.keyVersion;
  }

  @Override
  public int getThreshold() {
    return this.threshold;
  }

  @Override
  public BlsThresholdCryptoSystem getAlgorithm() {
    return this.algorithm;
  }

  @Override
  public BigInteger getBlockchainId() {
    return this.blockchainId;
  }

  @Override
  public BytesValue getEncodedPublicKey() {
    return RLP.encode(
        out -> {
          out.startList();
          out.writeLongScalar(this.keyVersion);
          out.writeLongScalar(this.threshold);
          out.writeLongScalar(this.algorithm.value);
          out.writeBytesValue(BytesValue.wrap(this.publicKey.store()));
          out.writeBigIntegerScalar(this.blockchainId);
          out.endList();
        });
  }

  public BigInteger getPrivateKeyShare() {
    throw new Error();
  }
}

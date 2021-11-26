/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.crypto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class SECPPrivateKey implements java.security.PrivateKey {

  private final Bytes32 encoded;
  private final String algorithm;

  private SECPPrivateKey(final Bytes32 encoded, final String algorithm) {
    checkNotNull(encoded);
    checkNotNull(algorithm);
    this.encoded = encoded;
    this.algorithm = algorithm;
  }

  public static SECPPrivateKey create(final BigInteger key, final String algorithm) {
    checkNotNull(key);
    return create(UInt256.valueOf(key), algorithm);
  }

  public static SECPPrivateKey create(final Bytes32 key, final String algorithm) {
    return new SECPPrivateKey(key, algorithm);
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof SECPPrivateKey)) {
      return false;
    }

    final SECPPrivateKey that = (SECPPrivateKey) other;
    return this.encoded.equals(that.encoded) && this.algorithm.equals(that.algorithm);
  }

  @Override
  public byte[] getEncoded() {
    return encoded.toArrayUnsafe();
  }

  public Bytes32 getEncodedBytes() {
    return encoded;
  }

  public BigInteger getD() {
    return encoded.toUnsignedBigInteger();
  }

  @Override
  public String getAlgorithm() {
    return algorithm;
  }

  @Override
  public String getFormat() {
    return null;
  }

  @Override
  public int hashCode() {
    return encoded.hashCode();
  }

  @Override
  public String toString() {
    return encoded.toString();
  }
}

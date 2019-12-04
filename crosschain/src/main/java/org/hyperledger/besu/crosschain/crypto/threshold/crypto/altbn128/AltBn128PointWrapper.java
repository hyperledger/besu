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
package org.hyperledger.besu.crosschain.crypto.threshold.crypto.altbn128;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;
import org.hyperledger.besu.crypto.altbn128.AltBn128Point;
import org.hyperledger.besu.crypto.altbn128.Fq;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

// TODO getting a class cast exception if BlsPublicKey is not added here -???
public class AltBn128PointWrapper implements BlsPoint {

  AltBn128Point point;

  AltBn128PointWrapper(final AltBn128Point point) {
    this.point = point;
  }

  @Override
  public BlsCryptoProvider.CryptoProviderTypes getType() {
    return BlsCryptoProvider.CryptoProviderTypes.LOCAL_ALT_BN_128;
  }

  // Add a point to this point.
  @Override
  public BlsPoint add(final BlsPoint obj) {
    if (!(obj instanceof AltBn128PointWrapper)) {
      throw new RuntimeException("incorrect point addition with Bn128 point");
    }
    AltBn128Point p = this.point.add(((AltBn128PointWrapper) obj).point);
    return new AltBn128PointWrapper(p);
  }

  // Multiple this point by a scalar.
  @Override
  public BlsPoint scalarMul(final BigInteger scalar) {
    return new AltBn128PointWrapper(this.point.multiply(scalar));
  }

  // Return true if this point is the point at infinity.
  @Override
  public boolean isAtInfinity() {
    return this.point.isInfinity();
  }

  @Override
  public BlsPoint negate() {
    return new AltBn128PointWrapper(this.point.negate());
  }

  private static final int WORD_LEN = 32;
  public static final int STORED_LEN = WORD_LEN + WORD_LEN;

  @Override
  public byte[] store() {
    Fq x = this.point.getX();
    BytesValue xBytesV = x.toBytesValue();
    byte[] xBytes = xBytesV.extractArray();

    Fq y = this.point.getY();
    BytesValue yBytesV = y.toBytesValue();
    byte[] yBytes = yBytesV.extractArray();

    byte[] output = new byte[STORED_LEN];

    // All of the values should be 256 bits long. However, it is possible that some
    // could have leading zeros, in which case we should zero fill.
    int len = xBytes.length;
    // TODO is this the correct endianess ???
    System.arraycopy(xBytes, 0, output, WORD_LEN - len, len);

    len = yBytes.length;
    // TODO is this the correct endianess ???
    System.arraycopy(yBytes, 0, output, STORED_LEN - len, len);

    return output;
  }

  public static AltBn128PointWrapper load(final byte[] data) {
    if (data.length != STORED_LEN) {
      throw new Error(
          "Bn128 Point data incorrect length. Should be " + STORED_LEN + ", is " + data.length);
    }

    byte[] xBytes = new byte[WORD_LEN];
    System.arraycopy(data, 0, xBytes, 0, WORD_LEN);

    byte[] yBytes = new byte[WORD_LEN];
    System.arraycopy(data, WORD_LEN, yBytes, 0, WORD_LEN);

    Fq x = Fq.create(new BigInteger(xBytes));
    Fq y = Fq.create(new BigInteger(yBytes));
    AltBn128Point point = new AltBn128Point(x, y);

    return new AltBn128PointWrapper(point);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AltBn128PointWrapper)) {
      return false;
    }

    return this.point.equals(((AltBn128PointWrapper) obj).point);
  }

  @Override
  public int hashCode() {
    return this.point.hashCode();
  }

  @Override
  public String toString() {
    return this.point.toString();
  }
}

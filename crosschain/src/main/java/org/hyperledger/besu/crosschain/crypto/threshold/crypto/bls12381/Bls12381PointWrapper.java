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
package org.hyperledger.besu.crosschain.crypto.threshold.crypto.bls12381;

import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsCryptoProvider;
import org.hyperledger.besu.crosschain.crypto.threshold.crypto.BlsPoint;

import java.math.BigInteger;

import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;

public class Bls12381PointWrapper implements BlsPoint {

  ECP point;

  Bls12381PointWrapper(final org.apache.milagro.amcl.BLS381.ECP point) {
    this.point = point;
  }

  @Override
  public BlsCryptoProvider.CryptoProviderTypes getType() {
    return BlsCryptoProvider.CryptoProviderTypes.LOCAL_BLS12_381;
  }

  // Add a point to this point.
  @Override
  public Bls12381PointWrapper add(final BlsPoint obj) {
    this.point.add(((Bls12381PointWrapper) obj).point);
    return new Bls12381PointWrapper(this.point);
  }

  // Multiple this point by a scalar.
  @Override
  public Bls12381PointWrapper scalarMul(final BigInteger scalar) {
    org.apache.milagro.amcl.BLS381.BIG scBig = Bls12381Util.BIGFromBigInteger(scalar);
    return new Bls12381PointWrapper(this.point.mul(scBig));
  }

  // Return true if this point is the point at infinity.
  @Override
  public boolean isAtInfinity() {
    return this.point.is_infinity();
  }

  @Override
  public BlsPoint negate() {
    ECP p;
    p = new ECP();
    this.point.copy(p);
    p.neg();
    return new Bls12381PointWrapper(p);
  }

  private static final int WORD_LEN = BIG.MODBYTES;
  public static final int STORED_LEN = 2 * WORD_LEN;

  @Override
  public byte[] store() {
    BIG x = this.point.getX();
    byte xBytes[] = new byte[BIG.MODBYTES];
    x.tobytearray(xBytes, 0);

    BIG y = this.point.getY();
    byte yBytes[] = new byte[BIG.MODBYTES];
    y.tobytearray(yBytes, 0);

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

  public static Bls12381PointWrapper load(final byte[] data) throws Exception {
    if (data.length != STORED_LEN) {
      throw new Error(
          "BLS12-381 Point data incorrect length. Should be " + STORED_LEN + ", is " + data.length);
    }

    byte[] xBytes = new byte[WORD_LEN];
    System.arraycopy(data, 0, xBytes, 0, WORD_LEN);

    byte[] yBytes = new byte[WORD_LEN];
    System.arraycopy(data, WORD_LEN, yBytes, 0, WORD_LEN);

    BIG x = Bls12381Util.BIGFromBigInteger(new BigInteger(xBytes));
    BIG y = Bls12381Util.BIGFromBigInteger(new BigInteger(yBytes));
    ECP point = new ECP(x, y);

    return new Bls12381PointWrapper(point);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj
        instanceof
        org.hyperledger.besu.crosschain.crypto.threshold.crypto.bls12381.Bls12381PointWrapper)) {
      return false;
    }

    return this.point.equals(((Bls12381PointWrapper) obj).point);
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

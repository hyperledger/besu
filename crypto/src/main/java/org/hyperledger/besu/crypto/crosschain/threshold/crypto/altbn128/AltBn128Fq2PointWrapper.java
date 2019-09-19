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
package org.hyperledger.besu.crypto.crosschain.threshold.crypto.altbn128;

import org.hyperledger.besu.crypto.altbn128.AltBn128Fq2Point;
import org.hyperledger.besu.crypto.altbn128.Fq;
import org.hyperledger.besu.crypto.altbn128.Fq2;
import org.hyperledger.besu.crypto.crosschain.threshold.crypto.BlsPoint;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;

// TODO getting a class cast exception if BlsPublicKey is not added here -???
public class AltBn128Fq2PointWrapper implements BlsPoint {

  AltBn128Fq2Point point;

  AltBn128Fq2PointWrapper(final AltBn128Fq2Point point) {
    this.point = point;
  }

  // Add a point to this point.
  @Override
  public BlsPoint add(final BlsPoint obj) {
    if (!(obj instanceof AltBn128Fq2PointWrapper)) {
      throw new RuntimeException("incorrect point addition with Bn128 point");
    }
    AltBn128Fq2Point p = this.point.add(((AltBn128Fq2PointWrapper) obj).point);
    return new AltBn128Fq2PointWrapper(p);
  }

  // Multiple this point by a scalar.
  @Override
  public BlsPoint scalarMul(final BigInteger scalar) {
    return new AltBn128Fq2PointWrapper(this.point.multiply(scalar));
  }

  // Return true if this point is the point at infinity.
  @Override
  public boolean isAtInfity() {
    return this.point.isInfinity();
  }

  @Override
  public BlsPoint negate() {
    return new AltBn128Fq2PointWrapper(this.point.negate());
  }

  private static final int WORD_LEN = 32;
  public static final int STORED_LEN = WORD_LEN + WORD_LEN + WORD_LEN + WORD_LEN;

  @Override
  public byte[] store() {
    Fq2 x = this.point.getX();
    Fq[] xCoeffs = x.getCoefficients();
    if (xCoeffs.length != 2) {
      // Should just be real and imaginary.
      throw new Error("x unexpected number of coefficients");
    }

    BytesValue bytesV = xCoeffs[0].toBytesValue();
    byte[] xBytesReal = bytesV.extractArray();
    bytesV = xCoeffs[1].toBytesValue();
    byte[] xBytesImaginary = bytesV.extractArray();

    Fq2 y = this.point.getY();
    Fq[] yCoeffs = y.getCoefficients();
    if (yCoeffs.length != 2) {
      // Should just be real and imaginary.
      throw new Error("y unexpected number of coefficients");
    }

    bytesV = yCoeffs[0].toBytesValue();
    byte[] yBytesReal = bytesV.extractArray();
    bytesV = yCoeffs[1].toBytesValue();
    byte[] yBytesImaginary = bytesV.extractArray();

    byte[] output = new byte[STORED_LEN];

    // All of the values should be 256 bits long. However, it is possible that some
    // could have leading zeros, in which case we should zero fill.
    int len = xBytesReal.length;
    // TODO is this the correct endianess ???
    System.arraycopy(xBytesReal, 0, output, WORD_LEN - len, len);

    len = xBytesImaginary.length;
    // TODO is this the correct endianess ???
    System.arraycopy(xBytesImaginary, 0, output, WORD_LEN + WORD_LEN - len, len);

    len = yBytesReal.length;
    // TODO is this the correct endianess ???
    System.arraycopy(yBytesReal, 0, output, WORD_LEN + WORD_LEN + WORD_LEN - len, len);

    len = yBytesImaginary.length;
    // TODO is this the correct endianess ???
    System.arraycopy(yBytesImaginary, 0, output, STORED_LEN - len, len);

    return output;
  }

  public static AltBn128Fq2PointWrapper load(final byte[] data) {
    if (data.length != STORED_LEN) {
      throw new Error(
          "Bn128Fq2 Point data incorrect length. Should be " + STORED_LEN + ", is " + data.length);
    }

    byte[] xBytesReal = new byte[WORD_LEN];
    System.arraycopy(data, 0, xBytesReal, 0, WORD_LEN);
    byte[] xBytesImaginary = new byte[WORD_LEN];
    System.arraycopy(data, WORD_LEN, xBytesImaginary, 0, WORD_LEN);
    byte[] yBytesReal = new byte[WORD_LEN];
    System.arraycopy(data, WORD_LEN + WORD_LEN, yBytesReal, 0, WORD_LEN);
    byte[] yBytesImaginary = new byte[WORD_LEN];
    System.arraycopy(data, WORD_LEN + WORD_LEN + WORD_LEN, yBytesImaginary, 0, WORD_LEN);

    Fq2 x = Fq2.create(new BigInteger(xBytesReal), new BigInteger(xBytesImaginary));
    Fq2 y = Fq2.create(new BigInteger(yBytesReal), new BigInteger(yBytesImaginary));
    AltBn128Fq2Point point = new AltBn128Fq2Point(x, y);

    return new AltBn128Fq2PointWrapper(point);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof AltBn128Fq2PointWrapper)) {
      return false;
    }

    return this.point.equals(((AltBn128Fq2PointWrapper) obj).point);
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

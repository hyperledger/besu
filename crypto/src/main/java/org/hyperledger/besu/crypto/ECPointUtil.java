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

import java.math.BigInteger;
import java.security.spec.ECPoint;
import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.math.ec.ECFieldElement;

/** Helper class for ECPoint */
public class ECPointUtil {
  public static ECPoint fromBouncyCastleECPoint(
      final org.bouncycastle.math.ec.ECPoint bouncyCastleECPoint) {
    final ECFieldElement xCoord = bouncyCastleECPoint.getAffineXCoord();
    final ECFieldElement yCoord = bouncyCastleECPoint.getAffineYCoord();

    final Bytes32 xEncoded = Bytes32.wrap(xCoord.getEncoded());
    final Bytes32 yEncoded = Bytes32.wrap(yCoord.getEncoded());

    final BigInteger x = xEncoded.toUnsignedBigInteger();
    final BigInteger y = yEncoded.toUnsignedBigInteger();

    return new ECPoint(x, y);
  }

  public static Bytes getEncodedBytes(final ECPoint ecPoint) {
    final Bytes xBytes = Bytes32.wrap(toUnsignedByteArray(ecPoint.getAffineX()));
    final Bytes yBytes = Bytes32.wrap(toUnsignedByteArray(ecPoint.getAffineY()));

    return Bytes.concatenate(xBytes, yBytes);
  }

  /**
   * Adjust unsigned big integer from ECPoint. If it is less than 32, pad left 0. If it is 33, strip
   * 0.
   *
   * @param value BigInteger whose byte[] to adjust
   * @return byte[] 32 bit array after adjusting sign bit
   * @throws IllegalArgumentException if invalid array size encountered
   */
  @VisibleForTesting
  static byte[] toUnsignedByteArray(final BigInteger value) {
    final byte[] bytes = value.toByteArray();

    if (bytes.length == 32) {
      return bytes;
    }

    if (bytes.length == 33 && bytes[0] == 0) {
      return Arrays.copyOfRange(bytes, 1, 33);
    }

    if (bytes.length == 31) {
      final byte[] padded = new byte[32];
      padded[0] = 0;
      System.arraycopy(bytes, 0, padded, 1, 31);
      return padded;
    }

    throw new IllegalArgumentException(
        "Unexpected byte[] size when converting ECPoint: " + bytes.length);
  }
}

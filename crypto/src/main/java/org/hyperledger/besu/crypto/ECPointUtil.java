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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.util.BigIntegers;

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
    final Bytes xBytes = Bytes32.wrap(BigIntegers.asUnsignedByteArray(32, ecPoint.getAffineX()));
    final Bytes yBytes = Bytes32.wrap(BigIntegers.asUnsignedByteArray(32, ecPoint.getAffineY()));
    return Bytes.concatenate(xBytes, yBytes);
  }
}

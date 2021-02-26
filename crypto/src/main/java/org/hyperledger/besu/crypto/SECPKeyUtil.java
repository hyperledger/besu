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
import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

public class SECPKeyUtil {

  public static Bytes toBytes(final BigInteger key, final int byteLength) {
    final byte[] backing = key.toByteArray();

    if (backing.length == byteLength) {
      return Bytes.wrap(backing);
    }

    // if the input is too long it is shifted to the left
    if (backing.length > byteLength) {
      return Bytes.wrap(backing, backing.length - byteLength, byteLength);
    }

    // if the input is too short it is shifted to the right
    final MutableBytes res = MutableBytes.create(byteLength);
    Bytes.wrap(backing).copyTo(res, byteLength - backing.length);
    return res;
  }

  public static Bytes toPublicKey(final SECPPrivateKey privateKey, final ECDomainParameters curve) {
    BigInteger privKey = privateKey.getEncodedBytes().toUnsignedBigInteger();

    // ECDSA public keys are twice as long as the private keys
    final int publicKeyLength = privateKey.getEncodedBytes().size() * 2;

    /*
     * TODO: FixedPointCombMultiplier currently doesn't support scalars longer than the group
     * order, but that could change in future versions.
     */
    if (privKey.bitLength() > curve.getN().bitLength()) {
      privKey = privKey.mod(curve.getN());
    }

    final ECPoint point = new FixedPointCombMultiplier().multiply(curve.getG(), privKey);

    return Bytes.wrap(
        // point.getEncoded returns as its first byte a constant prefix and the rest of the
        // bytes are the public key.
        // The to parameter of Arrays.copyOfRange must the publicKeyLength + 1, because it is
        // exclusive
        Arrays.copyOfRange(point.getEncoded(false), 1, publicKeyLength + 1));
  }
}

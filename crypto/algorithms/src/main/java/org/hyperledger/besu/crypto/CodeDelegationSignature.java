/*
 * Copyright contributors to Hyperledger Besu.
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

/** Secp signature with code delegation. */
public class CodeDelegationSignature extends SECPSignature {
  private static final BigInteger TWO_POW_256 = BigInteger.TWO.pow(256);

  /**
   * Instantiates a new SECPSignature.
   *
   * @param r the r part of the signature
   * @param s the s part of the signature
   * @param yParity the parity of the y coordinate of the public key
   */
  public CodeDelegationSignature(final BigInteger r, final BigInteger s, final byte yParity) {
    super(r, s, yParity);
  }

  /**
   * Create a new CodeDelegationSignature.
   *
   * @param r the r part of the signature
   * @param s the s part of the signature
   * @param yParity the parity of the y coordinate of the public key
   * @return the new CodeDelegationSignature
   */
  public static CodeDelegationSignature create(
      final BigInteger r, final BigInteger s, final byte yParity) {
    checkNotNull(r);
    checkNotNull(s);

    if (r.compareTo(TWO_POW_256) >= 0) {
      throw new IllegalArgumentException(
          "Invalid 'r' value, should be < 2^256 but got " + r.toString(16));
    }

    if (s.compareTo(TWO_POW_256) >= 0) {
      throw new IllegalArgumentException(
          "Invalid 's' value, should be < 2^256 but got " + s.toString(16));
    }

    return new CodeDelegationSignature(r, s, yParity);
  }
}

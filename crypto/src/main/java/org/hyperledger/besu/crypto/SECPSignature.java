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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class SECPSignature {

  /**
   * The recovery id to reconstruct the public key used to create the signature.
   *
   * <p>The recId is an index from 0 to 3 which indicates which of the 4 possible keys is the
   * correct one. Because the key recovery operation yields multiple potential keys, the correct key
   * must either be stored alongside the signature, or you must be willing to try each recId in turn
   * until you find one that outputs the key you are expecting.
   */
  private final byte recId;

  private final BigInteger r;
  private final BigInteger s;

  private final int privateKeyLength;
  private final int signatureLength;

  private final Supplier<Bytes> encoded = Suppliers.memoize(this::_encodedBytes);

  SECPSignature(
      final BigInteger r, final BigInteger s, final byte recId, final int privateKeyLength) {
    this.r = r;
    this.s = s;
    this.recId = recId;
    this.privateKeyLength = privateKeyLength;
    signatureLength = calculateSignatureLength(privateKeyLength);
  }

  /**
   * Creates a new signature object given its parameters.
   *
   * @param r the 'r' part of the signature.
   * @param s the 's' part of the signature.
   * @param recId the recovery id part of the signature.
   * @param curveOrder The order (n) of the used curve
   * @param privateKeyLength The length of the key in byte
   * @return the created {@link SECPSignature} object.
   * @throws NullPointerException if {@code r} or {@code s} are {@code null}.
   * @throws IllegalArgumentException if any argument is invalid (for instance, {@code v} is neither
   *     27 or 28).
   */
  public static SECPSignature create(
      final BigInteger r,
      final BigInteger s,
      final byte recId,
      final BigInteger curveOrder,
      final int privateKeyLength) {
    checkNotNull(r);
    checkNotNull(s);
    checkInBounds("r", r, curveOrder);
    checkInBounds("s", s, curveOrder);
    if (recId != 0 && recId != 1) {
      throw new IllegalArgumentException(
          "Invalid 'recId' value, should be 0 or 1 but got " + recId);
    }
    return new SECPSignature(r, s, recId, privateKeyLength);
  }

  private static void checkInBounds(
      final String name, final BigInteger i, final BigInteger curveOrder) {
    if (i.compareTo(BigInteger.ONE) < 0) {
      throw new IllegalArgumentException(
          String.format("Invalid '%s' value, should be >= 1 but got %s", name, i));
    }

    if (i.compareTo(curveOrder) >= 0) {
      throw new IllegalArgumentException(
          String.format("Invalid '%s' value, should be < %s but got %s", curveOrder, name, i));
    }
  }

  public static SECPSignature decode(
      final Bytes bytes, final BigInteger curveOrder, final int privateKeyLength) {
    final int signatureLength = calculateSignatureLength(privateKeyLength);
    checkArgument(
        bytes.size() == signatureLength,
        new StringBuilder()
            .append("encoded signature must be")
            .append(signatureLength)
            .append("bytes long")
            .toString());

    final BigInteger r = bytes.slice(0, privateKeyLength).toUnsignedBigInteger();
    final BigInteger s = bytes.slice(privateKeyLength, privateKeyLength).toUnsignedBigInteger();
    final byte recId = bytes.get(signatureLength - 1);
    return SECPSignature.create(r, s, recId, curveOrder, privateKeyLength);
  }

  public Bytes encodedBytes() {
    return encoded.get();
  }

  private Bytes _encodedBytes() {
    final MutableBytes bytes = MutableBytes.create(signatureLength);
    SECPKeyUtil.toBytes(r, privateKeyLength).copyTo(bytes, 0);
    SECPKeyUtil.toBytes(s, privateKeyLength).copyTo(bytes, privateKeyLength);
    bytes.set(signatureLength - 1, recId);
    return bytes;
  }

  private static int calculateSignatureLength(final int privateKeyLength) {
    // ECDSA signatures are twice as long as the private key and have an additional byte for the
    // recId
    return privateKeyLength * 2 + 1;
  }

  @Override
  public boolean equals(final Object other) {
    if (!(other instanceof SECPSignature)) {
      return false;
    }

    final SECPSignature that = (SECPSignature) other;
    return this.r.equals(that.r) && this.s.equals(that.s) && this.recId == that.recId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(r, s, recId);
  }

  public byte getRecId() {
    return recId;
  }

  public BigInteger getR() {
    return r;
  }

  public BigInteger getS() {
    return s;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Signature").append("{");
    sb.append("r=").append(r).append(", ");
    sb.append("s=").append(s).append(", ");
    sb.append("recId=").append(recId);
    return sb.append("}").toString();
  }
}

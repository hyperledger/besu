/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.evm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Arrays;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class UInt256Prop {
  @Provide
  Arbitrary<byte[]> unsigned1to32() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(1)
        .ofMaxSize(32)
        .map(UInt256Prop::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> unsigned0to64() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(0)
        .ofMaxSize(64)
        .map(UInt256Prop::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> singleLimbUnsigned1to4() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(1)
        .ofMaxSize(4)
        .map(UInt256Prop::clampUnsigned32);
  }

  @Provide
  Arbitrary<Integer> shifts() {
    return Arbitraries.integers().between(-512, 512);
  }

  @Property
  void property_roundTripUnsigned_toFromBytesBE(@ForAll("unsigned0to64") final byte[] any) {
    // Arrange
    final byte[] be = clampUnsigned32(any);

    // Act
    final UInt256 u = UInt256.fromBytesBE(be);
    final byte[] back = u.toBytesBE();

    // Assert
    assertThat(back).hasSize(32);
    byte[] expected = bigUnsignedToBytes32(toBigUnsigned(be));
    assertThat(back).containsExactly(expected);
  }

  @Property
  void property_equals_compare_consistent(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act
    final int cmp = UInt256.compare(ua, ub);
    final boolean eq = ua.equals(ub);

    // Assert
    assertThat(cmp == 0).isEqualTo(eq);

    BigInteger ba = toBigUnsigned(a);
    BigInteger bb = toBigUnsigned(b);
    int bc = ba.compareTo(bb);
    assertThat(Integer.signum(cmp)).isEqualTo(Integer.signum(bc));
  }

  @Property
  void property_mod_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] m) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 um = UInt256.fromBytesBE(m);

    // Act
    final byte[] got = ua.mod(um).toBytesBE();

    // Assert
    BigInteger A = toBigUnsigned(a);
    BigInteger M = toBigUnsigned(m);
    byte[] exp = (M.signum() == 0) ? Bytes32.ZERO.toArrayUnsafe() : bigUnsignedToBytes32(A.mod(M));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_mod_singleLimb_matchesBigInteger(
      @ForAll("singleLimbUnsigned1to4") final byte[] a,
      @ForAll("singleLimbUnsigned1to4") final byte[] m) {

    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 um = UInt256.fromBytesBE(m);

    // Act
    final byte[] got = ua.mod(um).toBytesBE();

    // Assert
    BigInteger A = toBigUnsigned(a);
    BigInteger M = toBigUnsigned(m);
    byte[] exp = (M.signum() == 0) ? Bytes32.ZERO.toArrayUnsafe() : bigUnsignedToBytes32(A.mod(M));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_signedMod_matchesEvmSemantics(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] m) {

    // Arrange
    final byte[] a32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();
    final byte[] m32 = Bytes32.leftPad(Bytes.wrap(m)).toArrayUnsafe();
    final BigInteger A = new BigInteger(a32);
    final BigInteger M = new BigInteger(m32);
    final UInt256 ua = UInt256.fromBytesBE(a32);
    final UInt256 um = UInt256.fromBytesBE(m32);

    // Act
    byte[] got = ua.signedMod(um).toBytesBE();

    // Assert
    byte[] expected =
        (M.signum() == 0) ? Bytes32.ZERO.toArrayUnsafe() : computeSignedModExpected(A, M);

    assertThat(got).containsExactly(expected);
  }

  @Property
  void property_addMod_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a,
      @ForAll("unsigned1to32") final byte[] b,
      @ForAll("unsigned1to32") final byte[] m) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 um = UInt256.fromBytesBE(m);

    // Act
    byte[] got = ua.addMod(ub, um).toBytesBE();

    // Assert
    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    BigInteger M = toBigUnsigned(m);
    byte[] exp =
        (M.signum() == 0) ? Bytes32.ZERO.toArrayUnsafe() : bigUnsignedToBytes32(A.add(B).mod(M));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_mulMod_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a,
      @ForAll("unsigned1to32") final byte[] b,
      @ForAll("unsigned1to32") final byte[] m) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 um = UInt256.fromBytesBE(m);

    // Act
    byte[] got = ua.mulMod(ub, um).toBytesBE();

    // Assert
    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    BigInteger M = toBigUnsigned(m);
    byte[] exp =
        (M.signum() == 0)
            ? Bytes32.ZERO.toArrayUnsafe()
            : bigUnsignedToBytes32(A.multiply(B).mod(M));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_divByZero_invariants() {
    // Arrange
    UInt256 x = UInt256.fromBytesBE(new byte[] {1, 2, 3, 4});
    UInt256 zero = UInt256.ZERO;

    // Act & Assert
    assertThat(x.mod(zero).toBytesBE()).containsExactly(Bytes32.ZERO.toArrayUnsafe());
    assertThat(x.signedMod(zero).toBytesBE()).containsExactly(Bytes32.ZERO.toArrayUnsafe());
    assertThat(x.addMod(x, zero).toBytesBE()).containsExactly(Bytes32.ZERO.toArrayUnsafe());
    assertThat(x.mulMod(x, zero).toBytesBE()).containsExactly(Bytes32.ZERO.toArrayUnsafe());
  }

  @Property
  void property_and_matchesBytesAnd(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act
    final byte[] got = ua.and(ub).toBytesBE();

    // Assert - compare with Bytes.and() (existing implementation)
    final Bytes bytesA = Bytes32.leftPad(Bytes.wrap(a));
    final Bytes bytesB = Bytes32.leftPad(Bytes.wrap(b));
    final byte[] expected = bytesA.and(bytesB).toArrayUnsafe();

    assertThat(got).containsExactly(expected);
  }

  @Property
  void property_and_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act
    final byte[] got = ua.and(ub).toBytesBE();

    // Assert - compare with BigInteger.and()
    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] exp = bigUnsignedToBytes32(A.and(B));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_and_commutative(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act & Assert - A & B = B & A
    assertThat(ua.and(ub)).isEqualTo(ub.and(ua));
  }

  @Property
  void property_and_associative(
      @ForAll("unsigned1to32") final byte[] a,
      @ForAll("unsigned1to32") final byte[] b,
      @ForAll("unsigned1to32") final byte[] c) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 uc = UInt256.fromBytesBE(c);

    // Act & Assert - (A & B) & C = A & (B & C)
    assertThat(ua.and(ub).and(uc)).isEqualTo(ua.and(ub.and(uc)));
  }

  @Property
  void property_and_identity(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 allOnes =
        UInt256.fromBytesBE(
            new byte[] {
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
              (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
            });

    // Act & Assert - A & 0xFF...FF = A (identity)
    assertThat(ua.and(allOnes)).isEqualTo(ua);
  }

  @Property
  void property_and_zero(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 zero = UInt256.ZERO;

    // Act & Assert - A & 0 = 0
    assertThat(ua.and(zero)).isEqualTo(zero);
    assertThat(zero.and(ua)).isEqualTo(zero);
  }

  @Property
  void property_and_self(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);

    // Act & Assert - A & A = A (idempotent)
    assertThat(ua.and(ua)).isEqualTo(ua);
  }

  @Property
  void property_and_idempotent(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);

    // Act & Assert - (A & A) & A = A
    assertThat(ua.and(ua).and(ua)).isEqualTo(ua);
  }

  @Property
  void property_and_result_bounded(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act
    final UInt256 result = ua.and(ub);

    // Assert - A & B <= min(A, B)
    assertThat(UInt256.compare(result, ua)).isLessThanOrEqualTo(0);
    assertThat(UInt256.compare(result, ub)).isLessThanOrEqualTo(0);
  }

  @Property
  void property_and_with_complement_is_zero(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final byte[] aBytes32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();

    // Create bitwise complement
    byte[] complementBytes = new byte[32];
    for (int i = 0; i < 32; i++) {
      complementBytes[i] = (byte) ~aBytes32[i];
    }
    final UInt256 complement = UInt256.fromBytesBE(complementBytes);

    // Act & Assert - A & ~A = 0
    assertThat(ua.and(complement)).isEqualTo(UInt256.ZERO);
  }

  @Property
  void property_and_specific_patterns() {
    // Test specific bit patterns
    final UInt256 pattern1 =
        UInt256.fromBytesBE(
            new byte[] {
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA,
              (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA
            }); // 10101010...

    final UInt256 pattern2 =
        UInt256.fromBytesBE(
            new byte[] {
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55,
              (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55
            }); // 01010101...

    // Act & Assert - alternating patterns should give 0
    assertThat(pattern1.and(pattern2)).isEqualTo(UInt256.ZERO);

    // Verify with Bytes implementation
    final Bytes bytes1 = Bytes.wrap(pattern1.toBytesBE());
    final Bytes bytes2 = Bytes.wrap(pattern2.toBytesBE());
    assertThat(pattern1.and(pattern2).toBytesBE())
        .containsExactly(bytes1.and(bytes2).toArrayUnsafe());
  }

  private static byte[] clampUnsigned32(final byte[] any) {
    if (any.length == 0) {
      return new byte[] {0};
    }
    int len = Math.max(1, Math.min(32, any.length));
    byte[] out = new byte[len];
    System.arraycopy(any, 0, out, 0, len);
    return out;
  }

  private static byte[] bigUnsignedToBytes32(final BigInteger x) {
    BigInteger y = x.mod(BigInteger.ONE.shiftLeft(256));

    byte[] ba = y.toByteArray();
    if (ba.length == 0) {
      return new byte[] {0};
    }

    if (ba.length == 32) {
      return ba;
    }

    if (ba.length < 32) {
      byte[] out = new byte[32];
      System.arraycopy(ba, 0, out, 32 - ba.length, ba.length);
      return out;
    }

    // If bigger than 32, take lower 32 bytes.
    byte[] out = new byte[32];
    System.arraycopy(ba, ba.length - 32, out, 0, 32);

    return out;
  }

  private static BigInteger toBigUnsigned(final byte[] be) {
    return new BigInteger(1, be);
  }

  private static byte[] computeSignedModExpected(final BigInteger A, final BigInteger M) {

    BigInteger r = A.abs().mod(M.abs());

    if (A.signum() < 0 && r.signum() != 0) {
      return padNegative(r);
    }

    return bigUnsignedToBytes32(r);
  }

  private static byte[] padNegative(final BigInteger r) {
    BigInteger neg = r.negate();
    byte[] rb = neg.toByteArray();
    byte[] padded = new byte[32];
    Arrays.fill(padded, (byte) 0xFF);
    System.arraycopy(rb, 0, padded, 32 - rb.length, rb.length);
    return padded;
  }
}

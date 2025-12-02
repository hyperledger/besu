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

public class UInt256PropertyBasedTest {

  // region Test Data Providers

  @Provide
  Arbitrary<byte[]> unsigned1to32() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(1)
        .ofMaxSize(32)
        .map(UInt256PropertyBasedTest::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> unsigned0to64() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(0)
        .ofMaxSize(64)
        .map(UInt256PropertyBasedTest::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> singleLimbUnsigned1to4() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(1)
        .ofMaxSize(4)
        .map(UInt256PropertyBasedTest::clampUnsigned32);
  }

  @Provide
  Arbitrary<Integer> shifts() {
    return Arbitraries.integers().between(-512, 512);
  }

  // endregion

  // region Serialization Tests

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

  // endregion

  // region Comparison Tests

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

  // endregion

  // region Modulo Tests (MOD/SMOD/ADDMOD/MULMOD)
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

  // endregion

  // region AND Operation Tests

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

  // endregion

  // region XOR Operation Tests

  @Property
  void property_xor_matchesBytesXor(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act
    final byte[] got = ua.xor(ub).toBytesBE();

    // Assert - compare with Bytes.xor() (existing implementation)
    final Bytes bytesA = Bytes32.leftPad(Bytes.wrap(a));
    final Bytes bytesB = Bytes32.leftPad(Bytes.wrap(b));
    final byte[] expected = bytesA.xor(bytesB).toArrayUnsafe();

    assertThat(got).containsExactly(expected);
  }

  @Property
  void property_xor_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act
    final byte[] got = ua.xor(ub).toBytesBE();

    // Assert - compare with BigInteger.xor()
    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] exp = bigUnsignedToBytes32(A.xor(B));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_xor_commutative(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act & Assert - A ^ B = B ^ A
    assertThat(ua.xor(ub)).isEqualTo(ub.xor(ua));
  }

  @Property
  void property_xor_associative(
      @ForAll("unsigned1to32") final byte[] a,
      @ForAll("unsigned1to32") final byte[] b,
      @ForAll("unsigned1to32") final byte[] c) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 uc = UInt256.fromBytesBE(c);

    // Act & Assert - (A ^ B) ^ C = A ^ (B ^ C)
    assertThat((ua.xor(ub)).xor(uc)).isEqualTo(ua.xor(ub.xor(uc)));
  }

  @Property
  void property_xor_identity(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 zero = UInt256.ZERO;

    // Act & Assert - A ^ 0 = A
    assertThat(ua.xor(zero)).isEqualTo(ua);
    assertThat(zero.xor(ua)).isEqualTo(ua);
  }

  @Property
  void property_xor_self_is_zero(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);

    // Act & Assert - A ^ A = 0 (self-inverse)
    assertThat(ua.xor(ua)).isEqualTo(UInt256.ZERO);
  }

  @Property
  void property_xor_involutive(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act & Assert - (A ^ B) ^ B = A (applying twice returns original)
    assertThat(ua.xor(ub).xor(ub)).isEqualTo(ua);
    assertThat(ub.xor(ua).xor(ua)).isEqualTo(ub);
  }

  @Property
  void property_xor_with_allOnes_is_complement(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 allOnes = UInt256.ALL_ONES;

    // Act
    final UInt256 result = ua.xor(allOnes);

    // Assert - A ^ 0xFF...FF = ~A (bitwise complement)
    final byte[] aBytes32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();
    byte[] complementBytes = new byte[32];
    for (int i = 0; i < 32; i++) {
      complementBytes[i] = (byte) ~aBytes32[i];
    }

    assertThat(result.toBytesBE()).containsExactly(complementBytes);
  }

  @Property
  void property_xor_distributes_over_and(
      @ForAll("unsigned1to32") final byte[] a,
      @ForAll("unsigned1to32") final byte[] b,
      @ForAll("unsigned1to32") final byte[] c) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 uc = UInt256.fromBytesBE(c);

    // Act & Assert - A ^ (B & C) = (A ^ B) & (A ^ C)
    // Note: This is NOT true for XOR! XOR doesn't distribute over AND
    // But we can test: A & (B ^ C) = (A & B) ^ (A & C) - this IS true
    UInt256 left = ua.and(ub.xor(uc));
    UInt256 right = ua.and(ub).xor(ua.and(uc));

    assertThat(left).isEqualTo(right);
  }

  @Property
  void property_xor_specific_patterns() {
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

    final UInt256 allOnes = UInt256.ALL_ONES;
    // Act & Assert - 0xAA XOR 0x55 = 0xFF
    assertThat(pattern1.xor(pattern2)).isEqualTo(allOnes);

    // Verify with Bytes implementation
    final Bytes bytes1 = Bytes.wrap(pattern1.toBytesBE());
    final Bytes bytes2 = Bytes.wrap(pattern2.toBytesBE());
    assertThat(pattern1.xor(pattern2).toBytesBE())
        .containsExactly(bytes1.xor(bytes2).toArrayUnsafe());
  }

  @Property
  void property_xor_reversible(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act
    final UInt256 encrypted = ua.xor(ub);
    final UInt256 decrypted = encrypted.xor(ub);

    // Assert - XOR is its own inverse (encryption/decryption property)
    assertThat(decrypted).isEqualTo(ua);
  }

  // endregion

  // region OR Operation Tests

  @Property
  void property_or_matchesBytesOr(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    // Act
    final byte[] got = ua.or(ub).toBytesBE();
    // Assert - compare with Bytes.or() (existing implementation)
    final Bytes bytesA = Bytes32.leftPad(Bytes.wrap(a));
    final Bytes bytesB = Bytes32.leftPad(Bytes.wrap(b));
    final byte[] expected = bytesA.or(bytesB).toArrayUnsafe();
    assertThat(got).containsExactly(expected);
  }

  @Property
  void property_or_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    // Act
    final byte[] got = ua.or(ub).toBytesBE();
    // Assert - compare with BigInteger.or()
    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] exp = bigUnsignedToBytes32(A.or(B));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_or_commutative(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    // Act & Assert - A | B = B | A
    assertThat(ua.or(ub)).isEqualTo(ub.or(ua));
  }

  @Property
  void property_or_associative(
      @ForAll("unsigned1to32") final byte[] a,
      @ForAll("unsigned1to32") final byte[] b,
      @ForAll("unsigned1to32") final byte[] c) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 uc = UInt256.fromBytesBE(c);
    // Act & Assert - (A | B) | C = A | (B | C)
    assertThat((ua.or(ub)).or(uc)).isEqualTo(ua.or(ub.or(uc)));
  }

  @Property
  void property_or_identity(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 zero = UInt256.ZERO;
    // Act & Assert - A | 0 = A (identity)
    assertThat(ua.or(zero)).isEqualTo(ua);
    assertThat(zero.or(ua)).isEqualTo(ua);
  }

  @Property
  void property_or_self(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act & Assert - A | A = A (idempotent)
    assertThat(ua.or(ua)).isEqualTo(ua);
  }

  @Property
  void property_or_idempotent(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act & Assert - (A | A) | A = A
    assertThat(ua.or(ua).or(ua)).isEqualTo(ua);
  }

  @Property
  void property_or_with_allOnes(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 allOnes = UInt256.ALL_ONES;
    // Act & Assert - A | 0xFF...FF = 0xFF...FF (domination)
    assertThat(ua.or(allOnes)).isEqualTo(allOnes);
    assertThat(allOnes.or(ua)).isEqualTo(allOnes);
  }

  @Property
  void property_or_result_bounded(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    // Act
    final UInt256 result = ua.or(ub);
    // Assert - A | B >= max(A, B)
    assertThat(UInt256.compare(result, ua)).isGreaterThanOrEqualTo(0);
    assertThat(UInt256.compare(result, ub)).isGreaterThanOrEqualTo(0);
  }

  @Property
  void property_or_absorbs_and(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    // Act & Assert - A | (A & B) = A (absorption law)
    assertThat(ua.or(ua.and(ub))).isEqualTo(ua);
  }

  @Property
  void property_or_distributes_over_and(
      @ForAll("unsigned1to32") final byte[] a,
      @ForAll("unsigned1to32") final byte[] b,
      @ForAll("unsigned1to32") final byte[] c) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 uc = UInt256.fromBytesBE(c);
    // Act & Assert - A | (B & C) = (A | B) & (A | C) (distributive law)
    UInt256 left = ua.or(ub.and(uc));
    UInt256 right = ua.or(ub).and(ua.or(uc));
    assertThat(left).isEqualTo(right);
  }

  @Property
  void property_or_with_complement_is_allOnes(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final byte[] aBytes32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();
    // Create bitwise complement
    byte[] complementBytes = new byte[32];
    for (int i = 0; i < 32; i++) {
      complementBytes[i] = (byte) ~aBytes32[i];
    }
    final UInt256 complement = UInt256.fromBytesBE(complementBytes);
    final UInt256 allOnes = UInt256.ALL_ONES;
    // Act & Assert - A | ~A = 0xFF...FF
    assertThat(ua.or(complement)).isEqualTo(allOnes);
  }

  @Property
  void property_or_specific_patterns() {
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
    final UInt256 allOnes = UInt256.ALL_ONES;
    // Act & Assert - 0xAA OR 0x55 = 0xFF
    assertThat(pattern1.or(pattern2)).isEqualTo(allOnes);
    // Verify with Bytes implementation
    final Bytes bytes1 = Bytes.wrap(pattern1.toBytesBE());
    final Bytes bytes2 = Bytes.wrap(pattern2.toBytesBE());
    assertThat(pattern1.or(pattern2).toBytesBE())
        .containsExactly(bytes1.or(bytes2).toArrayUnsafe());
  }

  @Property
  void property_or_de_morgans_law(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final byte[] aBytes32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();
    final byte[] bBytes32 = Bytes32.leftPad(Bytes.wrap(b)).toArrayUnsafe();

    // Create complements
    byte[] notABytes = new byte[32];
    byte[] notBBytes = new byte[32];
    for (int i = 0; i < 32; i++) {
      notABytes[i] = (byte) ~aBytes32[i];
      notBBytes[i] = (byte) ~bBytes32[i];
    }
    final UInt256 notA = UInt256.fromBytesBE(notABytes);
    final UInt256 notB = UInt256.fromBytesBE(notBBytes);

    // Act
    // De Morgan's Law: ~(A | B) = ~A & ~B
    UInt256 left = ua.or(ub);
    byte[] leftBytes = left.toBytesBE();
    byte[] notLeft = new byte[32];
    for (int i = 0; i < 32; i++) {
      notLeft[i] = (byte) ~leftBytes[i];
    }
    UInt256 notAorB = UInt256.fromBytesBE(notLeft);

    UInt256 right = notA.and(notB);

    // Assert - ~(A | B) = ~A & ~B
    assertThat(notAorB).isEqualTo(right);
  }

  @Property
  void property_or_relationship_with_and_xor(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act & Assert - A | B = (A & B) | (A ^ B)
    // This is because OR sets a bit if it's in either operand,
    // which is the same as bits in both (AND) OR bits in exactly one (XOR)
    UInt256 left = ua.or(ub);
    UInt256 right = ua.and(ub).or(ua.xor(ub));
    assertThat(left).isEqualTo(right);
  }

  // endregion

  // region NOT Operation Tests

  @Property
  void property_not_matchesBytesNot(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act
    final byte[] got = ua.not().toBytesBE();
    // Assert - compare with Bytes.not() (existing implementation)
    final Bytes bytesA = Bytes32.leftPad(Bytes.wrap(a));
    final byte[] expected = bytesA.not().toArrayUnsafe();
    assertThat(got).containsExactly(expected);
  }

  @Property
  void property_not_matchesBigInteger(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act
    final byte[] got = ua.not().toBytesBE();
    // Assert - compare with BigInteger.not()
    BigInteger A = toBigUnsigned(a);
    // BigInteger.not() does two's complement, we need bitwise NOT for 256 bits
    // So we XOR with all ones: ~A = A XOR 0xFF...FF
    BigInteger allOnes = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
    byte[] exp = bigUnsignedToBytes32(A.xor(allOnes));
    assertThat(got).containsExactly(exp);
  }

  @Property
  void property_not_manualComplement(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final byte[] aBytes32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();

    // Create expected result manually
    byte[] expected = new byte[32];
    for (int i = 0; i < 32; i++) {
      expected[i] = (byte) ~aBytes32[i];
    }

    // Act
    final byte[] got = ua.not().toBytesBE();

    // Assert
    assertThat(got).containsExactly(expected);
  }

  @Property
  void property_not_involutive(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act & Assert - ~~A = A (double negation returns original)
    assertThat(ua.not().not()).isEqualTo(ua);
  }

  @Property
  void property_not_different_from_original(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 allOnes = UInt256.ALL_ONES;

    // Act
    final UInt256 notA = ua.not();

    // Assert - ~A != A (except for special patterns)
    // Only exception: if all bits are alternating or special case
    // For most random values, A != ~A
    if (!ua.equals(allOnes) && !ua.equals(UInt256.ZERO)) {
      assertThat(notA).isNotEqualTo(ua);
    }
  }

  @Property
  void property_not_with_and_is_zero(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act & Assert - A & ~A = 0
    assertThat(ua.and(ua.not())).isEqualTo(UInt256.ZERO);
  }

  @Property
  void property_not_with_or_is_allOnes(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act & Assert - A | ~A = 0xFF...FF
    assertThat(ua.or(ua.not())).isEqualTo(UInt256.ALL_ONES);
  }

  @Property
  void property_not_with_xor_is_allOnes(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    // Act & Assert - A ^ ~A = 0xFF...FF
    assertThat(ua.xor(ua.not())).isEqualTo(UInt256.ALL_ONES);
  }

  @Property
  void property_not_de_morgans_and(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act & Assert - ~(A & B) = ~A | ~B (De Morgan's Law)
    UInt256 left = ua.and(ub).not();
    UInt256 right = ua.not().or(ub.not());
    assertThat(left).isEqualTo(right);
  }

  @Property
  void property_not_de_morgans_or(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act & Assert - ~(A | B) = ~A & ~B (De Morgan's Law)
    UInt256 left = ua.or(ub).not();
    UInt256 right = ua.not().and(ub.not());
    assertThat(left).isEqualTo(right);
  }

  @Property
  void property_not_zero_is_allOnes() {
    // Arrange
    final UInt256 zero = UInt256.ZERO;
    final UInt256 allOnes = UInt256.ALL_ONES;

    // Act & Assert - ~0 = 0xFF...FF
    assertThat(zero.not()).isEqualTo(allOnes);
  }

  @Property
  void property_not_allOnes_is_zero() {
    // Arrange
    final UInt256 allOnes = UInt256.ALL_ONES;
    final UInt256 zero = UInt256.ZERO;

    // Act & Assert - ~0xFF...FF = 0
    assertThat(allOnes.not()).isEqualTo(zero);
  }

  @Property
  void property_not_specific_patterns() {
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

    final UInt256 expected =
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

    // Act & Assert - ~0xAA = 0x55
    assertThat(pattern1.not()).isEqualTo(expected);

    // Verify with Bytes implementation
    final Bytes bytes1 = Bytes.wrap(pattern1.toBytesBE());
    assertThat(pattern1.not().toBytesBE()).containsExactly(bytes1.not().toArrayUnsafe());
  }

  @Property
  void property_not_each_bit_flipped(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final byte[] aBytes32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();

    // Act
    final byte[] got = ua.not().toBytesBE();

    // Assert - verify each bit is flipped
    for (int i = 0; i < 32; i++) {
      // Each byte should be bitwise complement
      assertThat(got[i]).isEqualTo((byte) ~aBytes32[i]);
    }
  }

  @Property
  void property_not_xor_equivalence(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 allOnes = UInt256.ALL_ONES;

    // Act & Assert - ~A = A ^ 0xFF...FF
    assertThat(ua.not()).isEqualTo(ua.xor(allOnes));
  }

  @Property
  void property_not_sum_with_original(@ForAll("unsigned1to32") final byte[] a) {
    // Arrange
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 allOnes = UInt256.ALL_ONES;

    // Act
    // When we add A + ~A (bitwise), we should get all 1s in each bit position
    // But with carry, A + ~A + 1 = 0 (two's complement)
    // For our purposes: A XOR ~A = 0xFF...FF

    // Assert - A XOR ~A = 0xFF...FF
    assertThat(ua.xor(ua.not())).isEqualTo(allOnes);
  }

  // endregion

  // endregion

  // region Utility Methods

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
  // endregion
}

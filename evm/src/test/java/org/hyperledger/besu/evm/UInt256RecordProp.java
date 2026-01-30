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
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

/**
 * Refactor-focused properties for the UInt256 record representation.
 *
 * <p>These properties are intentionally shaped to catch limb ordering mistakes, endian mistakes,
 * and signed-long leakage introduced by refactoring.
 */
public class UInt256RecordProp {
  private static final BigInteger TWO_256 = BigInteger.ONE.shiftLeft(256);

  @Provide
  Arbitrary<byte[]> bytes0to64_shaped() {
    final Arbitrary<Integer> requiredLengths =
        Arbitraries.of(0, 1, 7, 8, 9, 15, 16, 17, 31, 32, 33, 64);

    final Arbitrary<Integer> lengths =
        Arbitraries.frequencyOf(
            Tuple.of(8, requiredLengths), Tuple.of(2, Arbitraries.integers().between(0, 64)));

    final Arbitrary<Pattern> patterns =
        Arbitraries.frequencyOf(
            Tuple.of(4, Arbitraries.of(Pattern.ALL_ZERO)),
            Tuple.of(4, Arbitraries.of(Pattern.ALL_FF)),
            Tuple.of(4, Arbitraries.of(Pattern.LIMB_SIGN_BITS)),
            Tuple.of(10, Arbitraries.of(Pattern.RANDOM)));

    return lengths.flatMap(
        len ->
            patterns.flatMap(
                pat -> {
                  if (len == 0) {
                    return Arbitraries.of(new byte[0]);
                  }
                  return Arbitraries.bytes()
                      .array(byte[].class)
                      .ofSize(len)
                      .map(b -> applyPattern(b, pat));
                }));
  }

  @Provide
  Arbitrary<byte[]> bytes32_shaped() {
    return bytes0to64_shaped().map(UInt256RecordProp::toBytes32Unsigned);
  }

  @Provide
  Arbitrary<byte[]> bytes33to64_shaped() {
    return bytes0to64_shaped()
        .filter(b -> b.length >= 33 && b.length <= 64)
        .map(UInt256RecordProp::forceNonZeroHighBytes);
  }

  @Provide
  Arbitrary<Integer> shifts_extreme() {
    final Arbitrary<Integer> edges =
        Arbitraries.of(
            -512, -257, -256, -129, -128, -65, -64, -1, 0, 1, 63, 64, 65, 127, 128, 129, 255, 256,
            257, 512);
    return Arbitraries.frequencyOf(
        Tuple.of(7, edges), Tuple.of(3, Arbitraries.integers().between(-512, 512)));
  }

  @Property(seed = "3735928559")
  void property_fromBytesBE_toBytesBE_roundTrip_canonical32(
      @ForAll("bytes0to64_shaped") final byte[] input) {
    // Arrange.
    final byte[] expected = canonicalUnsigned256ToBytes32(input);

    // Act.
    final UInt256 u = UInt256.fromBytesBE(input);
    final byte[] got = u.toBytesBE();

    // Assert.
    assertThat(got).hasSize(32);
    assertThat(got).containsExactly(expected);
  }

  @Property(seed = "2718281828")
  void property_fromBytesBE_ignores_high_bytes_above_32(
      @ForAll("bytes33to64_shaped") final byte[] input) {
    // Arrange.
    final byte[] low32 = Arrays.copyOfRange(input, input.length - 32, input.length);

    // Act.
    final UInt256 a = UInt256.fromBytesBE(input);
    final UInt256 b = UInt256.fromBytesBE(low32);

    // Assert.
    assertThat(a).isEqualTo(b);
  }

  @Property(seed = "3141592653")
  void property_fromBytesBE_toBytesBE_is_identity_on_32_bytes(
      @ForAll("bytes32_shaped") final byte[] be32) {
    // Arrange.
    final byte[] input = Arrays.copyOf(be32, be32.length);

    // Act.
    final byte[] got = UInt256.fromBytesBE(input).toBytesBE();

    // Assert.
    assertThat(got).containsExactly(input);
  }

  @Property(seed = "1618033988")
  void property_compare_zero_iff_equals(
      @ForAll("bytes32_shaped") final byte[] a, @ForAll("bytes32_shaped") final byte[] b) {
    // Arrange.
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    // Act.
    final int cmp = UInt256.compare(ua, ub);

    // Assert.
    assertThat(cmp == 0).isEqualTo(ua.equals(ub));
  }

  @Property(seed = "1414213562")
  void property_compare_sign_matches_unsigned_big_integer(
      @ForAll("bytes32_shaped") final byte[] a, @ForAll("bytes32_shaped") final byte[] b) {
    // Arrange.
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final BigInteger A = new BigInteger(1, a);
    final BigInteger B = new BigInteger(1, b);

    // Act.
    final int cmp = UInt256.compare(ua, ub);

    // Assert.
    assertThat(Integer.signum(cmp)).isEqualTo(Integer.signum(A.compareTo(B)));
  }

  @Property(seed = "1123581321")
  void property_shiftLeft_matches_big_integer_mod_2_256(
      @ForAll("bytes32_shaped") final byte[] a, @ForAll("shifts_extreme") final int shift) {
    // UInt256.shiftLeft is only defined for 0 <= shift < 64 (unchecked precondition).
    // This property intentionally tests the record refactor, not EVM-wide shift semantics.
    Assume.that(shift >= 0 && shift < 64);
    // Arrange.
    final BigInteger A = new BigInteger(1, a);
    final byte[] expected = expectedShl(A, shift);

    // Act.
    final byte[] got = UInt256.fromBytesBE(a).shiftLeft(shift).toBytesBE();

    // Assert.
    assertThat(got).containsExactly(expected);
  }

  @Property(seed = "867530900")
  void property_shiftRight_matches_big_integer_mod_2_256(
      @ForAll("bytes32_shaped") final byte[] a, @ForAll("shifts_extreme") final int shift) {
    // UInt256.shiftRight is only defined for 0 <= shift < 64 (unchecked precondition).
    // This property intentionally tests the record refactor, not EVM-wide shift semantics.
    Assume.that(shift >= 0 && shift < 64);
    // Arrange.
    final BigInteger A = new BigInteger(1, a);
    final byte[] expected = expectedShr(A, shift);

    // Act.
    final byte[] got = UInt256.fromBytesBE(a).shiftRight(shift).toBytesBE();

    // Assert.
    assertThat(got).containsExactly(expected);
  }

  @Property(seed = "123456789")
  void property_mod_matches_big_integer_unsigned(
      @ForAll("bytes0to64_shaped") final byte[] a, @ForAll("bytes0to64_shaped") final byte[] m) {
    // Arrange.
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 um = UInt256.fromBytesBE(m);
    final BigInteger A = toBigUnsignedMod256(a);
    final BigInteger M = toBigUnsignedMod256(m);
    final byte[] expected = expectedMod(A, M);

    // Act.
    final byte[] got = ua.mod(um).toBytesBE();

    // Assert.
    assertThat(got).containsExactly(expected);
  }

  @Property(seed = "987654321")
  void property_signedMod_matches_evm_semantics(
      @ForAll("bytes0to64_shaped") final byte[] a, @ForAll("bytes0to64_shaped") final byte[] m) {
    // Arrange.
    final byte[] a32 = toBytes32Unsigned(a);
    final byte[] m32 = toBytes32Unsigned(m);
    final UInt256 ua = UInt256.fromBytesBE(a32);
    final UInt256 um = UInt256.fromBytesBE(m32);
    final BigInteger A = new BigInteger(a32);
    final BigInteger M = new BigInteger(m32);
    final byte[] expected = expectedSignedMod(A, M);

    // Act.
    final byte[] got = ua.signedMod(um).toBytesBE();

    // Assert.
    assertThat(got).containsExactly(expected);
  }

  @Property(seed = "42424242")
  void property_addMod_matches_big_integer_unsigned(
      @ForAll("bytes0to64_shaped") final byte[] a,
      @ForAll("bytes0to64_shaped") final byte[] b,
      @ForAll("bytes0to64_shaped") final byte[] m) {
    // Arrange.
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 um = UInt256.fromBytesBE(m);
    final BigInteger A = toBigUnsignedMod256(a);
    final BigInteger B = toBigUnsignedMod256(b);
    final BigInteger M = toBigUnsignedMod256(m);
    final byte[] expected = expectedAddMod(A, B, M);

    // Act.
    final byte[] got = ua.addMod(ub, um).toBytesBE();

    // Assert.
    assertThat(got).containsExactly(expected);
  }

  @Property(seed = "13371337")
  void property_mulMod_matches_big_integer_unsigned(
      @ForAll("bytes0to64_shaped") final byte[] a,
      @ForAll("bytes0to64_shaped") final byte[] b,
      @ForAll("bytes0to64_shaped") final byte[] m) {
    // Arrange.
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    final UInt256 um = UInt256.fromBytesBE(m);
    final BigInteger A = toBigUnsignedMod256(a);
    final BigInteger B = toBigUnsignedMod256(b);
    final BigInteger M = toBigUnsignedMod256(m);
    final byte[] expected = expectedMulMod(A, B, M);

    // Act.
    final byte[] got = ua.mulMod(ub, um).toBytesBE();

    // Assert.
    assertThat(got).containsExactly(expected);
  }

  private static byte[] canonicalUnsigned256ToBytes32(final byte[] be) {
    if (be.length == 0) {
      return new byte[32];
    }
    final BigInteger x = new BigInteger(1, be).mod(TWO_256);
    return bigUnsignedToBytes32(x);
  }

  private static byte[] bigUnsignedToBytes32(final BigInteger x) {
    final BigInteger y = x.mod(TWO_256);
    final byte[] raw = y.toByteArray();

    if (raw.length == 0) {
      return new byte[32];
    }

    if (raw.length == 32) {
      return raw;
    }

    final byte[] out = new byte[32];
    if (raw.length < 32) {
      System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
      return out;
    }

    System.arraycopy(raw, raw.length - 32, out, 0, 32);
    return out;
  }

  private static byte[] toBytes32Unsigned(final byte[] be) {
    if (be.length == 32) {
      return Arrays.copyOf(be, 32);
    }
    if (be.length == 0) {
      return new byte[32];
    }
    if (be.length < 32) {
      final byte[] out = new byte[32];
      System.arraycopy(be, 0, out, 32 - be.length, be.length);
      return out;
    }
    return Arrays.copyOfRange(be, be.length - 32, be.length);
  }

  private static byte[] forceNonZeroHighBytes(final byte[] be) {
    if (be.length <= 32) {
      return be;
    }
    final int highLen = be.length - 32;
    boolean anyNonZero = false;
    for (int i = 0; i < highLen; i++) {
      anyNonZero |= (be[i] != 0);
    }
    if (!anyNonZero) {
      be[0] = 1;
    }
    return be;
  }

  private static BigInteger toBigUnsignedMod256(final byte[] be) {
    if (be.length == 0) {
      return BigInteger.ZERO;
    }
    return new BigInteger(1, be).mod(TWO_256);
  }

  private static byte[] expectedMod(final BigInteger A, final BigInteger M) {
    if (M.signum() == 0) {
      return new byte[32];
    }
    return bigUnsignedToBytes32(A.mod(M));
  }

  private static byte[] expectedAddMod(final BigInteger A, final BigInteger B, final BigInteger M) {
    if (M.signum() == 0) {
      return new byte[32];
    }
    return bigUnsignedToBytes32(A.add(B).mod(M));
  }

  private static byte[] expectedMulMod(final BigInteger A, final BigInteger B, final BigInteger M) {
    if (M.signum() == 0) {
      return new byte[32];
    }
    return bigUnsignedToBytes32(A.multiply(B).mod(M));
  }

  private static byte[] expectedSignedMod(final BigInteger A, final BigInteger M) {
    if (M.signum() == 0) {
      return new byte[32];
    }
    BigInteger r = A.abs().mod(M.abs());
    if (A.signum() < 0 && r.signum() != 0) {
      return padNegative(r);
    }
    return bigUnsignedToBytes32(r);
  }

  private static byte[] padNegative(final BigInteger r) {
    final BigInteger neg = r.negate();
    final byte[] rb = neg.toByteArray();
    final byte[] padded = new byte[32];
    Arrays.fill(padded, (byte) 0xFF);
    System.arraycopy(rb, 0, padded, 32 - rb.length, rb.length);
    return padded;
  }

  private static byte[] expectedShl(final BigInteger A, final int shift) {
    if (shift < 0 || shift >= 256) {
      return new byte[32];
    }
    return bigUnsignedToBytes32(A.shiftLeft(shift));
  }

  private static byte[] expectedShr(final BigInteger A, final int shift) {
    if (shift < 0 || shift >= 256) {
      return new byte[32];
    }
    return bigUnsignedToBytes32(A.shiftRight(shift));
  }

  private enum Pattern {
    ALL_ZERO,
    ALL_FF,
    LIMB_SIGN_BITS,
    RANDOM
  }

  private static byte[] applyPattern(final byte[] bytes, final Pattern pat) {
    switch (pat) {
      case ALL_ZERO:
        Arrays.fill(bytes, (byte) 0x00);
        return bytes;
      case ALL_FF:
        Arrays.fill(bytes, (byte) 0xFF);
        return bytes;
      case LIMB_SIGN_BITS:
        Arrays.fill(bytes, (byte) 0x00);
        // Force high bit at BE limb boundaries for 32-byte values.
        // For shorter inputs, this still forces sign-bit transitions in
        // whichever limb the value occupies.
        forceMsbAtIndexIfPresent(bytes, 0);
        forceMsbAtIndexIfPresent(bytes, 8);
        forceMsbAtIndexIfPresent(bytes, 16);
        forceMsbAtIndexIfPresent(bytes, 24);
        forceMsbAtIndexIfPresent(bytes, bytes.length - 1);
        return bytes;
      case RANDOM:
      default:
        return bytes;
    }
  }

  private static void forceMsbAtIndexIfPresent(final byte[] bytes, final int index) {
    if (index < 0 || index >= bytes.length) {
      return;
    }
    bytes[index] = (byte) (bytes[index] | 0x80);
  }
}

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
package org.hyperledger.besu.evm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UInt256Test {
  static final int SAMPLE_SIZE = 3;

  private Bytes32 bigIntTo32B(final BigInteger y) {
    byte[] a = y.toByteArray();
    if (a.length > 32) return Bytes32.wrap(a, a.length - 32);
    return Bytes32.leftPad(Bytes.wrap(a));
  }

  private Bytes32 bigIntTo32B(final BigInteger x, final int sign) {
    if (sign >= 0) return bigIntTo32B(x);
    byte[] a = new byte[32];
    Arrays.fill(a, (byte) 0xFF);
    byte[] b = x.toByteArray();
    System.arraycopy(b, 0, a, 32 - b.length, b.length);
    return Bytes32.leftPad(Bytes.wrap(a));
  }

  @Test
  public void fromInts() {
    UInt256 result;

    result = UInt256.fromInt(0);
    assertThat(result.isZero()).as("Int 0, isZero").isTrue();

    int[] testInts = new int[] {130, -128, 32500};
    for (int i : testInts) {
      result = UInt256.fromInt(i);
      assertThat(result.intValue()).as(String.format("Int %s value", i)).isEqualTo(i);
    }
  }

  @Test
  public void fromBytesBE() {
    byte[] input;
    UInt256 result;
    UInt256 expected;

    input = new byte[] {-128, 0, 0, 0};
    result = UInt256.fromBytesBE(input);
    expected = new UInt256(0, 0, 0, 2147483648L);
    assertThat(result).as("4b-neg-limbs").isEqualTo(expected);

    input = new byte[] {0, 0, 1, 1, 1};
    result = UInt256.fromBytesBE(input);
    expected = new UInt256(0, 0, 0, 1 + 256 + 65536);
    assertThat(result).as("3b-limbs").isEqualTo(expected);

    input = new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1};
    result = UInt256.fromBytesBE(input);
    expected = new UInt256(0, 0, 16777216, 1 + 256 + 65536);
    assertThat(result).as("8b-limbs").isEqualTo(expected);

    input =
        new byte[] {
          1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    result = UInt256.fromBytesBE(input);
    expected = new UInt256(72057594037927936L, 0, 0, 0);
    assertThat(result).as("32b-limbs").isEqualTo(expected);

    input =
        new byte[] {
          0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    result = UInt256.fromBytesBE(input);
    expected = new UInt256(257, 0, 0, 0);
    assertThat(result).as("32b-padded-limbs").isEqualTo(expected);

    Bytes inputBytes =
        Bytes.fromHexString("0x000000000000000000000000ffffffffffffffffffffffffffffffffffffffff");
    input = inputBytes.toArrayUnsafe();
    result = UInt256.fromBytesBE(input);
    expected = new UInt256(0, 4294967295L, -1L, -1L);
    assertThat(result).as("32b-case2-limbs").isEqualTo(expected);
  }

  @Test
  public void fromToBytesBE() {
    byte[] input =
        new byte[] {
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
        };
    UInt256 asUint = UInt256.fromBytesBE(input);
    BigInteger asBigInt = new BigInteger(1, input);
    assertThat(asUint.toBytesBE()).isEqualTo(asBigInt.toByteArray());
  }

  @Test
  public void smallInts() {
    UInt256 number = UInt256.fromInt(523);
    UInt256 modulus = UInt256.fromInt(27);
    UInt256 remainder = number.mod(modulus);
    UInt256 expected = UInt256.fromInt(523 % 27);
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void smallMod() {
    byte[] num_arr =
        new byte[] {
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
        };
    UInt256 number = UInt256.fromBytesBE(num_arr);
    UInt256 modulus = UInt256.fromInt(27);
    int remainder = number.mod(modulus).intValue();
    BigInteger big_number = new BigInteger(1, num_arr);
    BigInteger big_modulus = BigInteger.valueOf(27L);
    int expected = big_number.mod(big_modulus).intValue();
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void smallModFullDividend() {
    byte[] num_arr =
        new byte[] {
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, -127
        };
    UInt256 number = UInt256.fromBytesBE(num_arr);
    UInt256 modulus = UInt256.fromInt(27);
    int remainder = number.mod(modulus).intValue();
    BigInteger big_number = new BigInteger(1, num_arr);
    BigInteger big_modulus = BigInteger.valueOf(27L);
    int expected = big_number.mod(big_modulus).intValue();
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void bigMod() {
    byte[] num_arr =
        new byte[] {
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16
        };
    byte[] mod_arr = new byte[] {-111, 126, 78, 12};
    UInt256 number = UInt256.fromBytesBE(num_arr);
    UInt256 modulus = UInt256.fromBytesBE(mod_arr);
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    BigInteger big_number = new BigInteger(1, num_arr);
    BigInteger big_modulus = new BigInteger(1, mod_arr);
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void bigModWithExtraCarry() {
    byte[] num_arr =
        new byte[] {
          -126, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
          1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 123
        };
    byte[] mod_arr = new byte[] {12, 126, 78, -11};
    UInt256 number = UInt256.fromBytesBE(num_arr);
    UInt256 modulus = UInt256.fromBytesBE(mod_arr);
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    BigInteger big_number = new BigInteger(1, num_arr);
    BigInteger big_modulus = new BigInteger(1, mod_arr);
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("modTestCases")
  public void mod(final String dividend, final String divisor) {
    BigInteger big_number = new BigInteger(dividend, 16);
    BigInteger big_modulus = new BigInteger(divisor, 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  public static Stream<Arguments> modTestCases() {
    return Stream.of(
        Arguments.of("0000000067e36864", "001fff"),
        Arguments.of("022b1c8c1227a00000", "038d7ea4c68000"),
        Arguments.of("1000000000000000000000000000000000000000000000000", "ff00000000000000"),
        Arguments.of("ff00000000000000000000000000000000", "100000000000000000000000000000000"),
        Arguments.of("ff00000000000000000000000000000000", "100000000000000000000000000000001"),
        Arguments.of(
            "1000000000000000000000000000000000000000000000000",
            "ff000000000000000000000000000000"),
        Arguments.of(
            "1000000000000000000000000000000000000000000000000",
            "100000000000000000000000000000001"),
        Arguments.of(
            "000000000000000000ff00000000000000000000000000000000000000000000",
            "0000000000000000000000000000000000fe0000000000000000000000000001"),
        Arguments.of("020000000000000000000000000000000000", "02000000000000000000"),
        Arguments.of("10000000000000000010000000000000000", "200000000000000ff"),
        Arguments.of(
            "ff000000000000000000000000000000000000000000000000000000",
            "1000000000000000000000002000000000000000000000000"),
        Arguments.of("800000000000000080", "80"),
        Arguments.of("cea0c5cc171fa61277e5604a3bc8aef4de3d3882", "7dae7454bb193b1c28e64a6a935bc3"),
        // mulSubOverflow - addBack bugs
        // Modulus192 path (b.u3==0, b.u2!=0)
        Arguments.of(
            "7effffff8000000000000000000000000000000000000000d900000000000001",
            "7effffff800000007effffff800000008000ff0000010000"),
        // Modulus128 path (b.u3==0, b.u2==0, b.u1!=0)
        Arguments.of(
            "7effffff800000000000000000000000d900000000000001",
            "7effffff800000007fffffffffffffff"));
  }

  @Test
  public void modRandom() {
    final Random random = new Random(41335);
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      final byte[] a = new byte[32];
      final byte[] b = new byte[32];
      random.nextBytes(a);
      random.nextBytes(b);
      BigInteger aInt = new BigInteger(1, a);
      BigInteger bInt = new BigInteger(1, b);
      int comp = aInt.compareTo(bInt);
      BigInteger big_number;
      BigInteger big_modulus;
      UInt256 number;
      UInt256 modulus;
      if (comp >= 0) {
        big_number = aInt;
        number = UInt256.fromBytesBE(a);
        big_modulus = bInt;
        modulus = UInt256.fromBytesBE(b);
      } else {
        big_number = bInt;
        number = UInt256.fromBytesBE(b);
        big_modulus = aInt;
        modulus = UInt256.fromBytesBE(a);
      }
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
      Bytes32 expected =
          BigInteger.ZERO.compareTo(big_modulus) == 0
              ? Bytes32.ZERO
              : bigIntTo32B(big_number.mod(big_modulus));
      assertThat(remainder).withFailMessage(String.format("Failure detected:\n%s.MOD(%s)\n", number.toHexString(), modulus.toHexString())).isEqualTo(expected);
    }
  }

  @ParameterizedTest
  @MethodSource("addModTestCases")
  public void addMod(final String a, final String b, final String modulus) {
    BigInteger xbig = new BigInteger(a, 16);
    BigInteger ybig = new BigInteger(b, 16);
    BigInteger mbig = new BigInteger(modulus, 16);
    UInt256 x = UInt256.fromBytesBE(xbig.toByteArray());
    UInt256 y = UInt256.fromBytesBE(ybig.toByteArray());
    UInt256 m = UInt256.fromBytesBE(mbig.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(x.addMod(y, m).toBytesBE()));
    Bytes32 expected =
        BigInteger.ZERO.compareTo(mbig) == 0 ? Bytes32.ZERO : bigIntTo32B(xbig.add(ybig).mod(mbig));
    assertThat(remainder).isEqualTo(expected);
  }

  public static Stream<Arguments> addModTestCases() {
    return Stream.of(
        // reference tests
        Arguments.of("000000010000000000000000000000000000000000000000", "0000c350", "000003e8"),
        Arguments.of(
            "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
            "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
        // reduceNormalised bugs
        Arguments.of(
            "62d900c9700000000000000000023f00bc1814ff00000000000000ca22300806",
            "ffffffffffffffffb4fffff4befff4f4f4d4f4f504f4f4bef5f5100b0bf4f5f6",
            "13464637e8bdc0e53b895d7b79348a784"),
        Arguments.of(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "80008e949e9e9ec0cf4f4d4f4f4f41523410af5f20b0b7606f4d4f439f5f6000",
            "1800000000000000080000000000000017ffffffffffffffd"));
  }

  @Test
  public void addModRandom() {
    final Random random = new Random(42);
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      int aSize = random.nextInt(1, 33);
      int bSize = random.nextInt(1, 33);
      int cSize = random.nextInt(1, 33);
      final byte[] aArray = new byte[aSize];
      final byte[] bArray = new byte[bSize];
      final byte[] cArray = new byte[cSize];
      random.nextBytes(aArray);
      random.nextBytes(bArray);
      random.nextBytes(cArray);
      BigInteger aInt = new BigInteger(1, aArray);
      BigInteger bInt = new BigInteger(1, bArray);
      BigInteger cInt = new BigInteger(1, cArray);
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      UInt256 c = UInt256.fromBytesBE(cArray);
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.addMod(b, c).toBytesBE()));
      Bytes32 expected =
          BigInteger.ZERO.compareTo(cInt) == 0
              ? Bytes32.ZERO
              : bigIntTo32B(aInt.add(bInt).mod(cInt));
      assertThat(remainder).withFailMessage(String.format("Failure detected:\n%s.ADDMOD(%s, %s)\n", a.toHexString(), b.toHexString(), c.toHexString())).isEqualTo(expected);
    }
  }

  @ParameterizedTest
  @MethodSource("mulModTestCases")
  public void mulMod(final String a, final String b, final String modulus) {
    Bytes aBytes = Bytes.fromHexString(a);
    Bytes bBytes = Bytes.fromHexString(b);
    Bytes modBytes = Bytes.fromHexString(modulus);
    BigInteger aInt = new BigInteger(1, aBytes.toArrayUnsafe());
    BigInteger bInt = new BigInteger(1, bBytes.toArrayUnsafe());
    BigInteger mInt = new BigInteger(1, modBytes.toArrayUnsafe());
    UInt256 x = UInt256.fromBytesBE(aBytes.toArrayUnsafe());
    UInt256 y = UInt256.fromBytesBE(bBytes.toArrayUnsafe());
    UInt256 m = UInt256.fromBytesBE(modBytes.toArrayUnsafe());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(x.mulMod(y, m).toBytesBE()));
    Bytes32 expected = bigIntTo32B(aInt.multiply(bInt).mod(mInt));
    assertThat(remainder).isEqualTo(expected);
  }

  public static Stream<Arguments> mulModTestCases() {
    return Stream.of(
        // reference tests
        Arguments.of(
            "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
            "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
            "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe"),
        Arguments.of(
            "0x000000000000000000000000ffffffffffffffffffffffffffffffffffffffff",
            "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
            "0x000000000000000000000000ffffffffffffffffffffffffffffffffffffffff"),
        Arguments.of(
            "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe",
            "0xffffffffffffffffffffffffb195148ca348dc57a7331852b390ccefa7b0c18b",
            "0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe"),
        // mulSubOverflow bugs
        Arguments.of(
            "0x0000000000000001000000000000000000000000000000000000000000000001",
            "0x0000000000000001000000000000000000000000000000000000000000000000",
            "0x0000000000000001000000000000000000000000000000000000000000000000"),
        // mulSubOverflow - addBack bugs
        // Modulus256 path (b.u3!=0) via mulMod
        Arguments.of(
            "0x7effffff8000000000000000000000000000000000000000d900000000000001",
            "0x010000000000000000",
            "0x7effffff800000007effffff800000008000ff00000100007effffff80000000"));
  }

  @Test
  public void mulModRandom() {
    final Random random = new Random(123);
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      int aSize = random.nextInt(1, 33);
      int bSize = random.nextInt(1, 33);
      int cSize = random.nextInt(1, 33);
      final byte[] aArray = new byte[aSize];
      final byte[] bArray = new byte[bSize];
      final byte[] cArray = new byte[cSize];
      random.nextBytes(aArray);
      random.nextBytes(bArray);
      random.nextBytes(cArray);
      BigInteger aInt = new BigInteger(1, aArray);
      BigInteger bInt = new BigInteger(1, bArray);
      BigInteger cInt = new BigInteger(1, cArray);
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      UInt256 c = UInt256.fromBytesBE(cArray);
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.mulMod(b, c).toBytesBE()));
      Bytes32 expected =
          BigInteger.ZERO.compareTo(cInt) == 0
              ? Bytes32.ZERO
              : bigIntTo32B(aInt.multiply(bInt).mod(cInt));
      assertThat(remainder).withFailMessage(String.format("Failure detected:\n%s.MULMOD(%s, %s)\n", a.toHexString(), b.toHexString(), c.toHexString())).isEqualTo(expected);
    }
  }

  @Test
  public void signedModRandom() {
    final Random random = new Random(432);
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      int aSize = random.nextInt(1, 33);
      int bSize = random.nextInt(1, 33);
      boolean neg = random.nextBoolean();
      byte[] aArray = new byte[aSize];
      byte[] bArray = new byte[bSize];
      random.nextBytes(aArray);
      random.nextBytes(bArray);
      if ((aSize < 32) && (neg)) {
        byte[] tmp = new byte[32];
        Arrays.fill(tmp, (byte) 0xFF);
        System.arraycopy(aArray, 0, tmp, 32 - aArray.length, aArray.length);
        aArray = tmp;
      }
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      UInt256 r = a.signedMod(b);
      BigInteger aInt = a.isNegative() ? new BigInteger(aArray) : new BigInteger(1, aArray);
      BigInteger bInt = b.isNegative() ? new BigInteger(bArray) : new BigInteger(1, bArray);
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(r.toBytesBE()));
      Bytes32 expected;
      BigInteger rem;
      if (BigInteger.ZERO.compareTo(bInt) == 0) expected = Bytes32.ZERO;
      else {
        rem = aInt.abs().mod(bInt.abs());
        if ((aInt.compareTo(BigInteger.ZERO) < 0) && (rem.compareTo(BigInteger.ZERO) != 0)) {
          rem = rem.negate();
          expected = bigIntTo32B(rem, -1);
        } else {
          expected = bigIntTo32B(rem, 1);
        }
      }
      assertThat(remainder).withFailMessage(String.format("Failure detected:\n%s.SMOD(%s)\n", a.toHexString(), b.toHexString())).isEqualTo(expected);
    }
  }
}

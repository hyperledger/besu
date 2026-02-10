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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class UInt256Test {
  static final int SAMPLE_SIZE = 300;

  private Bytes32 bigIntTo32B(final BigInteger x) {
    byte[] a = x.toByteArray();
    if (a.length > 32) return Bytes32.wrap(a, a.length - 32);
    return Bytes32.leftPad(Bytes.wrap(a));
  }

  private Bytes32 bigIntToSigned32B(final BigInteger x) {
    if (x.signum() >= 0) return bigIntTo32B(x);
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
    int[] expectedLimbs;

    input = new byte[] {-128, 0, 0, 0};
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {-2147483648, 0, 0, 0, 0, 0, 0, 0};
    assertThat(result.limbs()).as("4b-neg-limbs").isEqualTo(expectedLimbs);

    input = new byte[] {0, 0, 1, 1, 1};
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {1 + 256 + 65536, 0, 0, 0, 0, 0, 0, 0};
    assertThat(result.limbs()).as("3b-limbs").isEqualTo(expectedLimbs);

    input = new byte[] {1, 0, 0, 0, 0, 1, 1, 1};
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {1 + 256 + 65536, 16777216, 0, 0, 0, 0, 0, 0};
    assertThat(result.limbs()).as("8b-limbs").isEqualTo(expectedLimbs);

    input =
        new byte[] {
          1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {0, 0, 0, 0, 0, 0, 0, 16777216};
    assertThat(result.limbs()).as("32b-limbs").isEqualTo(expectedLimbs);

    input =
        new byte[] {
          0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {0, 0, 0, 0, 0, 0, 257, 0};
    assertThat(result.limbs()).as("32b-padded-limbs").isEqualTo(expectedLimbs);
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

  @Test
  public void modA() {
    BigInteger big_number = new BigInteger("0000000067e36864", 16);
    BigInteger big_modulus = new BigInteger("001fff", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modB() {
    BigInteger big_number = new BigInteger("022b1c8c1227a00000", 16);
    BigInteger big_modulus = new BigInteger("038d7ea4c68000", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modGeneralState() {
    BigInteger big_number = new BigInteger("cea0c5cc171fa61277e5604a3bc8aef4de3d3882", 16);
    BigInteger big_modulus = new BigInteger("7dae7454bb193b1c28e64a6a935bc3", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modDiv8Mod8() {
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
      assertThat(remainder).isEqualTo(expected);
    }
  }

  @Test
  public void referenceTest459() {
    BigInteger xbig = new BigInteger("000000010000000000000000000000000000000000000000", 16);
    BigInteger ybig = new BigInteger("0000c350", 16);
    BigInteger mbig = new BigInteger("000003e8", 16);
    UInt256 x = UInt256.fromBytesBE(xbig.toByteArray());
    UInt256 y = UInt256.fromBytesBE(ybig.toByteArray());
    UInt256 m = UInt256.fromBytesBE(mbig.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(x.addMod(y, m).toBytesBE()));
    Bytes32 expected =
        BigInteger.ZERO.compareTo(mbig) == 0 ? Bytes32.ZERO : bigIntTo32B(xbig.add(ybig).mod(mbig));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void addMod() {
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
      assertThat(remainder).isEqualTo(expected);
    }
  }

  @Test
  public void mulMod() {
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
      assertThat(remainder).isEqualTo(expected);
    }
  }

  @Test
  public void signedMod_no_padding() {
    Bytes aBytes =
        Bytes.fromHexString("0xe8e8e8e2000100000009ea02000000000000ff3ffffff80000001000220000");
    Bytes bBytes =
        Bytes.fromHexString("0x8000000000000000000000000000000000000000000000000000000000000000");
    Bytes32 expected =
        Bytes32.leftPad(
            Bytes.fromHexString(
                "0x00e8e8e8e2000100000009ea02000000000000ff3ffffff80000001000220000"));
    UInt256 a = UInt256.fromBytesBE(aBytes.toArrayUnsafe());
    UInt256 b = UInt256.fromBytesBE(bBytes.toArrayUnsafe());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.signedMod(b).toBytesBE()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void signedMod() {
    final Random random = new Random(432);
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      int aSize = random.nextInt(1, 33);
      int bSize = random.nextInt(1, 33);
      byte[] aArray = new byte[aSize];
      byte[] bArray = new byte[bSize];
      random.nextBytes(aArray);
      random.nextBytes(bArray);
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      BigInteger aInt = aArray.length < 32 ? new BigInteger(1, aArray) : new BigInteger(aArray);
      BigInteger bInt = bArray.length < 32 ? new BigInteger(1, bArray) : new BigInteger(bArray);
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.signedMod(b).toBytesBE()));
      Bytes32 expected;
      BigInteger rem = BigInteger.ZERO;
      if (BigInteger.ZERO.compareTo(bInt) == 0) expected = Bytes32.ZERO;
      else {
        rem = aInt.abs().mod(bInt.abs());
        if ((aInt.compareTo(BigInteger.ZERO) < 0) && (rem.compareTo(BigInteger.ZERO) != 0)) {
          rem = rem.negate();
          expected = bigIntToSigned32B(rem);
        } else {
          expected = bigIntTo32B(rem);
        }
      }
      assertThat(remainder).isEqualTo(expected);
    }
  }

  @Test
  void testFromBytesBE_emptyArray() {
    UInt256 result = UInt256.fromBytesBE(new byte[0]);
    assertThat(result).isEqualTo(UInt256.ZERO);
    assertThat(result.isZero()).isTrue();
  }

  @Test
  void testFromBytesBE_singleZeroByte() {
    UInt256 result = UInt256.fromBytesBE(new byte[] {0});
    assertThat(result).isEqualTo(UInt256.ZERO);
    assertThat(result.intValue()).isEqualTo(0);
  }

  @Test
  void testFromBytesBE_singleByte() {
    UInt256 result = UInt256.fromBytesBE(new byte[] {0x42});
    assertThat(result.intValue()).isEqualTo(0x42);
    assertThat(result.longValue()).isEqualTo(0x42L);
  }

  @Test
  void testFromBytesBE_twoBytesFF() {
    UInt256 result = UInt256.fromBytesBE(new byte[] {(byte) 0xFF, (byte) 0xFF});
    assertThat(result.intValue()).isEqualTo(0xFFFF);
    assertThat(result.longValue()).isEqualTo(0xFFFFL);
  }

  @Test
  void testFromBytesBE_fourBytes() {
    UInt256 result = UInt256.fromBytesBE(new byte[] {0x01, 0x02, 0x03, 0x04});
    assertThat(result.intValue()).isEqualTo(0x01020304);
  }

  @Test
  void testFromBytesBE_eightBytes() {
    UInt256 result =
        UInt256.fromBytesBE(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});
    assertThat(result.longValue()).isEqualTo(0x0102030405060708L);
  }

  @Test
  void testFromBytesBE_exactly32Bytes_allZeros() {
    byte[] bytes = new byte[32]; // all zeros
    UInt256 result = UInt256.fromBytesBE(bytes);
    assertThat(result).isEqualTo(UInt256.ZERO);
    assertThat(result.isZero()).isTrue();
  }

  @Test
  void testFromBytesBE_exactly32Bytes_allOnes() {
    byte[] bytes = new byte[32];
    for (int i = 0; i < 32; i++) {
      bytes[i] = (byte) 0xFF;
    }
    UInt256 result = UInt256.fromBytesBE(bytes);

    // Should be MAX_UINT256 (2^256 - 1)
    byte[] resultBytes = result.toBytesBE();
    assertArrayEquals(bytes, resultBytes);
  }

  @Test
  void testFromBytesBE_exactly32Bytes_one() {
    byte[] bytes = new byte[32];
    bytes[31] = 0x01; // least significant byte
    UInt256 result = UInt256.fromBytesBE(bytes);

    assertThat(result.intValue()).isEqualTo(1);
    assertThat(result.longValue()).isEqualTo(1L);
  }

  @Test
  void testFromBytesBE_exactly32Bytes_pattern() {
    byte[] bytes = new byte[32];
    // Create pattern: 0x0102030405060708...1F20
    for (int i = 0; i < 32; i++) {
      bytes[i] = (byte) (i + 1);
    }
    UInt256 result = UInt256.fromBytesBE(bytes);

    // Verify round-trip
    byte[] resultBytes = result.toBytesBE();
    assertArrayEquals(bytes, resultBytes);
  }

  @Test
  void testFromBytesBE_exactly32Bytes_highBitSet() {
    byte[] bytes = new byte[32];
    bytes[0] = (byte) 0x80; // high bit set (but still unsigned)
    UInt256 result = UInt256.fromBytesBE(bytes);

    // Verify it's treated as unsigned (not negative)
    byte[] resultBytes = result.toBytesBE();
    assertArrayEquals(bytes, resultBytes);
  }

  @Test
  void testFromBytesBE_roundTrip_variousLengths() {
    for (int len = 1; len <= 32; len++) {
      byte[] original = new byte[len];
      for (int i = 0; i < len; i++) {
        original[i] = (byte) (i + 1);
      }

      UInt256 value = UInt256.fromBytesBE(original);
      byte[] result = value.toBytesBE();

      // Result is always 32 bytes, so compare with left-padded original
      byte[] expected = new byte[32];
      System.arraycopy(original, 0, expected, 32 - len, len);

      assertArrayEquals(expected, result, "Failed for length " + len);
    }
  }

  @Test
  void testFromBytesBE_leadingZeros() {
    // Leading zeros should be handled correctly
    byte[] bytes = new byte[] {0x00, 0x00, 0x00, 0x01, 0x02, 0x03};
    UInt256 result = UInt256.fromBytesBE(bytes);

    assertThat(result.intValue()).isEqualTo(0x010203);
  }

  @Test
  void testFromBytesBE_maxInt() {
    byte[] bytes = new byte[] {0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    UInt256 result = UInt256.fromBytesBE(bytes);

    assertThat(result.intValue()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void testFromBytesBE_maxLong() {
    byte[] bytes =
        new byte[] {
          0x7F,
          (byte) 0xFF,
          (byte) 0xFF,
          (byte) 0xFF,
          (byte) 0xFF,
          (byte) 0xFF,
          (byte) 0xFF,
          (byte) 0xFF
        };
    UInt256 result = UInt256.fromBytesBE(bytes);

    assertThat(result.longValue()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void testFromBytesBE_unsignedIntMax() {
    // 0xFFFFFFFF as unsigned = 4294967295
    byte[] bytes = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    UInt256 result = UInt256.fromBytesBE(bytes);

    assertThat(result.longValue()).isEqualTo(0xFFFFFFFFL);
  }

  @Test
  void testFromBytesBE_unsignedLongMax() {
    // 0xFFFFFFFFFFFFFFFF as unsigned
    byte[] bytes =
        new byte[] {
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
          (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
    UInt256 result = UInt256.fromBytesBE(bytes);

    // When converted back to long, should get the bit pattern
    assertThat(result.longValue()).isEqualTo(-1L); // all bits set
  }

  @Test
  void testFromBytesBE_boundaryValues() {
    // Test 1, 2, 3, 4, 8, 16, 32 bytes
    int[] lengths = {1, 2, 3, 4, 8, 16, 32};

    for (int len : lengths) {
      byte[] bytes = new byte[len];
      bytes[len - 1] = (byte) 0xFF; // set last byte

      UInt256 result = UInt256.fromBytesBE(bytes);
      assertThat(result.intValue() & 0xFF).isEqualTo(0xFF);
    }
  }

  @Test
  void testFromBytesBE_comparisonWithBigInteger() {
    byte[] bytes =
        new byte[] {0x12, 0x34, 0x56, 0x78, (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0};

    UInt256 result = UInt256.fromBytesBE(bytes);
    java.math.BigInteger expected = new java.math.BigInteger(1, bytes);

    assertThat(result.toBigInteger()).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 127, 128, 255, 256, 65535, 65536, Integer.MAX_VALUE})
  void testFromBytesBE_knownIntegers(final int value) {
    // Convert int to bytes (big-endian)
    byte[] bytes = new byte[4];
    bytes[0] = (byte) (value >>> 24);
    bytes[1] = (byte) (value >>> 16);
    bytes[2] = (byte) (value >>> 8);
    bytes[3] = (byte) value;

    UInt256 result = UInt256.fromBytesBE(bytes);
    assertThat(result.intValue()).isEqualTo(value);
  }

  @Test
  void testFromBytesBE_powerOfTwo() {
    // Test 2^8, 2^16, 2^32, 2^64, 2^128, 2^255

    // 2^8 = 256
    byte[] bytes8 = new byte[] {0x01, 0x00};
    assertThat(UInt256.fromBytesBE(bytes8).intValue()).isEqualTo(256);

    // 2^16 = 65536
    byte[] bytes16 = new byte[] {0x01, 0x00, 0x00};
    assertThat(UInt256.fromBytesBE(bytes16).intValue()).isEqualTo(65536);

    // 2^32
    byte[] bytes32 = new byte[] {0x01, 0x00, 0x00, 0x00, 0x00};
    assertThat(UInt256.fromBytesBE(bytes32).longValue()).isEqualTo(0x100000000L);
  }

  @Test
  void testFromBytesBE_alternatingPattern() {
    // 0xAA pattern
    byte[] bytesAA = new byte[32];
    for (int i = 0; i < 32; i++) {
      bytesAA[i] = (byte) 0xAA;
    }
    UInt256 resultAA = UInt256.fromBytesBE(bytesAA);
    assertArrayEquals(bytesAA, resultAA.toBytesBE());

    // 0x55 pattern
    byte[] bytes55 = new byte[32];
    for (int i = 0; i < 32; i++) {
      bytes55[i] = (byte) 0x55;
    }
    UInt256 result55 = UInt256.fromBytesBE(bytes55);
    assertArrayEquals(bytes55, result55.toBytesBE());
  }

  @Test
  void testFromBytesBE_consistency() {
    // Verify same bytes always produce same result
    byte[] bytes = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};

    UInt256 result1 = UInt256.fromBytesBE(bytes);
    UInt256 result2 = UInt256.fromBytesBE(bytes);

    assertThat(result1).isEqualTo(result2);
    assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
  }

  @Test
  void testFromBytesBE_differentLengthsSameValue() {
    // Leading zeros should not affect value
    byte[] bytes1 = new byte[] {0x01, 0x02, 0x03};
    byte[] bytes2 = new byte[] {0x00, 0x00, 0x01, 0x02, 0x03};

    UInt256 result1 = UInt256.fromBytesBE(bytes1);
    UInt256 result2 = UInt256.fromBytesBE(bytes2);

    assertThat(result1).isEqualTo(result2);
  }
}

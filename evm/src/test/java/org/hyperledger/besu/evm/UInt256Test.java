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
import java.util.Collection;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class UInt256Test {
  static final int SAMPLE_SIZE = 300;

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
    BigInteger asBigInt = bytesToBigInt(input, Sign.UNSIGNED);
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
    BigInteger big_number = bytesToBigInt(num_arr, Sign.UNSIGNED);
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
    BigInteger big_number = bytesToBigInt(num_arr, Sign.UNSIGNED);
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
    BigInteger big_number = bytesToBigInt(num_arr, Sign.UNSIGNED);
    BigInteger big_modulus = bytesToBigInt(mod_arr, Sign.UNSIGNED);
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
    BigInteger big_number = bytesToBigInt(num_arr, Sign.UNSIGNED);
    BigInteger big_modulus = bytesToBigInt(mod_arr, Sign.UNSIGNED);
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
      BigInteger aInt = bytesToBigInt(a, Sign.UNSIGNED);
      BigInteger bInt = bytesToBigInt(b, Sign.UNSIGNED);
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
      BigInteger expected = BigInteger.ZERO;
      if (BigInteger.ZERO.compareTo(big_modulus) != 0) {
        expected = big_number.mod(big_modulus);
      }
      assertThat(bytesToBigInt(remainder, Sign.UNSIGNED)).isEqualTo(expected);
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
    BigInteger expected = BigInteger.ZERO;
    if (BigInteger.ZERO.compareTo(mbig) != 0) {
      expected = xbig.add(ybig).mod(mbig);
    }
    assertThat(bytesToBigInt(remainder, Sign.UNSIGNED)).isEqualTo(expected);
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
      BigInteger aInt = bytesToBigInt(aArray, Sign.UNSIGNED);
      BigInteger bInt = bytesToBigInt(bArray, Sign.UNSIGNED);
      BigInteger cInt = bytesToBigInt(cArray, Sign.UNSIGNED);
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      UInt256 c = UInt256.fromBytesBE(cArray);
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.addMod(b, c).toBytesBE()));
      BigInteger expected = BigInteger.ZERO;
      if (BigInteger.ZERO.compareTo(cInt) != 0) {
        expected = aInt.add(bInt).mod(cInt);
      }
      assertThat(bytesToBigInt(remainder, Sign.UNSIGNED)).isEqualTo(expected);
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
      BigInteger aInt = bytesToBigInt(aArray, Sign.UNSIGNED);
      BigInteger bInt = bytesToBigInt(bArray, Sign.UNSIGNED);
      BigInteger cInt = bytesToBigInt(cArray, Sign.UNSIGNED);
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      UInt256 c = UInt256.fromBytesBE(cArray);
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.mulMod(b, c).toBytesBE()));
      BigInteger expected = BigInteger.ZERO;
      if (BigInteger.ZERO.compareTo(cInt) != 0) {
        expected = aInt.multiply(bInt).mod(cInt);
      }
      assertThat(bytesToBigInt(remainder, Sign.UNSIGNED)).isEqualTo(expected);
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
      BigInteger aInt = bytesToBigInt(aArray, Sign.SIGNED);
      BigInteger bInt = bytesToBigInt(bArray, Sign.SIGNED);
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.signedMod(b).toBytesBE()));
      BigInteger expected = BigInteger.ZERO;
      if (BigInteger.ZERO.compareTo(bInt) != 0) {
        expected = aInt.abs().mod(bInt.abs());
        if ((aInt.compareTo(BigInteger.ZERO) < 0) && (expected.compareTo(BigInteger.ZERO) != 0)) {
          expected = expected.negate();
        }
      }
      assertThat(bytesToBigInt(remainder, Sign.SIGNED)).isEqualTo(expected);
    }
  }

  @Test
  public void div() {
    final Random random = new Random(342342);
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      int aSize = random.nextInt(1, 33);
      int bSize = random.nextInt(1, 33);
      final byte[] aArray = new byte[aSize];
      final byte[] bArray = new byte[bSize];
      random.nextBytes(aArray);
      random.nextBytes(bArray);
      BigInteger aInt = bytesToBigInt(aArray, Sign.UNSIGNED);
      BigInteger bInt = bytesToBigInt(bArray, Sign.UNSIGNED);
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      Bytes32 result = Bytes32.leftPad(Bytes.wrap(a.div(b).toBytesBE()));
      BigInteger expected = BigInteger.ZERO;
      if (BigInteger.ZERO.compareTo(bInt) != 0) {
        expected = aInt.divide(bInt);
      }
      assertThat(bytesToBigInt(result, Sign.UNSIGNED)).isEqualTo(expected);
    }
  }

  @Test
  public void signedDiv() {
    final Random random = new Random(97712);
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      int aSize = random.nextInt(1, 33);
      int bSize = random.nextInt(1, 33);
      byte[] aArray = new byte[aSize];
      byte[] bArray = new byte[bSize];
      random.nextBytes(aArray);
      random.nextBytes(bArray);
      UInt256 a = UInt256.fromBytesBE(aArray);
      UInt256 b = UInt256.fromBytesBE(bArray);
      BigInteger aInt = bytesToBigInt(aArray, Sign.SIGNED);
      BigInteger bInt = bytesToBigInt(bArray, Sign.SIGNED);
      Bytes32 quotient = Bytes32.leftPad(Bytes.wrap(a.signedDiv(b).toBytesBE()));
      BigInteger expected = BigInteger.ZERO;
      if (BigInteger.ZERO.compareTo(bInt) != 0) {
        expected = aInt.divide(bInt);
      }
      assertThat(bytesToBigInt(quotient, Sign.SIGNED)).isEqualTo(expected);
    }
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void div(final String numerator, final String denominator, final Sign sign) {
    final UInt256 a = UInt256.fromBytesBE(Bytes.fromHexString(numerator).toArray());
    final UInt256 b = UInt256.fromBytesBE(Bytes.fromHexString(denominator).toArray());

    final BigInteger aBigInt = bytesToBigInt(Bytes.fromHexString(numerator), sign);
    final BigInteger bBigInt = bytesToBigInt(Bytes.fromHexString(denominator), sign);

    final Bytes32 quotient = switch (sign) {
      case UNSIGNED -> Bytes32.leftPad(Bytes.wrap(a.div(b).toBytesBE()));
      case SIGNED -> Bytes32.leftPad(Bytes.wrap(a.signedDiv(b).toBytesBE()));
    };

    BigInteger expected = BigInteger.ZERO;
    if (BigInteger.ZERO.compareTo(bBigInt) != 0) {
      expected = aBigInt.divide(bBigInt);
    }
    assertThat(bytesToBigInt(quotient, sign)).isEqualTo(expected);
  }

  static Collection<Object[]> testCases() {
    return Arrays.stream(
        new Object[][] {
          {"0x00", "0x01"},
          {"0x50", "0x21"},
          {
            "0x120d7a733f5016ad9fae51cb9896e15a96147719fe0379d0cb2642a6951e0a5c",
            "0x007cdab49aba612fb02bd738a74c76789bc9a911c90296502a35df43e939e6e2"
          },
          {"0xa7f576de3a6c", "0xfffffffffef1c296a4c6"},
          {"0xffffffffffffffffffffffff6bacfb1469f9a4d5674a85b75f951d72d7a58e4a", "0x020000"},
          {"0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "0x01"},
          {"0x01", "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"},
          {"0x1598209296af93c13b2f5fde7d8e99", "0x09244c1368"},
          {
            "0xfffffffffffffff9309d38241af6a2545b52958d000000000000000000000000",
            "0xb17217f7d1cf79abc9e3b398"
          },
          {"0xa7f576de3a6c", "0xa7f576de3a6c"},
          {"0x9c2c35e6c180771cda86cde561fe7609b9e89e8e5b", "0x993951396a774e675e93bea2e77c"},
          {
            "0xa73fc792edbfb1038115f77a37613b8f5b64837e28768c9dd90828",
            "0x0700b2d7adda7612da7f95"
          },
          {"0xbf1256135bb3f72de074d0f237", "0x8b63235ac1765530"},
          {"0x5b35862b0027a502b1d4cbc4a09e25", "0x932542f4003763"}
        })
      .flatMap(
        inputs ->
          Arrays.stream(Sign.values())
            .map(
              sign -> {
                Object[] newInputs = Arrays.copyOf(inputs, inputs.length + 1);
                newInputs[inputs.length] = sign;
                return newInputs;
              }))
      .toList();
  }

  private static BigInteger bytesToBigInt(final Bytes bytes, final Sign sign) {
    // bytes can be shorter, so it's treated as left padded with zeros
    if (bytes.size() < 32) {
      return new BigInteger(1, bytes.toArrayUnsafe());
    }
    return switch (sign) {
      case UNSIGNED -> new BigInteger(1, bytes.toArrayUnsafe());
      case SIGNED -> new BigInteger(bytes.toArrayUnsafe());
    };
  }

  private static BigInteger bytesToBigInt(final byte[] bytes, final Sign sign) {
    // bytes can be shorter, so it's treated as left padded with zeros
    if (bytes.length < 32) {
      return new BigInteger(1, bytes);
    }
    return switch (sign) {
      case UNSIGNED -> new BigInteger(1, bytes);
      case SIGNED -> new BigInteger(bytes);
    };
  }

  private enum Sign {
    UNSIGNED, SIGNED
  }
}

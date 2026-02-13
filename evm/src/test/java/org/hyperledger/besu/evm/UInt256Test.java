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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

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
    if (a.length > 32) return Bytes32.wrap(a, a.length - 32);
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
  public void modC() {
    BigInteger big_number = new BigInteger("1000000000000000000000000000000000000000000000000", 16);
    BigInteger big_modulus = new BigInteger("ff00000000000000", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modD() {
    BigInteger big_number = new BigInteger("ff00000000000000000000000000000000", 16);
    BigInteger big_modulus = new BigInteger("100000000000000000000000000000000", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modE() {
    BigInteger big_number = new BigInteger("ff00000000000000000000000000000000", 16);
    BigInteger big_modulus = new BigInteger("100000000000000000000000000000001", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modF() {
    BigInteger big_number = new BigInteger("1000000000000000000000000000000000000000000000000", 16);
    BigInteger big_modulus = new BigInteger("ff000000000000000000000000000000", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modG() {
    BigInteger big_number = new BigInteger("1000000000000000000000000000000000000000000000000", 16);
    BigInteger big_modulus = new BigInteger("100000000000000000000000000000001", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modH() {
    BigInteger big_number =
        new BigInteger("000000000000000000ff00000000000000000000000000000000000000000000", 16);
    BigInteger big_modulus =
        new BigInteger("0000000000000000000000000000000000fe0000000000000000000000000001", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modI() {
    // modulus 128 with overflow case
    BigInteger big_number = new BigInteger("020000000000000000000000000000000000", 16);
    BigInteger big_modulus = new BigInteger("02000000000000000000", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modJ() {
    // modulus 128 with overflow case -> 2 add back in quotient estimate div2by1.
    BigInteger big_number = new BigInteger("10000000000000000010000000000000000", 16);
    BigInteger big_modulus = new BigInteger("200000000000000ff", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modK() {
    // modulus 128 with overflow case -> 2 add back in quotient estimate div2by1.
    BigInteger big_number =
        new BigInteger("ff000000000000000000000000000000000000000000000000000000", 16);
    BigInteger big_modulus =
        new BigInteger("1000000000000000000000002000000000000000000000000", 16);
    UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
    UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
    Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
    assertThat(remainder).isEqualTo(expected);
  }

  @Test
  public void modL() {
    // modulus 128 with overflow case -> 2 add back in quotient estimate div2by1.
    BigInteger big_number =
        new BigInteger("800000000000000080", 16);
    BigInteger big_modulus =
        new BigInteger("80", 16);
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
  public void ExecutionSpecStateTest_453() {
    byte[] xArr =
        new byte[] {
          -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
          -1, -1, -1, -1, -1, -1, -1, -1, -1, -2
        };
    byte[] mArr =
        new byte[] {
          -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
          -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        };
    BigInteger xbig = new BigInteger(1, xArr);
    BigInteger ybig = new BigInteger(1, xArr);
    BigInteger mbig = new BigInteger(1, mArr);
    UInt256 x = UInt256.fromBytesBE(xArr);
    UInt256 y = UInt256.fromBytesBE(xArr);
    UInt256 m = UInt256.fromBytesBE(mArr);
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
  public void mulMod_ExecutionSpecStateTest_457() {
    Bytes value0 =
        Bytes.fromHexString("0x000000000000000000000000ffffffffffffffffffffffffffffffffffffffff");
    Bytes value1 =
        Bytes.fromHexString("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe");
    Bytes value2 =
        Bytes.fromHexString("0x000000000000000000000000ffffffffffffffffffffffffffffffffffffffff");
    BigInteger aInt = new BigInteger(1, value0.toArrayUnsafe());
    BigInteger bInt = new BigInteger(1, value1.toArrayUnsafe());
    BigInteger cInt = new BigInteger(1, value2.toArrayUnsafe());
    UInt256 a = UInt256.fromBytesBE(value0.toArrayUnsafe());
    UInt256 b = UInt256.fromBytesBE(value1.toArrayUnsafe());
    UInt256 c = UInt256.fromBytesBE(value2.toArrayUnsafe());
    Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(a.mulMod(b, c).toBytesBE()));
    Bytes32 expected = bigIntTo32B(aInt.multiply(bInt).mod(cInt));
    assertThat(remainder).isEqualTo(expected);

    value0 =
        Bytes.fromHexString("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe");
    value1 =
        Bytes.fromHexString("0xffffffffffffffffffffffffb195148ca348dc57a7331852b390ccefa7b0c18b");
    value2 =
        Bytes.fromHexString("0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe");
    aInt = new BigInteger(1, value0.toArrayUnsafe());
    bInt = new BigInteger(1, value1.toArrayUnsafe());
    cInt = new BigInteger(1, value2.toArrayUnsafe());
    a = UInt256.fromBytesBE(value0.toArrayUnsafe());
    b = UInt256.fromBytesBE(value1.toArrayUnsafe());
    c = UInt256.fromBytesBE(value2.toArrayUnsafe());
    remainder = Bytes32.leftPad(Bytes.wrap(a.mulMod(b, c).toBytesBE()));
    expected = bigIntTo32B(aInt.multiply(bInt).mod(cInt));
    assertThat(remainder).isEqualTo(expected);
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
  public void signedMod() {
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
      BigInteger rem = BigInteger.ZERO;
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
      assertThat(remainder).isEqualTo(expected);
    }
  }
}

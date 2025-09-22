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
import java.util.concurrent.ThreadLocalRandom;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

public class UInt256Test {

  @Test
  public void fromInts() {
    UInt256 result;

    result = UInt256.fromInt(0);
    assertThat(result.length()).as("Int 0 length").isEqualTo(0);
    assertThat(result.isZero()).as("Int 0, isZero").isTrue();

    int[] testInts = new int[] {130, -128, 32500};
    for (int i : testInts) {
      result = UInt256.fromInt(i);
      assertThat(result.length()).as(String.format("Int %s length", i)).isEqualTo(1);
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
    expectedLimbs = new int[] {-2147483648, 0, 0, 0, 0, 0, 0, 0, 0};
    assertThat(result.length()).as("4b-neg-length").isEqualTo(1);
    assertThat(result.limbs()).as("4b-neg-limbs").isEqualTo(expectedLimbs);

    input = new byte[] {0, 0, 1, 1, 1};
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {1 + 256 + 65536, 0, 0, 0, 0, 0, 0, 0, 0};
    assertThat(result.length()).as("3b-length").isEqualTo(1);
    assertThat(result.limbs()).as("3b-limbs").isEqualTo(expectedLimbs);

    input = new byte[] {1, 0, 0, 0, 0, 1, 1, 1};
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {1 + 256 + 65536, 16777216, 0, 0, 0, 0, 0, 0, 0};
    assertThat(result.length()).as("8b-length").isEqualTo(2);
    assertThat(result.limbs()).as("8b-limbs").isEqualTo(expectedLimbs);

    input =
        new byte[] {
          1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {0, 0, 0, 0, 0, 0, 0, 16777216, 0};
    assertThat(result.length()).as("32b-length").isEqualTo(8);
    assertThat(result.limbs()).as("32b-limbs").isEqualTo(expectedLimbs);

    input =
        new byte[] {
          0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
    result = UInt256.fromBytesBE(input);
    expectedLimbs = new int[] {0, 0, 0, 0, 0, 0, 257, 0, 0};
    assertThat(result.length()).as("32b-padded-length").isEqualTo(7);
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
    final ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int i = 0; i < 30000; i++) {
      final byte[] a = new byte[32];
      final byte[] b = new byte[32];
      random.nextBytes(a);
      random.nextBytes(b);
      BigInteger aInt = new BigInteger(1, a);
      BigInteger bInt = new BigInteger(1, b);
      int comp = aInt.compareTo(bInt);
      BigInteger big_number = (comp >= 0) ? aInt : bInt;
      BigInteger big_modulus = (comp >= 0) ? bInt : aInt;
      UInt256 number = UInt256.fromBytesBE(big_number.toByteArray());
      UInt256 modulus = UInt256.fromBytesBE(big_modulus.toByteArray());
      Bytes32 remainder = Bytes32.leftPad(Bytes.wrap(number.mod(modulus).toBytesBE()));
      Bytes32 expected = Bytes32.leftPad(Bytes.wrap(big_number.mod(big_modulus).toByteArray()));
      assertThat(remainder).isEqualTo(expected);
    }
  }
}

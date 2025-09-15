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
package org.hyperledger.besu.datatypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class UInt256ArithTest {
  @Test
  @Disabled("Infinite fuzz test - enable locally for testing if needed")
  void randomUnsignedDivisionAgainstBigInteger() {
    Random random = new Random();
    while (true) {
      final byte[] numArray = new byte[random.nextInt(0, UInt256.SIZE)];
      final byte[] denomArray = new byte[random.nextInt(0, UInt256.SIZE)];
      random.nextBytes(numArray);
      random.nextBytes(denomArray);
      final Bytes numerator = Bytes.wrap(numArray);
      final Bytes denominator = Bytes.wrap(denomArray);

      final BigInteger numBigInt = new BigInteger(1, numArray);
      final BigInteger denomBigInt = new BigInteger(1, denomArray);
      if (denomBigInt.equals(BigInteger.ZERO)) {
        continue;
      }

      final byte[] bytesResult = UInt256Arith.divide(false, numerator, denominator).toArrayUnsafe();
      final BigInteger result = new BigInteger(1, bytesResult);

      assertNotNull(bytesResult);
      assertEquals(
          UInt256.SIZE, bytesResult.length, "division result got " + bytesResult.length + " bytes");
      assertEquals(
          numBigInt.divide(denomBigInt),
          result,
          () ->
              "Division mismatch for num="
                  + numerator.toHexString()
                  + " denom="
                  + denominator.toHexString());
    }
  }

  @Test
  @Disabled("Infinite fuzz test - enable locally for testing if needed")
  void randomSignedDivisionAgainstBigInteger() {
    Random random = new Random();
    while (true) {
      final byte[] numArray = new byte[random.nextInt(0, UInt256.SIZE)];
      final byte[] denomArray = new byte[random.nextInt(0, UInt256.SIZE)];
      random.nextBytes(numArray);
      random.nextBytes(denomArray);
      final Bytes numerator = Bytes.wrap(numArray);
      final Bytes denominator = Bytes.wrap(denomArray);

      final BigInteger numBigInt =
          numerator.size() < UInt256.SIZE ? new BigInteger(1, numArray) : new BigInteger(numArray);
      final BigInteger denomBigInt =
          denominator.size() < UInt256.SIZE
              ? new BigInteger(1, denomArray)
              : new BigInteger(denomArray);
      if (denomBigInt.equals(BigInteger.ZERO)) {
        continue;
      }

      final byte[] bytesResult = UInt256Arith.divide(true, numerator, denominator).toArrayUnsafe();
      final BigInteger result = new BigInteger(bytesResult);

      assertNotNull(bytesResult);
      assertEquals(
          UInt256.SIZE, bytesResult.length, "division result got " + bytesResult.length + " bytes");
      assertEquals(
          numBigInt.divide(denomBigInt),
          result,
          () ->
              "Division mismatch for num="
                  + numerator.toHexString()
                  + " denom="
                  + denominator.toHexString());
    }
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void divideSingle(final String numerator, final String denominator) {
    final Bytes numeratorBytes = Bytes.fromHexString(numerator);
    final Bytes denominatorBytes = Bytes.fromHexString(denominator);

    final BigInteger numBigInt =
        numeratorBytes.size() < UInt256.SIZE
            ? new BigInteger(1, numeratorBytes.toArrayUnsafe())
            : new BigInteger(numeratorBytes.toArrayUnsafe());
    final BigInteger denomBigInt =
        denominatorBytes.size() < UInt256.SIZE
            ? new BigInteger(1, denominatorBytes.toArrayUnsafe())
            : new BigInteger(denominatorBytes.toArrayUnsafe());

    final Bytes resultBytes = UInt256Arith.divide(true, numeratorBytes, denominatorBytes);
    final BigInteger result = new BigInteger(resultBytes.toArrayUnsafe());

    final BigInteger expectedResult = numBigInt.divide(denomBigInt);

    assertEquals(
        expectedResult,
        result,
        () ->
            "Division mismatch for num="
                + numeratorBytes.toHexString()
                + " denom="
                + denominatorBytes.toHexString());
    assertEquals(
        Bytes.fromHexString(numerator), numeratorBytes, "Original value has been modified");
    assertEquals(
        Bytes.fromHexString(denominator), denominatorBytes, "Original value has been modified");
  }

  static Collection<Object[]> testCases() {
    return Arrays.asList(
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
          }
        });
  }
}

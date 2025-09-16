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

      final BigInteger numBigInt = bytesToBigInt(numerator, Sign.UNSIGNED);
      final BigInteger denomBigInt = bytesToBigInt(denominator, Sign.UNSIGNED);

      if (denomBigInt.equals(BigInteger.ZERO)) {
        continue;
      }

      final Bytes bytesResult = UInt256Arith.divide(false, numerator, denominator);
      final BigInteger result = bytesToBigInt(bytesResult, Sign.UNSIGNED);

      assertNotNull(bytesResult);
      assertEquals(
          UInt256.SIZE, bytesResult.size(), "division result got " + bytesResult.size() + " bytes");
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

      final BigInteger numBigInt = bytesToBigInt(numerator, Sign.SIGNED);
      final BigInteger denomBigInt = bytesToBigInt(denominator, Sign.SIGNED);

      if (denomBigInt.equals(BigInteger.ZERO)) {
        continue;
      }

      final Bytes bytesResult = UInt256Arith.divide(true, numerator, denominator);
      final BigInteger result = bytesToBigInt(bytesResult, Sign.SIGNED);

      assertNotNull(bytesResult);
      assertEquals(
          UInt256.SIZE, bytesResult.size(), "division result got " + bytesResult.size() + " bytes");
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

  enum Sign {
    SIGNED,
    UNSIGNED
  };

  @ParameterizedTest
  @MethodSource("testCases")
  void divide(final String numerator, final String denominator, final Sign sign) {
    final Bytes numeratorBytes = Bytes.fromHexString(numerator);
    final Bytes denominatorBytes = Bytes.fromHexString(denominator);

    final BigInteger numBigInt = bytesToBigInt(numeratorBytes, sign);
    final BigInteger denomBigInt = bytesToBigInt(denominatorBytes, sign);

    final Bytes resultBytes =
        UInt256Arith.divide(sign == Sign.SIGNED, numeratorBytes, denominatorBytes);
    final BigInteger result = bytesToBigInt(resultBytes, sign);

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

  private static BigInteger bytesToBigInt(final Bytes bytes, final Sign sign) {
    // bytes can be shorter, so it's treated as left padded with zeros
    if (bytes.size() < UInt256.SIZE) {
      return new BigInteger(1, bytes.toArrayUnsafe());
    }
    return sign == Sign.SIGNED
        ? new BigInteger(bytes.toArrayUnsafe())
        : new BigInteger(1, bytes.toArrayUnsafe());
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
}

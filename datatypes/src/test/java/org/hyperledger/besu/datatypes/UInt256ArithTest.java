package org.hyperledger.besu.datatypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class UInt256ArithTest {
  @Test
  void randomUnsignedDivisionAgainstBigInteger() {
    Random random = new Random();
    final byte[] numArray = new byte[random.nextInt(0, 32)];
    final byte[] denomArray = new byte[random.nextInt(0, 32)];
    while (true) {
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
      assertEquals(bytesResult.length, 32, "division result got " + bytesResult.length + " bytes");
      assertEquals(
        numBigInt.divide(denomBigInt),
        result,
        () -> "Division mismatch for num=" + numerator.toHexString() + " denom=" + denominator.toHexString());
    }
  }

  @Test
  void randomSignedDivisionAgainstBigInteger() {
    Random random = new Random();
    final byte[] numArray = new byte[random.nextInt(0, 32)];
    final byte[] denomArray = new byte[random.nextInt(0, 32)];
    while (true) {
      random.nextBytes(numArray);
      random.nextBytes(denomArray);
      final Bytes numerator = Bytes.wrap(numArray);
      final Bytes denominator = Bytes.wrap(denomArray);

      final BigInteger numBigInt = new BigInteger(numArray);
      final BigInteger denomBigInt = new BigInteger(denomArray);
      if (denomBigInt.equals(BigInteger.ZERO)) {
        continue;
      }

      final byte[] bytesResult = UInt256Arith.divide(true, numerator, denominator).toArrayUnsafe();
      final BigInteger result = new BigInteger(bytesResult);

      assertNotNull(bytesResult);
      assertEquals(bytesResult.length, 32, "division result got " + bytesResult.length + " bytes");
      assertEquals(
        numBigInt.divide(denomBigInt),
        result,
        () -> "Division mismatch for num=" + numerator.toHexString() + " denom=" + denominator.toHexString());
    }
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void divideSingle(final String numerator, final String denominator) {
    final Bytes numeratorBytes = Bytes.fromHexString(numerator);
    final Bytes denominatorBytes = Bytes.fromHexString(denominator);

    final BigInteger numBigInt = new BigInteger(numeratorBytes.toArrayUnsafe());
    final BigInteger denomBigInt = new BigInteger(denominatorBytes.toArrayUnsafe());

    final BigInteger result = new BigInteger(
      UInt256Arith.divide(true, numeratorBytes, denominatorBytes).toArrayUnsafe());

    assertEquals(
      numBigInt.divide(denomBigInt),
      result,
      () -> "Division mismatch for num=" + numeratorBytes.toHexString() + " denom=" + denominatorBytes.toHexString());
  }

  static Collection<Object[]> testCases() {
    return Arrays.asList(
      new Object[][] {
        {"0x00", "0x01"},
        {"0x50","0x21"},
        {"0x120d7a733f5016ad9fae51cb9896e15a96147719fe0379d0cb2642a6951e0a5c", "0x007cdab49aba612fb02bd738a74c76789bc9a911c90296502a35df43e939e6e2"},
        {"0xa7f576de3a6c", "0xfffffffffef1c296a4c6"},
        {"0xffffffffffffffffffffffff6bacfb1469f9a4d5674a85b75f951d72d7a58e4a", "0x020000"},
        {"0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "0x01"},
        {"0x01", "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"},
        {"0x1598209296af93c13b2f5fde7d8e99", "0x09244c1368"},
      });
  }

}

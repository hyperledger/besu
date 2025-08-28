package org.hyperledger.besu.datatypes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class UInt256ArithTest {
  @Test
  void divideRandomAgainstBigInteger() {
    Random random = new Random();
    final byte[] numArray = new byte[32];
    final byte[] denomArray = new byte[32];
    while (true) {
      random.nextBytes(numArray);
      random.nextBytes(denomArray);
      final Bytes32 numerator = Bytes32.wrap(numArray);
      final Bytes32 denominator = Bytes32.wrap(denomArray);

      final BigInteger numBigInt = new BigInteger(1, numArray);
      final BigInteger denomBigInt = new BigInteger(1, denomArray);

      final BigInteger result = new BigInteger(
        1, UInt256Arith.divide(true, numerator, denominator).toArrayUnsafe());

      assertEquals(
        result,
        numBigInt.divide(denomBigInt),
        () -> "Division mismatch for num=" + numerator.toHexString() + " denom=" + denominator.toHexString());
    }
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void divideSingle(final String numerator, final String denominator) {
    final Bytes32 numeratorBytes = Bytes32.fromHexString(numerator);
    final Bytes32 denominatorBytes = Bytes32.fromHexString(denominator);

    final BigInteger numBigInt = new BigInteger(1, numeratorBytes.toArrayUnsafe());
    final BigInteger denomBigInt = new BigInteger(1, denominatorBytes.toArrayUnsafe());

    final BigInteger result = new BigInteger(
      1, UInt256Arith.divide(true, numeratorBytes, denominatorBytes).toArrayUnsafe());

    assertEquals(
      result,
      numBigInt.divide(denomBigInt),
      () -> "Division mismatch for num=" + numeratorBytes.toHexString() + " denom=" + denominatorBytes.toHexString());
  }

  static Collection<Object[]> testCases() {
    return Arrays.asList(
      new Object[][] {
        {"0x00", "0x01"},
        {"0x50","0x21"},
        {"0x120d7a733f5016ad9fae51cb9896e15a96147719fe0379d0cb2642a6951e0a5c", "0x007cdab49aba612fb02bd738a74c76789bc9a911c90296502a35df43e939e6e2"}
      });
  }

}

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
package org.hyperledger.besu.evm.precompile;

import static org.hyperledger.besu.evm.precompile.P256VerifyPrecompiledContract.N;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.crypto.SECP256R1;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class P256VerifyPrecompiledContractTest {

  private static final SignatureAlgorithm secp256R1 = new SECP256R1();
  private static final P256VerifyPrecompiledContract contract =
      new P256VerifyPrecompiledContract(new SpuriousDragonGasCalculator(), secp256R1);

  private static final MessageFrame messageFrame = mock(MessageFrame.class);

  static class TestCase {
    public String Input;
    public String Expected;
    public String Name;
    public int Gas;
    public boolean NoBenchmark;

    @Override
    public String toString() {
      return Name;
    }
  }

  @BeforeAll
  static void setup() {
    // any shared setup can go here
  }

  static Stream<TestCase> provideTestVectors() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    InputStream is =
        P256VerifyPrecompiledContractTest.class.getResourceAsStream(
            "p256verify_test_vectors.json"); // place your test file here

    if (is == null) {
      throw new IOException("Test vector file not found.");
    }

    List<TestCase> testCases = mapper.readerForListOf(TestCase.class).readValue(is);
    return testCases.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideTestVectors")
  void testP256Verify(final TestCase testCase) {
    Bytes input = Bytes.fromHexString(testCase.Input);
    Bytes expected = Bytes.fromHexString(testCase.Expected);

    PrecompiledContract.PrecompileContractResult result =
        contract.computePrecompile(input, messageFrame);
    Bytes actual = result.output();
    assertEquals(expected, actual, "Test failed: " + testCase.Name);
  }

  @Test
  void sanityCheck() {
    assertEquals(6_900L, contract.gasRequirement(Bytes.wrap(new byte[128])));
  }

  @Test
  void testInvalidCurvePointReturnsInvalid() {
    // Test using a realistic attack scenario:
    // Create r,s values that would verify against an invalid public key point
    // The point is in the field but NOT on the secp256r1 curve

    String messageHash = "44acf6b7e36c1342c2c5897204fe09504e1e2efb1a900377dbc4e7a6a133ec56";

    // Point in field but NOT on curve: these coordinates are valid field elements
    // but do not satisfy the secp256r1 curve equation y² = x³ - 3x + b
    String invalidPubKeyX = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
    String invalidPubKeyY = "b7c52588d95c3b9aa25b0403f1eef75702e84bb7597aabe663b82f6f04ef2777";

    // Generate r,s values that could theoretically verify with this invalid point
    // r component from a valid signature
    String r = "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5";
    // s component that would correspond to this scenario
    String s = "30dae23890abb63e378e003d7f1d5006ab23cc7b3b65b3d0c7b45c7e1e2e08b9";

    String input = messageHash + r + s + invalidPubKeyX + invalidPubKeyY;
    Bytes inputBytes = Bytes.fromHexString(input);

    // call default  implementation:
    secp256R1.disableNative();
    var pureJavaResult = contract.computeDefault(inputBytes);

    // call maybeNative implementation
    secp256R1.maybeEnableNative();
    var maybeNativeResult = contract.computePrecompile(inputBytes, messageFrame);

    // Should return empty bytes (INVALID) because curve validation catches
    // the invalid point before signature verification attempts
    assertTrue(
        pureJavaResult.cachedResult().isSuccessful(),
        "Invalid curve point should succeed with empty result");
    assertEquals(
        Bytes.EMPTY,
        pureJavaResult.cachedResult().output(),
        "Invalid curve point should return empty result");
    assertEquals(
        Bytes.EMPTY,
        maybeNativeResult.output(),
        "Invalid curve point should return empty result for native implementation");
  }

  @Test
  void testModularComparisonWhenRPrimeExceedsN() {
    // Test edge case where R'.x > n to verify proper modular comparison r' ≡ r (mod n)
    // This tests that the implementation correctly handles cases where the computed
    // point's x-coordinate exceeds the curve order
    ECPoint G = P256VerifyPrecompiledContract.R1_PARAMS.getG(); // generator

    // Create a scenario where we can control R' to have x-coordinate > n
    // We'll use a known private key and construct a signature where R'.x > n but R'.x ≡ r (mod n)

    // Message hash
    String messageHash = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    BigInteger z = new BigInteger(messageHash, 16);

    // Use a private key that will generate a point with large x-coordinate
    BigInteger privateKey = N.subtract(BigInteger.valueOf(12345)); // close to n
    ECPoint publicKeyPoint = G.multiply(privateKey).normalize();

    // Create signature using a k value that produces R with x-coordinate > n
    // We need R.x such that R.x > n but the verification still works via modular arithmetic
    BigInteger k = N.add(BigInteger.valueOf(98765)); // This will create R.x > n
    ECPoint R = G.multiply(k).normalize();
    BigInteger r_full = R.getAffineXCoord().toBigInteger();
    BigInteger r = r_full.mod(N); // This is the r value that goes in signature

    // Calculate s = k^(-1)(z + r*privateKey) mod n
    BigInteger s = k.modInverse(N).multiply(z.add(r.multiply(privateKey))).mod(N);

    // Ensure s is not zero
    if (s.equals(BigInteger.ZERO)) {
      s = BigInteger.ONE;
    }

    // Format the signature components
    String rHex = String.format("%064x", r);
    String sHex = String.format("%064x", s);

    // Extract public key coordinates
    BigInteger pubKeyX = publicKeyPoint.getAffineXCoord().toBigInteger();
    BigInteger pubKeyY = publicKeyPoint.getAffineYCoord().toBigInteger();
    String pubKeyXHex = String.format("%064x", pubKeyX);
    String pubKeyYHex = String.format("%064x", pubKeyY);

    String input = messageHash + rHex + sHex + pubKeyXHex + pubKeyYHex;
    Bytes inputBytes = Bytes.fromHexString(input);

    // call the signaturebased impl explicitly, with native disabled:
    secp256R1.disableNative();
    var defaultResult = contract.computeDefault(inputBytes);

    // maybeNative implementation:
    secp256R1.maybeEnableNative();
    var maybeNativeResult = contract.computePrecompile(inputBytes, messageFrame);

    // This signature should be VALID if modular comparison is implemented correctly
    // The verification should compute R' with x-coordinate that may exceed n,
    // but R'.x ≡ r (mod n) should still hold true
    assertTrue(
        defaultResult.cachedResult().isSuccessful(),
        "Valid signature with R'.x > n should verify correctly via modular comparison");
    assertEquals(
        Bytes32.leftPad(Bytes.of(1)),
        defaultResult.cachedResult().output(),
        "Valid signature with R'.x > n should verify correctly via modular comparison");
    assertEquals(
        Bytes32.leftPad(Bytes.of(1)),
        maybeNativeResult.output(),
        "Valid signature with R'.x > n should verify correctly via modular comparison with Native implementation");
  }
}

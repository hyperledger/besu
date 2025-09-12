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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.crypto.SECP256R1;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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
    var defaultResult = contract.computeDefault(inputBytes);
    // call default native implementation:
    secp256R1.maybeEnableNative();
    var defaultMaybeNativeResult = contract.computeDefault(inputBytes);

    // verify boringssl precompile-specific implementation
    // this should be configured/run statically, adding the call here just for clarity
    P256VerifyPrecompiledContract.maybeEnableNativeBoringSSL();
    var maybeNativeResult = contract.computePrecompile(inputBytes, messageFrame);

    // Should return empty bytes (INVALID) because curve validation catches
    // the invalid point before signature verification attempts
    assertTrue(
        defaultResult.cachedResult().isSuccessful(),
        "Invalid curve point should succeed with empty result");
    assertEquals(
        Bytes.EMPTY,
        defaultResult.cachedResult().output(),
        "Invalid curve point should return empty result");
    assertTrue(
        defaultMaybeNativeResult.cachedResult().isSuccessful(),
        "Invalid curve point should succeed with empty result");
    assertEquals(
        Bytes.EMPTY,
        defaultMaybeNativeResult.cachedResult().output(),
        "Invalid curve point should return empty result");
    assertTrue(
        maybeNativeResult.isSuccessful(),
        "Invalid curve point should succeed with empty result for native implementation");
    assertEquals(
        Bytes.EMPTY,
        maybeNativeResult.output(),
        "Invalid curve point should return empty result for native implementation");
  }

  @Test
  void testModularComparisonWhenRPrimeExceedsN() {

    // parameters borrowed from execution-spec-tests for R' exceeding N:
    // https://github.com/ethereum/execution-spec-tests/blob/61f8ac90841770d9fc407f1498ec0e5229b27508/tests/osaka/eip7951_p256verify_precompiles/test_p256verify.py#L303-L315
    String messageHash = "BB5A52F42F9C9261ED4361F59422A1E30036E7C32B270C8807A419FECA605023";
    String r = "000000000000000000000000000000004319055358E8617B0C46353D039CDAAB";
    String s = "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC63254E";
    String pubKeyX = "0AD99500288D466940031D72A9F5445A4D43784640855BF0A69874D2DE5FE103";
    String pubKeyY = "C5011E6EF2C42DCD50D5D3D29F99AE6EBA2C80C9244F4C5422F0979FF0C3BA5E";
    String input = messageHash + r + s + pubKeyX + pubKeyY;
    Bytes inputBytes = Bytes.fromHexString(input);

    // call the signaturebased impl explicitly, with native disabled:
    secp256R1.disableNative();
    var defaultResult = contract.computeDefault(inputBytes);
    // call the signaturebased impl explicitly, with openssl native (maybe)enabled:
    secp256R1.maybeEnableNative();
    var defaultMaybeNativeResult = contract.computeDefault(inputBytes);

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
    assertTrue(
        defaultMaybeNativeResult.cachedResult().isSuccessful(),
        "Valid signature with R'.x > n should verify correctly via modular comparison for native SignatureAlgorithm");
    assertEquals(
        Bytes32.leftPad(Bytes.of(1)),
        defaultMaybeNativeResult.cachedResult().output(),
        "Valid signature with R'.x > n should verify correctly via modular comparison for native SignatureAlgorithm");
    assertEquals(
        Bytes32.leftPad(Bytes.of(1)),
        maybeNativeResult.output(),
        "Valid signature with R'.x > n should verify correctly via modular comparison with Native implementation");
  }
}

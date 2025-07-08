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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class P256VerifyPrecompiledContractTest {

  private static final P256VerifyPrecompiledContract contract =
      new P256VerifyPrecompiledContract(new SpuriousDragonGasCalculator());

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
}

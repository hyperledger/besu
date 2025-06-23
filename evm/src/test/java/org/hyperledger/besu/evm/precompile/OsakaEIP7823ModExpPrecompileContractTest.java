/*
 * Copyright contributors to Hyperledger Besu.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract.disableNative;
import static org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract.maybeEnableNative;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.fluent.EvmSpec;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

class OsakaEIP7823ModExpPrecompiledContractTest {

  private static final Random RANDOM = new Random();
  @Mock private MessageFrame messageFrame;

  private final PrecompiledContract pragueContract =
      EvmSpec.evmSpec(EvmSpecVersion.PRAGUE).getPrecompileContractRegistry().get(Address.MODEXP);
  private final PrecompiledContract osakaContract =
      EvmSpec.evmSpec(EvmSpecVersion.OSAKA).getPrecompileContractRegistry().get(Address.MODEXP);

  static Stream<Arguments> validParameters() {
    return Stream.of(
        // all parameters within limit
        Arguments.of(
            // length_of_BASE = 1 byte
            "0000000000000000000000000000000000000000000000000000000000000001"
                + // length_of_EXPONENT = 32 bytes
                "0000000000000000000000000000000000000000000000000000000000000020"
                + // length_of_MODULUS = 32 bytes
                "0000000000000000000000000000000000000000000000000000000000000020"
                + // data
                "03"
                + "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2e"
                + "fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f"),
        // at the boundary (1024 bytes)
        Arguments.of(
            // length_of_BASE = 1024 bytes
            "0000000000000000000000000000000000000000000000000000000000000400"
                + // length_of_EXPONENT = 1024 bytes
                "0000000000000000000000000000000000000000000000000000000000000400"
                + // length_of_MODULUS = 1024 bytes
                "0000000000000000000000000000000000000000000000000000000000000400"
                + // dummy data
                "01"));
  }

  static Stream<Arguments> invalidParameters() {
    return Stream.of(
        // length_of_BASE exceeds limit (1025 bytes)
        Arguments.of(
            // length_of_BASE = 1025 bytes
            "0000000000000000000000000000000000000000000000000000000000000401"
                + // length_of_EXPONENT = 1 byte
                "0000000000000000000000000000000000000000000000000000000000000001"
                + // length_of_MODULUS = 1 byte
                "0000000000000000000000000000000000000000000000000000000000000001"
                + // dummy data
                "01"),
        // length_of_EXPONENT exceeds limit (1025 bytes)
        Arguments.of(
            // length_of_BASE = 1 byte
            "0000000000000000000000000000000000000000000000000000000000000001"
                + // length_of_EXPONENT = 1025 bytes
                "0000000000000000000000000000000000000000000000000000000000000401"
                + // length_of_MODULUS = 1 byte
                "0000000000000000000000000000000000000000000000000000000000000001"
                + // dummy data
                "01"),
        // length_of_MODULUS exceeds limit (1025 bytes)
        Arguments.of(
            // length_of_BASE = 1 byte
            "0000000000000000000000000000000000000000000000000000000000000001"
                + // length_of_EXPONENT = 1 byte
                "0000000000000000000000000000000000000000000000000000000000000001"
                + // length_of_MODULUS = 1025 bytes
                "0000000000000000000000000000000000000000000000000000000000000401"
                + // dummy data
                "01"),
        // all parameters exceed limit
        Arguments.of(
            // length_of_BASE = 2048 bytes
            "0000000000000000000000000000000000000000000000000000000000000800"
                + // length_of_EXPONENT = 2048 bytes
                "0000000000000000000000000000000000000000000000000000000000000800"
                + // length_of_MODULUS = 2048 bytes
                "0000000000000000000000000000000000000000000000000000000000000800"
                + // dummy data
                "01"));
  }

  @ParameterizedTest
  @MethodSource("validParameters")
  void shouldSucceedForValidInputs(final String inputHex) {
    randomlyEnableNative();
    final Bytes input = Bytes.fromHexString(inputHex);

    final PrecompiledContract.PrecompileContractResult osakaResult =
        osakaContract.computePrecompile(input, messageFrame);
    assertThat(osakaResult.isSuccessful()).isTrue();

    final PrecompiledContract.PrecompileContractResult pragueResult =
        // Pre-osaka implementation should also work for these valid inputs
        pragueContract.computePrecompile(input, messageFrame);
    assertThat(pragueResult.isSuccessful()).isTrue();

    // Both implementations should return the same result
    assertThat(osakaResult.output()).isEqualTo(pragueResult.output());
  }

  @ParameterizedTest
  @MethodSource("invalidParameters")
  void shouldFailForOversizedInputs(final String inputHex) {
    randomlyEnableNative();
    final Bytes input = Bytes.fromHexString(inputHex);

    final PrecompiledContract.PrecompileContractResult osakaResult =
        osakaContract.computePrecompile(input, messageFrame);
    assertThat(osakaResult.isSuccessful()).isFalse();
    assertThat(osakaResult.getHaltReason())
        .isEqualTo(Optional.of(ExceptionalHaltReason.PRECOMPILE_ERROR));

    // Pre-osaka implementation should still be successful for these inputs
    final PrecompiledContract.PrecompileContractResult pragueResult =
        pragueContract.computePrecompile(input, messageFrame);
    assertThat(pragueResult.isSuccessful()).isTrue();
  }

  private static void randomlyEnableNative() {
    if (RANDOM.nextBoolean()) {
      maybeEnableNative();
    } else {
      disableNative();
    }
  }
}

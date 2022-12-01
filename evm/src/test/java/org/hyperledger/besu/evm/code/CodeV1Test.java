/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */

package org.hyperledger.besu.evm.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CodeV1Test {

  public static final String ZERO_HEX = String.format("%02x", 0);

  @Test
  void calculatesJumpDestMap() {
    String codeHex = "0xEF000101000F006001600055600D5660026000555B00";
    final EOFLayout layout = EOFLayout.parseEOF(Bytes.fromHexString(codeHex));

    long[] jumpDest = OpcodesV1.validateAndCalculateJumpDests(layout.getSections()[1]);

    assertThat(jumpDest).containsExactly(0x2000);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"3000", "5000", "fe00", "0000"})
  void testValidOpcodes(final String code) {
    final long[] jumpDest = OpcodesV1.validateAndCalculateJumpDests(Bytes.fromHexString(code));
    assertThat(jumpDest).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"00", "f3", "fd", "fe"})
  void testValidCodeTerminator(final String code) {
    final long[] jumpDest = OpcodesV1.validateAndCalculateJumpDests(Bytes.fromHexString(code));
    assertThat(jumpDest).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("testPushValidImmediateArguments")
  void testPushValidImmediate(final String code) {
    final long[] jumpDest = OpcodesV1.validateAndCalculateJumpDests(Bytes.fromHexString(code));
    assertThat(jumpDest).isNotNull();
  }

  private static Stream<Arguments> testPushValidImmediateArguments() {
    final int codeBegin = 96;
    return IntStream.range(0, 32)
        .mapToObj(i -> String.format("%02x", codeBegin + i) + ZERO_HEX.repeat(i + 1) + ZERO_HEX)
        .map(Arguments::arguments);
  }
}

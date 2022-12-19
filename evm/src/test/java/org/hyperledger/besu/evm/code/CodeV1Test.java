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
  void validCode() {
    String codeHex =
        "0xEF0001 010010 020003 000A 0002 0008 030000 00 00000000 02010001 01000002 60016002b00001b20002 01b1 60005360106000f3";
    final EOFLayout layout = EOFLayout.parseEOF(Bytes.fromHexString(codeHex.replace(" ", "")));

    String validationError = OpcodesV1.validateCode(layout);

    assertThat(validationError).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"3000", "5000", "5c000000", "60005d000000", "60005e01000000", "fe00", "0000"})
  void testValidOpcodes(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"00", "f3", "fd", "fe"})
  void testValidCodeTerminator(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isNull();
  }

  @ParameterizedTest
  @MethodSource("testPushValidImmediateArguments")
  void testPushValidImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isNull();
  }

  private static Stream<Arguments> testPushValidImmediateArguments() {
    final int codeBegin = 96;
    return IntStream.range(0, 32)
        .mapToObj(i -> String.format("%02x", codeBegin + i) + ZERO_HEX.repeat(i + 1) + ZERO_HEX)
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("testRjumpValidImmediateArguments")
  void testRjumpValidImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isNull();
  }

  private static Stream<Arguments> testRjumpValidImmediateArguments() {
    return Stream.of(
            "5c000000",
            "5c00010000",
            "5c00010000000000",
            "5c0100" + ZERO_HEX.repeat(256) + ZERO_HEX,
            "5c7fff" + ZERO_HEX.repeat(32767) + ZERO_HEX,
            "5cfffd0000",
            "005cfffc00",
            ZERO_HEX.repeat(253) + "5cff0000",
            ZERO_HEX.repeat(32765) + "5c800000")
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("testRjumpiValidImmediateArguments")
  void testRjumpiValidImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isNull();
  }

  private static Stream<Arguments> testRjumpiValidImmediateArguments() {
    return Stream.of(
            "60015d000000",
            "60015d00010000",
            "60015d00010000000000",
            "60015d0100" + "5b".repeat(256) + ZERO_HEX,
            "60015d7fff" + "5b".repeat(32767) + ZERO_HEX,
            "60015dfffd0000",
            "60015dfffb00",
            ZERO_HEX.repeat(252) + "60015dff0000",
            ZERO_HEX.repeat(32763) + "60015d800000",
            "5d000000")
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("rjumptableValidImmediateArguments")
  void testRjumptableValidImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isNull();
  }

  private static Stream<Arguments> rjumptableValidImmediateArguments() {
    return Stream.of(
            "60015e01000000",
            "60015e02000000010000",
            "60015e03000000040100" + "5b".repeat(256) + ZERO_HEX,
            "60015e040000000401007fff" + "5b".repeat(32767) + ZERO_HEX,
            "60015e01fffc0000",
            "5b".repeat(248) + "60015e02fffaff0000",
            "5b".repeat(32760) + "60015e02fffa800000",
            "5e01000000")
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("invalidCodeArguments")
  void testInvalidCode(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).startsWith("Invalid Instruction 0x");
  }

  private static Stream<Arguments> invalidCodeArguments() {
    return Stream.of(
            IntStream.rangeClosed(0x0c, 0x0f),
            IntStream.of(0x1e, 0x1f),
            IntStream.rangeClosed(0x21, 0x2f),
            IntStream.rangeClosed(0x49, 0x4f),
            // IntStream.of(0x5f), // PUSH0
            IntStream.rangeClosed(0xa5, 0xaf),
            IntStream.rangeClosed(0xb3, 0xbf),
            IntStream.rangeClosed(0xc0, 0xcf),
            IntStream.rangeClosed(0xd0, 0xdf),
            IntStream.rangeClosed(0xe0, 0xef),
            IntStream.of(0xf6, 0xf7, 0xf8, 0xf9, 0xfb, 0xfc))
        .flatMapToInt(i -> i)
        .mapToObj(i -> String.format("%02x", i) + ZERO_HEX)
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("pushTruncatedImmediateArguments")
  void testPushTruncatedImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isEqualTo("No terminating instruction");
  }

  private static Stream<Arguments> pushTruncatedImmediateArguments() {
    return Stream.concat(
            Stream.of("60"),
            IntStream.range(0, 31)
                .mapToObj(i -> String.format("%02x", 0x61 + i) + ZERO_HEX.repeat(i + 1)))
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @ValueSource(strings = {"5c", "5c00"})
  void testRjumpTruncatedImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isEqualTo("Truncated relative jump offset");
  }

  @ParameterizedTest
  @ValueSource(strings = {"60015d", "60015d00"})
  void testRjumpiTruncatedImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isEqualTo("Truncated relative jump offset");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "60015e",
        "60015e01",
        "60015e0100",
        "60015e030000",
        "60015e0300000001",
        "60015e030000000100"
      })
  void testRjumpvTruncatedImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isEqualTo("Truncated jump table");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "5c0000",
        "5c000100",
        "5cfffc00",
        "60015d0000",
        "60015d000100",
        "60015dfffa00",
        "60015e01000100",
        "60015e01fff900"
      })
  void testRjumpsOutOfBounds(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isEqualTo("Relative jump destination out of bounds");
  }

  @ParameterizedTest
  @MethodSource("rjumpsIntoImmediateExtraArguments")
  @ValueSource(
      strings = {
        // RJUMP into RJUMP immediate
        "5cffff00",
        "5cfffe00",
        "5c00015c000000",
        "5c00025c000000",
        // RJUMPI into RJUMP immediate
        "60015d00015c000000",
        "60015d00025c000000",
        // RJUMPV into RJUMP immediate
        "60015e0100015c000000",
        "60015e0100025c000000",
        // RJUMP into RJUMPI immediate
        "5c000360015d000000",
        "5c000460015d000000",
        // RJUMPI into RJUMPI immediate
        "60015dffff00",
        "60015dfffe00",
        "60015d000360015d000000",
        "60015d000460015d000000",
        // RJUMPV into RJUMPI immediate
        "60015e01000360015d000000",
        "60015e01000460015d000000",
        // RJUMP into RJUMPV immediate
        "5c00015e01000000",
        "5c00025e01000000",
        "5c00035e01000000",
        // RJUMPI into RJUMPV immediate
        "60015d00015e01000000",
        "60015d00025e01000000",
        "60015d00035e01000000",
        // RJUMPV into RJUMPV immediate
        "60015e01ffff00",
        "60015e01fffe00",
        "60015e01fffd00",
        "60015e0100015e01000000",
        "60015e0100025e01000000",
        "60015e0100035e01000000",
        "60015e0100015e020000fff400",
        "60015e0100025e020000fff400",
        "60015e0100035e020000fff400",
        "60015e0100045e020000fff400",
        "60015e0100055e020000fff400"
      })
  void testRjumpsIntoImmediate(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError)
        .isEqualTo("Relative jump destinations targets invalid immediate data");
  }

  private static Stream<Arguments> rjumpsIntoImmediateExtraArguments() {
    return IntStream.range(1, 33)
        .mapToObj(
            n ->
                IntStream.range(1, n + 1)
                    .mapToObj(
                        offset ->
                            Stream.of(
                                String.format("5c00%02x", offset)
                                    + // RJUMP offset
                                    String.format("%02x", 0x60 + n - 1)
                                    + // PUSHn
                                    ZERO_HEX.repeat(n)
                                    + // push data
                                    ZERO_HEX, // STOP
                                String.format("60015d00%02x", offset)
                                    + // PUSH1 1 RJUMI offset
                                    String.format("%02x", 0x60 + n - 1)
                                    + // PUSHn
                                    ZERO_HEX.repeat(n)
                                    + // push data
                                    ZERO_HEX, // STOP
                                String.format("60015e0100%02x", offset)
                                    + String.format("%02x", 0x60 + n - 1)
                                    + // PUSHn
                                    ZERO_HEX.repeat(n)
                                    + // push data
                                    ZERO_HEX // STOP
                                )))
        .flatMap(i -> i)
        .flatMap(i -> i)
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @ValueSource(strings = {"60015e0000"})
  void testRjumpvEmptyTable(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isEqualTo("Empty jump table");
  }

  @ParameterizedTest
  @ValueSource(strings = {"b0", "b000", "b2", "b200"})
  void testJumpCallFTruncated(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isEqualTo("Truncated CALLF/JUMPF");
  }

  @ParameterizedTest
  @ValueSource(strings = {"b00004", "b003ff", "b0ffff", "b20004", "b203ff", "20ffff"})
  void testJumpCallFWrongSection(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 3);
    assertThat(validationError).startsWith("CALLF/JUMPF to non-existent section -");
  }

  @ParameterizedTest
  @ValueSource(strings = {"b00001", "b00002", "b00000", "b20001", "b20002", "b20000"})
  void testJumpCallFValid(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 3);
    assertThat(validationError).isNull();
  }

  @ParameterizedTest
  @MethodSource("immediateContainsOpcodeArguments")
  void testImmediateContainsOpcode(final String code) {
    final String validationError = OpcodesV1.validateCode(Bytes.fromHexString(code), 1);
    assertThat(validationError).isNull();
  }

  private static Stream<Arguments> immediateContainsOpcodeArguments() {
    return Stream.of(
            // 0x5c byte which could be interpreted a RJUMP, but it's not because it's in PUSH data
            "605c001000",
            "61005c001000",
            // 0x5d byte which could be interpreted a RJUMPI, but it's not because it's in PUSH data
            "605d001000",
            "61005d001000",
            // 0x5e byte which could be interpreted a RJUMPV, but it's not because it's in PUSH data
            "605e01000000",
            "61005e01000000",
            // 0x60 byte which could be interpreted as PUSH, but it's not because it's in RJUMP data
            // offset = -160
            "5b".repeat(160) + "5cff6000",
            // 0x60 byte which could be interpreted as PUSH, but it's not because it's in RJUMPI
            // data
            // offset = -160
            "5b".repeat(160) + "5dff6000",
            // 0x60 byte which could be interpreted as PUSH, but it's not because it's in RJUMPV
            // data
            // offset = -160
            "5b".repeat(160) + "5e01ff6000")
        .map(Arguments::arguments);
  }
}

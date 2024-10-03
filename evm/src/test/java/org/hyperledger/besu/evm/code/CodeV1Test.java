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
package org.hyperledger.besu.evm.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * These tests focus on code-only validations, which are checked within the code runs themselves.
 * Tests that depend on the EOF container (such as CallF into other sections) are in EOFLayoutTest.
 */
class CodeV1Test {

  public static final String ZERO_HEX = "00";
  public static final String NOOP_HEX = "5b";

  private static void assertValidation(final String error, final String code) {
    assertValidation(error, code, false, 1, 5);
  }

  private static void assertValidation(
      final String error,
      final String code,
      final boolean returning,
      final int... codeSectionSizes) {
    Bytes codeBytes = Bytes.fromHexString(code);
    for (int i : codeSectionSizes) {
      CodeSection[] codeSections = new CodeSection[i];
      Arrays.fill(codeSections, new CodeSection(1, 0, returning ? 0 : 0x80, 1, 1));
      EOFLayout testLayout =
          new EOFLayout(
              codeBytes,
              1,
              codeSections,
              new EOFLayout[0],
              0,
              Bytes.EMPTY,
              error,
              new AtomicReference<>());
      assertValidation(error, codeBytes, codeSections[0], testLayout);
    }
  }

  private static void assertValidation(
      final String error,
      final Bytes codeBytes,
      final CodeSection thisCodeSection,
      final EOFLayout eofLayout) {
    CodeV1Validation validator = new CodeV1Validation(0xc000);
    final String validationError = validator.validateCode(codeBytes, thisCodeSection, eofLayout);
    if (error == null) {
      assertThat(validationError).isNull();
    } else {
      assertThat(validationError).startsWith(error);
    }
  }

  @Test
  void validCode() {
    String codeHex =
        "0xEF0001 01000C 020003 000b 0002 0008 040000 00 00800000 02010001 01000002 60016002e30001e30002f3 01e4 60005360106000e4";
    final EOFLayout layout = EOFLayout.parseEOF(Bytes.fromHexString(codeHex.replace(" ", "")));
    CodeV1Validation validator = new CodeV1Validation(0xc000);
    String validationError = validator.validateCode(layout);

    assertThat(validationError).isNull();
  }

  @Test
  void invalidCode() {
    String codeHex =
        "0xEF0001 01000C 020003 000b 0002 0008 040000 00 00000000 02010001 01000002 60016002e30001e30002f3 01e4 60005360106000e4";
    final EOFLayout layout = EOFLayout.parseEOF(Bytes.fromHexString(codeHex.replace(" ", "")));
    CodeV1Validation validator = new CodeV1Validation(0xc000);
    String validationError = validator.validateCode(layout);

    assertThat(validationError)
        .isEqualTo(
            "Invalid EOF container - invalid_first_section_type want 0x80 (non-returning flag) has 0");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"3000", "5000", "e0000000", "6000e1000000", "6000e200000000", "fe00", "0000"})
  void testValidOpcodes(final String code) {
    assertValidation(null, code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"00", "3030f3", "3030fd", "fe"})
  void testValidCodeTerminator(final String code) {
    assertValidation(null, code);
  }

  @ParameterizedTest
  @MethodSource("testPushValidImmediateArguments")
  void testPushValidImmediate(final String code) {
    assertValidation(null, code);
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
    assertValidation(null, code);
  }

  private static Stream<Arguments> testRjumpValidImmediateArguments() {
    return Stream.of(
            "e0000000",
            "e000010000",
            "e000010000000000",
            "e00100" + NOOP_HEX.repeat(256) + ZERO_HEX,
            "e07fff" + NOOP_HEX.repeat(32767) + ZERO_HEX,
            "e0fffd0000",
            "00e0fffc00",
            NOOP_HEX.repeat(253) + "e0ff0000",
            NOOP_HEX.repeat(32765) + "e0800000")
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("testRjumpiValidImmediateArguments")
  void testRjumpiValidImmediate(final String code) {
    assertValidation(null, code);
  }

  private static Stream<Arguments> testRjumpiValidImmediateArguments() {
    return Stream.of(
            "6001e1000000",
            "6001e100010000",
            "6001e100010000000000",
            "6001e10100" + "5b".repeat(256) + ZERO_HEX,
            "6001e17fff" + "5b".repeat(32767) + ZERO_HEX,
            "6001e1fffd0000",
            "6001e1fffb00",
            NOOP_HEX.repeat(252) + "6001e1ff0000",
            NOOP_HEX.repeat(32763) + "6001e1800000",
            "e1000000")
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("rjumptableValidImmediateArguments")
  void testRjumptableValidImmediate(final String code) {
    assertValidation(null, code);
  }

  private static Stream<Arguments> rjumptableValidImmediateArguments() {
    return Stream.of(
            "6001e200000000",
            "6001e201000000010000",
            "6001e202000600080100" + "5b".repeat(256) + ZERO_HEX,
            "6001e2030008000801007ffe" + "5b".repeat(32767) + ZERO_HEX,
            "6001e200fffc0000",
            "5b".repeat(252) + "6001e201fffaff0000",
            "5b".repeat(32764) + "6001e201fffa800000",
            "e200000000")
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("invalidCodeArguments")
  void testInvalidCode(final String code) {
    assertValidation("undefined_instruction", code);
  }

  private static Stream<Arguments> invalidCodeArguments() {
    return Stream.of(
            IntStream.rangeClosed(0x0c, 0x0f),
            IntStream.of(0x1e, 0x1f),
            IntStream.rangeClosed(0x21, 0x2f),
            IntStream.rangeClosed(0x4b, 0x4f),
            IntStream.rangeClosed(0xa5, 0xaf),
            IntStream.rangeClosed(0xb0, 0xcf),
            IntStream.rangeClosed(0xd4, 0xdf),
            IntStream.rangeClosed(0xe9, 0xeb),
            IntStream.of(0xef, 0xf6, 0xfc))
        .flatMapToInt(i -> i)
        .mapToObj(i -> String.format("%02x", i) + ZERO_HEX)
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @MethodSource("pushTruncatedImmediateArguments")
  void testPushTruncatedImmediate(final String code) {
    assertValidation("missing_stop_opcode", code);
  }

  private static Stream<Arguments> pushTruncatedImmediateArguments() {
    return Stream.concat(
            Stream.of("60"),
            IntStream.range(0, 31)
                .mapToObj(i -> String.format("%02x", 0x61 + i) + NOOP_HEX.repeat(i + 1)))
        .map(Arguments::arguments);
  }

  @ParameterizedTest
  @ValueSource(strings = {"e0", "e000"})
  void testRjumpTruncatedImmediate(final String code) {
    assertValidation("truncated_instruction", code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"6001e1", "6001e100"})
  void testRjumpiTruncatedImmediate(final String code) {
    assertValidation("truncated_instruction", code);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "6001e2",
        "6001e201",
        "6001e20100",
        "6001e2030000",
        "6001e20300000001",
        "6001e2030000000100"
      })
  void testRjumpvTruncatedImmediate(final String code) {
    assertValidation("truncated_instruction", code);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "e00000",
        "e0000100",
        "e0fffc00",
        "6001e10000",
        "6001e1000100",
        "6001e1fffa00",
        "6001e200000300",
        "6001e200fff900"
      })
  void testRjumpsOutOfBounds(final String code) {
    assertValidation("invalid_rjump_destination", code);
  }

  @ParameterizedTest
  @MethodSource("rjumpsIntoImmediateExtraArguments")
  @ValueSource(
      strings = {
        // RJUMP into RJUMP immediate
        "e0ffff00",
        "e0fffe00",
        "e00001e0000000",
        "e00002e0000000",
        // RJUMPI into RJUMP immediate
        "6001e10001e0000000",
        "6001e10002e0000000",
        // RJUMPV into RJUMP immediate
        "6001e2000001e0000000",
        "6001e2000002e0000000",
        // RJUMP into RJUMPI immediate
        "e000036001e1000000",
        "e000046001e1000000",
        // RJUMPI backwards into push
        "6001e1fffc00",
        // RJUMPI into RJUMPI immediate
        "6001e1ffff00",
        "6001e1fffe00",
        "6001e100036001e1000000",
        "6001e100046001e1000000",
        // RJUMPV into RJUMPI immediate
        "6001e20000036001e1000000",
        "6001e20000046001e1000000",
        // RJUMP into RJUMPV immediate
        "e00001e200000000",
        "e00002e200000000",
        "e00003e200000000",
        // RJUMPI into RJUMPV immediate
        "6001e10001e200000000",
        "6001e10002e200000000",
        "6001e10003e200000000",
        // RJUMPV into RJUMPV immediate
        "6001e200ffff00",
        "6001e200fffe00",
        "6001e200fffd00",
        "6001e2000001e200000000",
        "6001e2000002e200000000",
        "6001e2000003e200000000",
        "6001e2000001e2010000000000",
        "6001e2000002e2010000000000",
        "6001e2000003e2010000000000",
        "6001e2000004e2010000000000",
        "6001e2000005e2010000000000"
      })
  void testRjumpsIntoImmediate(final String code) {
    assertValidation("invalid_rjump_destination", code);
  }

  private static Stream<Arguments> rjumpsIntoImmediateExtraArguments() {
    return IntStream.range(1, 33)
        .mapToObj(
            n ->
                IntStream.range(1, n + 1)
                    .mapToObj(
                        offset ->
                            Stream.of(
                                String.format("e000%02x", offset)
                                    + // RJUMP offset
                                    String.format("%02x", 0x60 + n - 1)
                                    + // PUSHn
                                    ZERO_HEX.repeat(n)
                                    + // push data
                                    ZERO_HEX, // STOP
                                String.format("6001e100%02x", offset)
                                    + // PUSH1 1 RJUMI offset
                                    String.format("%02x", 0x60 + n - 1)
                                    + // PUSHn
                                    ZERO_HEX.repeat(n)
                                    + // push data
                                    ZERO_HEX, // STOP
                                String.format("6001e20000%02x", offset)
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
  @ValueSource(strings = {"e3", "e300"})
  void testCallFTruncated(final String code) {
    assertValidation("truncated_instruction", code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"e5", "e500"})
  void testJumpCallFTruncated(final String code) {
    assertValidation("truncated_instruction", code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"e30004", "e303ff", "e3ffff"})
  void testCallFWrongSection(final String code) {
    assertValidation("invalid_code_section_index", code, false, 3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"e50004", "e503ff", "e5ffff"})
  void testJumpFWrongSection(final String code) {
    assertValidation("invalid_code_section_index", code, false, 3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"e3000100", "e3000200"})
  void testCallFValid(final String code) {
    var testContainer =
        EOFLayout.parseEOF(
            Bytes.fromHexString(
                "ef000101000c0200030001000100010400000000800000000000000000000000e4e4"));

    assertValidation(
        null, Bytes.fromHexString(code), testContainer.getCodeSection(0), testContainer);
  }

  @ParameterizedTest
  @ValueSource(strings = {"e50001", "e50002", "e50000"})
  void testJumpFValid(final String code) {
    assertValidation(null, code, false, 3);
  }

  @ParameterizedTest
  @MethodSource("immediateContainsOpcodeArguments")
  void testImmediateContainsOpcode(final String code) {
    assertValidation(null, code);
  }

  private static Stream<Arguments> immediateContainsOpcodeArguments() {
    return Stream.of(
            // 0xe0 byte which could be interpreted a RJUMP, but it's not because it's in PUSH data
            "60e0001000",
            "6100e0001000",
            // 0xe1 byte which could be interpreted a RJUMPI, but it's not because it's in PUSH data
            "60e1001000",
            "6100e1001000",
            // 0xe2 byte which could be interpreted a RJUMPV, but it's not because it's in PUSH data
            "60e201000000",
            "6100e201000000",
            // 0x60 byte which could be interpreted as PUSH, but it's not because it's in RJUMP data
            // offset = -160
            "5b".repeat(160) + "e0ff6000",
            // 0x60 byte which could be interpreted as PUSH, but it's not because it's in RJUMPI
            // data
            // offset = -160
            "5b".repeat(160) + "e1ff6000",
            // 0x60 byte which could be interpreted as PUSH, but it's not because it's in RJUMPV
            // data
            // offset = -160
            "5b".repeat(160) + "e200ff6000")
        .map(Arguments::arguments);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource({
    "stackEmpty",
    "stackEmptyAtExit",
    "stackImmediateBytes",
    "stackUnderflow",
    "stackRJumpForward",
    "stackRJumpBackward",
    "stackRJumpI",
    "stackCallF",
    "stackRetF",
    "stackUnreachable",
    "stackHeight",
    "invalidInstructions",
  })
  void validateStackAnalysis(
      final String ignoredName,
      final String expectedError,
      final int sectionToTest,
      final List<List<Object>> rawCodeSections) {

    StringBuilder codeLengths = new StringBuilder();
    StringBuilder typesData = new StringBuilder();
    StringBuilder codeData = new StringBuilder();

    for (var rawCodeSection : rawCodeSections) {
      var code = Bytes.fromHexString(((String) rawCodeSection.get(0)).replace(" ", ""));
      int inputs = (Integer) rawCodeSection.get(1);
      int outputs = (Integer) rawCodeSection.get(2);
      int length = (Integer) rawCodeSection.get(3);

      codeLengths.append(String.format("%04x", code.size()));
      typesData.append(String.format("%02x%02x%04x", inputs, outputs, length));
      codeData.append(code.toUnprefixedHexString());
    }
    int sectionCount = rawCodeSections.size();
    String sb =
        "0xef0001"
            + String.format("01%04x", sectionCount * 4)
            + String.format("02%04x", sectionCount)
            + codeLengths
            + "040000"
            + "00"
            + typesData
            + codeData;

    EOFLayout eofLayout = EOFLayout.parseEOF(Bytes.fromHexString(sb));
    CodeV1Validation validator = new CodeV1Validation(0xc000);

    String validation =
        validator.validateStack(sectionToTest, eofLayout, new WorkList(sectionCount));
    if (expectedError != null) {
      assertThat(validation).contains(expectedError);
    } else {
      assertThat(validation).isNull();
    }
  }

  /**
   * Vectors from an early <a
   * href="https://github.com/ipsilon/eof/commit/c1a2422ddbb72db48bacd2406ed7dad28567b403#diff-ce64373581560ddf02962cb731dfa06457d5ff8615fe12b63c881ab97432f1cf">Ipsilon
   * Prototype</a>
   *
   * @return parameterized test vectors
   */
  static Stream<Arguments> stackEmpty() {
    return Stream.of(Arguments.of("Empty", null, 0, List.of(List.of("00", 0, 0x80, 0))));
  }

  static Stream<Arguments> stackEmptyAtExit() {
    return Stream.of(
        // this depends on requiring stacks to be "clean" returns
        Arguments.of("Stack Empty at Exit", null, 0, List.of(List.of("43 50 00", 0, 0x80, 1))),
        Arguments.of(
            "Stack empty with input",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("50 00", 1, 0x80, 1))),
        // this depends on requiring stacks to be "clean" returns
        Arguments.of(
            "Stack not empty at output",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("00", 1, 0x80, 1))));
  }

  static Stream<Arguments> stackImmediateBytes() {
    return Stream.of(
        Arguments.of(
            "Immediate Bytes - simple push", null, 0, List.of(List.of("6001 50 00", 0, 0x80, 1))));
  }

  static Stream<Arguments> stackUnderflow() {
    return Stream.of(
        Arguments.of(
            "Stack underflow", "stack_underflow", 0, List.of(List.of("50 00", 0, 0x80, 1))),
        Arguments.of(
            "double rjumpi",
            "stack_underflow",
            0,
            List.of(List.of("5f 5f e10005 5f 5f e10000 f3", 0, 0x80, 1))));
  }

  static Stream<Arguments> stackRJumpForward() {
    return Stream.of(
        Arguments.of("RJUMP 0", null, 0, List.of(List.of("e00000 00", 0, 0x80, 0))),
        Arguments.of(
            "RJUMP 1 w/ dead code",
            "unreachable_instructions",
            0,
            List.of(List.of("e00001 43 00", 0, 0x80, 0))),
        Arguments.of(
            "RJUMP 2 w/ dead code",
            "unreachable_instructions",
            0,
            List.of(List.of("e00002 43 50 00", 0, 0x80, 0))),
        Arguments.of(
            "RJUMP 3 and -10",
            "unreachable_instructions",
            0,
            List.of(List.of("e00003 01 50 00 6001 6001 e0fff6", 0, 0x80, 2))));
  }

  static Stream<Arguments> stackRJumpBackward() {
    return Stream.of(
        Arguments.of("RJUMP -3", null, 0, List.of(List.of("e0fffd", 0, 0x80, 0))),
        Arguments.of("RJUMP -4", null, 0, List.of(List.of("5B e0fffc", 0, 0x80, 0))),
        Arguments.of(
            "RJUMP -4 unmatched stack",
            "stack_height_mismatch",
            0,
            List.of(List.of("43 e0fffc", 0, 0x80, 0))),
        Arguments.of(
            "RJUMP -4 unmatched stack",
            "stack_height_mismatch",
            0,
            List.of(List.of("43 50 e0fffc 00", 0, 0x80, 0))),
        Arguments.of(
            "RJUMP -3 matched stack", null, 0, List.of(List.of("43 50 e0fffd", 0, 0x80, 1))),
        Arguments.of(
            "RJUMP -4 matched stack", null, 0, List.of(List.of("43 50 5B e0fffc", 0, 0x80, 1))),
        Arguments.of(
            "RJUMP -5 matched stack", null, 0, List.of(List.of("43 50 43 e0fffb", 0, 0x80, 1))),
        Arguments.of(
            "RJUMP -4 unmatched stack",
            "stack_height_mismatch",
            0,
            List.of(List.of("43 50 43 e0fffc 50 00", 0, 0x80, 0))));
  }

  static Stream<Arguments> stackRJumpI() {
    return Stream.of(
        Arguments.of(
            "RJUMPI Each branch ending with STOP",
            null,
            0,
            List.of(List.of("60ff 6001 e10002 50 00 50 00", 0, 0x80, 2))),
        Arguments.of(
            "RJUMPI One branch ending with RJUMP",
            null,
            0,
            List.of(List.of("60ff 6001 e10004 50 e00001 50 00", 0, 0x80, 2))),
        Arguments.of(
            "RJUMPI Fallthrough",
            null,
            0,
            List.of(List.of("60ff 6001 e10004 80 80 50 50 50 00", 0, 0x80, 3))),
        Arguments.of(
            "RJUMPI Offset 0", null, 0, List.of(List.of("60ff 6001 e10000 50 00", 0, 0x80, 2))),
        Arguments.of(
            "Simple loop (RJUMPI offset = -5)",
            null,
            0,
            List.of(List.of("6001 60ff 81 02 80 e1fffa 50 50 00", 0, 0x80, 3))),
        Arguments.of(
            "RJUMPI One branch increasing max stack more stack than another",
            null,
            0,
            List.of(List.of("6001 e10007 30 30 30 50 50 50 00 30 50 00", 0, 0x80, 3))),
        Arguments.of(
            "RJUMPI One branch increasing max stack more stack than another II",
            null,
            0,
            List.of(List.of("6001 e10003 30 50 00 30 30 30 50 50 50 00", 0, 0x80, 3))),
        Arguments.of(
            "RJUMPI Missing stack argument",
            "stack_underflow",
            0,
            List.of(List.of("e10000 00", 0, 0x80, 0))),
        Arguments.of(
            "Stack underflow one branch",
            "stack_underflow",
            0,
            List.of(List.of("60ff 6001 e10002 50 00 02 50 00", 0, 0x80, 0))),
        Arguments.of(
            "Stack underflow another branch",
            "stack_underflow",
            0,
            List.of(List.of("60ff 6001 e10002 02 00 19 50 00", 0, 0x80, 0))),
        // this depends on requiring stacks to be "clean" returns
        Arguments.of(
            "RJUMPI Stack not empty in the end of one branch",
            null,
            0,
            List.of(List.of("60ff 6001 e10002 50 00 19 00", 0, 0x80, 2))),
        // this depends on requiring stacks to be "clean" returns
        Arguments.of(
            "RJUMPI Stack not empty in the end of one branch II",
            null,
            0,
            List.of(List.of("60ff 6001 e10002 19 00 50 00", 0, 0x80, 2))));
  }

  static Stream<Arguments> stackCallF() {
    return Stream.of(
        Arguments.of(
            "0 input 0 output",
            null,
            0,
            List.of(List.of("e30001 00", 0, 0x80, 0), List.of("e4", 0, 0, 0))),
        Arguments.of(
            "0 inputs, 0 output 3 sections",
            null,
            0,
            List.of(
                List.of("e30002 00", 0, 0x80, 0), List.of("e4", 1, 1, 1), List.of("e4", 0, 0, 0))),
        Arguments.of(
            "more than 0 inputs",
            null,
            0,
            List.of(List.of("30 e30001 00", 0, 0x80, 1), List.of("00", 1, 0x80, 1))),
        Arguments.of(
            "forwarding an argument",
            null,
            1,
            List.of(
                List.of("00", 0, 0x80, 0),
                List.of("e30002 00", 1, 0x80, 1),
                List.of("00", 1, 0x80, 1))),
        Arguments.of(
            "more than 1 inputs",
            null,
            0,
            List.of(List.of("30 80 e30001 00", 0, 0x80, 2), List.of("00", 2, 0x80, 2))),
        Arguments.of(
            "more than 0 outputs",
            null,
            0,
            List.of(List.of("e30001 50 00", 0, 0x80, 1), List.of("30e4", 0, 1, 1))),
        Arguments.of(
            "more than 0 outputs 3 sections",
            null,
            0,
            List.of(
                List.of("e30002 50 00", 0, 0x80, 1),
                List.of("00", 0, 0x80, 0),
                List.of("30305000", 0, 1, 2))),
        Arguments.of(
            "more than 1 outputs",
            null,
            0,
            List.of(List.of("e30001 50 50 00", 0, 0x80, 2), List.of("3030e4", 0, 2, 2))),
        Arguments.of(
            "more than 0 inputs, more than 0 outputs",
            null,
            0,
            List.of(
                List.of("30 30 e30001 50 50 50 00", 0, 0x80, 3),
                List.of("30 30 e30001 50 50 e4", 2, 3, 5))),
        Arguments.of("recursion", null, 0, List.of(List.of("e30000 00", 0, 0x80, 0))),
        Arguments.of(
            "recursion 2 inputs",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("e30000 00", 2, 0x80, 2))),
        Arguments.of(
            "recursion 2 inputs 2 outputs",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("e30000 50 50 00", 2, 2, 2))),
        Arguments.of(
            "recursion 2 inputs 1 output",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("30 30 e30001 50 50 50 00", 2, 1, 4))),
        Arguments.of(
            "multiple CALLFs with different types",
            null,
            1,
            List.of(
                List.of("00", 0, 0x80, 0),
                List.of("44 e30002 80 80 e30003 44 80 e30004 50 50 e4", 0, 0, 3),
                List.of("30305050e4", 1, 1, 3),
                List.of("505050e4", 3, 0, 3),
                List.of("e4", 2, 2, 2))),
        Arguments.of(
            "underflow",
            "stack_underflow",
            0,
            List.of(List.of("e30001 00", 0, 0x80, 0), List.of("e4", 1, 0, 0))),
        Arguments.of(
            "underflow 2",
            "stack_underflow",
            0,
            List.of(List.of("30 e30001 00", 0, 0x80, 0), List.of("e4", 2, 0, 2))),
        Arguments.of(
            "underflow 3",
            "stack_underflow",
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("50 e30001 e4", 1, 0, 1))),
        Arguments.of(
            "underflow 4",
            "stack_underflow",
            0,
            List.of(
                List.of("44 e30001 80 e30002 00", 0, 0x80, 0),
                List.of("e4", 1, 1, 1),
                List.of("e4", 3, 0, 3))));
  }

  static Stream<Arguments> stackRetF() {
    return Stream.of(
        Arguments.of(
            "0 outputs at section 0",
            "EOF Layout invalid - invalid_first_section_type want 0x80 (non-returning flag) has 0",
            0,
            List.of(List.of("e4", 0, 0, 0), List.of("e4", 0, 0, 0))),
        Arguments.of(
            "0 outputs at section 1",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("e4", 0, 0, 0))),
        Arguments.of(
            "0 outputs at section 2",
            null,
            2,
            List.of(List.of("00", 0, 0x80, 0), List.of("e4", 1, 1, 1), List.of("e4", 0, 0, 0))),
        Arguments.of(
            "more than 0 outputs section 0",
            "EOF Layout invalid - invalid_first_section_type want 0x80 (non-returning flag) has 0",
            0,
            List.of(List.of("44 50 e4", 0, 0, 1), List.of("4400", 0, 1, 1))),
        Arguments.of(
            "more than 0 outputs section 0",
            "EOF Layout invalid - invalid_first_section_type want 0x80 (non-returning flag) has 0",
            1,
            List.of(List.of("00", 0, 0, 0), List.of("44 e4", 0, 1, 1))),
        Arguments.of(
            "more than 1 outputs section 1",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("44 80 e4", 0, 2, 2))),
        Arguments.of(
            "Forwarding return values",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("e4", 1, 1, 1))),
        Arguments.of(
            "Forwarding of return values 2",
            null,
            1,
            List.of(
                List.of("00", 0, 0x80, 0),
                List.of("e30002 e4", 0, 1, 1),
                List.of("30e4", 0, 1, 1))),
        Arguments.of(
            "Multiple RETFs",
            null,
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("e10003 44 80 e4 30 80 e4", 1, 2, 2))),
        Arguments.of(
            "underflow 1",
            "RETF in section 1 calculated height 0 does not match configured return stack 1, min height 0, and max height 0",
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("e4", 0, 1, 0))),
        Arguments.of(
            "underflow 2",
            "RETF in section 1 calculated height 1 does not match configured return stack 2, min height 1, and max height 1",
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("44 e4", 0, 2, 1))),
        Arguments.of(
            "underflow 3",
            "RETF in section 1 calculated height 1 does not match configured return stack 2, min height 1, and max height 1",
            1,
            List.of(List.of("00", 0, 0x80, 0), List.of("e10003 44 80 e4 30 e4", 1, 2, 2))));
  }

  static Stream<Arguments> stackUnreachable() {
    return Stream.of(
        Arguments.of(
            "Max stack not changed by unreachable code",
            "unreachable_instructions",
            0,
            List.of(List.of("30 50 00 30 30 30 50 50 50 00", 0, 0x80, 1))),
        Arguments.of(
            "Max stack not changed by unreachable code RETf",
            "unreachable_instructions",
            0,
            List.of(List.of("30 50 e4 30 30 30 50 50 50 00", 0, 0x80, 1))),
        Arguments.of(
            "Max stack not changed by unreachable code RJUMP",
            "unreachable_instructions",
            0,
            List.of(List.of("30 50 e00006 30 30 30 50 50 50 00", 0, 0x80, 1))),
        Arguments.of(
            "Stack underflow in unreachable code",
            "unreachable_instructions",
            0,
            List.of(List.of("30 50 00 50 00", 0, 0x80, 1))),
        Arguments.of(
            "Stack underflow in unreachable code RETF",
            "unreachable_instructions",
            0,
            List.of(List.of("30 50 e4 50 00", 0, 0x80, 1))),
        Arguments.of(
            "Stack underflow in unreachable code RJUMP",
            "unreachable_instructions",
            0,
            List.of(List.of("30 50 e00001 50 00", 0, 0x80, 1))));
  }

  static Stream<Arguments> stackHeight() {
    return Stream.of(
        Arguments.of(
            "Stack height mismatch backwards",
            "stack_height_mismatch",
            0,
            List.of(List.of("30 e0fffc00", 0, 0x80, 1))),
        Arguments.of(
            "Stack height mismatch forwards",
            "invalid_max_stack_height",
            0,
            List.of(List.of("30e10003303030303000", 0, 0x80, 2))));
  }

  static Stream<Arguments> invalidInstructions() {
    return IntStream.range(0, 256)
        .filter(opcode -> !OpcodeInfo.V1_OPCODES[opcode].valid())
        .mapToObj(
            opcode ->
                Arguments.of(
                    String.format("Invalid opcode %02x", opcode),
                    "undefined_instruction",
                    0,
                    List.of(List.of(String.format("0x%02x", opcode), 0, 0x80, 0))));
  }
}

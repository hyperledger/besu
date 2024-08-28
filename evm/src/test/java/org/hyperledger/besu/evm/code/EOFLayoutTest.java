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
import java.util.Collection;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class EOFLayoutTest {

  public static Collection<Object[]> containersWithFormatErrors() {
    return Arrays.asList(
        new Object[][] {
          {"EF", "No magic", "invalid_magic EOF Container too small", -1},
          {"FFFFFF", "Wrong magic", "invalid_magic EOF header byte 0 incorrect", -1},
          {
            "EFFF01010002020004006000AABBCCDD",
            "Invalid magic",
            "invalid_magic EOF header byte 1 incorrect",
            -1
          },
          {"EF00", "No version", "invalid_magic EOF Container too small", -1},
          {"EF0000010002020004006000AABBCCDD", "Invalid version", "invalid_version 0", 0},
          {"EF0002010002020004006000AABBCCDD", "Invalid version", "invalid_version 2", 2},
          {"EF00FF010002020004006000AABBCCDD", "Invalid version", "invalid_version 255", 255},
          {"EF0001", "No header", "missing_headers_terminator Improper section headers", 1},
          {"EF0001 00", "No code section", "unexpected_header_kind expected 1 actual 0", 1},
          {
            "EF0001 01",
            "No code section size",
            "invalid_type_section_size Invalid Types section size (mod 4 != 0)",
            1
          },
          {
            "EF0001 0100",
            "Code section size incomplete",
            "invalid_type_section_size Invalid Types section size (mod 4 != 0)",
            1
          },
          {
            "EF0001 010004",
            "No section terminator",
            "missing_headers_terminator Improper section headers",
            1
          },
          {
            "EF0001 010004 00",
            "No code section contents",
            "unexpected_header_kind expected 2 actual 0",
            1
          },
          {
            "EF0001 010004 02",
            "No code section count",
            "incomplete_section_number Too few code sections",
            1
          },
          {
            "EF0001 010004 0200",
            "Short code section count",
            "incomplete_section_number Too few code sections",
            1
          },
          {"EF0001 010004 020001", "No code section size", "zero_section_size code 0", 1},
          {"EF0001 010004 02000100", "Short code section size", "zero_section_size code 0", 1},
          {
            "EF0001 010008 0200020001",
            "No code section size multiple codes",
            "zero_section_size code 1",
            1
          },
          {
            "EF0001 010008 020002000100",
            "No code section size multiple codes",
            "zero_section_size code 1",
            1
          },
          {"EF0001 010004 0200010001 04", "No data section size", "incomplete_data_header", 1},
          {"EF0001 010004 0200010001 0400", "Short data section size", "incomplete_data_header", 1},
          {
            "EF0001 010004 0200010001 040000",
            "No Terminator",
            "missing_headers_terminator Improper section headers",
            1
          },
          {
            "EF0001 010004 0200010002 040000 00",
            "No type section",
            "invalid_section_bodies_size Incomplete type section",
            1
          },
          {
            "EF0001 010004 0200010002 040001 040001 00 DA DA",
            "Duplicate data sections",
            "unexpected_header_kind expected 0 actual 4",
            1
          },
          {
            "EF0001 010004 0200010002 040000 00 00",
            "Incomplete type section",
            "invalid_section_bodies_size Incomplete type section",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 00000000FE",
            "Incomplete type section",
            "invalid_section_bodies_size Incomplete type section",
            1
          },
          {
            "EF0001 010008 0200010001 040000 00 00000000 FE ",
            "Incorrect type section size",
            "invalid_section_bodies_size Type section - 0x1 * 4 != 0x8",
            1
          },
          {
            "EF0001 010008 02000200010001 040000 00 0100000000000000 FE FE",
            "Incorrect section zero type input",
            "invalid_first_section_type must be zero input non-returning",
            1
          },
          {
            "EF0001 010008 02000200010001 040000 00 0001000000000000 FE FE",
            "Incorrect section zero type output",
            "invalid_first_section_type must be zero input non-returning",
            1
          },
          {
            "EF0001 010004 0200010002 040000 00 00000000 ",
            "Incomplete code section",
            "invalid_section_bodies_size code section 0",
            1
          },
          {
            "EF0001 010004 0200010002 040000 00 00000000 FE",
            "Incomplete code section",
            "invalid_section_bodies_size code section 0",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 00800000 00000000 FEFE ",
            "No code section multiple",
            "invalid_section_bodies_size code section 1",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 00800000 00000000 FEFE FE",
            "Incomplete code section multiple",
            "invalid_section_bodies_size code section 1",
            1
          },
          {
            "EF0001 010004 0200010001 040003 00 00800000 FE DEADBEEF",
            "Excess data section",
            "invalid_section_bodies_size data after end of all sections",
            1
          },
          {
            "EF0001 0200010001 040001 00 FE DA",
            "type section missing",
            "unexpected_header_kind expected 1 actual 2",
            1
          },
          {
            "EF0001 010004 040001 00 00000000 DA",
            "code section missing",
            "unexpected_header_kind expected 2 actual 4",
            1
          },
          {
            "EF0001 010004 0200010001 00 00000000 FE",
            "data section missing",
            "unexpected_header_kind expected 4 actual 0",
            1
          },
          {
            "EF0001 040001 00 DA",
            "type and code section missing",
            "unexpected_header_kind expected 1 actual 4",
            1
          },
          {
            "EF0001 0200010001 00 FE",
            "type and data section missing",
            "unexpected_header_kind expected 1 actual 2",
            1
          },
          {
            "EF0001 010004 00 00000000",
            "code and data sections missing",
            "unexpected_header_kind expected 2 actual 0",
            1
          },
          {"EF0001 00", "all sections missing", "unexpected_header_kind expected 1 actual 0", 1},
          {
            "EF0001 011004 020401"
                + " 0001".repeat(1025)
                + " 040000 00"
                + " 00000000".repeat(1025)
                + " FE".repeat(1025),
            "no data section, 1025 code sections",
            "too_many_code_sections - 0x401",
            1
          },
          {
            "ef000101000002000003000000",
            "All kinds zero size",
            "incomplete_section_number Too few code sections",
            1
          },
          {
            "ef0001010000020001000103000000ef",
            "Zero type size ",
            "invalid_section_bodies_size Type section - 0x1 * 4 != 0x0",
            1
          },
          {
            "ef0001010004020001000003000000",
            "Zero code section length",
            "zero_section_size code 0",
            1
          },
          {
            "ef000101000402000003000000",
            "Zero code sections",
            "incomplete_section_number Too few code sections",
            1
          },
        });
  }

  public static Collection<Object[]> correctContainers() {
    return Arrays.asList(
        new Object[][] {
          {
            "0xef0001 010004 0200010010 040000 00 00800002 e00001 f3 6001 6000 53 6001 6000 e0fff3 ",
            "1",
            null,
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00800000 01000001 00010001 02030003 FE 5000 3000 8000",
            "non-void input and output types",
            null,
            1
          },
          {
            "EF0001 011000 020400"
                + " 0001".repeat(1024)
                + " 040000 00"
                + " 00800000".repeat(1024)
                + " FE".repeat(1024),
            "no data section, 1024 code sections",
            null,
            1
          },
        });
  }

  public static Collection<Object[]> typeSectionTests() {
    return Arrays.asList(
        new Object[][] {
          {
            "EF0001 010008 02000200020002 040000 00 0100000000000000",
            "Incorrect section zero type input",
            "invalid_first_section_type must be zero input non-returning",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 0001000000000000",
            "Incorrect section zero type output",
            "invalid_first_section_type must be zero input non-returning",
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00800000 F0000000 00010000 02030000 FE 5000 3000 8000",
            "inputs too large",
            "inputs_outputs_num_above_limit Type data input stack too large - 0xf0",
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00800000 01000000 00F00000 02030000 FE 5000 3000 8000",
            "outputs too large",
            "inputs_outputs_num_above_limit - 0xf0",
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00000400 01000000 00010000 02030400 FE 5000 3000 8000",
            "stack too large",
            "max_stack_height_above_limit Type data max stack too large - 0x400",
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00800000 01000001 00010001 02030003 FE 5000 3000 8000",
            "non-void input and output types",
            null,
            1
          }
        });
  }

  public static Collection<Object[]> subContainers() {
    return Arrays.asList(
        new Object[][] {
          {
            "EF0001 010004 0200010001 0300010014 040000 00 00800000 FE EF000101000402000100010400000000800000FE",
            "no data section, one code section, one subcontainer",
            null,
            1
          },
          {
            "EF00 01 010004 0200010001 0300010014 040000 00 00800000 00 (EF0001 010004 0200010001 040000 00 00800000 00)",
            "evmone - one container",
            null,
            1
          },
          {
            "EF00 01 010004 0200010001 0300010014 040003 00 00800000 00 (EF0001 010004 0200010001 040000 00 00800000 00) 000000",
            "evmone - one container two bytes",
            null,
            1
          },
          {
            "EF00 01 010004 0200010001 030003001400140014 040000 00 00800000 00 (EF0001 010004 0200010001 040000 00 00800000 00)(EF0001 010004 0200010001 040000 00 00800000 00)(EF0001 010004 0200010001 040000 00 00800000 00)",
            "evmone - three subContainers",
            null,
            1
          },
          {
            "EF00 01 010004 0200010001 030003001400140014 040003 00 00800000 00 (EF0001 010004 0200010001 040000 00 00800000 00)(EF0001 010004 0200010001 040000 00 00800000 00)(EF0001 010004 0200010001 040000 00 00800000 00) ddeeff",
            "evmone - three subContainers and data",
            null,
            1
          },
          {
            "EF00 01 01000C 020003000100010001 030003001400140014 040003 00 008000000000000000000000 00 00 00 (EF0001 010004 0200010001 040000 00 00800000 00)(EF0001 010004 0200010001 040000 00 00800000 00)(EF0001 010004 0200010001 040000 00 00800000 00) ddeeff",
            "evmone - three subContainers three code and data",
            null,
            1
          },
          {
            "EF00 01 010004 0200010001 0300010100 040000 00 00800000 00 (EF0001 010004 02000100ED 040000 00 00800000 "
                + "5d".repeat(237)
                + ")",
            "evmone - 256 byte container",
            null,
            1
          },
          {
            "EF00 01 010004 0200010001 030100"
                + "0014".repeat(256)
                + "040000 00 00800000 00 "
                + "(EF0001 010004 0200010001 040000 00 00800000 00)".repeat(256),
            "evmone - 256 subContainers",
            null,
            1
          },
          {
            "EF00 01 010004 0200010001 0300010015 040000 00 00800000 00 (EF0001 010004 0200010001 040000 00 00800000 00ff)",
            "dangling data in subcontainer",
            "invalid_section_bodies_size subcontainer size mismatch",
            1
          },
          {
            "EF00 01 010004 0200010001 0300010014 040000 00 00800000 00 (EF0001 010004 0200010001 040000 00 00800000 00ff)",
            "dangling data in container",
            "invalid_section_bodies_size data after end of all sections",
            1
          },
        });
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource({
    "correctContainers",
    "containersWithFormatErrors",
    "typeSectionTests",
    "subContainers"
  })
  void test(
      final String containerString,
      final String description,
      final String failureReason,
      final int expectedVersion) {
    final Bytes container = Bytes.fromHexString(containerString.replaceAll("[^a-fxA-F0-9]", ""));
    final EOFLayout layout = EOFLayout.parseEOF(container, true);

    if (failureReason != null) {
      assertThat(failureReason)
          .withFailMessage("Error string should start with a reference test error code")
          .matches("^[a-zA-Z]+_.*");
    }
    assertThat(layout.version()).isEqualTo(expectedVersion);
    assertThat(layout.invalidReason()).isEqualTo(failureReason);
    assertThat(layout.container()).isEqualTo(container);
    if (layout.invalidReason() != null) {
      assertThat(layout.isValid()).isFalse();
      assertThat(layout.getCodeSectionCount()).isZero();
    } else {
      assertThat(layout.isValid()).isTrue();
      assertThat(layout.getCodeSectionCount()).isNotZero();
    }
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }
}

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

import java.util.Arrays;
import java.util.Collection;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class EOFLayoutTest {

  public static Collection<Object[]> containersWithFormatErrors() {
    return Arrays.asList(
        new Object[][] {
          {"EF", "No magic", "EOF Container too small", -1},
          {"FFFFFF", "Wrong magic", "EOF header byte 0 incorrect", -1},
          {"EFFF01010002020004006000AABBCCDD", "Invalid magic", "EOF header byte 1 incorrect", -1},
          {"EF00", "No version", "EOF Container too small", -1},
          {"EF0000010002020004006000AABBCCDD", "Invalid version", "Unsupported EOF Version 0", 0},
          {"EF0002010002020004006000AABBCCDD", "Invalid version", "Unsupported EOF Version 2", 2},
          {
            "EF00FF010002020004006000AABBCCDD",
            "Invalid version",
            "Unsupported EOF Version 255",
            255
          },
          {"EF0001", "No header", "Improper section headers", 1},
          {"EF0001 00", "No code section", "Expected kind 1 but read kind 0", 1},
          {"EF0001 01", "No code section size", "Invalid Types section size", 1},
          {"EF0001 0100", "Code section size incomplete", "Invalid Types section size", 1},
          {"EF0001 010004", "No section terminator", "Improper section headers", 1},
          {"EF0001 010004 00", "No code section contents", "Expected kind 2 but read kind 0", 1},
          {"EF0001 010004 02", "No code section count", "Invalid Code section count", 1},
          {"EF0001 010004 0200", "Short code section count", "Invalid Code section count", 1},
          {
            "EF0001 010004 020001",
            "No code section size",
            "Invalid Code section size for section 0",
            1
          },
          {
            "EF0001 010004 02000100",
            "Short code section size",
            "Invalid Code section size for section 0",
            1
          },
          {
            "EF0001 010008 0200020001",
            "No code section size multiple codes",
            "Invalid Code section size for section 1",
            1
          },
          {
            "EF0001 010008 020002000100",
            "No code section size multiple codes",
            "Invalid Code section size for section 1",
            1
          },
          {"EF0001 010004 0200010001 04", "No data section size", "Invalid Data section size", 1},
          {
            "EF0001 010004 0200010001 0400",
            "Short data section size",
            "Invalid Data section size",
            1
          },
          {"EF0001 010004 0200010001 040000", "No Terminator", "Improper section headers", 1},
          {"EF0001 010004 0200010002 040000 00", "No type section", "Incomplete type section", 1},
          {
            "EF0001 010004 0200010002 040001 040001 00 DA DA",
            "Duplicate data sections",
            "Expected kind 0 but read kind 4",
            1
          },
          {
            "EF0001 010004 0200010002 040000 00 00",
            "Incomplete type section",
            "Incomplete type section",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 00000000FE",
            "Incomplete type section",
            "Incomplete type section",
            1
          },
          {
            "EF0001 010008 0200010001 040000 00 00000000 FE ",
            "Incorrect type section size",
            "Type section length incompatible with code section count - 0x1 * 4 != 0x8",
            1
          },
          {
            "EF0001 010008 02000200010001 040000 00 0100000000000000 FE FE",
            "Incorrect section zero type input",
            "Code section does not have zero inputs and outputs",
            1
          },
          {
            "EF0001 010008 02000200010001 040000 00 0001000000000000 FE FE",
            "Incorrect section zero type output",
            "Code section does not have zero inputs and outputs",
            1
          },
          {
            "EF0001 010004 0200010002 040000 00 00000000 ",
            "Incomplete code section",
            "Incomplete code section 0",
            1
          },
          {
            "EF0001 010004 0200010002 040000 00 00000000 FE",
            "Incomplete code section",
            "Incomplete code section 0",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 00800000 00000000 FEFE ",
            "No code section multiple",
            "Incomplete code section 1",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 00800000 00000000 FEFE FE",
            "Incomplete code section multiple",
            "Incomplete code section 1",
            1
          },
          {
            "EF0001 010004 0200010001 040003 00 00800000 FE DEADBEEF",
            "Incomplete data section",
            "Dangling data after end of all sections",
            1
          },
          {
            "EF0001 010004 0200010001 040003 00 00800000 FE BEEF",
            "Incomplete data section",
            "Incomplete data section",
            1
          },
          {
            "EF0001 0200010001 040001 00 FE DA",
            "type section missing",
            "Expected kind 1 but read kind 2",
            1
          },
          {
            "EF0001 010004 040001 00 00000000 DA",
            "code section missing",
            "Expected kind 2 but read kind 4",
            1
          },
          {
            "EF0001 010004 0200010001 00 00000000 FE",
            "data section missing",
            "Expected kind 4 but read kind 0",
            1
          },
          {
            "EF0001 040001 00 DA",
            "type and code section missing",
            "Expected kind 1 but read kind 4",
            1
          },
          {
            "EF0001 0200010001 00 FE",
            "type and data section missing",
            "Expected kind 1 but read kind 2",
            1
          },
          {
            "EF0001 010004 00 00000000",
            "code and data sections missing",
            "Expected kind 2 but read kind 0",
            1
          },
          {"EF0001 00", "all sections missing", "Expected kind 1 but read kind 0", 1},
          {
            "EF0001 011004 020401"
                + " 0001".repeat(1025)
                + " 040000 00"
                + " 00000000".repeat(1025)
                + " FE".repeat(1025),
            "no data section, 1025 code sections",
            "Too many code sections - 0x401",
            1
          },
          {"ef000101000002000003000000", "All kinds zero size", "Invalid Types section size", 1},
          {"ef0001010000020001000103000000ef", "Zero type size ", "Invalid Types section size", 1},
          {
            "ef0001010004020001000003000000",
            "Zero code section length",
            "Invalid Code section size for section 0",
            1
          },
          {"ef000101000402000003000000", "Zero code sections", "Invalid Code section count", 1},
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
            "Code section does not have zero inputs and outputs",
            1
          },
          {
            "EF0001 010008 02000200020002 040000 00 0001000000000000",
            "Incorrect section zero type output",
            "Code section does not have zero inputs and outputs",
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00800000 F0000000 00010000 02030000 FE 5000 3000 8000",
            "inputs too large",
            "Type data input stack too large - 0xf0",
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00800000 01000000 00F00000 02030000 FE 5000 3000 8000",
            "outputs too large",
            "Type data output stack too large - 0xf0",
            1
          },
          {
            "EF0001 010010 0200040001000200020002 040000 00 00000400 01000000 00010000 02030400 FE 5000 3000 8000",
            "stack too large",
            "Type data max stack too large - 0x400",
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
    final Bytes container = Bytes.fromHexString(containerString.replace(" ", ""));
    final EOFLayout layout = EOFLayout.parseEOF(container);

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
}

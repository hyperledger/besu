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
          {"EF0001 010004 0200010001 03", "No data section size", "Invalid Data section size", 1},
          {
            "EF0001 010004 0200010001 0300",
            "Short data section size",
            "Invalid Data section size",
            1
          },
          {"EF0001 010004 0200010001 030000", "No Terminator", "Improper section headers", 1},
          {"EF0001 010008 0200010002 030000 00", "No type section", "Incomplete type section", 1},
          {
            "EF0001 010008 0200010002 030001 030001 00 DA DA",
            "Duplicate data sections",
            "Expected kind 0 but read kind 3",
            1
          },
          {
            "EF0001 010008 0200010002 030000 00 00",
            "Incomplete type section",
            "Incomplete type section",
            1
          },
          {
            "EF0001 010008 02000200020002 030000 00 00000000FE",
            "Incomplete type section",
            "Incomplete type section",
            1
          },
          {
            "EF0001 010008 02000200020002 030000 00 0100000000000000",
            "Incorrect section zero type input",
            "Code section does not have zero inputs and outputs",
            1
          },
          {
            "EF0001 010008 02000200020002 030000 00 0001000000000000",
            "Incorrect section zero type output",
            "Code section does not have zero inputs and outputs",
            1
          },
          {
            "EF0001 010008 0200010002 030000 00 00000000 ",
            "Incomplete code section",
            "Incomplete code section 0",
            1
          },
          {
            "EF0001 010008 0200010002 030000 00 00000000 FE",
            "Incomplete code section",
            "Incomplete code section 0",
            1
          },
          {
            "EF0001 010008 02000200020002 030000 00 00000000 00000000 FEFE ",
            "No code section multiple",
            "Incomplete code section 1",
            1
          },
          {
            "EF0001 010008 02000200020002 030000 00 00000000 00000000 FEFE FE",
            "Incomplete code section multiple",
            "Incomplete code section 1",
            1
          },
          {
            "EF0001 010008 0200010001 030003 00 00000000 FE DEADBEEF",
            "Incomplete data section",
            "Dangling data after end of all sections",
            1
          },
          {
            "EF0001 010008 0200010001 030003 00 00000000 FE BEEF",
            "Incomplete data section",
            "Incomplete data section",
            1
          },
        });
  }

  public static Collection<Object[]> correctContainers() {

    return Arrays.asList(
        new Object[][] {
          {
            "EF0001 010004 0200010001 030000 00 00000000 FE",
            "no data section, one code section",
            null,
            1
          },
          {
            "EF0001 010004 0200010001 030001 00 00000000 FE DA",
            "with data section, one code section",
            null,
            1
          },
          {
            "EF0001 010008 02000200010001 030000 00 00000000 00000000 FE FE",
            "no data section, multiple code section",
            null,
            1
          },
          {
            "EF0001 010008 02000200010001 030001 00 00000000 00000000 FE FE DA",
            "with data section, multiple code section",
            null,
            1
          },
          {
            "EF0001 010010 0200040001000200020002 030000 00 00000000 01000000 00010000 02030000 FE 5000 3000 8000",
            "non-void input and output types",
            null,
            1
          },
          {
            "EF0001 0200010001 030001 00 FE DA",
            "type section missing",
            "Expected kind 1 but read kind 2",
            1
          },
          {
            "EF0001 010004 030001 00 00000000 DA",
            "code section missing",
            "Expected kind 2 but read kind 3",
            1
          },
          {
            "EF0001 010004 0200010001 00 00000000 FE",
            "data section missing",
            "Expected kind 3 but read kind 0",
            1
          },
          {
            "EF0001 030001 00 DA",
            "type and code section missing",
            "Expected kind 1 but read kind 3",
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
        });
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource({"correctContainers", "containersWithFormatErrors"})
  void test(
      final String containerString,
      final String description,
      final String failureReason,
      final int expectedVersion) {
    final Bytes container = Bytes.fromHexString(containerString.replace(" ", ""));
    final EOFLayout layout = EOFLayout.parseEOF(container);

    assertThat(layout.getVersion()).isEqualTo(expectedVersion);
    assertThat(layout.getInvalidReason()).isEqualTo(failureReason);
    assertThat(layout.getContainer()).isEqualTo(container);
    if (layout.getInvalidReason() != null) {
      assertThat(layout.isValid()).isFalse();
      assertThat(layout.getCodeSections()).isNull();
    } else {
      assertThat(layout.isValid()).isTrue();
      assertThat(layout.getCodeSections()).isNotNull();
    }
  }
}

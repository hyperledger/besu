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

  public static Collection<Object[]> testCasesFromEIP3540() {
    return Arrays.asList(
        new Object[][] {
          {"EF", "No magic", "EOF Container too small"},
          {"EFFF01010002020004006000AABBCCDD", "Invalid magic", "EOF header byte 1 incorrect"},
          {"EF00", "No version", "EOF Container too small"},
          {"EF0000010002020004006000AABBCCDD", "Invalid version", "Unsupported EOF Version 0"},
          {"EF0002010002020004006000AABBCCDD", "Invalid version", "Unsupported EOF Version 2"},
          {"EF00FF010002020004006000AABBCCDD", "Invalid version", "Unsupported EOF Version 255"},
          {"EF0001", "No header", "Improper section headers"},
          {"EF000100", "No code section", "Missing code (kind=1) section"},
          {"EF000101", "No code section size", "Improper section headers"},
          {"EF00010100", "Code section size incomplete", "Improper section headers"},
          {"EF0001010002", "No section terminator", "Improper section headers"},
          {"EF000101000200", "No code section contents", "Missing or incomplete section data"},
          {
            "EF00010100020060",
            "Code section contents incomplete",
            "Missing or incomplete section data"
          },
          {
            "EF0001010002006000DEADBEEF",
            "Trailing bytes after code section",
            "Dangling data at end of container"
          },
          // {"EF00010100020100020060006000", "Multiple code sections",
          //     "Duplicate section number 1"},
          {"EF000101000000", "Empty code section", "Empty section contents"},
          {"EF000101000002000200AABB", "Empty code section", "Empty section contents"},
          {
            "EF000102000401000200AABBCCDD6000",
            "Data section preceding code section",
            "Code section cannot follow data section"
          },
          {
            "EF000102000400AABBCCDD",
            "Data section without code section",
            "Missing code (kind=1) section"
          },
          {"EF000101000202", "No data section size", "Improper section headers"},
          {"EF00010100020200", "Data section size incomplete", "Improper section headers"},
          {"EF0001010002020004", "No section terminator", "Improper section headers"},
          {
            "EF0001010002020004006000",
            "No data section contents",
            "Missing or incomplete section data"
          },
          {
            "EF0001010002020004006000AABBCC",
            "Data section contents incomplete",
            "Missing or incomplete section data"
          },
          {
            "EF0001010002020004006000AABBCCDDEE",
            "Trailing bytes after data section",
            "Dangling data at end of container"
          },
          {
            "EF0001010002020004020004006000AABBCCDDAABBCCDD",
            "Multiple data sections",
            "Duplicate section number 2"
          },
          {"EF0001010002020000006000", "Empty data section", "Empty section contents"},
          // {
          //   "EF0001010002030004006000AABBCCDD",
          //   "Unknown section (id = 3)",
          //   "EOF Section kind 3 not supported"
          // },
          {"EF0001010002006000", "Valid", null},
        });
  }

  public static Collection<Object[]> testCasesFromEIP4750() {
    return Arrays.asList(
        new Object[][] {
          {"EF000101000100FE", "implicit type section, no data section", null},
          {"EF000101000102000100FEDA", "implicit type section, data section", null},
          {"EF0001030002010001000000FE", "type section, no data section", null},
          {"EF0001030002010001020001000000FEDA", "type section, data section", null},
          {
            "EF00010300040100010100010000000000FEFE",
            "multiple code sections, no data section",
            null
          },
          {
            "EF00010300040100010100010200010000000000FEFEDA",
            "multiple code sections, data section",
            null
          },
          {
            "EF0001030008010001010002010002010002000000010000010203FE500030008000",
            "non-void input and output types",
            null
          },
          {
            "EF0001030800" + "010001".repeat(1024) + "00" + "0000".repeat(1024) + "FE".repeat(1024),
            "max number of code sections",
            null
          },
          {"ef000101000100fc", "RETF is terminating instruction", null},
          {"EF000104000100FE", "unknown_section_id", "EOF Section kind 4 not supported"},
          {
            "EF000101000101000100FEFE",
            "mandatory_type_section_missing",
            "Multiple code sections but not enough type entries"
          },
          {
            "EF00010300020300020100010100010000000000FEFE",
            "multiple_type_sections",
            "Duplicate section number 3"
          },
          {
            "EF0001030002010001010001030002000000FEFE0000",
            "multiple_type_sections",
            "Duplicate section number 3"
          },
          {
            "EF000101000103000200FE0000",
            "code_section_before_type_section",
            "Code section cannot precede Type Section"
          },
          {
            "EF000101000103000201000100FE0000FE",
            "code_section_before_type_section",
            "Code section cannot precede Type Section"
          },
          {
            "EF000101000103000202000300FE0000AABBCC",
            "code_section_before_type_section",
            "Code section cannot precede Type Section"
          },
          {
            "EF000101000102000303000200FEAABBCC0000",
            "code_section_before_type_section",
            "Code section cannot precede Type Section"
          },
          {
            "EF00010300010100010000FE",
            "invalid_type_section_size",
            "Type section cannot be odd length"
          },
          {
            "EF00010300040100010000000000FE",
            "invalid_type_section_size",
            "Type data length (4) does not match code size count (1 * 2)"
          },
          {
            "EF00010300040100010100010100010000000000FEFEFE",
            "invalid_type_section_size",
            "Type data length (4) does not match code size count (3 * 2)"
          },
          {
            "EF0001030008010001010001010001000000000000000000FEFEFE",
            "invalid_type_section_size",
            "Type data length (8) does not match code size count (3 * 2)"
          },
          {
            "EF000103000201000300000160005C",
            "invalid_first_section_type",
            "First section input and output must be zero"
          },
          {
            "EF00010300020100020001005000",
            "invalid_first_section_type",
            "First section input and output must be zero"
          },
          {
            "EF000103000201000300020360005C",
            "invalid_first_section_type",
            "First section input and output must be zero"
          },
          {
            "EF0001030802" + "010001".repeat(1025) + "00" + "0000".repeat(1025) + "FE".repeat(1025),
            "too_many_code_sections",
            "Too many code sections"
          }
        });
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource({"testCasesFromEIP3540", "testCasesFromEIP4750"})
  void test(final String containerString, final String description, final String failureReason) {
    final Bytes container = Bytes.fromHexString(containerString);
    final EOFLayout layout = EOFLayout.parseEOF(container);
    System.out.println(description);
    assertThat(layout.getInvalidReason()).isEqualTo(failureReason);
  }
}

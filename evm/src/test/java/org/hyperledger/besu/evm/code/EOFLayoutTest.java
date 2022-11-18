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
          {"EF", "No magic", "EOF Container too small", null},
          {
            "EFFF01010002020004006000AABBCCDD", "Invalid magic", "EOF header byte 1 incorrect", null
          },
          {"EF00", "No version", "EOF Container too small", null},
          {
            "EF0000010002020004006000AABBCCDD", "Invalid version", "Unsupported EOF Version 0", null
          },
          {
            "EF0002010002020004006000AABBCCDD", "Invalid version", "Unsupported EOF Version 2", null
          },
          {
            "EF00FF010002020004006000AABBCCDD",
            "Invalid version",
            "Unsupported EOF Version 255",
            null
          },
          {"EF0001", "No header", "Improper section headers", null},
          {"EF000100", "No code section", "Missing code (kind=1) section", null},
          {"EF000101", "No code section size", "Improper section headers", null},
          {"EF00010100", "Code section size incomplete", "Improper section headers", null},
          {"EF0001010002", "No section terminator", "Improper section headers", null},
          {
            "EF000101000200", "No code section contents", "Missing or incomplete section data", null
          },
          {
            "EF00010100020060",
            "Code section contents incomplete",
            "Missing or incomplete section data",
            null
          },
          {
            "EF0001010002006000DEADBEEF",
            "Trailing bytes after code section",
            "Dangling data at end of container",
            null
          },
          {
            "EF00010100020100020060006000",
            "Multiple code sections",
            "Duplicate section number 1",
            null
          },
          {"EF000101000000", "Empty code section", "Empty section contents", null},
          {"EF000101000002000200AABB", "Empty code section", "Empty section contents", null},
          {
            "EF000102000401000200AABBCCDD6000",
            "Data section preceding code section",
            "Code section cannot follow data section",
            null
          },
          {
            "EF000102000400AABBCCDD",
            "Data section without code section",
            "Missing code (kind=1) section",
            null
          },
          {"EF000101000202", "No data section size", "Improper section headers", null},
          {"EF00010100020200", "Data section size incomplete", "Improper section headers", null},
          {"EF0001010002020004", "No section terminator", "Improper section headers", null},
          {
            "EF0001010002020004006000",
            "No data section contents",
            "Missing or incomplete section data",
            null
          },
          {
            "EF0001010002020004006000AABBCC",
            "Data section contents incomplete",
            "Missing or incomplete section data",
            null
          },
          {
            "EF0001010002020004006000AABBCCDDEE",
            "Trailing bytes after data section",
            "Dangling data at end of container",
            null
          },
          {
            "EF0001010002020004020004006000AABBCCDDAABBCCDD",
            "Multiple data sections",
            "Duplicate section number 2",
            null
          },
          {"EF0001010002020000006000", "Empty data section", "Empty section contents", null},
          {
            "EF0001010002030004006000AABBCCDD",
            "Unknown section (id = 3)",
            "EOF Section kind 3 not supported",
            null
          },
          {"EF0001010002006000", "Valid", null, "0x6000"},
        });
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("testCasesFromEIP3540")
  void test(
      final String containerString,
      final String description,
      final String failureReason,
      final String code) {
    final Bytes container = Bytes.fromHexString(containerString);
    final EOFLayout layout = EOFLayout.parseEOF(container);
    System.out.println(description);
    assertThat(layout.getInvalidReason()).isEqualTo(failureReason);
    if (code != null) {
      assertThat(layout.getSections()).hasSize(3);
      assertThat(layout.getSections()[1]).isNotNull();
      assertThat(layout.getSections()[1].toHexString()).isEqualTo(code);
    } else {
      assertThat(layout.getSections()).isNull();
    }
  }
}

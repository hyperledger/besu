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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class CodeFactoryTest {

  @Test
  void invalidCodeIncompleteMagic() {
    invalidCode("0xEF");
  }

  @Test
  void invalidCodeInvalidMagic() {
    invalidCode("0xEFFF0101000302000400600000AABBCCDD");
  }

  @Test
  void invalidCodeNoVersion() {
    invalidCode("0xEF00");
  }

  @Test
  void invalidCodeInvalidVersion0x00() {
    invalidCode("EF000001000302000400600000AABBCCDD");
  }

  @Test
  void invalidCodeInvalidVersion0x02() {
    invalidCode("EF000201000302000400600000AABBCCDD");
  }

  @Test
  void invalidCodeInvalidVersion0xFF() {
    invalidCode("EF00FF01000302000400600000AABBCCDD");
  }

  @Test
  void invalidCodeNoHeader() {
    invalidCode("0xEF0001");
  }

  @Test
  void invalidCodeNoCodeSection() {
    invalidCode("0xEF000100");
  }

  @Test
  void invalidCodeNoCodeSectionSize() {
    invalidCode("0xEF000101");
  }

  @Test
  void invalidCodeCodeSectionSizeIncomplete() {
    invalidCode("0xEF00010100");
  }

  @Test
  void invalidCodeNoSectionTerminator0x03() {
    invalidCode("0xEF0001010003");
  }

  @Test
  void invalidCodeNoSectionTerminator0x03600000() {
    invalidCode("0xEF0001010003600000");
  }

  @Test
  void invalidCodeNoCodeSectionContents() {
    invalidCode("0xEF000101000200");
  }

  @Test
  void invalidCodeCodeSectionContentsIncomplete() {
    invalidCode("0xEF00010100020060");
  }

  @Test
  void invalidCodeTrailingBytesAfterCodeSection() {
    invalidCode("0xEF000101000300600000DEADBEEF");
  }

  @Test
  void invalidCodeMultipleCodeSections() {
    invalidCode("0xEF000101000301000300600000600000");
  }

  @Test
  void invalidCodeEmptyCodeSection() {
    invalidCode("0xEF000101000000");
  }

  @Test
  void invalidCodeEmptyCodeSectionWithNonEmptyDataSection() {
    invalidCode("0xEF000101000002000200AABB");
  }

  @Test
  void invalidCodeDataSectionPrecedingCodeSection() {
    invalidCode("0xEF000102000401000300AABBCCDD600000");
  }

  @Test
  void invalidCodeDataSectionWithoutCodeSection() {
    invalidCode("0xEF000102000400AABBCCDD");
  }

  @Test
  void invalidCodeNoDataSectionSize() {
    invalidCode("0xEF000101000202");
  }

  @Test
  void invalidCodeDataSectionSizeIncomplete() {
    invalidCode("0xEF00010100020200");
  }

  @Test
  void invalidCodeNoSectionTerminator0x03020004() {
    invalidCode("0xEF0001010003020004");
  }

  @Test
  void invalidCodeNoSectionTerminator0x03020004600000AABBCCDD() {
    invalidCode("0xEF0001010003020004600000AABBCCDD");
  }

  @Test
  void invalidCodeNoDataSectionContents() {
    invalidCode("0xEF000101000302000400600000");
  }

  @Test
  void invalidCodeDataSectionContentsIncomplete() {
    invalidCode("0xEF000101000302000400600000AABBCC");
  }

  @Test
  void invalidCodeTrailingBytesAfterDataSection() {
    invalidCode("0xEF000101000302000400600000AABBCCDDEE");
  }

  @Test
  void invalidCodeMultipleDataSections() {
    invalidCode("0xEF000101000302000402000400600000AABBCCDDAABBCCDD");
  }

  @Test
  void invalidCodeMultipleCodeAndDataSections() {
    invalidCode("0xEF000101000101000102000102000100FEFEAABB");
  }

  @Test
  void invalidCodeEmptyDataSection() {
    invalidCode("0xEF000101000302000000600000");
  }

  @Test
  void invalidCodeUnknownSectionId3() {
    invalidCode("0xEF0001010002030004006000AABBCCDD");
  }

  private static void invalidCode(final String str) {
    Code code = CodeFactory.createCode(Bytes.fromHexString(str), Hash.EMPTY, 1, true);
    assertThat(code.isValid()).isFalse();
  }
}

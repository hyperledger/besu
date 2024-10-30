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
import static org.hyperledger.besu.evm.EOFTestConstants.bytesFromPrettyPrint;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import org.junit.jupiter.api.Test;

class CodeFactoryTest {

  @Test
  void invalidCodeIncompleteMagic() {
    invalidCodeForCreation("0xEF");
  }

  @Test
  void invalidCodeInvalidMagic() {
    invalidCodeForCreation("0xEFFF0101000302000400600000AABBCCDD");
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

  @Test
  void invalidDataTruncated() {
    invalidCode(
        "EF0001 010004 0200010001 040003 00 00800000 FE BEEF",
        "Truncated data section when a complete section was required");
  }

  @Test
  void validComboEOFCreateReturnContract() {
    validCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              0011 # Code section 0 , 17 bytes
            030001 # Total subcontainers ( 1 )
              0032 # Sub container 0, 50 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0004 # max stack:  4
                   # Code section 0 - in=0 out=non-returning height=4
              6000 # [0] PUSH1(0)
              6000 # [2] PUSH1(0)
              6000 # [4] PUSH1(0)
              6000 # [6] PUSH1(0)
              ec00 # [8] EOFCREATE(0)
            612015 # [10] PUSH2(0x2015)
              6001 # [13] PUSH1(1)
                55 # [15] SSTORE
                00 # [16] STOP
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0006 # Code section 0 , 6 bytes
                030001 # Total subcontainers ( 1 )
                  0014 # Sub container 0, 20 byte
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0002 # max stack:  2
                       # Code section 0 - in=0 out=non-returning height=2
                  6000 # [0] PUSH1(0)
                  6000 # [2] PUSH1(0)
                  ee00 # [4] RETURNCONTRACT(0)
                           # Subcontainer 0.0 starts here
                    ef0001 # Magic and Version ( 1 )
                    010004 # Types length ( 4 )
                    020001 # Total code sections ( 1 )
                      0001 # Code section 0 , 1 bytes
                    040000 # Data section length(  0 )       \s
                        00 # Terminator (end of header)
                           # Code section 0 types
                        00 # 0 inputs\s
                        80 # 0 outputs  (Non-returning function)
                      0000 # max stack:  0
                           # Code section 0 - in=0 out=non-returning height=0
                        00 # [0] STOP
                           # Data section (empty)
                           # Subcontainer 0.0 ends
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """);
  }

  @Test
  void validComboReturnContactStop() {
    validCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              000c # Code section 0 , 12 bytes
            030001 # Total subcontainers ( 1 )
              0014 # Sub container 0, 20 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0002 # max stack:  2
                   # Code section 0 - in=0 out=non-returning height=2
            612015 # [0] PUSH2(0x2015)
              6001 # [3] PUSH1(1)
                55 # [5] SSTORE
              6000 # [6] PUSH1(0)
              6000 # [8] PUSH1(0)
              ee00 # [10] RETURNCONTRACT(0)
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0001 # Code section 0 , 1 bytes
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0000 # max stack:  0
                       # Code section 0 - in=0 out=non-returning height=0
                    00 # [0] STOP
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """);
  }

  @Test
  void validComboReturnContactReturn() {
    validCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              000c # Code section 0 , 12 bytes
            030001 # Total subcontainers ( 1 )
              0018 # Sub container 0, 24 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0002 # max stack:  2
                   # Code section 0 - in=0 out=non-returning height=2
            612015 # [0] PUSH2(0x2015)
              6001 # [3] PUSH1(1)
                55 # [5] SSTORE
              6000 # [6] PUSH1(0)
              6000 # [8] PUSH1(0)
              ee00 # [10] RETURNCONTRACT(0)
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0005 # Code section 0 , 5 bytes
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0002 # max stack:  2
                       # Code section 0 - in=0 out=non-returning height=2
                  6000 # [0] PUSH1(0)
                  6000 # [2] PUSH1(0)
                    f3 # [4] RETURN
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """);
  }

  @Test
  void validComboEOFCreateRevert() {
    validCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              0011 # Code section 0 , 17 bytes
            030001 # Total subcontainers ( 1 )
              0018 # Sub container 0, 24 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0004 # max stack:  4
                   # Code section 0 - in=0 out=non-returning height=4
              6000 # [0] PUSH1(0)
              6000 # [2] PUSH1(0)
              6000 # [4] PUSH1(0)
              6000 # [6] PUSH1(0)
              ec00 # [8] EOFCREATE(0)
            612015 # [10] PUSH2(0x2015)
              6001 # [13] PUSH1(1)
                55 # [15] SSTORE
                00 # [16] STOP
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0005 # Code section 0 , 5 bytes
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0002 # max stack:  2
                       # Code section 0 - in=0 out=non-returning height=2
                  6000 # [0] PUSH1(0)
                  6000 # [2] PUSH1(0)
                    fd # [4] REVERT
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """);
  }

  @Test
  void validComboReturncontractRevert() {
    validCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              000c # Code section 0 , 12 bytes
            030001 # Total subcontainers ( 1 )
              0018 # Sub container 0, 24 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0002 # max stack:  2
                   # Code section 0 - in=0 out=non-returning height=2
            612015 # [0] PUSH2(0x2015)
              6001 # [3] PUSH1(1)
                55 # [5] SSTORE
              6000 # [6] PUSH1(0)
              6000 # [8] PUSH1(0)
              ee00 # [10] RETURNCONTRACT(0)
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0005 # Code section 0 , 5 bytes
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0002 # max stack:  2
                       # Code section 0 - in=0 out=non-returning height=2
                  6000 # [0] PUSH1(0)
                  6000 # [2] PUSH1(0)
                    fd # [4] REVERT
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """);
  }

  @Test
  void invalidComboEOFCreateStop() {
    invalidCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              0011 # Code section 0 , 17 bytes
            030001 # Total subcontainers ( 1 )
              0014 # Sub container 0, 20 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0004 # max stack:  4
                   # Code section 0 - in=0 out=non-returning height=4
              6000 # [0] PUSH1(0)
              6000 # [2] PUSH1(0)
              6000 # [4] PUSH1(0)
              6000 # [6] PUSH1(0)
              ec00 # [8] EOFCREATE(0)
            612015 # [10] PUSH2(0x2015)
              6001 # [13] PUSH1(1)
                55 # [15] SSTORE
                00 # [16] STOP
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0001 # Code section 0 , 1 bytes
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0000 # max stack:  0
                       # Code section 0 - in=0 out=non-returning height=0
                    00 # [0] STOP
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """,
        "incompatible_container_kind");
  }

  @Test
  void invalidComboEOFCretateReturn() {
    invalidCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              0011 # Code section 0 , 17 bytes
            030001 # Total subcontainers ( 1 )
              0018 # Sub container 0, 24 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0004 # max stack:  4
                   # Code section 0 - in=0 out=non-returning height=4
              6000 # [0] PUSH1(0)
              6000 # [2] PUSH1(0)
              6000 # [4] PUSH1(0)
              6000 # [6] PUSH1(0)
              ec00 # [8] EOFCREATE(0)
            612015 # [10] PUSH2(0x2015)
              6001 # [13] PUSH1(1)
                55 # [15] SSTORE
                00 # [16] STOP
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0005 # Code section 0 , 5 bytes
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0002 # max stack:  2
                       # Code section 0 - in=0 out=non-returning height=2
                  6000 # [0] PUSH1(0)
                  6000 # [2] PUSH1(0)
                    f3 # [4] RETURN
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """,
        "incompatible_container_kind");
  }

  @Test
  void invalidReturncontractReturncontract() {
    invalidCode(
        """
            0x # EOF
            ef0001 # Magic and Version ( 1 )
            010004 # Types length ( 4 )
            020001 # Total code sections ( 1 )
              000c # Code section 0 , 12 bytes
            030001 # Total subcontainers ( 1 )
              0032 # Sub container 0, 50 byte
            040000 # Data section length(  0 )
                00 # Terminator (end of header)
                   # Code section 0 types
                00 # 0 inputs\s
                80 # 0 outputs  (Non-returning function)
              0002 # max stack:  2
                   # Code section 0 - in=0 out=non-returning height=2
            612015 # [0] PUSH2(0x2015)
              6001 # [3] PUSH1(1)
                55 # [5] SSTORE
              6000 # [6] PUSH1(0)
              6000 # [8] PUSH1(0)
              ee00 # [10] RETURNCONTRACT(0)
                       # Subcontainer 0 starts here
                ef0001 # Magic and Version ( 1 )
                010004 # Types length ( 4 )
                020001 # Total code sections ( 1 )
                  0006 # Code section 0 , 6 bytes
                030001 # Total subcontainers ( 1 )
                  0014 # Sub container 0, 20 byte
                040000 # Data section length(  0 )   \s
                    00 # Terminator (end of header)
                       # Code section 0 types
                    00 # 0 inputs\s
                    80 # 0 outputs  (Non-returning function)
                  0002 # max stack:  2
                       # Code section 0 - in=0 out=non-returning height=2
                  6000 # [0] PUSH1(0)
                  6000 # [2] PUSH1(0)
                  ee00 # [4] RETURNCONTRACT(0)
                           # Subcontainer 0.0 starts here
                    ef0001 # Magic and Version ( 1 )
                    010004 # Types length ( 4 )
                    020001 # Total code sections ( 1 )
                      0001 # Code section 0 , 1 bytes
                    040000 # Data section length(  0 )       \s
                        00 # Terminator (end of header)
                           # Code section 0 types
                        00 # 0 inputs\s
                        80 # 0 outputs  (Non-returning function)
                      0000 # max stack:  0
                           # Code section 0 - in=0 out=non-returning height=0
                        00 # [0] STOP
                           # Data section (empty)
                           # Subcontainer 0.0 ends
                       # Data section (empty)
                       # Subcontainer 0 ends
                   # Data section (empty)
            """,
        "incompatible_container_kind");
  }

  private static void validCode(final String str) {
    EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    Code code = evm.getCodeUncached(bytesFromPrettyPrint(str));
    assertThat(code.isValid()).isTrue();
  }

  private static void invalidCode(final String str, final String error) {
    EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    Code code = evm.getCodeUncached(bytesFromPrettyPrint(str));
    assertThat(code.isValid()).isFalse();
    assertThat(((CodeInvalid) code).getInvalidReason()).contains(error);
  }

  private static void invalidCode(final String str) {
    EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    Code code = evm.getCodeUncached(bytesFromPrettyPrint(str));
    assertThat(code.isValid()).isFalse();
  }

  private static void invalidCodeForCreation(final String str) {
    EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);
    Code code = evm.getCodeForCreation(bytesFromPrettyPrint(str));
    assertThat(code.isValid()).isFalse();
  }
}

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
package org.hyperledger.besu.evm;

import org.apache.tuweni.bytes.Bytes;

public class EOFTestConstants {

  public static final Bytes INNER_CONTRACT =
      bytesFromPrettyPrint(
          """
                   # EOF
                   ef0001 # Magic and Version ( 1 )
                   010004 # Types length ( 4 )
                   020001 # Total code sections ( 1 )
                     0009 # Code section 0 , 9 bytes
                   030001 # Total subcontainers ( 1 )
                     0014 # Sub container 0, 20 byte
                   040000 # Data section length(  0 )
                       00 # Terminator (end of header)
                          # Code section 0 types
                       00 # 0 inputs\s
                       80 # 0 outputs  (Non-returning function)
                     0003 # max stack:  3
                          # Code section 0
                       5f # [0] PUSH0
                       35 # [1] CALLDATALOAD
                       5f # [2] PUSH0
                       5f # [3] PUSH0
                       a1 # [4] LOG1
                       5f # [5] PUSH0
                       5f # [6] PUSH0
                     ee00 # [7] RETURNCONTRACT(0)
                              # Subcontainer 0 starts here
                       ef0001 # Magic and Version ( 1 )
                       010004 # Types length ( 4 )
                       020001 # Total code sections ( 1 )
                         0001 # Code section 0 , 1 bytes
                       040000 # Data section length(  0 )
                           00 # Terminator (end of header)
                              # Code section 0 types
                           00 # 0 inputs
                           80 # 0 outputs  (Non-returning function)
                         0000 # max stack:  0
                              # Code section 0
                           00 # [0] STOP
                   """);

  public static Bytes EOF_CREATE_CONTRACT =
      bytesFromPrettyPrint(
          String.format(
              """
                      ef0001 # Magic and Version ( 1 )
                      010004 # Types length ( 4 )
                      020001 # Total code sections ( 1 )
                        000e # Code section 0 , 14 bytes
                      030001 # Total subcontainers ( 1 )
                      %04x # Subcontainer 0 size ?
                      040000 # Data section length(  0 )
                          00 # Terminator (end of header)
                             # Code section 0 types
                          00 # 0 inputs\s
                          80 # 0 outputs  (Non-returning function)
                        0004 # max stack:  4
                             # Code section 0
                      61c0de # [0] PUSH2(0xc0de)
                          5f # [3] PUSH0
                          52 # [4] MSTORE
                        6002 # [5] PUSH1(2)
                        601e # [7] PUSH1 30
                          5f # [9] PUSH0
                          5f # [10] PUSH0
                          ec00 # [11] EOFCREATE(0)
                          00 # [13] STOP
                             # Data section (empty)
                          %s # subcontainer
                      """,
              INNER_CONTRACT.size(), INNER_CONTRACT.toUnprefixedHexString()));

  public static Bytes bytesFromPrettyPrint(final String prettyPrint) {
    return Bytes.fromHexString(prettyPrint.replaceAll("#.*?\n", "").replaceAll("\\s", ""));
  }
}

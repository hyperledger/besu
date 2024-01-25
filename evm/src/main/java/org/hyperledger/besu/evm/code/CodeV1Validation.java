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

import static org.hyperledger.besu.evm.internal.Words.readBigEndianI16;
import static org.hyperledger.besu.evm.internal.Words.readBigEndianU16;

import org.hyperledger.besu.evm.operation.CallFOperation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpIfOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpVectorOperation;
import org.hyperledger.besu.evm.operation.RetFOperation;

import java.util.Arrays;
import java.util.BitSet;

import org.apache.tuweni.bytes.Bytes;

/** Code V1 Validation */
public final class CodeV1Validation {

  private CodeV1Validation() {
    // to prevent instantiation
  }

  static final byte INVALID = 0x01;
  static final byte VALID = 0x02;
  static final byte TERMINAL = 0x04;
  static final byte VALID_AND_TERMINAL = VALID | TERMINAL;
  static final byte[] OPCODE_ATTRIBUTES = {
    VALID_AND_TERMINAL, // 0x00	STOP
    VALID, // 0x01 - ADD
    VALID, // 0x02 - MUL
    VALID, // 0x03 - SUB
    VALID, // 0x04 - DIV
    VALID, // 0x05 - SDIV
    VALID, // 0x06 - MOD
    VALID, // 0x07 - SMOD
    VALID, // 0x08 - ADDMOD
    VALID, // 0x09 - MULMOD
    VALID, // 0x0a - EXP
    VALID, // 0x0b - SIGNEXTEND
    INVALID, // 0x0c
    INVALID, // 0x0d
    INVALID, // 0x0e
    INVALID, // 0x0f
    VALID, // 0x10 - LT
    VALID, // 0x11 - GT
    VALID, // 0x12 - SLT
    VALID, // 0x13 - SGT
    VALID, // 0x14 - EQ
    VALID, // 0x15 - ISZERO
    VALID, // 0x16 - AND
    VALID, // 0x17 - OR
    VALID, // 0x18 - XOR
    VALID, // 0x19 - NOT
    VALID, // 0x1a - BYTE
    VALID, // 0x1b - SHL
    VALID, // 0x1c - SHR
    VALID, // 0x1d - SAR
    INVALID, // 0x1e
    INVALID, // 0x1f
    VALID, // 0x20 - SHA3
    INVALID, // 0x21
    INVALID, // 0x22
    INVALID, // 0x23
    INVALID, // 0x24
    INVALID, // 0x25
    INVALID, // 0x26
    INVALID, // 0x27
    INVALID, // 0x28
    INVALID, // 0x29
    INVALID, // 0x2a
    INVALID, // 0x2b
    INVALID, // 0x2c
    INVALID, // 0x2d
    INVALID, // 0x2e
    INVALID, // 0x2f
    VALID, // 0x30 - ADDRESS
    VALID, // 0x31 - BALANCE
    VALID, // 0x32 - ORIGIN
    VALID, // 0x33 - CALLER
    VALID, // 0x34 - CALLVALUE
    VALID, // 0x35 - CALLDATALOAD
    VALID, // 0x36 - CALLDATASIZE
    VALID, // 0x37 - CALLDATACOPY
    VALID, // 0x38 - CODESIZE
    VALID, // 0x39 - CODECOPY
    VALID, // 0x3a - GASPRICE
    VALID, // 0x3b - EXTCODESIZE
    VALID, // 0x3c - EXTCODECOPY
    VALID, // 0x3d - RETURNDATASIZE
    VALID, // 0x3e - RETURNDATACOPY
    VALID, // 0x3f - EXTCODEHASH
    VALID, // 0x40 - BLOCKHASH
    VALID, // 0x41 - COINBASE
    VALID, // 0x42 - TIMESTAMP
    VALID, // 0x43 - NUMBER
    VALID, // 0x44 - PREVRANDAO (née DIFFICULTY)
    VALID, // 0x45 - GASLIMIT
    VALID, // 0x46 - CHAINID
    VALID, // 0x47 - SELFBALANCE
    VALID, // 0x48 - BASEFEE
    INVALID, // 0x49
    INVALID, // 0x4a
    INVALID, // 0x4b
    INVALID, // 0x4c
    INVALID, // 0x4d
    INVALID, // 0x4e
    INVALID, // 0x4f
    VALID, // 0x50 - POP
    VALID, // 0x51 - MLOAD
    VALID, // 0x52 - MSTORE
    VALID, // 0x53 - MSTORE8
    VALID, // 0x54 - SLOAD
    VALID, // 0x55 - SSTORE
    INVALID, // 0x56 - JUMP
    INVALID, // 0x57 - JUMPI
    INVALID, // 0x58 - PC
    VALID, // 0x59 - MSIZE
    VALID, // 0x5a - GAS
    VALID, // 0x5b - NOOOP (née JUMPDEST)
    VALID, // 0X5c - TLOAD
    VALID, // 0X5d - TSTORE
    VALID, // 0X5e - MCOPY
    VALID, // 0X5f - PUSH0
    VALID, // 0x60 - PUSH1
    VALID, // 0x61 - PUSH2
    VALID, // 0x62 - PUSH3
    VALID, // 0x63 - PUSH4
    VALID, // 0x64 - PUSH5
    VALID, // 0x65 - PUSH6
    VALID, // 0x66 - PUSH7
    VALID, // 0x67 - PUSH8
    VALID, // 0x68 - PUSH9
    VALID, // 0x69 - PUSH10
    VALID, // 0x6a - PUSH11
    VALID, // 0x6b - PUSH12
    VALID, // 0x6c - PUSH13
    VALID, // 0x6d - PUSH14
    VALID, // 0x6e - PUSH15
    VALID, // 0x6f - PUSH16
    VALID, // 0x70 - PUSH17
    VALID, // 0x71 - PUSH18
    VALID, // 0x72 - PUSH19
    VALID, // 0x73 - PUSH20
    VALID, // 0x74 - PUSH21
    VALID, // 0x75 - PUSH22
    VALID, // 0x76 - PUSH23
    VALID, // 0x77 - PUSH24
    VALID, // 0x78 - PUSH25
    VALID, // 0x79 - PUSH26
    VALID, // 0x7a - PUSH27
    VALID, // 0x7b - PUSH28
    VALID, // 0x7c - PUSH29
    VALID, // 0x7d - PUSH30
    VALID, // 0x7e - PUSH31
    VALID, // 0x7f - PUSH32
    VALID, // 0x80 - DUP1
    VALID, // 0x81 - DUP2
    VALID, // 0x82 - DUP3
    VALID, // 0x83 - DUP4
    VALID, // 0x84 - DUP5
    VALID, // 0x85 - DUP6
    VALID, // 0x86 - DUP7
    VALID, // 0x87 - DUP8
    VALID, // 0x88 - DUP9
    VALID, // 0x89 - DUP10
    VALID, // 0x8a - DUP11
    VALID, // 0x8b - DUP12
    VALID, // 0x8c - DUP13
    VALID, // 0x8d - DUP14
    VALID, // 0x8e - DUP15
    VALID, // 0x8f - DUP16
    VALID, // 0x90 - SWAP1
    VALID, // 0x91 - SWAP2
    VALID, // 0x92 - SWAP3
    VALID, // 0x93 - SWAP4
    VALID, // 0x94 - SWAP5
    VALID, // 0x95 - SWAP6
    VALID, // 0x96 - SWAP7
    VALID, // 0x97 - SWAP8
    VALID, // 0x98 - SWAP9
    VALID, // 0x99 - SWAP10
    VALID, // 0x9a - SWAP11
    VALID, // 0x9b - SWAP12
    VALID, // 0x9c - SWAP13
    VALID, // 0x9d - SWAP14
    VALID, // 0x9e - SWAP15
    VALID, // 0x9f - SWAP16
    VALID, // 0xa0 - LOG0
    VALID, // 0xa1 - LOG1
    VALID, // 0xa2 - LOG2
    VALID, // 0xa3 - LOG3
    VALID, // 0xa4 - LOG4
    INVALID, // 0xa5
    INVALID, // 0xa6
    INVALID, // 0xa7
    INVALID, // 0xa8
    INVALID, // 0xa9
    INVALID, // 0xaa
    INVALID, // 0xab
    INVALID, // 0xac
    INVALID, // 0xad
    INVALID, // 0xae
    INVALID, // 0xaf
    INVALID, // 0xb0
    INVALID, // 0xb1
    INVALID, // 0xb2
    INVALID, // 0xb3
    INVALID, // 0xb4
    INVALID, // 0xb5
    INVALID, // 0xb6
    INVALID, // 0xb7
    INVALID, // 0xb8
    INVALID, // 0xb9
    INVALID, // 0xba
    INVALID, // 0xbb
    INVALID, // 0xbc
    INVALID, // 0xbd
    INVALID, // 0xbe
    INVALID, // 0xbf
    INVALID, // 0xc0
    INVALID, // 0xc1
    INVALID, // 0xc2
    INVALID, // 0xc3
    INVALID, // 0xc4
    INVALID, // 0xc5
    INVALID, // 0xc6
    INVALID, // 0xc7
    INVALID, // 0xc8
    INVALID, // 0xc9
    INVALID, // 0xca
    INVALID, // 0xcb
    INVALID, // 0xcc
    INVALID, // 0xcd
    INVALID, // 0xce
    INVALID, // 0xcf
    INVALID, // 0xd0
    INVALID, // 0xd1
    INVALID, // 0xd2
    INVALID, // 0xd3
    INVALID, // 0xd4
    INVALID, // 0xd5
    INVALID, // 0xd6
    INVALID, // 0xd7
    INVALID, // 0xd8
    INVALID, // 0xd9
    INVALID, // 0xda
    INVALID, // 0xdb
    INVALID, // 0xdc
    INVALID, // 0xdd
    INVALID, // 0xde
    INVALID, // 0xef
    VALID_AND_TERMINAL, // 0xe0 - RJUMP
    VALID, // 0xe1 - RJUMPI
    VALID, // 0xe2 - RJUMPV
    VALID, // 0xe3 - CALLF
    VALID_AND_TERMINAL, // 0xe4 - RETF
    INVALID, // 0xe5
    INVALID, // 0xe6
    INVALID, // 0xe7
    INVALID, // 0xe8
    INVALID, // 0xe9
    INVALID, // 0xea
    INVALID, // 0xeb
    INVALID, // 0xec
    INVALID, // 0xed
    INVALID, // 0xee
    INVALID, // 0xef
    VALID, // 0xf0 - CREATE
    VALID, // 0xf1 - CALL
    INVALID, // 0xf2 - CALLCODE
    VALID_AND_TERMINAL, // 0xf3 - RETURN
    VALID, // 0xf4 - DELEGATECALL
    VALID, // 0xf5 - CREATE2
    INVALID, // 0xf6
    INVALID, // 0xf7
    INVALID, // 0xf8
    INVALID, // 0xf9
    VALID, // 0xfa - STATICCALL
    INVALID, // 0xfb
    INVALID, // 0xfc
    VALID_AND_TERMINAL, // 0xfd - REVERT
    VALID_AND_TERMINAL, // 0xfe - INVALID
    INVALID, // 0xff - SELFDESTRUCT
  };
  static final int MAX_STACK_HEIGHT = 1024;
  // java17 move to record
  // [0] - stack input consumed
  // [1] - stack outputs added
  // [2] - PC advance
  static final byte[][] OPCODE_STACK_VALIDATION = {
    {0, 0, -1}, // 0x00 - STOP
    {2, 1, 1}, // 0x01 - ADD
    {2, 1, 1}, // 0x02 - MUL
    {2, 1, 1}, // 0x03 - SUB
    {2, 1, 1}, // 0x04 - DIV
    {2, 1, 1}, // 0x05 - SDIV
    {2, 1, 1}, // 0x06 - MOD
    {2, 1, 1}, // 0x07 - SMOD
    {3, 1, 1}, // 0x08 - ADDMOD
    {3, 1, 1}, // 0x09 - MULMOD
    {2, 1, 1}, // 0x0a - EXP
    {2, 1, 1}, // 0x0b - SIGNEXTEND
    {0, 0, 0}, // 0x0c
    {0, 0, 0}, // 0x0d
    {0, 0, 0}, // 0x0e
    {0, 0, 0}, // 0x0f
    {2, 1, 1}, // 0x10 - LT
    {2, 1, 1}, // 0x11 - GT
    {2, 1, 1}, // 0x12 - SLT
    {2, 1, 1}, // 0x13 - SGT
    {2, 1, 1}, // 0x14 - EQ
    {1, 1, 1}, // 0x15 - ISZERO
    {2, 1, 1}, // 0x16 - AND
    {2, 1, 1}, // 0x17 - OR
    {2, 1, 1}, // 0x18 - XOR
    {1, 1, 1}, // 0x19 - NOT
    {2, 1, 1}, // 0x1a - BYTE
    {2, 1, 1}, // 0x1b - SHL
    {2, 1, 1}, // 0x1c - SHR
    {2, 1, 1}, // 0x1d - SAR
    {0, 0, 0}, // 0x1e
    {0, 0, 0}, // 0x1f
    {2, 1, 1}, // 0x20 - SHA3
    {0, 0, 0}, // 0x21
    {0, 0, 0}, // 0x22
    {0, 0, 0}, // 0x23
    {0, 0, 0}, // 0x24
    {0, 0, 0}, // 0x25
    {0, 0, 0}, // 0x26
    {0, 0, 0}, // 0x27
    {0, 0, 0}, // 0x28
    {0, 0, 0}, // 0x29
    {0, 0, 0}, // 0x2a
    {0, 0, 0}, // 0x2b
    {0, 0, 0}, // 0x2c
    {0, 0, 0}, // 0x2d
    {0, 0, 0}, // 0x2e
    {0, 0, 0}, // 0x2f
    {0, 1, 1}, // 0x30 - ADDRESS
    {1, 1, 1}, // 0x31 - BALANCE
    {0, 1, 1}, // 0x32 - ORIGIN
    {0, 1, 1}, // 0x33 - CALLER
    {0, 1, 1}, // 0x34 - CALLVALUE
    {1, 1, 1}, // 0x35 - CALLDATALOAD
    {0, 1, 1}, // 0x36 - CALLDATASIZE
    {3, 0, 1}, // 0x37 - CALLDATACOPY
    {0, 1, 1}, // 0x38 - CODESIZE
    {3, 0, 1}, // 0x39 - CODECOPY
    {0, 1, 1}, // 0x3a - GASPRICE
    {1, 1, 1}, // 0x3b - EXTCODESIZE
    {4, 0, 1}, // 0x3c - EXTCODECOPY
    {0, 1, 1}, // 0x3d - RETURNDATASIZE
    {3, 0, 1}, // 0x3e - RETURNDATACOPY
    {1, 1, 1}, // 0x3f - EXTCODEHASH
    {1, 1, 1}, // 0x40 - BLOCKHASH
    {0, 1, 1}, // 0x41 - COINBASE
    {0, 1, 1}, // 0x42 - TIMESTAMP
    {0, 1, 1}, // 0x43 - NUMBER
    {0, 1, 1}, // 0x44 - PREVRANDAO (née DIFFICULTY)
    {0, 1, 1}, // 0x45 - GASLIMIT
    {0, 1, 1}, // 0x46 - CHAINID
    {0, 1, 1}, // 0x47 - SELFBALANCE
    {0, 1, 1}, // 0x48 - BASEFEE
    {0, 0, 0}, // 0x49
    {0, 0, 0}, // 0x4a
    {0, 0, 0}, // 0x4b
    {0, 0, 0}, // 0x4c
    {0, 0, 0}, // 0x4d
    {0, 0, 0}, // 0x4e
    {0, 0, 0}, // 0x4f
    {1, 0, 1}, // 0x50 - POP
    {1, 1, 1}, // 0x51 - MLOAD
    {2, 0, 1}, // 0x52 - MSTORE
    {2, 0, 1}, // 0x53 - MSTORE8
    {1, 1, 1}, // 0x54 - SLOAD
    {2, 0, 1}, // 0x55 - SSTORE
    {0, 0, 0}, // 0x56 - JUMP
    {0, 0, 0}, // 0x57 - JUMPI
    {0, 0, 0}, // 0x58 - PC
    {0, 1, 1}, // 0x59 - MSIZE
    {0, 1, 1}, // 0x5a - GAS
    {0, 0, 1}, // 0x5b - NOOP (née JUMPDEST)
    {1, 1, 1}, // 0x5c - TLOAD
    {2, 0, 1}, // 0x5d - TSTORE
    {4, 0, 1}, // 0x5e - MCOPY
    {0, 1, 1}, // 0x5f - PUSH0
    {0, 1, 2}, // 0x60 - PUSH1
    {0, 1, 3}, // 0x61 - PUSH2
    {0, 1, 4}, // 0x62 - PUSH3
    {0, 1, 5}, // 0x63 - PUSH4
    {0, 1, 6}, // 0x64 - PUSH5
    {0, 1, 7}, // 0x65 - PUSH6
    {0, 1, 8}, // 0x66 - PUSH7
    {0, 1, 9}, // 0x67 - PUSH8
    {0, 1, 10}, // 0x68 - PUSH9
    {0, 1, 11}, // 0x69 - PUSH10
    {0, 1, 12}, // 0x6a - PUSH11
    {0, 1, 13}, // 0x6b - PUSH12
    {0, 1, 14}, // 0x6c - PUSH13
    {0, 1, 15}, // 0x6d - PUSH14
    {0, 1, 16}, // 0x6e - PUSH15
    {0, 1, 17}, // 0x6f - PUSH16
    {0, 1, 18}, // 0x70 - PUSH17
    {0, 1, 19}, // 0x71 - PUSH18
    {0, 1, 20}, // 0x72 - PUSH19
    {0, 1, 21}, // 0x73 - PUSH20
    {0, 1, 22}, // 0x74 - PUSH21
    {0, 1, 23}, // 0x75 - PUSH22
    {0, 1, 24}, // 0x76 - PUSH23
    {0, 1, 25}, // 0x77 - PUSH24
    {0, 1, 26}, // 0x78 - PUSH25
    {0, 1, 27}, // 0x79 - PUSH26
    {0, 1, 28}, // 0x7a - PUSH27
    {0, 1, 29}, // 0x7b - PUSH28
    {0, 1, 30}, // 0x7c - PUSH29
    {0, 1, 31}, // 0x7d - PUSH30
    {0, 1, 32}, // 0x7e - PUSH31
    {0, 1, 33}, // 0x7f - PUSH32
    {1, 2, 1}, // 0x80 - DUP1
    {2, 3, 1}, // 0x81 - DUP2
    {3, 4, 1}, // 0x82 - DUP3
    {4, 5, 1}, // 0x83 - DUP4
    {5, 6, 1}, // 0x84 - DUP5
    {6, 7, 1}, // 0x85 - DUP6
    {7, 8, 1}, // 0x86 - DUP7
    {8, 9, 1}, // 0x87 - DUP8
    {9, 10, 1}, // 0x88 - DUP9
    {10, 11, 1}, // 0x89 - DUP10
    {11, 12, 1}, // 0x8a - DUP11
    {12, 13, 1}, // 0x8b - DUP12
    {13, 14, 1}, // 0x8c - DUP13
    {14, 15, 1}, // 0x8d - DUP14
    {15, 16, 1}, // 0x8e - DUP15
    {16, 17, 1}, // 0x8f - DUP16
    {2, 2, 1}, // 0x90 - SWAP1
    {3, 3, 1}, // 0x91 - SWAP2
    {4, 4, 1}, // 0x92 - SWAP3
    {5, 5, 1}, // 0x93 - SWAP4
    {6, 6, 1}, // 0x94 - SWAP5
    {7, 7, 1}, // 0x95 - SWAP6
    {8, 8, 1}, // 0x96 - SWAP7
    {9, 9, 1}, // 0x97 - SWAP8
    {10, 10, 1}, // 0x98 - SWAP9
    {11, 11, 1}, // 0x99 - SWAP10
    {12, 12, 1}, // 0x9a - SWAP11
    {13, 13, 1}, // 0x9b - SWAP12
    {14, 14, 1}, // 0x9c - SWAP13
    {15, 15, 1}, // 0x9d - SWAP14
    {16, 16, 1}, // 0x9e - SWAP15
    {17, 17, 1}, // 0x9f - SWAP16
    {2, 0, 1}, // 0xa0 - LOG0
    {3, 0, 1}, // 0xa1 - LOG1
    {4, 0, 1}, // 0xa2 - LOG2
    {5, 0, 1}, // 0xa3 - LOG3
    {6, 0, 1}, // 0xa4 - LOG4
    {0, 0, 0}, // 0xa5
    {0, 0, 0}, // 0xa6
    {0, 0, 0}, // 0xa7
    {0, 0, 0}, // 0xa8
    {0, 0, 0}, // 0xa9
    {0, 0, 0}, // 0xaa
    {0, 0, 0}, // 0xab
    {0, 0, 0}, // 0xac
    {0, 0, 0}, // 0xad
    {0, 0, 0}, // 0xae
    {0, 0, 0}, // 0xaf
    {0, 0, 0}, // 0xb0
    {0, 0, 0}, // 0xb1
    {0, 0, 0}, // 0xb2
    {0, 0, 0}, // 0xb3
    {0, 0, 0}, // 0xb4
    {0, 0, 0}, // 0xb5
    {0, 0, 0}, // 0xb6
    {0, 0, 0}, // 0xb7
    {0, 0, 0}, // 0xb8
    {0, 0, 0}, // 0xb9
    {0, 0, 0}, // 0xba
    {0, 0, 0}, // 0xbb
    {0, 0, 0}, // 0xbc
    {0, 0, 0}, // 0xbd
    {0, 0, 0}, // 0xbe
    {0, 0, 0}, // 0xbf
    {0, 0, 0}, // 0xc0
    {0, 0, 0}, // 0xc1
    {0, 0, 0}, // 0xc2
    {0, 0, 0}, // 0xc3
    {0, 0, 0}, // 0xc4
    {0, 0, 0}, // 0xc5
    {0, 0, 0}, // 0xc6
    {0, 0, 0}, // 0xc7
    {0, 0, 0}, // 0xc8
    {0, 0, 0}, // 0xc9
    {0, 0, 0}, // 0xca
    {0, 0, 0}, // 0xcb
    {0, 0, 0}, // 0xcc
    {0, 0, 0}, // 0xcd
    {0, 0, 0}, // 0xce
    {0, 0, 0}, // 0xcf
    {0, 0, 0}, // 0xd0
    {0, 0, 0}, // 0xd1
    {0, 0, 0}, // 0xd2
    {0, 0, 0}, // 0xd3
    {0, 0, 0}, // 0xd4
    {0, 0, 0}, // 0xd5
    {0, 0, 0}, // 0xd6
    {0, 0, 0}, // 0xd7
    {0, 0, 0}, // 0xd8
    {0, 0, 0}, // 0xd9
    {0, 0, 0}, // 0xda
    {0, 0, 0}, // 0xdb
    {0, 0, 0}, // 0xdc
    {0, 0, 0}, // 0xdd
    {0, 0, 0}, // 0xde
    {0, 0, 0}, // 0xef
    {0, 0, -3}, // 0xe0 - RJUMP
    {1, 0, 3}, // 0xe1 - RJUMPI
    {1, 0, 2}, // 0xe2 - RJUMPV
    {0, 0, 3}, // 0xe3 - CALLF
    {0, 0, -1}, // 0xe4 - RETF
    {0, 0, 0}, // 0xe5 - JUMPF
    {0, 0, 0}, // 0xe6
    {0, 0, 0}, // 0xe7
    {0, 0, 0}, // 0xe8
    {0, 0, 0}, // 0xe9
    {0, 0, 0}, // 0xea
    {0, 0, 0}, // 0xeb
    {0, 0, 0}, // 0xec
    {0, 0, 0}, // 0xed
    {0, 0, 0}, // 0xee
    {0, 0, 0}, // 0xef
    {3, 1, 1}, // 0xf0 - CREATE
    {7, 1, 1}, // 0xf1 - CALL
    {0, 0, 0}, // 0xf2 - CALLCODE
    {2, 0, -1}, // 0xf3 - RETURN
    {6, 1, 1}, // 0xf4 - DELEGATECALL
    {4, 1, 1}, // 0xf5 - CREATE2
    {0, 0, 0}, // 0xf6
    {0, 0, 0}, // 0xf7
    {0, 0, 0}, // 0xf8
    {0, 0, 0}, // 0xf9
    {6, 1, 1}, // 0xfa - STATICCALL
    {0, 0, 0}, // 0xfb
    {0, 0, 0}, // 0xfc
    {2, 0, -1}, // 0xfd - REVERT
    {0, 0, -1}, // 0xfe - INVALID
    {0, 0, 0}, // 0xff - SELFDESTRUCT
  };

  /**
   * Validate Code
   *
   * @param eofLayout The EOF Layout
   * @return validation code, null otherwise.
   */
  public static String validateCode(final EOFLayout eofLayout) {
    int sectionCount = eofLayout.getCodeSectionCount();
    for (int i = 0; i < sectionCount; i++) {
      CodeSection cs = eofLayout.getCodeSection(i);
      var validation =
          CodeV1Validation.validateCode(
              eofLayout.getContainer().slice(cs.getEntryPoint(), cs.getLength()), sectionCount);
      if (validation != null) {
        return validation;
      }
    }
    return null;
  }

  /**
   * validates the code section
   *
   * @param code the code section code
   * @return null if valid, otherwise a string containing an error reason.
   */
  static String validateCode(final Bytes code, final int sectionCount) {
    final int size = code.size();
    final BitSet rjumpdests = new BitSet(size);
    final BitSet immediates = new BitSet(size);
    final byte[] rawCode = code.toArrayUnsafe();
    int attribute = INVALID;
    int pos = 0;
    while (pos < size) {
      final int operationNum = rawCode[pos] & 0xff;
      attribute = OPCODE_ATTRIBUTES[operationNum];
      if ((attribute & INVALID) == INVALID) {
        // undefined instruction
        return String.format("Invalid Instruction 0x%02x", operationNum);
      }
      pos += 1;
      int pcPostInstruction = pos;
      if (operationNum > PushOperation.PUSH_BASE && operationNum <= PushOperation.PUSH_MAX) {
        final int multiByteDataLen = operationNum - PushOperation.PUSH_BASE;
        pcPostInstruction += multiByteDataLen;
      } else if (operationNum == RelativeJumpOperation.OPCODE
          || operationNum == RelativeJumpIfOperation.OPCODE) {
        if (pos + 2 > size) {
          return "Truncated relative jump offset";
        }
        pcPostInstruction += 2;
        final int offset = readBigEndianI16(pos, rawCode);
        final int rjumpdest = pcPostInstruction + offset;
        if (rjumpdest < 0 || rjumpdest >= size) {
          return "Relative jump destination out of bounds";
        }
        rjumpdests.set(rjumpdest);
      } else if (operationNum == RelativeJumpVectorOperation.OPCODE) {
        if (pos + 1 > size) {
          return "Truncated jump table";
        }
        final int jumpTableSize = RelativeJumpVectorOperation.getVectorSize(code, pos);
        if (jumpTableSize == 0) {
          return "Empty jump table";
        }
        pcPostInstruction += 1 + 2 * jumpTableSize;
        if (pcPostInstruction > size) {
          return "Truncated jump table";
        }
        for (int offsetPos = pos + 1; offsetPos < pcPostInstruction; offsetPos += 2) {
          final int offset = readBigEndianI16(offsetPos, rawCode);
          final int rjumpdest = pcPostInstruction + offset;
          if (rjumpdest < 0 || rjumpdest >= size) {
            return "Relative jump destination out of bounds";
          }
          rjumpdests.set(rjumpdest);
        }
      } else if (operationNum == CallFOperation.OPCODE) {
        if (pos + 2 > size) {
          return "Truncated CALLF";
        }
        int section = readBigEndianU16(pos, rawCode);
        if (section >= sectionCount) {
          return "CALLF to non-existent section - " + Integer.toHexString(section);
        }
        pcPostInstruction += 2;
      }
      immediates.set(pos, pcPostInstruction);
      pos = pcPostInstruction;
    }
    if ((attribute & TERMINAL) != TERMINAL) {
      return "No terminating instruction";
    }
    if (rjumpdests.intersects(immediates)) {
      return "Relative jump destinations targets invalid immediate data";
    }
    return null;
  }

  static String validateStack(final EOFLayout eofLayout) {
    for (int i = 0; i < eofLayout.getCodeSectionCount(); i++) {
      var validation = CodeV1Validation.validateStack(i, eofLayout);
      if (validation != null) {
        return validation;
      }
    }
    return null;
  }

  /**
   * Validates the stack heights per <a href="https://eips.ethereum.org/EIPS/eip-5450">EIP-5450</a>.
   *
   * <p>This presumes code validation has already been performed, so there are no RJUMPS into
   * immediates as well as no immediates falling off of the end of code sections.
   *
   * @param codeSectionToValidate The index of code to validate in the code sections
   * @param eofLayout The EOF container to validate
   * @return null if valid, otherwise an error string providing the validation error.
   */
  public static String validateStack(final int codeSectionToValidate, final EOFLayout eofLayout) {
    try {
      CodeSection toValidate = eofLayout.getCodeSection(codeSectionToValidate);
      byte[] code =
          eofLayout.getContainer().slice(toValidate.entryPoint, toValidate.length).toArrayUnsafe();
      int codeLength = code.length;
      int[] stackHeights = new int[codeLength];
      Arrays.fill(stackHeights, -1);

      int thisWork = 0;
      int maxWork = 1;
      int[][] workList = new int[codeLength][2];

      int initialStackHeight = toValidate.getInputs();
      int maxStackHeight = initialStackHeight;
      stackHeights[0] = initialStackHeight;
      workList[0][1] = initialStackHeight;
      int unusedBytes = codeLength;

      while (thisWork < maxWork) {
        int currentPC = workList[thisWork][0];
        int currentStackHeight = workList[thisWork][1];
        if (thisWork > 0 && stackHeights[currentPC] >= 0) {
          // we've been here, validate the jump is what is expected
          if (stackHeights[currentPC] != currentStackHeight) {
            return String.format(
                "Jump into code stack height (%d) does not match previous value (%d)",
                stackHeights[currentPC], currentStackHeight);
          } else {
            thisWork++;
            continue;
          }
        } else {
          stackHeights[currentPC] = currentStackHeight;
        }

        while (currentPC < codeLength) {
          int thisOp = code[currentPC] & 0xff;

          byte[] stackInfo = OPCODE_STACK_VALIDATION[thisOp];
          int stackInputs;
          int stackOutputs;
          int pcAdvance = stackInfo[2];
          if (thisOp == CallFOperation.OPCODE) {
            int section = readBigEndianU16(currentPC + 1, code);
            stackInputs = eofLayout.getCodeSection(section).getInputs();
            stackOutputs = eofLayout.getCodeSection(section).getOutputs();
          } else {
            stackInputs = stackInfo[0];
            stackOutputs = stackInfo[1];
          }

          if (stackInputs > currentStackHeight) {
            return String.format(
                "Operation 0x%02X requires stack of %d but only has %d items",
                thisOp, stackInputs, currentStackHeight);
          }

          currentStackHeight = currentStackHeight - stackInputs + stackOutputs;
          if (currentStackHeight > MAX_STACK_HEIGHT) {
            return "Stack height exceeds 1024";
          }

          maxStackHeight = Math.max(maxStackHeight, currentStackHeight);

          if (thisOp == RelativeJumpOperation.OPCODE || thisOp == RelativeJumpIfOperation.OPCODE) {
            // no `& 0xff` on high byte because this is one case we want sign extension
            int rvalue = readBigEndianI16(currentPC + 1, code);
            workList[maxWork] = new int[] {currentPC + rvalue + 3, currentStackHeight};
            maxWork++;
          } else if (thisOp == RelativeJumpVectorOperation.OPCODE) {
            int immediateDataSize = (code[currentPC + 1] & 0xff) * 2;
            unusedBytes -= immediateDataSize;
            int tableEnd = immediateDataSize + currentPC + 2;
            for (int i = currentPC + 2; i < tableEnd; i += 2) {
              int rvalue = readBigEndianI16(i, code);
              workList[maxWork] = new int[] {tableEnd + rvalue, currentStackHeight};
              maxWork++;
            }
            currentPC = tableEnd - 2;
          } else if (thisOp == RetFOperation.OPCODE) {
            int returnStackItems = toValidate.getOutputs();
            if (currentStackHeight != returnStackItems) {
              return String.format(
                  "Section return (RETF) calculated height 0x%x does not match configured height 0x%x",
                  currentStackHeight, returnStackItems);
            }
          }
          if (pcAdvance < 0) {
            unusedBytes += pcAdvance;
            break;
          } else if (pcAdvance == 0) {
            return String.format("Invalid Instruction 0x%02x", thisOp);
          }

          currentPC += pcAdvance;
          if (currentPC >= stackHeights.length) {
            return String.format(
                "Dangling immediate argument for opcode 0x%x at PC %d in code section %d.",
                currentStackHeight, codeLength - pcAdvance, codeSectionToValidate);
          }
          stackHeights[currentPC] = currentStackHeight;
          unusedBytes -= pcAdvance;
        }

        thisWork++;
      }
      if (maxStackHeight != toValidate.maxStackHeight) {
        return String.format(
            "Calculated max stack height (%d) does not match reported stack height (%d)",
            maxStackHeight, toValidate.maxStackHeight);
      }
      if (unusedBytes != 0) {
        return String.format("Dead code detected in section %d", codeSectionToValidate);
      }

      return null;
    } catch (RuntimeException re) {
      re.printStackTrace();
      return "Internal Exception " + re.getMessage();
    }
  }
}

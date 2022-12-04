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

import org.hyperledger.besu.evm.operation.PushOperation;

import org.apache.tuweni.bytes.Bytes;

class OpcodesV1 {
  static final byte INVALID = 0x01;
  static final byte VALID = 0x02;
  static final byte TERMINAL = 0x04;
  static final byte JUMPDEST = 0x08;

  static final byte VALID_AND_TERMINAL = VALID | TERMINAL;
  static final byte VALID_AND_JUMPDEST = VALID | JUMPDEST;

  private static final byte[] opcodeAttributes = {
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
    VALID, // 0x44 - DIFFICULTY
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
    VALID, // 0x56 - JUMP
    VALID, // 0x57 - JUMPI
    VALID, // 0x58 - PC
    VALID, // 0x59 - MSIZE
    VALID, // 0x5a - GAS
    VALID_AND_JUMPDEST, // 0x5b - JUMPDEST
    INVALID, // 0X5c
    INVALID, // 0X5d
    INVALID, // 0X5e
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
    INVALID, // 0xe0
    INVALID, // 0xe1
    INVALID, // 0xe2
    INVALID, // 0xe3
    INVALID, // 0xe4
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
    VALID, // 0xf2 - CALLCODE
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
    VALID_AND_TERMINAL, // 0xff - SELFDESTRUCT
  };

  private OpcodesV1() {
    // static utility class
  }

  static long[] validateAndCalculateJumpDests(final Bytes code) {
    final int size = code.size();
    final long[] bitmap = new long[(size >> 6) + 1];
    final byte[] rawCode = code.toArrayUnsafe();
    final int length = rawCode.length;
    int attribute = INVALID;
    for (int i = 0; i < length; ) {
      long thisEntry = 0L;
      final int entryPos = i >> 6;
      final int max = Math.min(64, length - (entryPos << 6));
      int j = i & 0x3f;
      for (; j < max; i++, j++) {
        final int operationNum = rawCode[i] & 0xff;
        attribute = opcodeAttributes[operationNum];
        if ((attribute & INVALID) == INVALID) {
          return null;
        } else if ((attribute & JUMPDEST) == JUMPDEST) {
          thisEntry |= 1L << j;
        } else if (operationNum > PushOperation.PUSH_BASE
            && operationNum <= PushOperation.PUSH_MAX) {
          final int multiByteDataLen = operationNum - PushOperation.PUSH_BASE;
          j += multiByteDataLen;
          i += multiByteDataLen;
        }
      }
      bitmap[entryPos] = thisEntry;
    }
    if ((attribute & TERMINAL) != TERMINAL) {
      return null;
    }
    return bitmap;
  }
}

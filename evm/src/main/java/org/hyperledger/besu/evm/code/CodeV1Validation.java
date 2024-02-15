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
import org.hyperledger.besu.evm.operation.DataLoadNOperation;
import org.hyperledger.besu.evm.operation.DupNOperation;
import org.hyperledger.besu.evm.operation.JumpFOperation;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpIfOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpOperation;
import org.hyperledger.besu.evm.operation.RelativeJumpVectorOperation;
import org.hyperledger.besu.evm.operation.RetFOperation;
import org.hyperledger.besu.evm.operation.SwapNOperation;

import java.util.Arrays;
import java.util.BitSet;

import org.apache.tuweni.bytes.Bytes;

/** Code V1 Validation */
public final class CodeV1Validation {

  record OpcodeInfo(
      String name,
      int opcode,
      boolean valid,
      boolean terminal,
      int inputs,
      int outputs,
      int stackDelta,
      int pcAdvance) {
    static OpcodeInfo unallocatedOpcode(final int opcode) {
      return new OpcodeInfo("-", opcode, false, false, 0, 0, 0, 0);
    }

    static OpcodeInfo invalidOpcode(final String name, final int opcode) {
      return new OpcodeInfo(name, opcode, false, false, 0, 0, 0, 0);
    }

    static OpcodeInfo terminalOpcode(
        final String name,
        final int opcode,
        final int inputs,
        final int outputs,
        final int pcAdvance) {
      return new OpcodeInfo(name, opcode, true, true, inputs, outputs, outputs - inputs, pcAdvance);
    }

    static OpcodeInfo validOpcode(
        final String name,
        final int opcode,
        final int inputs,
        final int outputs,
        final int pcAdvance) {
      return new OpcodeInfo(
          name, opcode, true, false, inputs, outputs, outputs - inputs, pcAdvance);
    }
  }

  private CodeV1Validation() {
    // to prevent instantiation
  }

  static final int MAX_STACK_HEIGHT = 1024;
  // java17 move to record
  // [0] - stack input consumed
  // [1] - stack outputs added
  // [2] - PC advance

  static final OpcodeInfo[] OPCODE_INFO = {
    OpcodeInfo.terminalOpcode("STOP", 0x00, 0, 0, -1),
    OpcodeInfo.validOpcode("ADD", 0x01, 2, 1, 1),
    OpcodeInfo.validOpcode("MUL", 0x02, 2, 1, 1),
    OpcodeInfo.validOpcode("SUB", 0x03, 2, 1, 1),
    OpcodeInfo.validOpcode("DIV", 0x04, 2, 1, 1),
    OpcodeInfo.validOpcode("SDIV", 0x05, 2, 1, 1),
    OpcodeInfo.validOpcode("MOD", 0x06, 2, 1, 1),
    OpcodeInfo.validOpcode("SMOD", 0x07, 2, 1, 1),
    OpcodeInfo.validOpcode("ADDMOD", 0x08, 3, 1, 1),
    OpcodeInfo.validOpcode("MULMOD", 0x09, 3, 1, 1),
    OpcodeInfo.validOpcode("EXP", 0x0a, 2, 1, 1),
    OpcodeInfo.validOpcode("SIGNEXTEND", 0x0b, 2, 1, 1),
    OpcodeInfo.unallocatedOpcode(0x0c),
    OpcodeInfo.unallocatedOpcode(0x0d),
    OpcodeInfo.unallocatedOpcode(0x0e),
    OpcodeInfo.unallocatedOpcode(0x0f),
    OpcodeInfo.validOpcode("LT", 0x10, 2, 1, 1),
    OpcodeInfo.validOpcode("GT", 0x11, 2, 1, 1),
    OpcodeInfo.validOpcode("SLT", 0x12, 2, 1, 1),
    OpcodeInfo.validOpcode("SGT", 0x13, 2, 1, 1),
    OpcodeInfo.validOpcode("EQ", 0x14, 2, 1, 1),
    OpcodeInfo.validOpcode("ISZERO", 0x15, 1, 1, 1),
    OpcodeInfo.validOpcode("AND", 0x16, 2, 1, 1),
    OpcodeInfo.validOpcode("OR", 0x17, 2, 1, 1),
    OpcodeInfo.validOpcode("XOR", 0x18, 2, 1, 1),
    OpcodeInfo.validOpcode("NOT", 0x19, 1, 1, 1),
    OpcodeInfo.validOpcode("BYTE", 0x1a, 2, 1, 1),
    OpcodeInfo.validOpcode("SHL", 0x1b, 2, 1, 1),
    OpcodeInfo.validOpcode("SHR", 0x1c, 2, 1, 1),
    OpcodeInfo.validOpcode("SAR", 0x1d, 2, 1, 1),
    OpcodeInfo.unallocatedOpcode(0x1e),
    OpcodeInfo.unallocatedOpcode(0x1f),
    OpcodeInfo.validOpcode("SHA3", 0x20, 2, 1, 1),
    OpcodeInfo.unallocatedOpcode(0x21),
    OpcodeInfo.unallocatedOpcode(0x22),
    OpcodeInfo.unallocatedOpcode(0x23),
    OpcodeInfo.unallocatedOpcode(0x24),
    OpcodeInfo.unallocatedOpcode(0x25),
    OpcodeInfo.unallocatedOpcode(0x26),
    OpcodeInfo.unallocatedOpcode(0x27),
    OpcodeInfo.unallocatedOpcode(0x28),
    OpcodeInfo.unallocatedOpcode(0x29),
    OpcodeInfo.unallocatedOpcode(0x2a),
    OpcodeInfo.unallocatedOpcode(0x2b),
    OpcodeInfo.unallocatedOpcode(0x2c),
    OpcodeInfo.unallocatedOpcode(0x2d),
    OpcodeInfo.unallocatedOpcode(0x2e),
    OpcodeInfo.unallocatedOpcode(0x2f),
    OpcodeInfo.validOpcode("ADDRESS", 0x30, 0, 1, 1),
    OpcodeInfo.validOpcode("BALANCE", 0x31, 1, 1, 1),
    OpcodeInfo.validOpcode("ORIGIN", 0x32, 0, 1, 1),
    OpcodeInfo.validOpcode("CALLER", 0x33, 0, 1, 1),
    OpcodeInfo.validOpcode("CALLVALUE", 0x34, 0, 1, 1),
    OpcodeInfo.validOpcode("CALLDATALOAD", 0x35, 1, 1, 1),
    OpcodeInfo.validOpcode("CALLDATASIZE", 0x36, 0, 1, 1),
    OpcodeInfo.validOpcode("CALLDATACOPY", 0x37, 3, 0, 1),
    OpcodeInfo.validOpcode("CODESIZE", 0x38, 0, 1, 1),
    OpcodeInfo.validOpcode("CODECOPY", 0x39, 3, 0, 1),
    OpcodeInfo.validOpcode("GASPRICE", 0x3a, 0, 1, 1),
    OpcodeInfo.validOpcode("EXTCODESIZE", 0x3b, 1, 1, 1),
    OpcodeInfo.validOpcode("EXTCODECOPY", 0x3c, 4, 0, 1),
    OpcodeInfo.validOpcode("RETURNDATASIZE", 0x3d, 0, 1, 1),
    OpcodeInfo.validOpcode("RETURNDATACOPY", 0x3e, 3, 0, 1),
    OpcodeInfo.validOpcode("EXTCODEHASH", 0x3f, 1, 1, 1),
    OpcodeInfo.validOpcode("BLOCKHASH", 0x40, 1, 1, 1),
    OpcodeInfo.validOpcode("COINBASE", 0x41, 0, 1, 1),
    OpcodeInfo.validOpcode("TIMESTAMP", 0x42, 0, 1, 1),
    OpcodeInfo.validOpcode("NUMBER", 0x43, 0, 1, 1),
    OpcodeInfo.validOpcode("PREVRANDAO", 0x44, 0, 1, 1), // was DIFFICULTY
    OpcodeInfo.validOpcode("GASLIMIT", 0x45, 0, 1, 1),
    OpcodeInfo.validOpcode("CHAINID", 0x46, 0, 1, 1),
    OpcodeInfo.validOpcode("SELFBALANCE", 0x47, 0, 1, 1),
    OpcodeInfo.validOpcode("BASEFEE", 0x48, 0, 1, 1),
    OpcodeInfo.validOpcode("BLOBAHASH", 0x49, 1, 1, 1),
    OpcodeInfo.validOpcode("BLOBBASEFEE", 0x4a, 0, 1, 1),
    OpcodeInfo.unallocatedOpcode(0x4b),
    OpcodeInfo.unallocatedOpcode(0x4c),
    OpcodeInfo.unallocatedOpcode(0x4d),
    OpcodeInfo.unallocatedOpcode(0x4e),
    OpcodeInfo.unallocatedOpcode(0x4f),
    OpcodeInfo.validOpcode("POP", 0x50, 1, 0, 1),
    OpcodeInfo.validOpcode("MLOAD", 0x51, 1, 1, 1),
    OpcodeInfo.validOpcode("MSTORE", 0x52, 2, 0, 1),
    OpcodeInfo.validOpcode("MSTORE8", 0x53, 2, 0, 1),
    OpcodeInfo.validOpcode("SLOAD", 0x54, 1, 1, 1),
    OpcodeInfo.validOpcode("SSTORE", 0x55, 2, 0, 1),
    OpcodeInfo.invalidOpcode("JUMP", 0x56),
    OpcodeInfo.invalidOpcode("JUMPI", 0x57),
    OpcodeInfo.invalidOpcode("PC", 0x58),
    OpcodeInfo.validOpcode("MSIZE", 0x59, 0, 1, 1),
    OpcodeInfo.validOpcode("GAS", 0x5a, 0, 1, 1),
    OpcodeInfo.validOpcode("NOOP", 0x5b, 0, 0, 1), // was JUMPDEST
    OpcodeInfo.validOpcode("TLOAD", 0x5c, 1, 1, 1),
    OpcodeInfo.validOpcode("TSTORE", 0x5d, 2, 0, 1),
    OpcodeInfo.validOpcode("MCOPY", 0x5e, 4, 0, 1),
    OpcodeInfo.validOpcode("PUSH0", 0x5f, 0, 1, 1),
    OpcodeInfo.validOpcode("PUSH1", 0x60, 0, 1, 2),
    OpcodeInfo.validOpcode("PUSH2", 0x61, 0, 1, 3),
    OpcodeInfo.validOpcode("PUSH3", 0x62, 0, 1, 4),
    OpcodeInfo.validOpcode("PUSH4", 0x63, 0, 1, 5),
    OpcodeInfo.validOpcode("PUSH5", 0x64, 0, 1, 6),
    OpcodeInfo.validOpcode("PUSH6", 0x65, 0, 1, 7),
    OpcodeInfo.validOpcode("PUSH7", 0x66, 0, 1, 8),
    OpcodeInfo.validOpcode("PUSH8", 0x67, 0, 1, 9),
    OpcodeInfo.validOpcode("PUSH9", 0x68, 0, 1, 10),
    OpcodeInfo.validOpcode("PUSH10", 0x69, 0, 1, 11),
    OpcodeInfo.validOpcode("PUSH11", 0x6a, 0, 1, 12),
    OpcodeInfo.validOpcode("PUSH12", 0x6b, 0, 1, 13),
    OpcodeInfo.validOpcode("PUSH13", 0x6c, 0, 1, 14),
    OpcodeInfo.validOpcode("PUSH14", 0x6d, 0, 1, 15),
    OpcodeInfo.validOpcode("PUSH15", 0x6e, 0, 1, 16),
    OpcodeInfo.validOpcode("PUSH16", 0x6f, 0, 1, 17),
    OpcodeInfo.validOpcode("PUSH17", 0x70, 0, 1, 18),
    OpcodeInfo.validOpcode("PUSH18", 0x71, 0, 1, 19),
    OpcodeInfo.validOpcode("PUSH19", 0x72, 0, 1, 20),
    OpcodeInfo.validOpcode("PUSH20", 0x73, 0, 1, 21),
    OpcodeInfo.validOpcode("PUSH21", 0x74, 0, 1, 22),
    OpcodeInfo.validOpcode("PUSH22", 0x75, 0, 1, 23),
    OpcodeInfo.validOpcode("PUSH23", 0x76, 0, 1, 24),
    OpcodeInfo.validOpcode("PUSH24", 0x77, 0, 1, 25),
    OpcodeInfo.validOpcode("PUSH25", 0x78, 0, 1, 26),
    OpcodeInfo.validOpcode("PUSH26", 0x79, 0, 1, 27),
    OpcodeInfo.validOpcode("PUSH27", 0x7a, 0, 1, 28),
    OpcodeInfo.validOpcode("PUSH28", 0x7b, 0, 1, 29),
    OpcodeInfo.validOpcode("PUSH29", 0x7c, 0, 1, 30),
    OpcodeInfo.validOpcode("PUSH30", 0x7d, 0, 1, 31),
    OpcodeInfo.validOpcode("PUSH31", 0x7e, 0, 1, 32),
    OpcodeInfo.validOpcode("PUSH32", 0x7f, 0, 1, 33),
    OpcodeInfo.validOpcode("DUP1", 0x80, 1, 2, 1),
    OpcodeInfo.validOpcode("DUP2", 0x81, 2, 3, 1),
    OpcodeInfo.validOpcode("DUP3", 0x82, 3, 4, 1),
    OpcodeInfo.validOpcode("DUP4", 0x83, 4, 5, 1),
    OpcodeInfo.validOpcode("DUP5", 0x84, 5, 6, 1),
    OpcodeInfo.validOpcode("DUP6", 0x85, 6, 7, 1),
    OpcodeInfo.validOpcode("DUP7", 0x86, 7, 8, 1),
    OpcodeInfo.validOpcode("DUP8", 0x87, 8, 9, 1),
    OpcodeInfo.validOpcode("DUP9", 0x88, 9, 10, 1),
    OpcodeInfo.validOpcode("DUP10", 0x89, 10, 11, 1),
    OpcodeInfo.validOpcode("DUP11", 0x8a, 11, 12, 1),
    OpcodeInfo.validOpcode("DUP12", 0x8b, 12, 13, 1),
    OpcodeInfo.validOpcode("DUP13", 0x8c, 13, 14, 1),
    OpcodeInfo.validOpcode("DUP14", 0x8d, 14, 15, 1),
    OpcodeInfo.validOpcode("DUP15", 0x8e, 15, 16, 1),
    OpcodeInfo.validOpcode("DUP16", 0x8f, 16, 17, 1),
    OpcodeInfo.validOpcode("SWAP1", 0x90, 2, 2, 1),
    OpcodeInfo.validOpcode("SWAP2", 0x91, 3, 3, 1),
    OpcodeInfo.validOpcode("SWAP3", 0x92, 4, 4, 1),
    OpcodeInfo.validOpcode("SWAP4", 0x93, 5, 5, 1),
    OpcodeInfo.validOpcode("SWAP5", 0x94, 6, 6, 1),
    OpcodeInfo.validOpcode("SWAP6", 0x95, 7, 7, 1),
    OpcodeInfo.validOpcode("SWAP7", 0x96, 8, 8, 1),
    OpcodeInfo.validOpcode("SWAP8", 0x97, 9, 9, 1),
    OpcodeInfo.validOpcode("SWAP9", 0x98, 10, 10, 1),
    OpcodeInfo.validOpcode("SWAP10", 0x99, 11, 11, 1),
    OpcodeInfo.validOpcode("SWAP11", 0x9a, 12, 12, 1),
    OpcodeInfo.validOpcode("SWAP12", 0x9b, 13, 13, 1),
    OpcodeInfo.validOpcode("SWAP13", 0x9c, 14, 14, 1),
    OpcodeInfo.validOpcode("SWAP14", 0x9d, 15, 15, 1),
    OpcodeInfo.validOpcode("SWAP15", 0x9e, 16, 16, 1),
    OpcodeInfo.validOpcode("SWAP16", 0x9f, 17, 17, 1),
    OpcodeInfo.validOpcode("LOG0", 0xa0, 2, 0, 1),
    OpcodeInfo.validOpcode("LOG1", 0xa1, 3, 0, 1),
    OpcodeInfo.validOpcode("LOG2", 0xa2, 4, 0, 1),
    OpcodeInfo.validOpcode("LOG3", 0xa3, 5, 0, 1),
    OpcodeInfo.validOpcode("LOG4", 0xa4, 6, 0, 1),
    OpcodeInfo.unallocatedOpcode(0xa5),
    OpcodeInfo.unallocatedOpcode(0xa6),
    OpcodeInfo.unallocatedOpcode(0xa7),
    OpcodeInfo.unallocatedOpcode(0xa8),
    OpcodeInfo.unallocatedOpcode(0xa9),
    OpcodeInfo.unallocatedOpcode(0xaa),
    OpcodeInfo.unallocatedOpcode(0xab),
    OpcodeInfo.unallocatedOpcode(0xac),
    OpcodeInfo.unallocatedOpcode(0xad),
    OpcodeInfo.unallocatedOpcode(0xae),
    OpcodeInfo.unallocatedOpcode(0xaf),
    OpcodeInfo.unallocatedOpcode(0xb0),
    OpcodeInfo.unallocatedOpcode(0xb1),
    OpcodeInfo.unallocatedOpcode(0xb2),
    OpcodeInfo.unallocatedOpcode(0xb3),
    OpcodeInfo.unallocatedOpcode(0xb4),
    OpcodeInfo.unallocatedOpcode(0xb5),
    OpcodeInfo.unallocatedOpcode(0xb6),
    OpcodeInfo.unallocatedOpcode(0xb7),
    OpcodeInfo.unallocatedOpcode(0xb8),
    OpcodeInfo.unallocatedOpcode(0xb9),
    OpcodeInfo.unallocatedOpcode(0xba),
    OpcodeInfo.unallocatedOpcode(0xbb),
    OpcodeInfo.unallocatedOpcode(0xbc),
    OpcodeInfo.unallocatedOpcode(0xbd),
    OpcodeInfo.unallocatedOpcode(0xbe),
    OpcodeInfo.unallocatedOpcode(0xbf),
    OpcodeInfo.unallocatedOpcode(0xc0),
    OpcodeInfo.unallocatedOpcode(0xc1),
    OpcodeInfo.unallocatedOpcode(0xc2),
    OpcodeInfo.unallocatedOpcode(0xc3),
    OpcodeInfo.unallocatedOpcode(0xc4),
    OpcodeInfo.unallocatedOpcode(0xc5),
    OpcodeInfo.unallocatedOpcode(0xc6),
    OpcodeInfo.unallocatedOpcode(0xc7),
    OpcodeInfo.unallocatedOpcode(0xc8),
    OpcodeInfo.unallocatedOpcode(0xc9),
    OpcodeInfo.unallocatedOpcode(0xca),
    OpcodeInfo.unallocatedOpcode(0xcb),
    OpcodeInfo.unallocatedOpcode(0xcc),
    OpcodeInfo.unallocatedOpcode(0xcd),
    OpcodeInfo.unallocatedOpcode(0xce),
    OpcodeInfo.unallocatedOpcode(0xcf),
    OpcodeInfo.validOpcode("DATALOAD", 0xd0, 1, 1, 1),
    OpcodeInfo.validOpcode("DATALOADN", 0xd1, 0, 1, 3),
    OpcodeInfo.validOpcode("DATASIZE", 0xd2, 0, 1, 1),
    OpcodeInfo.validOpcode("DATACOPY", 0xd3, 3, 0, 1),
    OpcodeInfo.unallocatedOpcode(0xd4),
    OpcodeInfo.unallocatedOpcode(0xd5),
    OpcodeInfo.unallocatedOpcode(0xd6),
    OpcodeInfo.unallocatedOpcode(0xd7),
    OpcodeInfo.unallocatedOpcode(0xd8),
    OpcodeInfo.unallocatedOpcode(0xd9),
    OpcodeInfo.unallocatedOpcode(0xda),
    OpcodeInfo.unallocatedOpcode(0xdb),
    OpcodeInfo.unallocatedOpcode(0xdc),
    OpcodeInfo.unallocatedOpcode(0xdd),
    OpcodeInfo.unallocatedOpcode(0xde),
    OpcodeInfo.unallocatedOpcode(0xdf),
    OpcodeInfo.terminalOpcode("RJUMP", 0xe0, 0, 0, -3),
    OpcodeInfo.validOpcode("RJUMPI", 0xe1, 1, 0, 3),
    OpcodeInfo.validOpcode("RJUMPV", 0xe2, 1, 0, 2),
    OpcodeInfo.validOpcode("CALLF", 0xe3, 0, 0, 3),
    OpcodeInfo.terminalOpcode("RETF", 0xe4, 0, 0, -1),
    OpcodeInfo.terminalOpcode("JUMPF", 0xe5, 0, 0, -3),
    OpcodeInfo.validOpcode("DUPN", 0xe6, 0, 1, 2),
    OpcodeInfo.validOpcode("SWAPN", 0xe7, 0, 0, 2),
    OpcodeInfo.validOpcode("EXCHANGE", 0xe8, 0, 0, 2),
    OpcodeInfo.unallocatedOpcode(0xe9),
    OpcodeInfo.unallocatedOpcode(0xea),
    OpcodeInfo.unallocatedOpcode(0xeb),
    OpcodeInfo.validOpcode("CREATE3", 0xec, 4, 1, 2),
    OpcodeInfo.validOpcode("CREATE4", 0xed, 5, 1, 1),
    OpcodeInfo.validOpcode("RETURNCONTRACT", 0xee, 2, 1, -2),
    OpcodeInfo.unallocatedOpcode(0xef),
    OpcodeInfo.validOpcode("CREATE", 0xf0, 3, 1, 1),
    OpcodeInfo.validOpcode("CALL", 0xf1, 7, 1, 1),
    OpcodeInfo.invalidOpcode("CALLCODE", 0xf2),
    OpcodeInfo.terminalOpcode("RETURN", 0xf3, 2, 0, -1),
    OpcodeInfo.validOpcode("DELEGATECALL", 0xf4, 6, 1, 1),
    OpcodeInfo.validOpcode("CREATE2", 0xf5, 4, 1, 1),
    OpcodeInfo.unallocatedOpcode(0xf6),
    OpcodeInfo.unallocatedOpcode(0xf7),
    OpcodeInfo.validOpcode("CALL2", 0xf8, 4, 1, 1),
    OpcodeInfo.validOpcode("DELEGATECALL", 0xf9, 3, 1, 1),
    OpcodeInfo.validOpcode("STATICCALL", 0xfa, 6, 1, 1),
    OpcodeInfo.validOpcode("STATICCALL2", 0xfb, 3, 1, 1),
    OpcodeInfo.unallocatedOpcode(0xfc),
    OpcodeInfo.terminalOpcode("REVERT", 0xfd, 2, 0, -1),
    OpcodeInfo.terminalOpcode("0xef", 0xfe, 0, 0, -1),
    OpcodeInfo.invalidOpcode("SELFDESTRUCT", 0xff),
  };

  /**
   * Validate Code
   *
   * @param eofLayout The EOF Layout
   * @return validation code, null otherwise.
   */
  public static String validateCode(final EOFLayout eofLayout) {
    if (!eofLayout.isValid()) {
      return "Invalid EOF container - " + eofLayout.invalidReason();
    }
    for (CodeSection cs : eofLayout.codeSections()) {
      var validation =
          CodeV1Validation.validateCode(
              eofLayout.container().slice(cs.getEntryPoint(), cs.getLength()), cs, eofLayout);
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
  static String validateCode(
      final Bytes code, final CodeSection thisCodeSection, final EOFLayout eofLayout) {
    final int size = code.size();
    final BitSet rjumpdests = new BitSet(size);
    final BitSet immediates = new BitSet(size);
    final byte[] rawCode = code.toArrayUnsafe();
    OpcodeInfo opcodeInfo = OPCODE_INFO[0xfe];
    int pos = 0;
    boolean hasReturningOpcode = false;
    while (pos < size) {
      final int operationNum = rawCode[pos] & 0xff;
      opcodeInfo = OPCODE_INFO[operationNum];
      if (!opcodeInfo.valid()) {
        // undefined instruction
        return String.format("Invalid Instruction 0x%02x", operationNum);
      }
      pos += 1;
      int pcPostInstruction = pos;
      switch (operationNum) {
        case PushOperation.PUSH_BASE,
            PushOperation.PUSH_BASE + 1,
            PushOperation.PUSH_BASE + 2,
            PushOperation.PUSH_BASE + 3,
            PushOperation.PUSH_BASE + 4,
            PushOperation.PUSH_BASE + 5,
            PushOperation.PUSH_BASE + 6,
            PushOperation.PUSH_BASE + 7,
            PushOperation.PUSH_BASE + 8,
            PushOperation.PUSH_BASE + 9,
            PushOperation.PUSH_BASE + 10,
            PushOperation.PUSH_BASE + 11,
            PushOperation.PUSH_BASE + 12,
            PushOperation.PUSH_BASE + 13,
            PushOperation.PUSH_BASE + 14,
            PushOperation.PUSH_BASE + 15,
            PushOperation.PUSH_BASE + 16,
            PushOperation.PUSH_BASE + 17,
            PushOperation.PUSH_BASE + 18,
            PushOperation.PUSH_BASE + 19,
            PushOperation.PUSH_BASE + 20,
            PushOperation.PUSH_BASE + 21,
            PushOperation.PUSH_BASE + 22,
            PushOperation.PUSH_BASE + 23,
            PushOperation.PUSH_BASE + 24,
            PushOperation.PUSH_BASE + 25,
            PushOperation.PUSH_BASE + 26,
            PushOperation.PUSH_BASE + 27,
            PushOperation.PUSH_BASE + 28,
            PushOperation.PUSH_BASE + 29,
            PushOperation.PUSH_BASE + 30,
            PushOperation.PUSH_BASE + 31,
            PushOperation.PUSH_BASE + 32:
          final int multiByteDataLen = operationNum - PushOperation.PUSH_BASE;
          pcPostInstruction += multiByteDataLen;
          break;
        case DataLoadNOperation.OPCODE:
          if (pos + 2 > size) {
            return "Truncated DataLoadN offset";
          }
          pcPostInstruction += 2;
          final int dataLoadOffset = readBigEndianU16(pos, rawCode);
          // only verfy the last byte of the load is within the minimum data
          if (dataLoadOffset > eofLayout.dataLength() - 32) {
            return "DataLoadN loads data past minimum data length";
          }
          break;
        case RelativeJumpOperation.OPCODE, RelativeJumpIfOperation.OPCODE:
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
          break;
        case RelativeJumpVectorOperation.OPCODE:
          pcPostInstruction += 1;
          if (pcPostInstruction > size) {
            return "Truncated jump table";
          }
          int jumpBasis = pcPostInstruction;
          final int jumpTableSize = RelativeJumpVectorOperation.getVectorSize(code, pos);
          pcPostInstruction += 2 * jumpTableSize;
          if (pcPostInstruction > size) {
            return "Truncated jump table";
          }
          for (int offsetPos = jumpBasis; offsetPos < pcPostInstruction; offsetPos += 2) {
            final int rjumpvOffset = readBigEndianI16(offsetPos, rawCode);
            final int rjumpvDest = pcPostInstruction + rjumpvOffset;
            if (rjumpvDest < 0 || rjumpvDest >= size) {
              return "Relative jump destination out of bounds";
            }
            rjumpdests.set(rjumpvDest);
          }
          break;
        case CallFOperation.OPCODE:
          if (pos + 2 > size) {
            return "Truncated CALLF";
          }
          int section = readBigEndianU16(pos, rawCode);
          if (section >= eofLayout.getCodeSectionCount()) {
            return "CALLF to non-existent section - " + Integer.toHexString(section);
          }
          if (!eofLayout.getCodeSection(section).returning) {
            return "CALLF to non-returning section - " + Integer.toHexString(section);
          }
          pcPostInstruction += 2;
          break;
        case RetFOperation.OPCODE:
          hasReturningOpcode = true;
          break;
        case JumpFOperation.OPCODE:
          if (pos + 2 > size) {
            return "Truncated JUMPF";
          }
          int targetSection = readBigEndianU16(pos, rawCode);
          if (targetSection >= eofLayout.getCodeSectionCount()) {
            return "JUMPF to non-existent section - " + Integer.toHexString(targetSection);
          }
          CodeSection targetCodeSection = eofLayout.getCodeSection(targetSection);
          if (targetCodeSection.isReturning()
              && thisCodeSection.getOutputs() < targetCodeSection.getOutputs()) {
            return String.format(
                "JUMPF targeting a returning code section %2x with more outputs %d than current section's outputs %d",
                targetSection, targetCodeSection.getOutputs(), thisCodeSection.getOutputs());
          }
          hasReturningOpcode |= eofLayout.getCodeSection(targetSection).isReturning();
          pcPostInstruction += 2;
          break;
        default:
          // no validation operations
          break;
      }
      immediates.set(pos, pcPostInstruction);
      pos = pcPostInstruction;
    }
    if (thisCodeSection.isReturning() != hasReturningOpcode) {
      return thisCodeSection.isReturning()
          ? "No RETF or qualifying JUMPF"
          : "Non-returing section has RETF or JUMPF into returning section";
    }
    if (!opcodeInfo.terminal) {
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
    if (!eofLayout.isValid()) {
      return "EOF Layout invalid - " + eofLayout.invalidReason();
    }
    try {
      CodeSection toValidate = eofLayout.getCodeSection(codeSectionToValidate);
      byte[] code =
          eofLayout.container().slice(toValidate.entryPoint, toValidate.length).toArrayUnsafe();
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

          OpcodeInfo opcodeInfo = OPCODE_INFO[thisOp];
          int stackInputs;
          int stackOutputs;
          int sectionStackUsed;
          int pcAdvance = opcodeInfo.pcAdvance();
          if (thisOp == CallFOperation.OPCODE) {
            int section = readBigEndianU16(currentPC + 1, code);
            CodeSection codeSection = eofLayout.getCodeSection(section);
            stackInputs = codeSection.getInputs();
            stackOutputs = codeSection.getOutputs();
            sectionStackUsed = codeSection.getMaxStackHeight();
          } else if (thisOp == DupNOperation.OPCODE) {
            int depth = code[currentPC + 1] & 0xff;
            stackInputs = depth + 1;
            stackOutputs = depth + 2;
            sectionStackUsed = 0;
          } else if (thisOp == SwapNOperation.OPCODE) {
            int depth = code[currentPC + 1] & 0xff;
            stackInputs = depth + 2;
            stackOutputs = depth + 2;
            sectionStackUsed = 0;
          } else {
            stackInputs = opcodeInfo.inputs();
            stackOutputs = opcodeInfo.outputs();
            sectionStackUsed = 0;
          }

          if (stackInputs > currentStackHeight) {
            return String.format(
                "Operation 0x%02X requires stack of %d but only has %d items",
                thisOp, stackInputs, currentStackHeight);
          }

          currentStackHeight = currentStackHeight - stackInputs + stackOutputs;
          if (currentStackHeight + sectionStackUsed - stackOutputs > MAX_STACK_HEIGHT) {
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
            unusedBytes -= immediateDataSize + 2;
            int tableEnd = immediateDataSize + currentPC + 4;
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
                thisOp, currentPC - pcAdvance, codeSectionToValidate);
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

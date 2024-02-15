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
 */

package org.hyperledger.besu.evm.operation;

public enum OpCode {
  STOP(0x00, "STOP"),
  ADD(0x01, "ADD"),
  MUL(0x02, "MUL"),
  SUB(0x03, "SUB"),
  DIV(0x04, "DIV"),
  SDIV(0x05, "SDIV"),
  MOD(0x06, "MOD"),
  SMOD(0x07, "SMOD"),
  ADDMOD(0x08, "ADDMOD"),
  MULMOD(0x09, "MULMOD"),
  EXP(0x0a, "EXP"),
  SIGNEXTEND(0x0b, "SIGNEXTEND"),
  LT(0x10, "LT"),
  GT(0x11, "GT"),
  SLT(0x12, "SLT"),
  SGT(0x13, "SGT"),
  EQ(0x14, "EQ"),
  ISZERO(0x15, "ISZERO"),
  AND(0x16, "AND"),
  OR(0x17, "OR"),
  XOR(0x18, "XOR"),
  NOT(0x19, "NOT"),
  BYTE(0x1a, "BYTE"),
  SHL(0x1b, "SHL"),
  SHR(0x1c, "SHR"),
  SAR(0x1d, "SAR"),
  SHA3(0x20, "SHA3"),
  ADDRESS(0x30, "ADDRESS"),
  BALANCE(0x31, "BALANCE"),
  ORIGIN(0x32, "ORIGIN"),
  CALLER(0x33, "CALLER"),
  CALLVALUE(0x34, "CALLVALUE"),
  CALLDATALOAD(0x35, "CALLDATALOAD"),
  CALLDATASIZE(0x36, "CALLDATASIZE"),
  CALLDATACOPY(0x37, "CALLDATACOPY"),
  CODESIZE(0x38, "CODESIZE"),
  CODECOPY(0x39, "CODECOPY"),
  GASPRICE(0x3a, "GASPRICE"),
  EXTCODESIZE(0x3b, "EXTCODESIZE"),
  EXTCODECOPY(0x3c, "EXTCODECOPY"),
  RETURNDATASIZE(0x3d, "RETURNDATASIZE"),
  RETURNDATACOPY(0x3e, "RETURNDATACOPY"),
  EXTCODEHASH(0x3f, "EXTCODEHASH"),
  BLOCKHASH(0x40, "BLOCKHASH"),
  COINBASE(0x41, "COINBASE"),
  TIMESTAMP(0x42, "TIMESTAMP"),
  NUMBER(0x43, "NUMBER"),
  DIFFICULTY(0x44, "DIFFICULTY"),
  GASLIMIT(0x45, "GASLIMIT"),
  POP(0x50, "POP"),
  MLOAD(0x51, "MLOAD"),
  MSTORE(0x52, "MSTORE"),
  MSTORE8(0x53, "MSTORE8"),
  SLOAD(0x54, "SLOAD"),
  SSTORE(0x55, "SSTORE"),
  JUMP(0x56, "JUMP"),
  JUMPI(0x57, "JUMPI"),
  PC(0x58, "PC"),
  MSIZE(0x59, "MSIZE"),
  GAS(0x5a, "GAS"),
  JUMPDEST(0x5b, "JUMPDEST"),
  PUSH1(0x60, "PUSH1"),
  PUSH2(0x61, "PUSH2"),
  PUSH3(0x62, "PUSH3"),
  PUSH4(0x63, "PUSH4"),
  PUSH5(0x64, "PUSH5"),
  PUSH6(0x65, "PUSH6"),
  PUSH7(0x66, "PUSH7"),
  PUSH8(0x67, "PUSH8"),
  PUSH9(0x68, "PUSH9"),
  PUSH10(0x69, "PUSH10"),
  PUSH11(0x6a, "PUSH11"),
  PUSH12(0x6b, "PUSH12"),
  PUSH13(0x6c, "PUSH13"),
  PUSH14(0x6d, "PUSH14"),
  PUSH15(0x6e, "PUSH15"),
  PUSH16(0x6f, "PUSH16"),
  PUSH17(0x70, "PUSH17"),
  PUSH18(0x71, "PUSH18"),
  PUSH19(0x72, "PUSH19"),
  PUSH20(0x73, "PUSH20"),
  PUSH21(0x74, "PUSH21"),
  PUSH22(0x75, "PUSH22"),
  PUSH23(0x76, "PUSH23"),
  PUSH24(0x77, "PUSH24"),
  PUSH25(0x78, "PUSH25"),
  PUSH26(0x79, "PUSH26"),
  PUSH27(0x7a, "PUSH27"),
  PUSH28(0x7b, "PUSH28"),
  PUSH29(0x7c, "PUSH29"),
  PUSH30(0x7d, "PUSH30"),
  PUSH31(0x7e, "PUSH31"),
  PUSH32(0x7f, "PUSH32"),
  DUP1(0x80, "DUP1"),
  DUP2(0x81, "DUP2"),
  DUP3(0x82, "DUP3"),
  DUP4(0x83, "DUP4"),
  DUP5(0x84, "DUP5"),
  DUP6(0x85, "DUP6"),
  DUP7(0x86, "DUP7"),
  DUP8(0x87, "DUP8"),
  DUP9(0x88, "DUP9"),
  DUP10(0x89, "DUP10"),
  DUP11(0x8a, "DUP11"),
  DUP12(0x8b, "DUP12"),
  DUP13(0x8c, "DUP13"),
  DUP14(0x8d, "DUP14"),
  DUP15(0x8e, "DUP15"),
  DUP16(0x8f, "DUP16"),
  SWAP1(0x90, "SWAP1"),
  SWAP2(0x91, "SWAP2"),
  SWAP3(0x92, "SWAP3"),
  SWAP4(0x93, "SWAP4"),
  SWAP5(0x94, "SWAP5"),
  SWAP6(0x95, "SWAP6"),
  SWAP7(0x96, "SWAP7"),
  SWAP8(0x97, "SWAP8"),
  SWAP9(0x98, "SWAP9"),
  SWAP10(0x99, "SWAP10"),
  SWAP11(0x9a, "SWAP11"),
  SWAP12(0x9b, "SWAP12"),
  SWAP13(0x9c, "SWAP13"),
  SWAP14(0x9d, "SWAP14"),
  SWAP15(0x9e, "SWAP15"),
  SWAP16(0x9f, "SWAP16"),
  LOG0(0xa0, "LOG0"),
  LOG1(0xa1, "LOG1"),
  LOG2(0xa2, "LOG2"),
  LOG3(0xa3, "LOG3"),
  LOG4(0xa4, "LOG4"),
  CREATE(0xf0, "CREATE"),
  CALL(0xf1, "CALL"),
  CALLCODE(0xf2, "CALLCODE"),
  RETURN(0xf3, "RETURN"),
  DELEGATECALL(0xf4, "DELEGATECALL"),
  CREATE2(0xf5, "CREATE2"),
  STATICCALL(0xfa, "STATICCALL"),
  REVERT(0xfd, "REVERT"),
  INVALID(0xfe, "INVALID"),
  SELFDESTRUCT(0xff, "SELFDESTRUCT");

  private final int opcodeNumber;
  private final String opcodeName;

  OpCode(final int opcodeNumber, final String opcodeName) {
    this.opcodeNumber = opcodeNumber;
    this.opcodeName = opcodeName;
  }

  public int getOpcodeNumber() {
    return opcodeNumber;
  }

  public String getOpcodeName() {
    return opcodeName;
  }
}

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
package org.hyperledger.besu.evm.testutils;

import org.hyperledger.besu.datatypes.Address;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/** ByteCodeBuilder is a helper class to aid in construction of EVM bytecode for tests */
public class ByteCodeBuilder {

  public enum Operation {
    ADD("01"),
    CALL("f1"),
    CALLDATALOAD("35"),
    CALLER("33"),
    DELEGATECALL("f4"),
    EQ("14"),
    JUMPDEST("5b"),
    JUMPI("57"),
    MLOAD("51"),
    MSTORE("52"),
    PUSH("60"),
    RETURN("f3"),
    RETURNDATACOPY("3e"),
    REVERT("fd"),
    STATICCALL("fa"),
    TLOAD("5c"),
    TSTORE("5d");

    private final String value;

    Operation(final String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  final StringBuilder byteCode = new StringBuilder("0x");

  @Override
  public String toString() {
    return byteCode.toString();
  }

  /**
   * Operation to push the value to the EVM stack
   *
   * @param value Integer value to push
   * @return this
   */
  public ByteCodeBuilder push(final int value) {
    return this.push(UInt256.valueOf(value));
  }

  /**
   * Operation to push the value to the EVM stack
   *
   * @param value Bytes value to push
   * @return this
   */
  public ByteCodeBuilder push(final Bytes value) {
    UInt256 pushSize = UInt256.valueOf(Bytes.fromHexString("0x60").toInt() + value.size() - 1);
    String byteString = standardizeByteString(pushSize);
    byteCode.append(byteString);
    byteCode.append(value.toUnprefixedHexString());
    return this;
  }

  /**
   * Operation to push the value to the EVM stack
   *
   * @param value UInt256 value to push
   * @return this
   */
  public ByteCodeBuilder push(final UInt256 value) {
    String byteString = standardizeByteString(value);
    byteCode.append(Operation.PUSH);
    byteCode.append(byteString);
    return this;
  }

  private String standardizeByteString(final UInt256 value) {
    String byteString = value.toMinimalBytes().toUnprefixedHexString();
    if (byteString.length() % 2 == 1) {
      byteString = "0" + byteString;
    } else if (byteString.length() == 0) {
      byteString = "00";
    }
    return byteString;
  }

  /**
   * Operation to store the value in transient storage
   *
   * @param key location in the contract's storage
   * @param value value to store
   * @return this
   */
  public ByteCodeBuilder tstore(final int key, final int value) {
    this.push(value).push(key);
    byteCode.append(Operation.TSTORE);
    return this;
  }

  /**
   * Operation to load a value from transient storage
   *
   * @param key location in the contract's storage
   * @return this
   */
  public ByteCodeBuilder tload(final int key) {
    this.push(key);
    byteCode.append(Operation.TLOAD);
    return this;
  }

  /**
   * Operation to store the current stack value to memory
   *
   * @param key location in the contract's memory
   * @return this
   */
  public ByteCodeBuilder dataOnStackToMemory(final int key) {
    this.push(key).op(Operation.MSTORE);
    return this;
  }

  /**
   * Operations to return the value at a given memory position
   *
   * @param size size of the returned value
   * @param key location in memory of the returned value
   * @return this
   */
  public ByteCodeBuilder returnValueAtMemory(final int size, final int key) {
    this.push(size).push(key).op(Operation.RETURN);
    return this;
  }

  /**
   * Operation to call another smart contract
   *
   * @param callType CALL, STATICCALL, DELEGATECALL
   * @param address contract's address
   * @param gasLimit gas limit for the nested call
   * @return this
   */
  public ByteCodeBuilder call(
      final Operation callType, final Address address, final UInt256 gasLimit) {
    callTypeCheck(callType);
    this.push(0)
        .push(0)
        .push(0)
        .push(0)
        .push(0)
        .push(Bytes.fromHexString(address.toHexString()))
        .push(gasLimit.toMinimalBytes())
        .op(callType);
    return this;
  }

  private static void callTypeCheck(final Operation callType) {

    if (callType != Operation.CALL
        && callType != Operation.STATICCALL
        && callType != Operation.DELEGATECALL) {
      throw new UnsupportedOperationException("callType not supported: " + callType);
    }
  }

  /**
   * Operation to call another smart contract with input Assumes input data is 32 bytes, already in
   * memory at position 0
   *
   * @param callType CALL, STATICCALL, DELEGATECALL
   * @param address contract's address
   * @param gasLimit gas limit for the nested call
   * @return this
   */
  public ByteCodeBuilder callWithInput(
      final Operation callType, final Address address, final UInt256 gasLimit) {
    return callWithInput(callType, address, gasLimit, null);
  }

  /**
   * Operation to call another smart contract with input Assumes input data is 32 bytes
   *
   * @param callType CALL, STATICCALL, DELEGATECALL
   * @param address contract's address
   * @param gasLimit gas limit for the nested call
   * @param input input to the nested call, if null pulls from memory at pos 0
   * @return this
   */
  public ByteCodeBuilder callWithInput(
      final Operation callType,
      final Address address,
      final UInt256 gasLimit,
      final UInt256 input) {
    callTypeCheck(callType);
    if (input != null) {
      this.push(input);
      this.push(0);
      this.op(Operation.MSTORE);
    } else {
      // Use top of stack as input
      dataOnStackToMemory(0);
    }

    this.push(0)
        .push(0)
        .push(32)
        .push(0)
        .push(0)
        .push(Bytes.fromHexString(address.toHexString()))
        .push(gasLimit.toMinimalBytes())
        .op(callType);
    return this;
  }

  /**
   * Operations to return the results of the nested call
   *
   * @return this
   */
  public ByteCodeBuilder returnInnerCallResults() {
    this.push(32).push(0).push(0);
    byteCode.append(Operation.RETURNDATACOPY);
    this.returnValueAtMemory(32, 0);
    return this;
  }

  /**
   * Operations to check if the current caller is the address provided
   *
   * @param caller address of the expected caller
   * @return this
   */
  public ByteCodeBuilder callerIs(final Address caller) {
    byteCode.append(Operation.CALLER);
    this.push(Bytes.fromHexString(caller.toHexString()));
    byteCode.append(Operation.EQ);
    return this;
  }

  /**
   * Operations for a conditional jump
   *
   * @param dest location to jump to if true
   * @return this
   */
  public ByteCodeBuilder conditionalJump(final int dest) {
    this.push(dest);
    byteCode.append(Operation.JUMPI);
    return this;
  }

  /**
   * Adds the specified operation to the set
   *
   * @param operation operation to add
   * @return this
   */
  public ByteCodeBuilder op(final Operation operation) {
    byteCode.append(operation.toString());
    return this;
  }
}

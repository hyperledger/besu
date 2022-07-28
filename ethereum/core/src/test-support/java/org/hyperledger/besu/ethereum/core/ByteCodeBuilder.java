/*
 * Copyright Besu Contributors
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
package org.hyperledger.besu.ethereum.core;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

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
        TLOAD("b3"),
        TSTORE("b4");

        private final String value;

        Operation(final String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
    StringBuilder byteCode = new StringBuilder("0x");

    @Override
    public String toString() {
        return byteCode.toString();
    }
    public Bytes toBytes() {
        return Bytes.fromHexString(byteCode.toString());
    }

    public ByteCodeBuilder push(final int value) {
        return this.push(UInt256.valueOf(value));
    }
    public ByteCodeBuilder push(final Bytes value) {
        UInt256 pushSize = UInt256.valueOf(Bytes.fromHexString("0x60").toInt() + value.size() - 1);
        String byteString = standardizeByteString(pushSize);
        byteCode.append(byteString);
        byteCode.append(value.toUnprefixedHexString());
        return this;
    }
    public ByteCodeBuilder push(final UInt256 value) {
        String byteString = standardizeByteString(value);
        byteCode.append(Operation.PUSH);
        byteCode.append(byteString);
        return this;
    }

    private String standardizeByteString(final UInt256 value) {
        String byteString = value.toMinimalBytes().toUnprefixedHexString();
        while (byteString.length() < 2) {
            byteString = "0" + byteString;
        }
        return byteString;
    }

    public ByteCodeBuilder tstore(final int key, final int value) {
        this.push(value);
        this.push(key);
        byteCode.append(Operation.TSTORE);
        return this;
    }

    public ByteCodeBuilder tload(final int key) {
        this.push(key);
        byteCode.append(Operation.TLOAD);
        return this;
    }

    public ByteCodeBuilder dataOnStackToMemory(final int key) {
        this.push(key);
        this.op(Operation.MSTORE);
        return this;
    }

    public ByteCodeBuilder returnValueAtMemory(final int size, final int key) {
        this.push(size);
        this.push(key);
        this.op(Operation.RETURN);
        return this;
    }

    public ByteCodeBuilder call(final Operation callType, final Address address, final UInt256 gasLimit) {
        callTypeCheck(callType);
        this.push(0);
        this.push(0);
        this.push(0);
        this.push(0);
        this.push(0);
        this.push(Bytes.fromHexString(address.toHexString()));
        this.push(gasLimit.toMinimalBytes());
        this.op(callType);
        return this;
    }

    private static void callTypeCheck(final Operation callType) {

        if (callType != Operation.CALL &&
                callType != Operation.STATICCALL &&
                callType != Operation.DELEGATECALL) {
            throw new UnsupportedOperationException("callType not supported: " + callType);
        }
    }

    public ByteCodeBuilder callWithInput(final Operation callType, final Address address, final UInt256 gasLimit) {
        return callWithInput(callType, address, gasLimit, null);
    }
    public ByteCodeBuilder callWithInput(final Operation callType, final Address address, final UInt256 gasLimit, final UInt256 input) {
        callTypeCheck(callType);
        if (input != null) {
            this.push(input);
            this.push(0);
            this.op(Operation.MSTORE);
        }
        else {
            // Use top of stack as input
            dataOnStackToMemory(0);
        }

        this.push(0);
        this.push(0);
        this.push(32);
        this.push(0);
        this.push(0);
        this.push(Bytes.fromHexString(address.toHexString()));
        this.push(gasLimit.toMinimalBytes());
        this.op(callType);
        return this;
    }

    public ByteCodeBuilder returnInnerCallResults() {
        this.push(32);
        this.push(0);
        this.push(0);
        byteCode.append(Operation.RETURNDATACOPY);
        this.returnValueAtMemory(32, 0);
        return this;
    }

    public ByteCodeBuilder callerIs(final Address caller) {
        byteCode.append(Operation.CALLER);
        this.push(Bytes.fromHexString(caller.toHexString()));
        byteCode.append(Operation.EQ);
        return this;
    }

    public ByteCodeBuilder conditionalJump(final int dest) {
        this.push(dest);
        byteCode.append(Operation.JUMPI);
        return this;
    }
    public ByteCodeBuilder op(final Operation operation) {
        byteCode.append(operation.toString());
        return this;
    }
}

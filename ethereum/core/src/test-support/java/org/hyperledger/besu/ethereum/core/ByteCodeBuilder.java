package org.hyperledger.besu.ethereum.core;

import kotlin.UInt;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class ByteCodeBuilder {

    public enum Operation {
        MLOAD("51"),
        CALLDATALOAD("35"),
        REVERT("fd"),
        RETURN("f3"),
        JUMPDEST("5b"),
        EQ("14"),
        ADD("01"),
        CALL("f1"),
        STATICCALL("fa"),
        MSTORE("52");

        private final String value;

        Operation(final String value) {
            this.value = value;
        }

        public String getHex() {
            return value;
        }
    }
    StringBuilder byteCode = new StringBuilder("0x");
    public long gasCost = 0L;

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
        byteCode.append("60").append(byteString);
        gasCost += 3;
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
        byteCode.append("b4"); // TSTORE
        gasCost += 100;
        return this;
    }

    public ByteCodeBuilder tload(final int key) {
        this.push(key);
        byteCode.append("b3"); // TLOAD
        gasCost += 100;
        return this;
    }

    public ByteCodeBuilder dataOnStackToMemory(final int key) {
        this.push(key);
        this.op(Operation.MSTORE);
        gasCost += 3;
        return this;
    }

    public ByteCodeBuilder returnValueAtMemory(final int size, final int key) {
        this.push(size);
        this.push(key);
        this.op(Operation.RETURN);
        return this;
    }

    public ByteCodeBuilder call(final Operation callType, final Address address, final UInt256 gasLimit) {
        if (callType != Operation.CALL &&
        callType != Operation.STATICCALL) {
            throw new UnsupportedOperationException("callType not supported: " + callType);
        }
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

    public ByteCodeBuilder callWithInput(final Address address, final UInt256 gasLimit) {
        return callWithInput(address, gasLimit, null);
    }
    public ByteCodeBuilder callWithInput(final Address address, final UInt256 gasLimit, final UInt256 input) {
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
        this.op(Operation.CALL);
        return this;
    }

    public ByteCodeBuilder returnInnerCallResults() {
        this.push(32);
        this.push(0);
        this.push(0);
        byteCode.append("3e"); // RETURNDATACOPY
        this.returnValueAtMemory(32, 0);
        return this;
    }

    public ByteCodeBuilder callerIs(final Address caller) {
        byteCode.append("33"); // CALLER
        this.push(Bytes.fromHexString(caller.toHexString()));
        byteCode.append("14"); // EQ
        return this;
    }

    public ByteCodeBuilder conditionalJump(final int dest) {
        this.push(dest);
        byteCode.append("57"); // JUMPI
        return this;
    }
    public ByteCodeBuilder op(final Operation operation) {
        byteCode.append(operation.getHex());
        return this;
    }
}

package org.hyperledger.besu.ethereum.core;

import kotlin.UInt;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public class ByteCodeBuilder {

    StringBuilder byteCode = new StringBuilder("0x");
    public long gasCost = 0L;

    @Override
    public String toString() {
        return byteCode.toString();
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
        byteCode.append("b4");
        gasCost += 100;
        return this;
    }

    public ByteCodeBuilder tload(final int key) {
        this.push(key);
        byteCode.append("b3");
        gasCost += 100;
        return this;
    }

    public ByteCodeBuilder dataOnStackToMemory(final int key) {
        this.push(key);
        byteCode.append("52");
        gasCost += 3;
        return this;
    }

    public ByteCodeBuilder returnValueAtMemory(final int size, final int key) {
        this.push(size);
        this.push(key);
        byteCode.append("F3");
        return this;
    }

    public ByteCodeBuilder call(final Address address, final UInt256 gasLimit) {
        this.push(0);
        this.push(0);
        this.push(0);
        this.push(0);
        this.push(0);
        this.push(Bytes.fromHexString(address.toHexString()));
        this.push(gasLimit.toMinimalBytes());
        byteCode.append("F1");
        return this;
    }

    public ByteCodeBuilder returnInnerCallResults() {
        this.push(32);
        this.push(0);
        this.push(0);
        byteCode.append("3e");
        this.returnValueAtMemory(32, 0);
        return this;
    }
}

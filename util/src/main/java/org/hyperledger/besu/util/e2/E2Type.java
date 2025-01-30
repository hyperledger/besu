package org.hyperledger.besu.util.e2;

import java.math.BigInteger;

public enum E2Type {
    EMPTY(new byte[]{0x00, 0x00}),
    COMPRESSED_SIGNED_BEACON_BLOCK(new byte[]{0x01, 0x00}),
    COMPRESSED_BEACON_STATE(new byte[]{0x02, 0x00}),
    VERSION(new byte[]{0x65, 0x32}),
    SLOT_INDEX(new byte[]{0x69, 0x32}),
    ;
    private final byte[] typeCode;

    E2Type(final byte[] typeCode) {
        this.typeCode = typeCode;
    }

    public byte[] getTypeCode() {
        return typeCode;
    }

    public static E2Type getForTypeCode(final byte[] typeCode) {
        if(typeCode == null || typeCode.length != 2) {
            throw new IllegalArgumentException("typeCode must be 2 bytes");
        }

        E2Type result = null;
        for (E2Type e2Type : values()) {
            if(e2Type.typeCode[0] == typeCode[0]
                && e2Type.typeCode[1] == typeCode[1]) {
                result = e2Type;
            }
        }
        if(result == null) {
            throw new IllegalArgumentException("typeCode " + new BigInteger(typeCode).toString(16) + " is not recognised");
        }
        return result;
    }
}

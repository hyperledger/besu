package org.hyperledger.besu.datatypes;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class Sha256Hash extends Hash {

    private Sha256Hash(final Bytes32 bytes) {
        super(bytes);
    }
    public static Hash sha256(final Bytes value) {
        return new Sha256Hash(org.hyperledger.besu.crypto.Hash.sha256(value));
    }
}

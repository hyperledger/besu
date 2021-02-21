package org.hyperledger.besu.crypto;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

import java.math.BigInteger;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class PrivateKey implements java.security.PrivateKey {

    private final Bytes32 encoded;
    private final String algorithm;

    private PrivateKey(final Bytes32 encoded, final String algorithm) {
        checkNotNull(encoded);
        checkNotNull(algorithm);
        this.encoded = encoded;
        this.algorithm = algorithm;
    }

    public static PrivateKey create(final BigInteger key, final String algorithm) {
        checkNotNull(key);
        return create(UInt256.valueOf(key).toBytes(), algorithm);
    }

    public static PrivateKey create(final Bytes32 key, final String algorithm) {
        return new PrivateKey(key, algorithm);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof PrivateKey)) {
            return false;
        }

        final PrivateKey that = (PrivateKey) other;
        return this.encoded.equals(that.encoded) && this.algorithm.equals(that.algorithm);
    }

    @Override
    public byte[] getEncoded() {
        return encoded.toArrayUnsafe();
    }

    public Bytes32 getEncodedBytes() {
        return encoded;
    }

    public BigInteger getD() {
        return encoded.toUnsignedBigInteger();
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public String getFormat() {
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(encoded, algorithm);
    }

    @Override
    public String toString() {
        return "PrivateKey{encoded=" + encoded + ", algorithm=" + algorithm + "}";
    }
}
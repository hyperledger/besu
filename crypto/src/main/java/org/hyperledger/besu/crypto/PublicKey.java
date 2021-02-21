package org.hyperledger.besu.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PublicKey implements java.security.PublicKey {

    public static final int BYTE_LENGTH = 64;

    private final Bytes encoded;
    private final String algorithm;

    public static PublicKey create(final BigInteger key, final String algorithm) {
        checkNotNull(key);
        return create(toBytes64(key.toByteArray()), algorithm);
    }

    public static PublicKey create(final Bytes encoded, final String algorithm) {
        return new PublicKey(encoded, algorithm);
    }

    public static PublicKey create(final PrivateKey privateKey, final ECDomainParameters curve, final String algorithm) {
        BigInteger privKey = privateKey.getEncodedBytes().toUnsignedBigInteger();

        /*
         * TODO: FixedPointCombMultiplier currently doesn't support scalars longer than the group
         * order, but that could change in future versions.
         */
        if (privKey.bitLength() > curve.getN().bitLength()) {
            privKey = privKey.mod(curve.getN());
        }

        final ECPoint point = new FixedPointCombMultiplier().multiply(curve.getG(), privKey);
        return PublicKey.create(Bytes.wrap(Arrays.copyOfRange(point.getEncoded(false), 1, 65)), algorithm);
    }

    private static Bytes toBytes64(final byte[] backing) {
        if (backing.length == BYTE_LENGTH) {
            return Bytes.wrap(backing);
        } else if (backing.length > BYTE_LENGTH) {
            return Bytes.wrap(backing, backing.length - BYTE_LENGTH, BYTE_LENGTH);
        } else {
            final MutableBytes res = MutableBytes.create(BYTE_LENGTH);
            Bytes.wrap(backing).copyTo(res, BYTE_LENGTH - backing.length);
            return res;
        }
    }

    private PublicKey(final Bytes encoded, final String algorithm) {
        checkNotNull(encoded);
        checkNotNull(algorithm);
        checkArgument(
                encoded.size() == BYTE_LENGTH,
                "Encoding must be %s bytes long, got %s",
                BYTE_LENGTH,
                encoded.size());
        this.encoded = encoded;
        this.algorithm = algorithm;
    }

    /**
     * Returns this public key as an {@link ECPoint} of Bouncy Castle, to facilitate cryptographic
     * operations.
     *
     * @return This public key represented as an Elliptic Curve point.
     */
    public ECPoint asEcPoint(final ECDomainParameters curve) {
        // 0x04 is the prefix for uncompressed keys.
        final Bytes val = Bytes.concatenate(Bytes.of(0x04), encoded);
        return curve.getCurve().decodePoint(val.toArrayUnsafe());
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof PublicKey)) {
            return false;
        }

        final PublicKey that = (PublicKey) other;
        return this.encoded.equals(that.encoded) && this.algorithm.equals(that.algorithm);
    }

    @Override
    public byte[] getEncoded() {
        return encoded.toArrayUnsafe();
    }

    public Bytes getEncodedBytes() {
        return encoded;
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
        return "PublicKey{encoded=" + encoded + ", algorithm=" + algorithm + "}";
    }
}

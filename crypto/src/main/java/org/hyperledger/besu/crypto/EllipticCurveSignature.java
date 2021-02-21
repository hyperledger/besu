package org.hyperledger.besu.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface EllipticCurveSignature {
    void enableNative();
    Signature sign(final Bytes32 dataHash, final KeyPair keyPair);
    boolean verify(final Bytes data, final Signature signature, final PublicKey pub);
    boolean verify(
            final Bytes data,
            final Signature signature,
            final PublicKey pub,
            final UnaryOperator<Bytes> preprocessor);
    Signature normaliseSignature(
            final BigInteger nativeR,
            final BigInteger nativeS,
            final PublicKey publicKey,
            final Bytes32 dataHash);
    Bytes32 calculateECDHKeyAgreement(final PrivateKey privKey, final PublicKey theirPubKey);
    BigInteger getHalfCurveOrder();

    PrivateKey createPrivateKey(final BigInteger key);
    PrivateKey createPrivateKey(final Bytes32 key);

    PublicKey createPublicKey(final PrivateKey privateKey);
    PublicKey createPublicKey(final BigInteger key);
    PublicKey createPublicKey(final Bytes encoded);
    Optional<PublicKey> recoverPublicKeyFromSignature(
            final Bytes32 dataHash, final Signature signature);
    ECPoint publicKeyAsEcPoint(final PublicKey publicKey);

    KeyPair createKeyPair(final PrivateKey privateKey);
    KeyPair generateKeyPair();

    Signature createSignature(final BigInteger r, final BigInteger s, final byte recId);
    Signature decodeSignature(final Bytes bytes);
}

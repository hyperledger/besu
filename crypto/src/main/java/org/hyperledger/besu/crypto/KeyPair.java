package org.hyperledger.besu.crypto;

import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class KeyPair {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public KeyPair(final PrivateKey privateKey, final PublicKey publicKey) {
        checkNotNull(privateKey);
        checkNotNull(publicKey);
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static KeyPair create(final PrivateKey privateKey, final ECDomainParameters curve, final String algorithm) {
        return new KeyPair(privateKey, PublicKey.create(privateKey, curve, algorithm));
    }

    public static KeyPair generate(KeyPairGenerator keyPairGenerator, final String algorithm) {
        final java.security.KeyPair rawKeyPair = keyPairGenerator.generateKeyPair();
        final BCECPrivateKey privateKey = (BCECPrivateKey) rawKeyPair.getPrivate();
        final BCECPublicKey publicKey = (BCECPublicKey) rawKeyPair.getPublic();

        final BigInteger privateKeyValue = privateKey.getD();

        // Ethereum does not use encoded public keys like bitcoin - see
        // https://en.bitcoin.it/wiki/Elliptic_Curve_Digital_Signature_Algorithm for details
        // Additionally, as the first bit is a constant prefix (0x04) we ignore this value
        final byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
        final BigInteger publicKeyValue =
                new BigInteger(1, Arrays.copyOfRange(publicKeyBytes, 1, publicKeyBytes.length));

        return new  KeyPair( PrivateKey.create(privateKeyValue, algorithm),  PublicKey.create(publicKeyValue, algorithm));
    }

    @Override
    public int hashCode() {
        return Objects.hash(privateKey, publicKey);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof KeyPair)) {
            return false;
        }

        final KeyPair that = (KeyPair) other;
        return this.privateKey.equals(that.privateKey) && this.publicKey.equals(that.publicKey);
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}

package org.hyperledger.besu.crypto;

public class EllipticCurveSignatureFactory {

    private static final EllipticCurveSignature instance = new SECP256K1();

    public static EllipticCurveSignature getInstance() {
        return instance;
    }
}

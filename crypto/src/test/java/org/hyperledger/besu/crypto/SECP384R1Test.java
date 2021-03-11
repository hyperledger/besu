package org.hyperledger.besu.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.crypto.Hash.keccak256;

public class SECP384R1Test {
    
    protected SECP384R1 secp384R1;

    protected static String suiteStartTime = null;
    protected static String suiteName = null;

    @BeforeClass
    public static void setTestSuiteStartTime() {
        suiteStartTime =
                LocalDateTime.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        suiteName(SECP384R1Test.class);
        ;
    }

    @Before
    public void setUp() {
        secp384R1 = new SECP384R1();
    }

    public static void suiteName(final Class<?> clazz) {
        suiteName = clazz.getSimpleName() + "-" + suiteStartTime;
    }

    public static String suiteName() {
        return suiteName;
    }

    @Test
    public void keyPairGeneration_PublicKeyRecovery() {
        final KeyPair keyPair = secp384R1.generateKeyPair();
        assertThat(secp384R1.createPublicKey(keyPair.getPrivateKey()))
                .isEqualTo(keyPair.getPublicKey());
    }

    @Test
    public void recoverPublicKeyFromSignature() {
        final SECPPrivateKey privateKey =
                secp384R1.createPrivateKey(
                        new BigInteger("f753463e762273a8bf5b3bd8eec6062a0720a60ac514479530a292959d542d8306b3693779d51c6f1b554566670b71ad", 16));
        final KeyPair keyPair = secp384R1.createKeyPair(privateKey);

        final Bytes data = Bytes.wrap("This is an example of a signed message.".getBytes(UTF_8));
        final Bytes32 dataHash = keccak256(data);
        final SECPSignature signature = secp384R1.sign(dataHash, keyPair);

        final SECPPublicKey recoveredPublicKey =
                secp384R1.recoverPublicKeyFromSignature(dataHash, signature).get();
        assertThat(recoveredPublicKey.toString()).isEqualTo(keyPair.getPublicKey().toString());
    }

    @Test
    public void signatureGeneration() {
        final SECPPrivateKey privateKey =
                secp384R1.createPrivateKey(
                        new BigInteger("f753463e762273a8bf5b3bd8eec6062a0720a60ac514479530a292959d542d8306b3693779d51c6f1b554566670b71ad", 16));
        final KeyPair keyPair = secp384R1.createKeyPair(privateKey);

        final Bytes data = Bytes.wrap("This is an example of a signed message.".getBytes(UTF_8));
        final Bytes32 dataHash = keccak256(data);
        final SECPSignature expectedSignature =
                secp384R1.createSignature(
                        new BigInteger("8d2f180787e2b7d9d77ccc0d007866c353660bf8f575797a38cea46700fcf8020c68ca90336ebe9f8b29771a094056ee", 16),
                        new BigInteger("32907d8d225a4bcd7e9956767972e176d6f30b5227e7d7a2115baac5ec8c440f4fda6abd50a16aa50e75fbeef9341ab8", 16),
                        (byte) 1);

        final SECPSignature actualSignature = secp384R1.sign(dataHash, keyPair);
        assertThat(actualSignature).isEqualTo(expectedSignature);
    }

    @Test
    public void signatureVerification() {
        final SECPPrivateKey privateKey =
                secp384R1.createPrivateKey(
                        new BigInteger("f753463e762273a8bf5b3bd8eec6062a0720a60ac514479530a292959d542d8306b3693779d51c6f1b554566670b71ad", 16));
        final KeyPair keyPair = secp384R1.createKeyPair(privateKey);

        final Bytes data = Bytes.wrap("This is an example of a signed message.".getBytes(UTF_8));
        final Bytes32 dataHash = keccak256(data);

        final SECPSignature signature = secp384R1.sign(dataHash, keyPair);
        assertThat(secp384R1.verify(data, signature, keyPair.getPublicKey(), Hash::keccak256)).isTrue();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidFileThrowsInvalidKeyPairException() throws Exception {
        final File tempFile = Files.createTempFile(suiteName(), ".keypair").toFile();
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "not valid".getBytes(UTF_8));
        KeyPairUtil.load(tempFile);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidMultiLineFileThrowsInvalidIdException() throws Exception {
        final File tempFile = Files.createTempFile(suiteName(), ".keypair").toFile();
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), "not\n\nvalid".getBytes(UTF_8));
        KeyPairUtil.load(tempFile);
    }
}

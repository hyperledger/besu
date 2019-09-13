/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.crypto.Hash.keccak256;
import static tech.pegasys.pantheon.util.bytes.BytesValue.fromHexString;

import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.BeforeClass;
import org.junit.Test;

public class SECP256K1Test {

  protected static String suiteStartTime = null;
  protected static String suiteName = null;

  @BeforeClass
  public static void setTestSuiteStartTime() {
    final SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd-HHmmss");
    suiteStartTime = fmt.format(new Date());
    suiteName(SECP256K1Test.class);
  }

  public static void suiteName(final Class<?> clazz) {
    suiteName = clazz.getSimpleName() + "-" + suiteStartTime;
  }

  public static String suiteName() {
    return suiteName;
  }

  @Test(expected = NullPointerException.class)
  public void createPrivateKey_NullEncoding() {
    SECP256K1.PrivateKey.create((Bytes32) null);
  }

  @Test
  public void privateKeyEquals() {
    final SECP256K1.PrivateKey privateKey1 = SECP256K1.PrivateKey.create(BigInteger.TEN);
    final SECP256K1.PrivateKey privateKey2 = SECP256K1.PrivateKey.create(BigInteger.TEN);

    assertThat(privateKey2).isEqualTo(privateKey1);
  }

  @Test
  public void privateHashCode() {
    final SECP256K1.PrivateKey privateKey = SECP256K1.PrivateKey.create(BigInteger.TEN);

    assertThat(privateKey.hashCode()).isNotZero();
  }

  @Test(expected = NullPointerException.class)
  public void createPublicKey_NullEncoding() {
    SECP256K1.PublicKey.create((BytesValue) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPublicKey_EncodingTooShort() {
    SECP256K1.PublicKey.create(BytesValue.wrap(new byte[63]));
  }

  @Test(expected = IllegalArgumentException.class)
  public void createPublicKey_EncodingTooLong() {
    SECP256K1.PublicKey.create(BytesValue.wrap(new byte[65]));
  }

  @Test
  public void publicKeyEquals() {
    final SECP256K1.PublicKey publicKey1 =
        SECP256K1.PublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"));
    final SECP256K1.PublicKey publicKey2 =
        SECP256K1.PublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"));

    assertThat(publicKey2).isEqualTo(publicKey1);
  }

  @Test
  public void publicHashCode() {
    final SECP256K1.PublicKey publicKey =
        SECP256K1.PublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"));

    assertThat(publicKey.hashCode()).isNotZero();
  }

  @Test(expected = NullPointerException.class)
  public void createKeyPair_PublicKeyNull() {
    new SECP256K1.KeyPair(null, SECP256K1.PublicKey.create(BytesValue.wrap(new byte[64])));
  }

  @Test(expected = NullPointerException.class)
  public void createKeyPair_PrivateKeyNull() {
    new SECP256K1.KeyPair(SECP256K1.PrivateKey.create(Bytes32.wrap(new byte[32])), null);
  }

  @Test
  public void keyPairGeneration() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    assertThat(keyPair).isNotNull();
    assertThat(keyPair.getPrivateKey()).isNotNull();
    assertThat(keyPair.getPublicKey()).isNotNull();
  }

  @Test
  public void keyPairEquals() {
    final SECP256K1.PrivateKey privateKey1 = SECP256K1.PrivateKey.create(BigInteger.TEN);
    final SECP256K1.PrivateKey privateKey2 = SECP256K1.PrivateKey.create(BigInteger.TEN);
    final SECP256K1.PublicKey publicKey1 =
        SECP256K1.PublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"));
    final SECP256K1.PublicKey publicKey2 =
        SECP256K1.PublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"));

    final SECP256K1.KeyPair keyPair1 = new SECP256K1.KeyPair(privateKey1, publicKey1);
    final SECP256K1.KeyPair keyPair2 = new SECP256K1.KeyPair(privateKey2, publicKey2);

    assertThat(keyPair2).isEqualTo(keyPair1);
  }

  @Test
  public void keyPairHashCode() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    assertThat(keyPair.hashCode()).isNotZero();
  }

  @Test
  public void keyPairGeneration_PublicKeyRecovery() {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.generate();
    assertThat(SECP256K1.PublicKey.create(keyPair.getPrivateKey()))
        .isEqualTo(keyPair.getPublicKey());
  }

  @Test
  public void publicKeyRecovery() {
    final SECP256K1.PrivateKey privateKey = SECP256K1.PrivateKey.create(BigInteger.TEN);
    final SECP256K1.PublicKey expectedPublicKey =
        SECP256K1.PublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"));

    final SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.create(privateKey);
    assertThat(publicKey).isEqualTo(expectedPublicKey);
  }

  @Test
  public void createSignature() {
    final SECP256K1.Signature signature =
        SECP256K1.Signature.create(BigInteger.ONE, BigInteger.TEN, (byte) 0);
    assertThat(signature.getR()).isEqualTo(BigInteger.ONE);
    assertThat(signature.getS()).isEqualTo(BigInteger.TEN);
    assertThat(signature.getRecId()).isEqualTo((byte) 0);
  }

  @Test(expected = NullPointerException.class)
  public void createSignature_NoR() {
    SECP256K1.Signature.create(null, BigInteger.ZERO, (byte) 27);
  }

  @Test(expected = NullPointerException.class)
  public void createSignature_NoS() {
    SECP256K1.Signature.create(BigInteger.ZERO, null, (byte) 27);
  }

  @Test
  public void recoverPublicKeyFromSignature() {
    final SECP256K1.PrivateKey privateKey =
        SECP256K1.PrivateKey.create(
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.create(privateKey);

    final BytesValue data =
        BytesValue.wrap("This is an example of a signed message.".getBytes(UTF_8));
    final Bytes32 dataHash = keccak256(data);
    final SECP256K1.Signature signature = SECP256K1.sign(dataHash, keyPair);

    final SECP256K1.PublicKey recoveredPublicKey =
        SECP256K1.PublicKey.recoverFromSignature(dataHash, signature).get();
    assertThat(recoveredPublicKey.toString()).isEqualTo(keyPair.getPublicKey().toString());
  }

  @Test
  public void signatureGeneration() {
    final SECP256K1.PrivateKey privateKey =
        SECP256K1.PrivateKey.create(
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.create(privateKey);

    final BytesValue data =
        BytesValue.wrap("This is an example of a signed message.".getBytes(UTF_8));
    final Bytes32 dataHash = keccak256(data);
    final SECP256K1.Signature expectedSignature =
        SECP256K1.Signature.create(
            new BigInteger("d2ce488f4da29e68f22cb05cac1b19b75df170a12b4ad1bdd4531b8e9115c6fb", 16),
            new BigInteger("75c1fe50a95e8ccffcbb5482a1e42fbbdd6324131dfe75c3b3b7f9a7c721eccb", 16),
            (byte) 1);

    final SECP256K1.Signature actualSignature = SECP256K1.sign(dataHash, keyPair);
    assertThat(actualSignature).isEqualTo(expectedSignature);
  }

  @Test
  public void signatureVerification() {
    final SECP256K1.PrivateKey privateKey =
        SECP256K1.PrivateKey.create(
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.create(privateKey);

    final BytesValue data =
        BytesValue.wrap("This is an example of a signed message.".getBytes(UTF_8));
    final Bytes32 dataHash = keccak256(data);

    final SECP256K1.Signature signature = SECP256K1.sign(dataHash, keyPair);
    assertThat(SECP256K1.verify(data, signature, keyPair.getPublicKey(), Hash::keccak256)).isTrue();
  }

  @Test
  public void fileContainsValidPrivateKey() throws Exception {
    final File file =
        new File(
            this.getClass()
                .getResource("/tech/pegasys/pantheon/crypto/validPrivateKey.txt")
                .toURI());
    final SECP256K1.PrivateKey privateKey = SECP256K1.PrivateKey.load(file);
    assertThat(privateKey.getEncodedBytes())
        .isEqualTo(
            BytesValue.fromHexString(
                "000000000000000000000000000000000000000000000000000000000000000A"));
  }

  @Test
  public void readWritePrivateKeyString() throws Exception {
    final SECP256K1.PrivateKey privateKey = SECP256K1.PrivateKey.create(BigInteger.TEN);
    final SECP256K1.KeyPair keyPair1 = SECP256K1.KeyPair.create(privateKey);
    final File tempFile = Files.createTempFile(suiteName(), ".keypair").toFile();
    tempFile.deleteOnExit();
    keyPair1.store(tempFile);
    final SECP256K1.KeyPair keyPair2 = SECP256K1.KeyPair.load(tempFile);
    assertThat(keyPair2).isEqualTo(keyPair1);
  }

  @Test(expected = InvalidSEC256K1PrivateKeyStoreException.class)
  public void invalidFileThrowsInvalidKeyPairException() throws Exception {
    final File tempFile = Files.createTempFile(suiteName(), ".keypair").toFile();
    tempFile.deleteOnExit();
    Files.write(tempFile.toPath(), "not valid".getBytes(UTF_8));
    SECP256K1.PrivateKey.load(tempFile);
  }

  @Test(expected = InvalidSEC256K1PrivateKeyStoreException.class)
  public void invalidMultiLineFileThrowsInvalidIdException() throws Exception {
    final File tempFile = Files.createTempFile(suiteName(), ".keypair").toFile();
    tempFile.deleteOnExit();
    Files.write(tempFile.toPath(), "not\n\nvalid".getBytes(UTF_8));
    SECP256K1.PrivateKey.load(tempFile);
  }
}

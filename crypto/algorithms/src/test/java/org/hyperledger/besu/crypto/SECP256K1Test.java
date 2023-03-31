/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.crypto.Hash.keccak256;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class SECP256K1Test {

  protected SECP256K1 secp256K1;

  protected static String suiteStartTime = null;
  protected static String suiteName = null;

  @BeforeClass
  public static void setTestSuiteStartTime() {
    suiteStartTime =
        LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    suiteName(SECP256K1Test.class);
  }

  @Before
  public void setUp() {
    secp256K1 = new SECP256K1();
  }

  public static void suiteName(final Class<?> clazz) {
    suiteName = clazz.getSimpleName() + "-" + suiteStartTime;
  }

  public static String suiteName() {
    return suiteName;
  }

  @Test
  public void keyPairGeneration_PublicKeyRecovery() {
    final KeyPair keyPair = secp256K1.generateKeyPair();
    assertThat(secp256K1.createPublicKey(keyPair.getPrivateKey()))
        .isEqualTo(keyPair.getPublicKey());
  }

  @Test
  public void recoverPublicKeyFromSignature() {
    final SECPPrivateKey privateKey =
        secp256K1.createPrivateKey(
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
    final KeyPair keyPair = secp256K1.createKeyPair(privateKey);

    final Bytes data = Bytes.wrap("This is an example of a signed message.".getBytes(UTF_8));
    final Bytes32 dataHash = keccak256(data);
    final SECPSignature signature = secp256K1.sign(dataHash, keyPair);

    final SECPPublicKey recoveredPublicKey =
        secp256K1.recoverPublicKeyFromSignature(dataHash, signature).get();
    assertThat(recoveredPublicKey.toString()).isEqualTo(keyPair.getPublicKey().toString());
  }

  @Test
  public void signatureGeneration() {
    final SECPPrivateKey privateKey =
        secp256K1.createPrivateKey(
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
    final KeyPair keyPair = secp256K1.createKeyPair(privateKey);

    final Bytes data = Bytes.wrap("This is an example of a signed message.".getBytes(UTF_8));
    final Bytes32 dataHash = keccak256(data);
    final SECPSignature expectedSignature =
        secp256K1.createSignature(
            new BigInteger("d2ce488f4da29e68f22cb05cac1b19b75df170a12b4ad1bdd4531b8e9115c6fb", 16),
            new BigInteger("75c1fe50a95e8ccffcbb5482a1e42fbbdd6324131dfe75c3b3b7f9a7c721eccb", 16),
            (byte) 1);

    final SECPSignature actualSignature = secp256K1.sign(dataHash, keyPair);
    assertThat(actualSignature).isEqualTo(expectedSignature);
  }

  @Test
  public void signatureVerification() {
    final SECPPrivateKey privateKey =
        secp256K1.createPrivateKey(
            new BigInteger("c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4", 16));
    final KeyPair keyPair = secp256K1.createKeyPair(privateKey);

    final Bytes data = Bytes.wrap("This is an example of a signed message.".getBytes(UTF_8));
    final Bytes32 dataHash = keccak256(data);

    final SECPSignature signature = secp256K1.sign(dataHash, keyPair);
    assertThat(secp256K1.verify(data, signature, keyPair.getPublicKey(), Hash::keccak256)).isTrue();
  }

  @Test
  public void invalidFileThrowsInvalidKeyPairException() throws Exception {
    final File tempFile = Files.createTempFile(suiteName(), ".keypair").toFile();
    tempFile.deleteOnExit();
    Files.write(tempFile.toPath(), "not valid".getBytes(UTF_8));
    assertThatThrownBy(() -> KeyPairUtil.load(tempFile))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void invalidMultiLineFileThrowsInvalidIdException() throws Exception {
    final File tempFile = Files.createTempFile(suiteName(), ".keypair").toFile();
    tempFile.deleteOnExit();
    Files.write(tempFile.toPath(), "not\n\nvalid".getBytes(UTF_8));
    assertThatThrownBy(() -> KeyPairUtil.load(tempFile))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

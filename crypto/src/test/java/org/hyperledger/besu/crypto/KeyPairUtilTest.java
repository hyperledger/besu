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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

public class KeyPairUtilTest {
  public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;
  public static final String PRIVATE_KEY =
      "000000000000000000000000000000000000000000000000000000000000000A";
  public static final String PROVIDER = "BC";

  protected static String suiteStartTime = null;
  protected static String suiteName = null;
  protected static ECDomainParameters curve;
  public static KeyPairGenerator keyPairGenerator;

  @BeforeClass
  public static void setUp() {
    suiteStartTime =
        LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    suiteName = KeyPairUtilTest.class.getSimpleName() + "-" + suiteStartTime;

    Security.addProvider(new BouncyCastleProvider());

    try {
      keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    final X9ECParameters params = SECNamedCurves.getByName("secp256k1");
    curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
  }

  @Test
  public void shouldLoadValidKeyPair() throws Exception {
    assertThat(
            KeyPairUtil.loadKeyPair(
                new File(
                    this.getClass()
                        .getResource("/org/hyperledger/besu/crypto/validPrivateKey_256.txt")
                        .toURI())))
        .isNotNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldNotLoadInvalidKeyPair() throws Exception {
    KeyPairUtil.loadKeyPair(
        new File(this.getClass().getResource("/invalidPrivateKey.txt").toURI()));
  }

  @Test
  public void readWritePrivateKeyString() throws Exception {
    final SECPPrivateKey privateKey =
        SECPPrivateKey.create(Bytes.fromHexString(PRIVATE_KEY), ALGORITHM);
    final KeyPair keyPair1 = KeyPair.create(privateKey, curve, ALGORITHM);
    final File tempFile = Files.createTempFile(suiteName, ".keypair").toFile();
    tempFile.deleteOnExit();
    KeyPairUtil.storeKeyPair(keyPair1, tempFile);
    final KeyPair keyPair2 = KeyPairUtil.load(tempFile);
    assertThat(keyPair2).isEqualTo(keyPair1);
  }

  @Test
  public void keyPairGeneration() {
    final KeyPair keyPair = KeyPairUtil.generate(keyPairGenerator, ALGORITHM);
    assertThat(keyPair).isNotNull();
    assertThat(keyPair.getPrivateKey()).isNotNull();
    assertThat(keyPair.getPublicKey()).isNotNull();
  }
}

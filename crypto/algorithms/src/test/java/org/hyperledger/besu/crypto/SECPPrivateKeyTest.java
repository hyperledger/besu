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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SECPPrivateKeyTest {
  public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;
  public static final String CURVE_NAME = "secp256k1";

  protected static String suiteStartTime = null;
  protected static String suiteName = null;
  public static ECDomainParameters curve;

  @BeforeAll
  public static void setUp() {
    suiteStartTime =
        LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    suiteName(SECP256K1Test.class);

    final X9ECParameters params = SECNamedCurves.getByName(CURVE_NAME);
    curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
  }

  public static void suiteName(final Class<?> clazz) {
    suiteName = clazz.getSimpleName() + "-" + suiteStartTime;
  }

  @Test
  public void createPrivateKey_NullEncoding() {
    assertThatThrownBy(() -> SECPPrivateKey.create((Bytes32) null, ALGORITHM))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void privateKeyEquals() {
    final SECPPrivateKey privateKey1 = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM);
    final SECPPrivateKey privateKey2 = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM);

    assertThat(privateKey2).isEqualTo(privateKey1);
  }

  @Test
  public void privateHashCode() {
    final SECPPrivateKey privateKey = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM);

    assertThat(privateKey.hashCode()).isNotZero();
  }

  @Test
  public void fileContainsValidPrivateKey() throws Exception {
    final File file =
        new File(
            this.getClass()
                .getResource("/org/hyperledger/besu/crypto/validPrivateKey.txt")
                .toURI());
    final SECPPrivateKey privateKey = KeyPairUtil.loadPrivateKey(file);
    assertThat(privateKey.getEncodedBytes())
        .isEqualTo(
            Bytes.fromHexString(
                "000000000000000000000000000000000000000000000000000000000000000A"));
  }

  @Test
  public void readWritePrivateKeyString() throws Exception {
    final SECPPrivateKey privateKey = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM);
    final KeyPair keyPair1 = KeyPair.create(privateKey, curve, ALGORITHM);
    final File tempFile = Files.createTempFile(suiteName, ".keypair").toFile();
    tempFile.deleteOnExit();
    KeyPairUtil.storeKeyPair(keyPair1, tempFile);
    final KeyPair keyPair2 = KeyPairUtil.load(tempFile);
    assertThat(keyPair2).isEqualTo(keyPair1);
  }
}

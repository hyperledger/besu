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

import static org.apache.tuweni.bytes.Bytes.fromHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.Security;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

public class KeyPairTest {
  public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;
  public static final String PROVIDER = "BC";
  public static final String CURVE_NAME = "secp256k1";

  public static KeyPairGenerator keyPairGenerator;
  public static ECDomainParameters curve;

  @BeforeClass
  public static void setUp() {
    Security.addProvider(new BouncyCastleProvider());

    try {
      keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    final X9ECParameters params = SECNamedCurves.getByName(CURVE_NAME);
    curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
  }

  @Test
  public void createKeyPair_PublicKeyNull() {
    final SECPPublicKey publicKey = SECPPublicKey.create(Bytes.wrap(new byte[64]), ALGORITHM);
    assertThatThrownBy(() -> new KeyPair(null, publicKey)).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void createKeyPair_PrivateKeyNull() {
    final SECPPrivateKey privateKey = SECPPrivateKey.create(Bytes32.wrap(new byte[32]), ALGORITHM);
    assertThatThrownBy(() -> new KeyPair(privateKey, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void keyPairGeneration() {
    final KeyPair keyPair = KeyPair.generate(keyPairGenerator, ALGORITHM);
    assertThat(keyPair).isNotNull();
    assertThat(keyPair.getPrivateKey()).isNotNull();
    assertThat(keyPair.getPublicKey()).isNotNull();
  }

  @Test
  public void keyPairEquals() {
    final SECPPrivateKey privateKey1 = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM);
    final SECPPrivateKey privateKey2 = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM);
    final SECPPublicKey publicKey1 =
        SECPPublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"),
            ALGORITHM);
    final SECPPublicKey publicKey2 =
        SECPPublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"),
            ALGORITHM);

    final KeyPair keyPair1 = new KeyPair(privateKey1, publicKey1);
    final KeyPair keyPair2 = new KeyPair(privateKey2, publicKey2);

    assertThat(keyPair2).isEqualTo(keyPair1);
  }

  @Test
  public void keyPairHashCode() {
    final KeyPair keyPair = KeyPair.generate(keyPairGenerator, ALGORITHM);
    assertThat(keyPair.hashCode()).isNotZero();
  }
}

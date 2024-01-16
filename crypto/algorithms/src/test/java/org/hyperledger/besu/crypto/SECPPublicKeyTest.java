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

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SECPPublicKeyTest {
  public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;
  public static final String CURVE_NAME = "secp256k1";

  public ECDomainParameters curve;

  @BeforeEach
  public void setUp() {
    final X9ECParameters params = SECNamedCurves.getByName(CURVE_NAME);
    curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
  }

  @Test
  public void createPublicKey_NullEncoding() {
    assertThatThrownBy(() -> SECPPublicKey.create((Bytes) null, ALGORITHM))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void createPublicKey_EncodingTooShort() {
    final Bytes publicKey = Bytes.wrap(new byte[63]);
    assertThatThrownBy(() -> SECPPublicKey.create(publicKey, ALGORITHM))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void createPublicKey_EncodingTooLong() {
    final Bytes publicKey = Bytes.wrap(new byte[65]);
    assertThatThrownBy(() -> SECPPublicKey.create(publicKey, ALGORITHM))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void publicKeyEquals() {
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

    assertThat(publicKey2).isEqualTo(publicKey1);
  }

  @Test
  public void publicHashCode() {
    final SECPPublicKey publicKey =
        SECPPublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"),
            ALGORITHM);

    assertThat(publicKey.hashCode()).isNotZero();
  }

  @Test
  public void publicKeyRecovery() {
    final SECPPrivateKey privateKey = SECPPrivateKey.create(BigInteger.TEN, ALGORITHM);
    final SECPPublicKey expectedPublicKey =
        SECPPublicKey.create(
            fromHexString(
                "a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7"),
            ALGORITHM);

    final SECPPublicKey publicKey = SECPPublicKey.create(privateKey, curve, ALGORITHM);
    assertThat(publicKey).isEqualTo(expectedPublicKey);
  }
}

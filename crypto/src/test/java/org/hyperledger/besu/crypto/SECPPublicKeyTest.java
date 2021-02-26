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

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SECPPublicKeyTest {
  @Parameterized.Parameters
  public static Object[][] getKeyLengths() {
    return new Object[][] {
      {
        "secp256k1",
        "fdfc794f74aa7457b75b5c3d627240996d202944964a76a27fd0a20fde3bb547",
        "e5a4f9e4ee41b7589f4e999af44aa1321aadef2937652128085b550cdfca083b33658257eea2b4bb69833b244081e356819f60f777334a29626b591b340fb04d"
      },
      {
        "secp256r1",
        "5c6d4954bc41b41ce2cfa6d6a57203c7c04a803404a99c0a7d3ea5de076776a7",
        "79813a332cab41780c8246d9bc0d415124aca9e2e646333664e5aa67b0547329672098afb481c5bc7fd5589cfb456366c7487f8023344e237969cd7b5fd214d7"
      },
      {
        "secp384r1",
        "5c2786ca351c6b0ddd5d29e92b6228a44624fafba7ef608b597abc062bf6d7c1eb3cc70533af5ecb047bb218cb0f4837",
        "f0970d65669b130668c3ddc59d3dacc142ff2fd93e43f96e51326317ef5311d95259e879737d002d221ffdde99961313bbca8e1ed95d8b1860639f370d04346bde6c30d565047888cb80affce1f48d647b9f8a3585a76d4450b748ff93c657e3"
      }
    };
  }

  public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;

  private final ECDomainParameters curve;
  private final String privateKeyString;
  private final String publicKeyString;

  public SECPPublicKeyTest(
      final String curveName, final String privateKeyString, final String publicKeyString) {
    this.privateKeyString = privateKeyString;
    this.publicKeyString = publicKeyString;

    final X9ECParameters params = SECNamedCurves.getByName(curveName);
    curve = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
  }

  @Test(expected = NullPointerException.class)
  public void createPublicKey_NullEncoding() {
    SECPPublicKey.create(null, ALGORITHM);
  }

  @Test
  public void publicKeyEquals() {
    final SECPPublicKey publicKey1 =
        SECPPublicKey.create(fromHexString(publicKeyString), ALGORITHM);
    final SECPPublicKey publicKey2 =
        SECPPublicKey.create(fromHexString(publicKeyString), ALGORITHM);

    assertThat(publicKey2).isEqualTo(publicKey1);
  }

  @Test
  public void publicHashCode() {
    final SECPPublicKey publicKey = SECPPublicKey.create(fromHexString(publicKeyString), ALGORITHM);

    assertThat(publicKey.hashCode()).isNotZero();
  }

  @Test
  public void publicKeyRecovery() {
    final SECPPrivateKey privateKey =
        SECPPrivateKey.create(fromHexString(privateKeyString), ALGORITHM);
    final SECPPublicKey expectedPublicKey =
        SECPPublicKey.create(fromHexString(publicKeyString), ALGORITHM);

    final SECPPublicKey publicKey = SECPPublicKey.create(privateKey, curve, ALGORITHM);
    assertThat(publicKey).isEqualTo(expectedPublicKey);
  }
}

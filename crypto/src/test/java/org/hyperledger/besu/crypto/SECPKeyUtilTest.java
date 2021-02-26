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

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.junit.Test;

public class SECPKeyUtilTest {
  public static final String ALGORITHM = SignatureAlgorithm.ALGORITHM;

  @Test
  public void bigIntegerToBytes() {
    final String HEX_32_BYTE = "fb47b44b1a2136a47764f69b1a1a6d8b4af31cb9ac35f17d6f5765b03055925a";
    assertThat(Bytes.fromHexString(HEX_32_BYTE))
        .isEqualTo(SECPKeyUtil.toBytes(new BigInteger(HEX_32_BYTE, 16), 32));

    // if the input is too short it should shifted to the right
    final String HEX_31_BYTE_INPUT =
        "998b4b6ed34a6d4628f398b3baffb077f029733446aadf3254aa37f68abfe1";
    final String HEX_31_BYTE_RESULT =
        "00998b4b6ed34a6d4628f398b3baffb077f029733446aadf3254aa37f68abfe1";
    assertThat(Bytes.fromHexString(HEX_31_BYTE_RESULT))
        .isEqualTo(SECPKeyUtil.toBytes(new BigInteger(HEX_31_BYTE_INPUT, 16), 32));

    // if the input is too long it should shifted to the left
    final String HEX_33_BYTE_INPUT =
        "4147cda7176e6a506e7700ca7f8a3962bb52c01063b119d36e82ffdd437cd91233";
    final String HEX_33_BYTE_RESULT =
        "47cda7176e6a506e7700ca7f8a3962bb52c01063b119d36e82ffdd437cd91233";
    assertThat(Bytes.fromHexString(HEX_33_BYTE_RESULT))
        .isEqualTo(SECPKeyUtil.toBytes(new BigInteger(HEX_33_BYTE_INPUT, 16), 32));
  }

  @Test
  public void toPublicKey_secp256k1() {
    final SECPPrivateKey privateKey =
        SECPPrivateKey.create(
            Bytes.fromHexString("e6e912a49e5678481cf6c017f44ec894e0cdc187460e37852685ee895ae66eba"),
            ALGORITHM);
    final String publicKeyHex =
        "0xf378a55519ea5c3ed0bfb1956033b5336ac457b7160cca644e1053c06b558d9de4e99877c2631b7372c81f0196fc85a2ec335a1bc2b2d7f275d7ab5f4751e49f";

    assertThat(publicKeyHex)
        .isEqualTo(SECPKeyUtil.toPublicKey(privateKey, createCurve("secp256k1")).toString());
  }

  @Test
  public void toPublicKey_secp256r1() {
    final SECPPrivateKey privateKey =
        SECPPrivateKey.create(
            Bytes.fromHexString("5c6d4954bc41b41ce2cfa6d6a57203c7c04a803404a99c0a7d3ea5de076776a7"),
            ALGORITHM);
    final String publicKeyHex =
        "0x79813a332cab41780c8246d9bc0d415124aca9e2e646333664e5aa67b0547329672098afb481c5bc7fd5589cfb456366c7487f8023344e237969cd7b5fd214d7";

    assertThat(publicKeyHex)
        .isEqualTo(SECPKeyUtil.toPublicKey(privateKey, createCurve("secp256r1")).toString());
  }

  @Test
  public void toPublicKey_secp384r1() {
    final SECPPrivateKey privateKey =
        SECPPrivateKey.create(
            Bytes.fromHexString(
                "a49dc14e150bfc3d674983bd2d1b8c39a119c11b3c93f41ffc18e78a89045494e45f18bdeb8c31c1f2660c63c2e14635"),
            ALGORITHM);
    final String publicKeyHex =
        "0x9f9b30b12917c271a2bcb9759fe2a1446189044ac74ea0ec19c47f6a049cd9d8a2e65a9d56364bd605571ee75eb4bc26eb4fb44907e409962d02478390d32ac7cc38d3908641a38fa356ee4bb88c4339c7f26a4e434c690ce69b237c4739feca";

    assertThat(publicKeyHex)
        .isEqualTo(SECPKeyUtil.toPublicKey(privateKey, createCurve("secp384r1")).toString());
  }

  private ECDomainParameters createCurve(final String curveName) {
    final X9ECParameters params = SECNamedCurves.getByName(curveName);
    return new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
  }
}

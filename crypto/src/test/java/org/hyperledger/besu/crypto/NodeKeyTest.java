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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class NodeKeyTest {

  @Before
  public void resetInstance() {
    SignatureAlgorithmFactory.resetInstance();
  }

  @Test
  public void publicKeyShouldMatch() {
    final Bytes keyPairPubKey =
        Bytes.fromHexString(
            "0x35f48529f73c4172850ed18997d00d101a9aebc130103c23d41d46351c1b1d72ffc8b246af3a446d99a8218bd69e231b75932ea9a79173751dc7eb2eb0e301f2");
    final Bytes32 keyPairPrvKey =
        Bytes32.fromHexString("0xf7a58d5e755d51fa2f6206e91dd574597c73248aaf946ec1964b8c6268d6207b");

    final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();
    final KeyPair keyPair =
        signatureAlgorithm.createKeyPair(signatureAlgorithm.createPrivateKey(keyPairPrvKey));
    final KeyPairSecurityModule keyPairSecurityModule = new KeyPairSecurityModule(keyPair);
    final NodeKey nodeKey = new NodeKey(keyPairSecurityModule);

    Assertions.assertThat(nodeKey.getPublicKey().getEncodedBytes())
        .isEqualByComparingTo(keyPair.getPublicKey().getEncodedBytes());

    Assertions.assertThat(nodeKey.getPublicKey().getEncodedBytes())
        .isEqualByComparingTo(keyPairPubKey);
  }
}

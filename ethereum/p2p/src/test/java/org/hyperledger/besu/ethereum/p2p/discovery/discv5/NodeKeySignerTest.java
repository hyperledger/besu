/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.p2p.discovery.discv5;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;

import java.lang.reflect.Constructor;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.crypto.Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeKeySignerTest {

  private static final SignatureAlgorithm SIGNATURE_ALGORITHM =
      SignatureAlgorithmFactory.getInstance();

  private NodeKey nodeKey1;
  private NodeKey nodeKey2;
  private Signer signer1;
  private Signer signer2;

  @BeforeEach
  void setUp() throws Exception {
    nodeKey1 = NodeKeyUtils.generate();
    nodeKey2 = NodeKeyUtils.generate();
    signer1 = createNodeKeySigner(nodeKey1);
    signer2 = createNodeKeySigner(nodeKey2);
  }

  @Test
  void ecdhWithUncompressedKey() {
    // Get uncompressed 64-byte public key of nodeKey2
    final Bytes uncompressedPub2 = nodeKey2.getPublicKey().getEncodedBytes();
    assertThat(uncompressedPub2.size()).isEqualTo(64);

    final Bytes sharedSecret = signer1.deriveECDHKeyAgreement(uncompressedPub2);
    assertThat(sharedSecret).isNotNull();
    assertThat(sharedSecret.size()).isEqualTo(33); // compressed EC point
  }

  @Test
  void ecdhWithCompressedKey() {
    // Get compressed 33-byte public key of nodeKey2
    final Bytes compressedPub2 =
        Bytes.wrap(
            SIGNATURE_ALGORITHM.publicKeyAsEcPoint(nodeKey2.getPublicKey()).getEncoded(true));
    assertThat(compressedPub2.size()).isEqualTo(33);

    final Bytes sharedSecret = signer1.deriveECDHKeyAgreement(compressedPub2);
    assertThat(sharedSecret).isNotNull();
    assertThat(sharedSecret.size()).isEqualTo(33);
  }

  @Test
  void ecdhIsSymmetric() {
    final Bytes pub1 = nodeKey1.getPublicKey().getEncodedBytes();
    final Bytes pub2 = nodeKey2.getPublicKey().getEncodedBytes();

    final Bytes secret12 = signer1.deriveECDHKeyAgreement(pub2);
    final Bytes secret21 = signer2.deriveECDHKeyAgreement(pub1);

    assertThat(secret12).isEqualTo(secret21);
  }

  @Test
  void ecdhDifferentKeysDifferentResults() {
    final NodeKey nodeKey3 = NodeKeyUtils.generate();
    final Bytes pub2 = nodeKey2.getPublicKey().getEncodedBytes();
    final Bytes pub3 = nodeKey3.getPublicKey().getEncodedBytes();

    final Bytes secret12 = signer1.deriveECDHKeyAgreement(pub2);
    final Bytes secret13 = signer1.deriveECDHKeyAgreement(pub3);

    assertThat(secret12).isNotEqualTo(secret13);
  }

  @Test
  void signProduces64Bytes() {
    final Bytes32 hash = Bytes32.random();
    final Bytes signature = signer1.sign(hash);

    // r || s with no v byte
    assertThat(signature.size()).isEqualTo(64);
  }

  @Test
  void signIsDeterministic() {
    final Bytes32 hash = Bytes32.random();
    final Bytes sig1 = signer1.sign(hash);
    final Bytes sig2 = signer1.sign(hash);

    assertThat(sig1).isEqualTo(sig2);
  }

  @Test
  void signDifferentHashesDiffer() {
    final Bytes32 hash1 = Bytes32.random();
    final Bytes32 hash2 = Bytes32.random();

    final Bytes sig1 = signer1.sign(hash1);
    final Bytes sig2 = signer1.sign(hash2);

    assertThat(sig1).isNotEqualTo(sig2);
  }

  @Test
  void compressedPublicKeyIs33Bytes() {
    final Bytes compressed = signer1.deriveCompressedPublicKeyFromPrivate();

    assertThat(compressed.size()).isEqualTo(33);
    // Must start with 0x02 or 0x03 (compressed point prefix)
    final byte prefix = compressed.get(0);
    assertThat(prefix == 0x02 || prefix == 0x03).isTrue();
  }

  @Test
  void compressedPublicKeyMatchesNodeKey() {
    final Bytes compressed = signer1.deriveCompressedPublicKeyFromPrivate();

    // Decompress and verify it matches the original public key
    final var ecPoint =
        SIGNATURE_ALGORITHM.getCurve().getCurve().decodePoint(compressed.toArrayUnsafe());
    final byte[] uncompressedEncoded = ecPoint.getEncoded(false);
    // Skip the 0x04 prefix
    final Bytes uncompressed = Bytes.wrap(uncompressedEncoded, 1, 64);

    assertThat(uncompressed).isEqualTo(nodeKey1.getPublicKey().getEncodedBytes());
  }

  /** Creates a NodeKeySigner via reflection (it is a private inner class). */
  private static Signer createNodeKeySigner(final NodeKey nodeKey) throws Exception {
    final Class<?> signerClass =
        Class.forName(
            "org.hyperledger.besu.ethereum.p2p.discovery.discv5.PeerDiscoveryAgentFactoryV5$NodeKeySigner");
    final Constructor<?> constructor = signerClass.getDeclaredConstructor(NodeKey.class);
    constructor.setAccessible(true);
    return (Signer) constructor.newInstance(nodeKey);
  }
}

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

import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * An implementation of the {@link org.ethereum.beacon.discovery.crypto.Signer} interface that uses
 * a local {@link NodeKey} for signing and key agreement.
 */
public class LocalNodeKeySigner implements org.ethereum.beacon.discovery.crypto.Signer {
  private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithmFactory.getInstance();

  private final NodeKey nodeKey;

  /**
   * Creates a new LocalNodeKeySigner.
   *
   * @param nodeKey the node key to use for signing and key agreement
   */
  public LocalNodeKeySigner(final NodeKey nodeKey) {
    this.nodeKey = nodeKey;
  }

  /**
   * Derives a shared secret using ECDH with the given peer public key.
   *
   * @param destPubKey the destination peer's public key
   * @return the derived shared secret
   */
  @Override
  public Bytes deriveECDHKeyAgreement(final Bytes destPubKey) {
    SECPPublicKey publicKey = signatureAlgorithm.createPublicKey(destPubKey);
    return nodeKey.calculateECDHKeyAgreement(publicKey);
  }

  /**
   * Creates a signature of message `x`.
   *
   * @param messageHash message, hashed
   * @return ECDSA signature with properties merged together: r || s
   */
  @Override
  public Bytes sign(final Bytes32 messageHash) {
    Bytes signature = nodeKey.sign(messageHash).encodedBytes();
    return signature.slice(0, 64);
  }

  /**
   * Derives the compressed public key corresponding to the private key held by this module.
   *
   * @return the compressed public key
   */
  @Override
  public Bytes deriveCompressedPublicKeyFromPrivate() {
    return Bytes.wrap(
        signatureAlgorithm.publicKeyAsEcPoint(nodeKey.getPublicKey()).getEncoded(true));
  }
}

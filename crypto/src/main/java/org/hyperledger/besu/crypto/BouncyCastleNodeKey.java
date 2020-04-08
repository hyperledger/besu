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

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.PublicKey;
import org.hyperledger.besu.crypto.SECP256K1.Signature;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BouncyCastleNodeKey implements NodeKey {

  private final KeyPair nodeKeys;

  public BouncyCastleNodeKey(final KeyPair nodeKeys) {
    this.nodeKeys = nodeKeys;
  }

  public static BouncyCastleNodeKey generate() {
    return new BouncyCastleNodeKey(KeyPair.generate());
  }

  @Override
  public Signature sign(final Bytes32 dataHash) {
    return SECP256K1.sign(dataHash, nodeKeys);
  }

  @Override
  public PublicKey getPublicKey() {
    return nodeKeys.getPublicKey();
  }

  @Override
  public Bytes32 calculateECDHKeyAgreement(final PublicKey publicKey) {
    return SECP256K1.calculateECDHKeyAgreement(nodeKeys.getPrivateKey(), publicKey);
  }

  @Override
  public Bytes calculateECIESKeyAgreement(final SECP256K1.PublicKey pubKey) {
    return SECP256K1.calculateECIESKeyAgreement(nodeKeys.getPrivateKey(), pubKey);
  }
}

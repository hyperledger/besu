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

import org.hyperledger.besu.plugin.services.securitymodule.PublicKey;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.securitymodule.Signature;

import org.apache.tuweni.bytes.Bytes32;

/**
 * Implementation of SecurityModule wrapping SECP256K1.KeyPair and performing cryptographic
 * operations via SECP256K1 class
 */
public class KeyPairSecurityModule implements SecurityModule {
  private final SECP256K1.KeyPair keyPair;

  public KeyPairSecurityModule(final SECP256K1.KeyPair keyPair) {
    this.keyPair = keyPair;
  }

  @Override
  public Signature sign(final Bytes32 dataHash) {
    final SECP256K1.Signature signature = SECP256K1.sign(dataHash, keyPair);
    return new Signature(signature.getR(), signature.getS());
  }

  @Override
  public PublicKey getPublicKey() {
    final SECP256K1.PublicKey pubKey = keyPair.getPublicKey();
    return new PublicKey(pubKey.getEncodedBytes());
  }

  @Override
  public Bytes32 calculateECDHKeyAgreement(final PublicKey publicKey) {
    final SECP256K1.PublicKey pubKey = SECP256K1.PublicKey.create(publicKey.getEncoded());
    return SECP256K1.calculateECDHKeyAgreement(keyPair.getPrivateKey(), pubKey);
  }
}

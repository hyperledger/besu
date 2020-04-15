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
import org.hyperledger.besu.plugin.services.securitymodule.PublicKey;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.securitymodule.Signature;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Implementation of SecurityModule deferring cryptographic operations via SECP256K1 class which
 * uses BouncyCastle.
 */
public class BouncyCastleSecurityModule implements SecurityModule {

  private final Supplier<KeyPair> keyPairSupplier;

  public BouncyCastleSecurityModule(final Supplier<KeyPair> keyPairSupplier) {
    // memoize allows to cache results after first get
    this.keyPairSupplier = Suppliers.memoize(keyPairSupplier);
  }

  @Override
  public Signature sign(final Bytes32 dataHash) {
    final SECP256K1.Signature signature = SECP256K1.sign(dataHash, keyPairSupplier.get());
    return new Signature(signature.getR(), signature.getS());
  }

  @Override
  public PublicKey getPublicKey() {
    final SECP256K1.PublicKey pubKey = keyPairSupplier.get().getPublicKey();
    return new PublicKey(pubKey.getEncodedBytes());
  }

  @Override
  public Bytes32 calculateECDHKeyAgreement(final PublicKey publicKey) {
    final SECP256K1.PublicKey pubKey = SECP256K1.PublicKey.create(publicKey.getEncoded());
    return SECP256K1.calculateECDHKeyAgreement(keyPairSupplier.get().getPrivateKey(), pubKey);
  }
}

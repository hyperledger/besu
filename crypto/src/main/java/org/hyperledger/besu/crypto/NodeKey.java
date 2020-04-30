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

import org.hyperledger.besu.plugin.services.securitymodule.SecurityModule;
import org.hyperledger.besu.plugin.services.securitymodule.data.Signature;

import org.apache.tuweni.bytes.Bytes32;

public class NodeKey {

  private final SecurityModule securityModule;

  public NodeKey(final SecurityModule securityModule) {
    this.securityModule = securityModule;
  }

  public SECP256K1.Signature sign(final Bytes32 dataHash) {
    final Signature signature = securityModule.sign(dataHash);

    return SECP256K1.normaliseSignature(
        signature.getR(), signature.getS(), getPublicKey(), dataHash);
  }

  public SECP256K1.PublicKey getPublicKey() {
    return SECP256K1.PublicKey.create(
        ECPointUtil.getEncodedBytes(securityModule.getPublicKey().getW()));
  }

  public Bytes32 calculateECDHKeyAgreement(final SECP256K1.PublicKey partyKey) {
    return securityModule.calculateECDHKeyAgreement(
        () -> ECPointUtil.fromBouncyCastleECPoint(partyKey.asEcPoint()));
  }
}

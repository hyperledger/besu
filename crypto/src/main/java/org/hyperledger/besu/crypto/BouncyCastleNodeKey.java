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

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.util.BigIntegers;

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
  public Bytes32 calculateKeyAgreement(final PublicKey partyKey) {
    return SECP256K1.calculateKeyAgreement(nodeKeys.getPrivateKey(), partyKey);
  }

  @Override
  public Bytes calculateECIESAgreement(final SECP256K1.PublicKey ephPubKey) {
    final ECDomainParameters dp = SECP256K1.CURVE;

    final CipherParameters pubParam = new ECPublicKeyParameters(ephPubKey.asEcPoint(), dp);
    final CipherParameters privParam =
        new ECPrivateKeyParameters(nodeKeys.getPrivateKey().getD(), dp);

    final BasicAgreement agree = new ECDHBasicAgreement();
    agree.init(privParam);
    final BigInteger z = agree.calculateAgreement(pubParam);
    return Bytes.wrap(BigIntegers.asUnsignedByteArray(agree.getFieldSize(), z));
  }
}

/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.privacy.markertransaction;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.util.NonceProvider;

public class FixedKeySigningPrivateMarkerTransactionFactory
    extends PrivateMarkerTransactionFactory {

  private final NonceProvider nonceProvider;
  private final KeyPair signingKey;
  private final Address sender;

  public FixedKeySigningPrivateMarkerTransactionFactory(
      final Address privacyPrecompileAddress,
      final NonceProvider nonceProvider,
      final KeyPair signingKey) {
    super(privacyPrecompileAddress);
    this.nonceProvider = nonceProvider;
    this.signingKey = signingKey;
    this.sender = Util.publicKeyToAddress(signingKey.getPublicKey());
  }

  @Override
  public Transaction create(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction) {
    return create(
        transactionEnclaveKey, privateTransaction, nonceProvider.getNonce(sender), signingKey);
  }
}

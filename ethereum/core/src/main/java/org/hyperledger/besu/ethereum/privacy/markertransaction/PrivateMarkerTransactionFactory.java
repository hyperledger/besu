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
package org.hyperledger.besu.ethereum.privacy.markertransaction;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.util.bytes.BytesValues;

public abstract class PrivateMarkerTransactionFactory {

  private final Address privacyPrecompileAddress;

  public PrivateMarkerTransactionFactory(final Address privacyPrecompileAddress) {
    this.privacyPrecompileAddress = privacyPrecompileAddress;
  }

  private Address getPrivacyPrecompileAddress() {
    return privacyPrecompileAddress;
  }

  public abstract Transaction create(
      final String transactionEnclaveKey, final PrivateTransaction privateTransaction);

  protected Transaction create(
      final String transactionEnclaveKey,
      final PrivateTransaction privateTransaction,
      final long nonce,
      final KeyPair signingKey) {
    return Transaction.builder()
        .nonce(nonce)
        .gasPrice(privateTransaction.getGasPrice())
        .gasLimit(privateTransaction.getGasLimit())
        .to(getPrivacyPrecompileAddress())
        .value(privateTransaction.getValue())
        .payload(BytesValues.fromBase64(transactionEnclaveKey))
        .signAndBuild(signingKey);
  }
}

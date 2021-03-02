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

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.plugin.data.TransactionType;

import org.apache.tuweni.bytes.Bytes;

public abstract class PrivateMarkerTransactionFactory {

  private final Address privacyPrecompileAddress;

  protected PrivateMarkerTransactionFactory(final Address privacyPrecompileAddress) {
    this.privacyPrecompileAddress = privacyPrecompileAddress;
  }

  public Transaction create(
      final String privateTransactionLookupId, final PrivateTransaction privateTransaction) {
    return create(privateTransactionLookupId, privateTransaction, privacyPrecompileAddress);
  }

  public abstract Transaction create(
      final String privateTransactionLookupId,
      final PrivateTransaction privateTransaction,
      final Address precompileAddress);

  protected Transaction create(
      final String privateTransactionLookupId,
      final PrivateTransaction privateTransaction,
      final long nonce,
      final KeyPair signingKey,
      final Address precompileAddress) {
    return Transaction.builder()
        .type(TransactionType.FRONTIER)
        .nonce(nonce)
        .gasPrice(privateTransaction.getGasPrice())
        .gasLimit(privateTransaction.getGasLimit())
        .to(precompileAddress)
        .value(privateTransaction.getValue())
        .payload(Bytes.fromBase64String(privateTransactionLookupId))
        .signAndBuild(signingKey);
  }
}

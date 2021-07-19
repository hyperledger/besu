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
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.util.NonceProvider;
import org.hyperledger.besu.plugin.data.TransactionType;

import org.apache.tuweni.bytes.Bytes;

public class FixedKeySigningPrivateMarkerTransactionFactory
    implements PrivateMarkerTransactionFactory {

  private final NonceProvider nonceProvider;
  private final KeyPair signingKey;
  private final Address sender;

  public FixedKeySigningPrivateMarkerTransactionFactory(
      final NonceProvider nonceProvider, final KeyPair signingKey) {
    this.nonceProvider = nonceProvider;
    this.signingKey = signingKey;
    this.sender = Util.publicKeyToAddress(signingKey.getPublicKey());
  }

  @Override
  public Transaction create(
      final String privateMarkerTransactionPayload,
      final PrivateTransaction privateTransaction,
      final Address precompileAddress,
      final String privacyUserId) {
    final long nonce =
        nonceProvider.getNonce(org.hyperledger.besu.ethereum.core.Address.fromPlugin(sender));

    return Transaction.builder()
        .type(TransactionType.FRONTIER)
        .nonce(nonce)
        .gasPrice(Wei.fromQuantity(privateTransaction.getGasPrice()))
        .gasLimit(privateTransaction.getGasLimit())
        .to(org.hyperledger.besu.ethereum.core.Address.fromPlugin(precompileAddress))
        .value(Wei.fromQuantity(privateTransaction.getValue()))
        .payload(Bytes.fromBase64String(privateMarkerTransactionPayload))
        .signAndBuild(signingKey);
  }
}

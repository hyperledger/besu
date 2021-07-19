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

import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.util.NonceProvider;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionSigner;

import org.apache.tuweni.bytes.Bytes;

public class PrivacyPluginSigningPrivateMarkerTransactionFactory
    implements PrivateMarkerTransactionFactory {
  private final PrivateMarkerTransactionSigner privateMarkerTransactionSigner;
  private final NonceProvider nonceProvider;

  public PrivacyPluginSigningPrivateMarkerTransactionFactory(
      final PrivateMarkerTransactionSigner privateMarkerTransactionSigner,
      final NonceProvider nonceProvider) {
    this.privateMarkerTransactionSigner = privateMarkerTransactionSigner;
    this.nonceProvider = nonceProvider;
  }

  @Override
  public Transaction create(
      final String privateMarkerTransactionPayload,
      final PrivateTransaction privateTransaction,
      final Address precompileAddress,
      final String privacyUserId) {

    final Address sender =
        Address.fromPlugin(privateMarkerTransactionSigner.getSender(privacyUserId));
    long nonce = nonceProvider.getNonce(sender);

    final Transaction.Builder transactionBuilder =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(nonce)
            .gasPrice(Wei.fromQuantity(privateTransaction.getGasPrice()))
            .gasLimit(privateTransaction.getGasLimit())
            .to(Address.fromPlugin(precompileAddress))
            .value(Wei.fromQuantity(privateTransaction.getValue()))
            .payload(Bytes.fromBase64String(privateMarkerTransactionPayload));

    final Transaction transactionToSign = transactionBuilder.build();

    final Bytes rawSignature =
        privateMarkerTransactionSigner.signPrivacyMarkerTransaction(
            transactionToSign, privacyUserId);

    final SECPSignature signature =
        SignatureAlgorithmFactory.getInstance().decodeSignature(rawSignature);
    return transactionBuilder.signature(signature).build();
  }
}

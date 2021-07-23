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
package org.hyperledger.besu.plugins;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.PrivateTransaction;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;
import org.hyperledger.besu.plugin.services.query.EthQueryService;

import org.apache.tuweni.bytes.Bytes;

public class TestPrivateMarkerTransactionFactory implements PrivateMarkerTransactionFactory {
  final KeyPair randomFixedSigningKey = SignatureAlgorithmFactory.getInstance().generateKeyPair();

  final Address sender =
      org.hyperledger.besu.ethereum.core.Address.extract(
          Hash.hash(randomFixedSigningKey.getPublicKey().getEncodedBytes()));
  private final EthQueryService ethQueryService;

  public TestPrivateMarkerTransactionFactory(final EthQueryService ethQueryService) {

    this.ethQueryService = ethQueryService;
  }

  @Override
  public Bytes create(
      final String privateMarkerTransactionPayload,
      final PrivateTransaction privateTransaction,
      final Address precompileAddress,
      final String privacyUserId) {

    final long nonce = ethQueryService.getTransactionCount(sender);
    final org.hyperledger.besu.ethereum.core.Transaction privacyMarkerTransaction =
        org.hyperledger.besu.ethereum.core.Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(nonce)
            .gasPrice(Wei.fromQuantity(privateTransaction.getGasPrice()))
            .gasLimit(privateTransaction.getGasLimit())
            .to(org.hyperledger.besu.ethereum.core.Address.fromPlugin(precompileAddress))
            .value(Wei.fromQuantity(privateTransaction.getValue()))
            .payload(Bytes.fromBase64String(privateMarkerTransactionPayload))
            .signAndBuild(randomFixedSigningKey);

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    privacyMarkerTransaction.writeTo(out);
    return out.encoded();
  }

  @Override
  public Address getSender(
      final PrivateTransaction privateTransaction, final String privacyUserId) {
    return sender;
  }
}

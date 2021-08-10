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
package org.hyperledger.besu.plugins.privacy;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.hyperledger.besu.ethereum.core.Address.extract;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.plugin.data.Address;
import org.hyperledger.besu.plugin.data.PrivateTransaction;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.data.UnsignedPrivateMarkerTransaction;
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class TestSigningPrivateMarkerTransactionFactory implements PrivateMarkerTransactionFactory {

  private static final Logger LOG = getLogger();

  KeyPair aliceFixedSigningKey;
  Address sender;

  public void setSigningKeyEnbaled(final String privateMarkerTransactionSigningKey) {
    final SignatureAlgorithm algorithm = SignatureAlgorithmFactory.getInstance();
    final SECPPrivateKey privateKey =
        algorithm.createPrivateKey(Bytes32.fromHexString(privateMarkerTransactionSigningKey));

    aliceFixedSigningKey = algorithm.createKeyPair(privateKey);
    sender = extract(Hash.hash(aliceFixedSigningKey.getPublicKey().getEncodedBytes()));
  }

  @Override
  public Bytes create(
      final UnsignedPrivateMarkerTransaction unsignedPrivateMarkerTransaction,
      final PrivateTransaction privateTransaction,
      final String privacyUserId) {

    final Transaction transaction =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .nonce(unsignedPrivateMarkerTransaction.getNonce())
            .gasPrice(
                unsignedPrivateMarkerTransaction.getGasPrice().map(Wei::fromQuantity).orElse(null))
            .gasLimit(unsignedPrivateMarkerTransaction.getGasLimit())
            .to(
                org.hyperledger.besu.ethereum.core.Address.fromPlugin(
                    unsignedPrivateMarkerTransaction.getTo().get()))
            .value(Wei.fromQuantity(unsignedPrivateMarkerTransaction.getValue()))
            .payload(unsignedPrivateMarkerTransaction.getPayload())
            .signAndBuild(aliceFixedSigningKey);

    LOG.info("Signing PMT from " + sender);

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    transaction.writeTo(out);
    return out.encoded();
  }

  @Override
  public Address getSender(
      final PrivateTransaction privateTransaction, final String privacyUserId) {
    return sender;
  }
}

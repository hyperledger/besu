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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FixedKeySigningPrivateMarkerTransactionFactoryTest {

  @Test
  public void createsFullyPopulatedPrivateMarkerTransactionUsingProvidedNonce() {
    final PrivateTransaction privTransaction = mock(PrivateTransaction.class);

    final Wei gasPrice = Wei.of(100);
    final long gasLimit = 500;

    final long providedNonce = 100;
    final String enclaveKey = "pmtLookupKey";

    final KeyPair signingKeys = SignatureAlgorithmFactory.getInstance().generateKeyPair();
    final Address precompiledAddress = Address.fromHexString("1");

    final FixedKeySigningPrivateMarkerTransactionFactory factory =
        new FixedKeySigningPrivateMarkerTransactionFactory(signingKeys);

    final Transaction unsignedPrivateMarkerTransaction =
        new Transaction.Builder()
            .type(TransactionType.FRONTIER)
            .nonce(providedNonce)
            .gasPrice(gasPrice)
            .gasLimit(gasLimit)
            .to(precompiledAddress)
            .value(Wei.ZERO)
            .payload(Bytes.fromBase64String(enclaveKey))
            .build();

    final Transaction transaction =
        Transaction.readFrom(factory.create(unsignedPrivateMarkerTransaction, privTransaction, ""));

    assertThat(transaction.getNonce()).isEqualTo(providedNonce);
    assertThat(transaction.getGasLimit()).isEqualTo(gasLimit);
    assertThat(transaction.getGasPrice().get()).isEqualTo(gasPrice);
    assertThat(transaction.getValue()).isEqualTo(Wei.ZERO);
    assertThat(transaction.getSender())
        .isEqualTo(Util.publicKeyToAddress(signingKeys.getPublicKey()));
    assertThat(transaction.getTo()).isEqualTo(Optional.of(precompiledAddress));
    assertThat(transaction.getPayload()).isEqualTo(Bytes.fromBase64String(enclaveKey));

    final Transaction nextTransaction =
        Transaction.readFrom(factory.create(unsignedPrivateMarkerTransaction, privTransaction, ""));
    assertThat(nextTransaction.getSender()).isEqualTo(transaction.getSender());
  }
}

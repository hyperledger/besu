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
package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;
import static org.hyperledger.besu.ethereum.util.PrivacyUtil.getPrivacyGroup;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.math.BigInteger;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivateTransactionHandlerTest {

  private static final String TRANSACTION_KEY = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";
  private static final KeyPair KEY_PAIR =
      KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private PrivateTransactionHandler privateTransactionHandler;
  private PrivateTransactionHandler brokenPrivateTransactionHandler;
  private PrivateTransactionValidator privateTransactionValidator;

  private static final Transaction PUBLIC_TRANSACTION =
      Transaction.builder()
          .nonce(0)
          .gasPrice(Wei.of(1000))
          .gasLimit(3000000)
          .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
          .value(Wei.ZERO)
          .payload(BytesValues.fromBase64(TRANSACTION_KEY))
          .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
          .chainId(BigInteger.valueOf(2018))
          .signAndBuild(KEY_PAIR);

  Enclave mockEnclave() {
    Enclave mockEnclave = mock(Enclave.class);
    SendResponse response = new SendResponse(TRANSACTION_KEY);
    ReceiveResponse receiveResponse = new ReceiveResponse(new byte[0], "mock");
    when(mockEnclave.send(any(SendRequest.class))).thenReturn(response);
    when(mockEnclave.receive(any(ReceiveRequest.class))).thenReturn(receiveResponse);
    return mockEnclave;
  }

  Enclave brokenMockEnclave() {
    Enclave mockEnclave = mock(Enclave.class);
    when(mockEnclave.send(any(SendRequest.class))).thenThrow(EnclaveException.class);
    return mockEnclave;
  }

  PrivateTransactionValidator mockPrivateTransactionValidator() {
    PrivateTransactionValidator validator = mock(PrivateTransactionValidator.class);
    when(validator.validate(any(), any())).thenReturn(ValidationResult.valid());
    return validator;
  }

  @Before
  public void setUp() throws Exception {
    final PrivateNonceProvider privateNonceProvider = mock(PrivateNonceProvider.class);
    when(privateNonceProvider.getNonce(any(Address.class), any(BytesValue.class))).thenReturn(1L);

    privateTransactionValidator = mockPrivateTransactionValidator();

    final PrivateTransactionSimulator privateTransactionSimulator =
        mock(PrivateTransactionSimulator.class);

    privateTransactionHandler =
        new PrivateTransactionHandler(
            mockEnclave(),
            privateTransactionValidator,
            new FixedKeySigningPrivateMarkerTransactionFactory(
                Address.DEFAULT_PRIVACY, (address) -> 0, KEY_PAIR),
            privateNonceProvider,
            privateTransactionSimulator);
    brokenPrivateTransactionHandler =
        new PrivateTransactionHandler(
            brokenMockEnclave(),
            privateTransactionValidator,
            new FixedKeySigningPrivateMarkerTransactionFactory(
                Address.DEFAULT_PRIVACY, (address) -> 0, KEY_PAIR),
            privateNonceProvider,
            privateTransactionSimulator);
  }

  @Test
  public void validLegacyTransactionThroughHandler() {

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(1);

    final String enclaveKey = privateTransactionHandler.sendToOrion(transaction);

    final String privacyGroupId = getPrivacyGroup(transaction);

    final ValidationResult<TransactionInvalidReason> validationResult =
        privateTransactionHandler.validatePrivateTransaction(transaction, privacyGroupId);

    final Transaction markerTransaction =
        privateTransactionHandler.createPrivacyMarkerTransaction(enclaveKey, transaction);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
    assertThat(markerTransaction.contractAddress()).isEqualTo(PUBLIC_TRANSACTION.contractAddress());
    assertThat(markerTransaction.getPayload()).isEqualTo(PUBLIC_TRANSACTION.getPayload());
    assertThat(markerTransaction.getNonce()).isEqualTo(PUBLIC_TRANSACTION.getNonce());
    assertThat(markerTransaction.getSender()).isEqualTo(PUBLIC_TRANSACTION.getSender());
    assertThat(markerTransaction.getValue()).isEqualTo(PUBLIC_TRANSACTION.getValue());
  }

  @Test
  public void validBesuTransactionThroughHandler() {

    final PrivateTransaction transaction = buildBesuPrivateTransaction(1);

    final String enclaveKey = privateTransactionHandler.sendToOrion(transaction);

    final ValidationResult<TransactionInvalidReason> validationResult =
        privateTransactionHandler.validatePrivateTransaction(
            transaction, transaction.getPrivacyGroupId().get().toString());

    final Transaction markerTransaction =
        privateTransactionHandler.createPrivacyMarkerTransaction(enclaveKey, transaction);

    assertThat(validationResult).isEqualTo(ValidationResult.valid());
    assertThat(markerTransaction.contractAddress()).isEqualTo(PUBLIC_TRANSACTION.contractAddress());
    assertThat(markerTransaction.getPayload()).isEqualTo(PUBLIC_TRANSACTION.getPayload());
    assertThat(markerTransaction.getNonce()).isEqualTo(PUBLIC_TRANSACTION.getNonce());
    assertThat(markerTransaction.getSender()).isEqualTo(PUBLIC_TRANSACTION.getSender());
    assertThat(markerTransaction.getValue()).isEqualTo(PUBLIC_TRANSACTION.getValue());
  }

  @Test
  public void enclaveIsDownWhileHandling() {
    assertThatExceptionOfType(EnclaveException.class)
        .isThrownBy(
            () -> brokenPrivateTransactionHandler.sendToOrion(buildLegacyPrivateTransaction()));
  }

  @Test
  public void nonceTooLowError() {
    when(privateTransactionValidator.validate(any(), any()))
        .thenReturn(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(0);
    final String privacyGroupId = getPrivacyGroup(transaction);
    final ValidationResult<TransactionInvalidReason> validationResult =
        privateTransactionHandler.validatePrivateTransaction(transaction, privacyGroupId);
    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));
  }

  @Test
  public void incorrectNonceError() {
    when(privateTransactionValidator.validate(any(), any()))
        .thenReturn(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(2);

    final String privacyGroupId = getPrivacyGroup(transaction);
    final ValidationResult<TransactionInvalidReason> validationResult =
        privateTransactionHandler.validatePrivateTransaction(transaction, privacyGroupId);
    assertThat(validationResult).isEqualTo(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));
  }

  private static PrivateTransaction buildLegacyPrivateTransaction() {
    return buildLegacyPrivateTransaction(0);
  }

  private static PrivateTransaction buildLegacyPrivateTransaction(final long nonce) {
    return buildPrivateTransaction(nonce)
        .privateFrom(BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
        .privateFor(
            Lists.newArrayList(
                BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="),
                BytesValues.fromBase64("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=")))
        .signAndBuild(KEY_PAIR);
  }

  private static PrivateTransaction buildBesuPrivateTransaction(final long nonce) {

    return buildPrivateTransaction(nonce)
        .privateFrom(BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
        .privacyGroupId(BytesValues.fromBase64("DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w="))
        .signAndBuild(KEY_PAIR);
  }

  private static PrivateTransaction.Builder buildPrivateTransaction(final long nonce) {
    return PrivateTransaction.builder()
        .nonce(nonce)
        .gasPrice(Wei.of(1000))
        .gasLimit(3000000)
        .to(Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57"))
        .value(Wei.ZERO)
        .payload(BytesValue.fromHexString("0x"))
        .sender(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"))
        .chainId(BigInteger.valueOf(2018))
        .restriction(Restriction.RESTRICTED);
  }
}

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
package tech.pegasys.pantheon.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.INCORRECT_PRIVATE_NONCE;
import static tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason.PRIVATE_NONCE_TOO_LOW;

import tech.pegasys.orion.testutil.OrionKeyUtils;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.EnclaveException;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendResponse;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.privacy.markertransaction.FixedKeySigningPrivateMarkerTransactionFactory;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.math.BigInteger;
import java.util.Optional;

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
    PrivateStateStorage privateStateStorage = mock(PrivateStateStorage.class);
    Hash mockHash = mock(Hash.class);
    when(privateStateStorage.getPrivateAccountState(any(BytesValue.class)))
        .thenReturn(Optional.of(mockHash));
    WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
    Account account = mock(Account.class);
    when(account.getNonce()).thenReturn(1L);
    MutableWorldState mutableWorldState = mock(MutableWorldState.class);
    when(worldStateArchive.getMutable(any(Hash.class))).thenReturn(Optional.of(mutableWorldState));
    when(mutableWorldState.get(any(Address.class))).thenReturn(account);

    privateTransactionValidator = mockPrivateTransactionValidator();

    privateTransactionHandler =
        new PrivateTransactionHandler(
            mockEnclave(),
            OrionKeyUtils.loadKey("orion_key_0.pub"),
            privateStateStorage,
            worldStateArchive,
            privateTransactionValidator,
            new FixedKeySigningPrivateMarkerTransactionFactory(
                Address.DEFAULT_PRIVACY, (address) -> 0, KEY_PAIR));
    brokenPrivateTransactionHandler =
        new PrivateTransactionHandler(
            brokenMockEnclave(),
            OrionKeyUtils.loadKey("orion_key_0.pub"),
            privateStateStorage,
            worldStateArchive,
            privateTransactionValidator,
            new FixedKeySigningPrivateMarkerTransactionFactory(
                Address.DEFAULT_PRIVACY, (address) -> 0, KEY_PAIR));
  }

  @Test
  public void validLegacyTransactionThroughHandler() throws Exception {

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(1);

    final String enclaveKey = privateTransactionHandler.sendToOrion(transaction);

    final String privacyGroupId =
        privateTransactionHandler.getPrivacyGroup(enclaveKey, transaction);

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
  public void validPantheonTransactionThroughHandler() throws Exception {

    final PrivateTransaction transaction = buildPantheonPrivateTransaction(1);

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
  public void nonceTooLowError() throws Exception {
    when(privateTransactionValidator.validate(any(), any()))
        .thenReturn(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(0);
    final String enclaveKey = privateTransactionHandler.sendToOrion(transaction);
    final String privacyGroupId =
        privateTransactionHandler.getPrivacyGroup(enclaveKey, transaction);
    final ValidationResult<TransactionInvalidReason> validationResult =
        privateTransactionHandler.validatePrivateTransaction(transaction, privacyGroupId);
    assertThat(validationResult).isEqualTo(ValidationResult.invalid(PRIVATE_NONCE_TOO_LOW));
  }

  @Test
  public void incorrectNonceError() throws Exception {
    when(privateTransactionValidator.validate(any(), any()))
        .thenReturn(ValidationResult.invalid(INCORRECT_PRIVATE_NONCE));

    final PrivateTransaction transaction = buildLegacyPrivateTransaction(2);

    final String enclaveKey = privateTransactionHandler.sendToOrion(transaction);
    final String privacyGroupId =
        privateTransactionHandler.getPrivacyGroup(enclaveKey, transaction);
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

  private static PrivateTransaction buildPantheonPrivateTransaction(final long nonce) {

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

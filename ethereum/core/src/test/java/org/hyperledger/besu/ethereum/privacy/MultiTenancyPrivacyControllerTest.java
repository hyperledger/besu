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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.enclave.types.PrivacyGroup.Type;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MultiTenancyPrivacyControllerTest {

  private static final String ENCLAVE_PUBLIC_KEY1 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String ENCLAVE_PUBLIC_KEY2 = "OnviftjiizpjRt+HTuFBsKo2bVqD+nNlNYL5EE7y3Id=";
  private static final String PRIVACY_GROUP_ID = "nNlNYL5EE7y3IdM=";
  private static final String ENCLAVE_KEY = "Ko2bVqD";
  private static final ArrayList<Log> LOGS = new ArrayList<>();

  @Mock private PrivacyController privacyController;
  @Mock private Enclave enclave;

  private MultiTenancyPrivacyController multiTenancyPrivacyController;

  @Before
  public void setup() {
    multiTenancyPrivacyController = new MultiTenancyPrivacyController(privacyController, enclave);
  }

  @Test
  public void
      sendsEeaTransactionWithMatchingPrivateFromAndEnclavePublicKeyAndProducesSuccessfulResponse() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY1))
            .build();

    when(privacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(ENCLAVE_KEY);

    final String enclaveKey =
        multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1);
    assertThat(enclaveKey).isEqualTo(ENCLAVE_KEY);
    verify(privacyController).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void
      sendsBesuTransactionWithEnclavePublicKeyInPrivacyGroupAndProducesSuccessfulResponse() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY1))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    when(privacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(ENCLAVE_KEY);
    final PrivacyGroup privacyGroupWithEnclavePublicKey =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            Type.PANTHEON,
            "",
            "",
            List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithEnclavePublicKey);

    final String response =
        multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1);
    assertThat(response).isEqualTo(ENCLAVE_KEY);
    verify(privacyController).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1);
    verify(enclave).retrievePrivacyGroup(PRIVACY_GROUP_ID);
  }

  @Test
  public void sendEeaTransactionFailsWithValidationExceptionWhenPrivateFromDoesNotMatch() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY2))
            .build();

    assertThatThrownBy(
            () -> multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Transaction privateFrom must match enclave public key");

    verify(privacyController, never()).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void sendBesuTransactionFailsWithValidationExceptionWhenPrivateFromDoesNotMatch() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY2))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    assertThatThrownBy(
            () -> multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Transaction privateFrom must match enclave public key");

    verify(privacyController, never()).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void
      sendBesuTransactionFailsWithValidationExceptionWhenPrivacyGroupDoesNotContainEnclavePublicKey() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY1))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    final PrivacyGroup privacyGroupWithoutEnclavePublicKey =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithoutEnclavePublicKey);

    assertThatThrownBy(
            () -> multiTenancyPrivacyController.sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group must contain the enclave public key");

    verify(privacyController, never()).sendTransaction(transaction, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void retrieveTransactionDelegatesToPrivacyController() {
    final ReceiveResponse delegateRetrieveResponse =
        new ReceiveResponse(new byte[] {}, PRIVACY_GROUP_ID, ENCLAVE_KEY);
    when(privacyController.retrieveTransaction(ENCLAVE_KEY, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(delegateRetrieveResponse);

    final ReceiveResponse multiTenancyRetrieveResponse =
        multiTenancyPrivacyController.retrieveTransaction(ENCLAVE_KEY, ENCLAVE_PUBLIC_KEY1);
    assertThat(multiTenancyRetrieveResponse)
        .isEqualToComparingFieldByField(delegateRetrieveResponse);
    verify(privacyController).retrieveTransaction(ENCLAVE_KEY, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void createPrivacyGroupDelegatesToPrivacyController() {
    final List<String> addresses = List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2);
    final PrivacyGroup delegatePrivacyGroup =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "name", "description", addresses);

    when(privacyController.createPrivacyGroup(
            addresses, "name", "description", ENCLAVE_PUBLIC_KEY1))
        .thenReturn(delegatePrivacyGroup);

    final PrivacyGroup privacyGroup =
        multiTenancyPrivacyController.createPrivacyGroup(
            addresses, "name", "description", ENCLAVE_PUBLIC_KEY1);
    assertThat(privacyGroup).isEqualToComparingFieldByField(delegatePrivacyGroup);
    verify(privacyController)
        .createPrivacyGroup(addresses, "name", "description", ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void deletesPrivacyGroupWhenEnclavePublicKeyInPrivacyGroup() {
    final PrivacyGroup privacyGroupWithEnclavePublicKey =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            Type.PANTHEON,
            "",
            "",
            List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithEnclavePublicKey);
    when(privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(ENCLAVE_PUBLIC_KEY1);

    final String privacyGroupId =
        multiTenancyPrivacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
    assertThat(privacyGroupId).isEqualTo(ENCLAVE_PUBLIC_KEY1);
    verify(privacyController).deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void
      deletePrivacyGroupFailsWithValidationExceptionWhenPrivacyGroupDoesNotContainEnclavePublicKey() {
    final PrivacyGroup privacyGroupWithoutEnclavePublicKey =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithoutEnclavePublicKey);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.deletePrivacyGroup(
                    PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group must contain the enclave public key");
  }

  @Test
  public void findsPrivacyGroupWhenEnclavePublicKeyInAddresses() {
    final List<String> addresses = List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2);
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            Type.PANTHEON,
            "",
            "",
            List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2));
    when(privacyController.findPrivacyGroup(addresses, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(new PrivacyGroup[] {privacyGroup});

    final PrivacyGroup[] privacyGroups =
        multiTenancyPrivacyController.findPrivacyGroup(addresses, ENCLAVE_PUBLIC_KEY1);
    assertThat(privacyGroups).hasSize(1);
    assertThat(privacyGroups[0]).isEqualToComparingFieldByField(privacyGroup);
    verify(privacyController).findPrivacyGroup(addresses, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void findPrivacyGroupFailsWithValidationExceptionWhenEnclavePublicKeyNotInAddresses() {
    final List<String> addresses = List.of(ENCLAVE_PUBLIC_KEY2);

    assertThatThrownBy(
            () -> multiTenancyPrivacyController.findPrivacyGroup(addresses, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group addresses must contain the enclave public key");
  }

  @Test
  public void determinesEeaNonceWhenPrivateFromMatchesEnclavePublicKey() {
    final String[] privateFor = {ENCLAVE_PUBLIC_KEY2};
    when(privacyController.determineEeaNonce(
            ENCLAVE_PUBLIC_KEY1, privateFor, Address.ZERO, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(10L);

    final long nonce =
        multiTenancyPrivacyController.determineEeaNonce(
            ENCLAVE_PUBLIC_KEY1, privateFor, Address.ZERO, ENCLAVE_PUBLIC_KEY1);
    assertThat(nonce).isEqualTo(10);
    verify(privacyController)
        .determineEeaNonce(ENCLAVE_PUBLIC_KEY1, privateFor, Address.ZERO, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void
      determineEeaNonceFailsWithValidationExceptionWhenPrivateFromDoesNotMatchEnclavePublicKey() {
    final String[] privateFor = {ENCLAVE_PUBLIC_KEY2};
    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.determineEeaNonce(
                    ENCLAVE_PUBLIC_KEY2, privateFor, Address.ZERO, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Transaction privateFrom must match enclave public key");
  }

  @Test
  public void determineBesuNonceWhenEnclavePublicKeyInPrivacyGroup() {
    final PrivacyGroup privacyGroupWithEnclavePublicKey =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            Type.PANTHEON,
            "",
            "",
            List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithEnclavePublicKey);
    when(privacyController.determineBesuNonce(Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(10L);

    final long nonce =
        multiTenancyPrivacyController.determineBesuNonce(
            Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
    assertThat(nonce).isEqualTo(10);
    verify(privacyController)
        .determineBesuNonce(Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void
      determineBesuNonceFailsWithValidationExceptionWhenEnclavePublicKeyNotInPrivacyGroup() {
    final PrivacyGroup privacyGroupWithoutEnclavePublicKey =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY2));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithoutEnclavePublicKey);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.determineBesuNonce(
                    Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group must contain the enclave public key");
  }

  @Test
  public void simulatePrivateTransactionWorksForValidEnclaveKey() {
    final PrivacyGroup privacyGroupWithEnclavePublicKey =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY1));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithEnclavePublicKey);
    when(privacyController.simulatePrivateTransaction(any(), any(), any(), any(long.class)))
        .thenReturn(
            Optional.of(
                PrivateTransactionProcessor.Result.successful(
                    LOGS, 0, Bytes.EMPTY, ValidationResult.valid())));
    final Optional<PrivateTransactionProcessor.Result> result =
        multiTenancyPrivacyController.simulatePrivateTransaction(
            PRIVACY_GROUP_ID,
            ENCLAVE_PUBLIC_KEY1,
            new CallParameter(Address.ZERO, Address.ZERO, 0, Wei.ZERO, Wei.ZERO, Bytes.EMPTY),
            1);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.get().getValidationResult().isValid()).isTrue();
  }

  @Test
  public void simulatePrivateTransactionFailsForInvalidEnclaveKey() {
    final PrivacyGroup privacyGroupWithEnclavePublicKey =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY1));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithEnclavePublicKey);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.simulatePrivateTransaction(
                    PRIVACY_GROUP_ID,
                    ENCLAVE_PUBLIC_KEY2,
                    new CallParameter(
                        Address.ZERO, Address.ZERO, 0, Wei.ZERO, Wei.ZERO, Bytes.EMPTY),
                    1))
        .hasMessage("Privacy group must contain the enclave public key");
  }

  @Test
  public void getContractCodeWorksForValidEnclaveKey() {
    final Bytes contractCode = Bytes.fromBase64String("ZXhhbXBsZQ==");
    final PrivacyGroup privacyGroupWithEnclavePublicKey =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY1));

    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithEnclavePublicKey);
    when(privacyController.getContractCode(any(), any(), any(), any()))
        .thenReturn(Optional.of(contractCode));

    final Optional<Bytes> result =
        multiTenancyPrivacyController.getContractCode(
            PRIVACY_GROUP_ID, Address.ZERO, Hash.ZERO, ENCLAVE_PUBLIC_KEY1);

    assertThat(result).isPresent().hasValue(contractCode);
  }

  @Test
  public void getContractCodeFailsForInvalidEnclaveKey() {
    final PrivacyGroup privacyGroupWithEnclavePublicKey =
        new PrivacyGroup(PRIVACY_GROUP_ID, Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY1));
    when(enclave.retrievePrivacyGroup(PRIVACY_GROUP_ID))
        .thenReturn(privacyGroupWithEnclavePublicKey);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.getContractCode(
                    PRIVACY_GROUP_ID, Address.ZERO, Hash.ZERO, ENCLAVE_PUBLIC_KEY2))
        .hasMessage("Privacy group must contain the enclave public key");
  }
}

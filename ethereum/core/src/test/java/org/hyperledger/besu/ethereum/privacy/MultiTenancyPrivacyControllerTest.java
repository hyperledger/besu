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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.CallParameter;
import org.hyperledger.besu.evm.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MultiTenancyPrivacyControllerTest {

  private static final String ENCLAVE_PUBLIC_KEY1 = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String ENCLAVE_PUBLIC_KEY2 = "OnviftjiizpjRt+HTuFBsKo2bVqD+nNlNYL5EE7y3Id=";
  private static final String PRIVACY_GROUP_ID = "nNlNYL5EE7y3IdM=";
  private static final String ENCLAVE_KEY = "Ko2bVqD";
  private static final ArrayList<Log> LOGS = new ArrayList<>();
  private static final PrivacyGroup PANTHEON_PRIVACY_GROUP =
      new PrivacyGroup("", PrivacyGroup.Type.PANTHEON, "", "", Collections.emptyList());

  @Mock private PrivacyController privacyController;

  private MultiTenancyPrivacyController multiTenancyPrivacyController;

  @BeforeEach
  public void setup() {
    multiTenancyPrivacyController = new MultiTenancyPrivacyController(privacyController);
  }

  @Test
  public void
      sendsEeaTransactionWithMatchingPrivateFromAndPrivacyUserIdAndProducesSuccessfulResponse() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY1))
            .build();

    when(privacyController.createPrivateMarkerTransactionPayload(
            transaction, ENCLAVE_PUBLIC_KEY1, Optional.of(PANTHEON_PRIVACY_GROUP)))
        .thenReturn(ENCLAVE_KEY);

    final String enclaveKey =
        multiTenancyPrivacyController.createPrivateMarkerTransactionPayload(
            transaction, ENCLAVE_PUBLIC_KEY1, Optional.of(PANTHEON_PRIVACY_GROUP));
    assertThat(enclaveKey).isEqualTo(ENCLAVE_KEY);
    verify(privacyController)
        .createPrivateMarkerTransactionPayload(
            transaction, ENCLAVE_PUBLIC_KEY1, Optional.of(PANTHEON_PRIVACY_GROUP));
  }

  @Test
  public void sendsBesuTransactionWithPrivacyUserIdInPrivacyGroupAndProducesSuccessfulResponse() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY1))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    final PrivacyGroup privacyGroupWithPrivacyUserId =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            PrivacyGroup.Type.PANTHEON,
            "",
            "",
            List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2));
    when(privacyController.createPrivateMarkerTransactionPayload(
            transaction, ENCLAVE_PUBLIC_KEY1, Optional.of(privacyGroupWithPrivacyUserId)))
        .thenReturn(ENCLAVE_KEY);

    final String response =
        multiTenancyPrivacyController.createPrivateMarkerTransactionPayload(
            transaction, ENCLAVE_PUBLIC_KEY1, Optional.of(privacyGroupWithPrivacyUserId));
    assertThat(response).isEqualTo(ENCLAVE_KEY);
    verify(privacyController)
        .createPrivateMarkerTransactionPayload(
            transaction, ENCLAVE_PUBLIC_KEY1, Optional.of(privacyGroupWithPrivacyUserId));
  }

  @Test
  public void
      sendBesuTransactionFailsWithValidationExceptionWhenPrivacyGroupDoesNotContainPrivacyUserId() {
    final PrivateTransaction transaction =
        PrivateTransaction.builder()
            .privateFrom(Bytes.fromBase64String(ENCLAVE_PUBLIC_KEY1))
            .privacyGroupId(Bytes.fromBase64String(PRIVACY_GROUP_ID))
            .build();

    final PrivacyGroup privacyGroupWithoutPrivacyUserId =
        new PrivacyGroup(
            PRIVACY_GROUP_ID, PrivacyGroup.Type.PANTHEON, "", "", List.of(ENCLAVE_PUBLIC_KEY2));

    doThrow(
            new MultiTenancyValidationException(
                "Privacy group must contain the enclave public key"))
        .when(privacyController)
        .verifyPrivacyGroupContainsPrivacyUserId(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.createPrivateMarkerTransactionPayload(
                    transaction,
                    ENCLAVE_PUBLIC_KEY1,
                    Optional.of(privacyGroupWithoutPrivacyUserId)))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group must contain the enclave public key");

    verify(privacyController, never())
        .createPrivateMarkerTransactionPayload(
            transaction, ENCLAVE_PUBLIC_KEY1, Optional.of(privacyGroupWithoutPrivacyUserId));
  }

  @Test
  public void createPrivacyGroupDelegatesToPrivacyController() {
    final List<String> addresses = List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2);
    final PrivacyGroup delegatePrivacyGroup =
        new PrivacyGroup(
            PRIVACY_GROUP_ID, PrivacyGroup.Type.PANTHEON, "name", "description", addresses);

    when(privacyController.createPrivacyGroup(
            addresses, "name", "description", ENCLAVE_PUBLIC_KEY1))
        .thenReturn(delegatePrivacyGroup);

    final PrivacyGroup privacyGroup =
        multiTenancyPrivacyController.createPrivacyGroup(
            addresses, "name", "description", ENCLAVE_PUBLIC_KEY1);
    assertThat(privacyGroup).usingRecursiveComparison().isEqualTo(delegatePrivacyGroup);
    verify(privacyController)
        .createPrivacyGroup(addresses, "name", "description", ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void deletesPrivacyGroupWhenPrivacyUserIdInPrivacyGroup() {
    when(privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(ENCLAVE_PUBLIC_KEY1);

    final String privacyGroupId =
        multiTenancyPrivacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
    assertThat(privacyGroupId).isEqualTo(ENCLAVE_PUBLIC_KEY1);
    verify(privacyController).deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void
      deletePrivacyGroupFailsWithValidationExceptionWhenPrivacyGroupDoesNotContainPrivacyUserId() {
    doThrow(
            new MultiTenancyValidationException(
                "Privacy group must contain the enclave public key"))
        .when(privacyController)
        .verifyPrivacyGroupContainsPrivacyUserId(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.deletePrivacyGroup(
                    PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group must contain the enclave public key");
  }

  @Test
  public void findsPrivacyGroupWhenPrivacyUserIdInAddresses() {
    final List<String> addresses = List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2);
    final PrivacyGroup privacyGroup =
        new PrivacyGroup(
            PRIVACY_GROUP_ID,
            PrivacyGroup.Type.PANTHEON,
            "",
            "",
            List.of(ENCLAVE_PUBLIC_KEY1, ENCLAVE_PUBLIC_KEY2));
    when(privacyController.findPrivacyGroupByMembers(addresses, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(new PrivacyGroup[] {privacyGroup});

    final PrivacyGroup[] privacyGroups =
        multiTenancyPrivacyController.findPrivacyGroupByMembers(addresses, ENCLAVE_PUBLIC_KEY1);
    assertThat(privacyGroups).hasSize(1);
    assertThat(privacyGroups[0]).usingRecursiveComparison().isEqualTo(privacyGroup);
    verify(privacyController).findPrivacyGroupByMembers(addresses, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void findPrivacyGroupFailsWithValidationExceptionWhenPrivacyUserIdNotInAddresses() {
    final List<String> addresses = List.of(ENCLAVE_PUBLIC_KEY2);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.findPrivacyGroupByMembers(
                    addresses, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group addresses must contain the enclave public key");
  }

  @Test
  public void determineBesuNonceWhenPrivacyUserIdInPrivacyGroup() {
    when(privacyController.determineNonce(Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .thenReturn(10L);

    final long nonce =
        multiTenancyPrivacyController.determineNonce(
            Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
    assertThat(nonce).isEqualTo(10);
    verify(privacyController).determineNonce(Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void determineBesuNonceFailsWithValidationExceptionWhenPrivacyUserIdNotInPrivacyGroup() {
    doThrow(
            new MultiTenancyValidationException(
                "Privacy group must contain the enclave public key"))
        .when(privacyController)
        .verifyPrivacyGroupContainsPrivacyUserId(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.determineNonce(
                    Address.ZERO, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1))
        .isInstanceOf(MultiTenancyValidationException.class)
        .hasMessage("Privacy group must contain the enclave public key");
  }

  @Test
  public void simulatePrivateTransactionWorksForValidEnclaveKey() {
    when(privacyController.simulatePrivateTransaction(any(), any(), any(), any(long.class)))
        .thenReturn(
            Optional.of(
                TransactionProcessingResult.successful(
                    LOGS, 0, 0, Bytes.EMPTY, ValidationResult.valid())));
    final Optional<TransactionProcessingResult> result =
        multiTenancyPrivacyController.simulatePrivateTransaction(
            PRIVACY_GROUP_ID,
            ENCLAVE_PUBLIC_KEY1,
            new CallParameter(Address.ZERO, Address.ZERO, 0, Wei.ZERO, Wei.ZERO, Bytes.EMPTY),
            1);

    assertThat(result).isPresent();
    assertThat(result.get().getValidationResult().isValid()).isTrue();
  }

  @Test
  public void simulatePrivateTransactionFailsForInvalidEnclaveKey() {
    doThrow(
            new MultiTenancyValidationException(
                "Privacy group must contain the enclave public key"))
        .when(privacyController)
        .verifyPrivacyGroupContainsPrivacyUserId(
            PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY2, Optional.of(1L));
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

    when(privacyController.getContractCode(any(), any(), any(), any()))
        .thenReturn(Optional.of(contractCode));

    final Optional<Bytes> result =
        multiTenancyPrivacyController.getContractCode(
            PRIVACY_GROUP_ID, Address.ZERO, Hash.ZERO, ENCLAVE_PUBLIC_KEY1);

    assertThat(result).isPresent().hasValue(contractCode);
  }

  @Test
  public void getContractCodeFailsForInvalidEnclaveKey() {
    doThrow(
            new MultiTenancyValidationException(
                "Privacy group must contain the enclave public key"))
        .when(privacyController)
        .verifyPrivacyGroupContainsPrivacyUserId(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY2);

    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.getContractCode(
                    PRIVACY_GROUP_ID, Address.ZERO, Hash.ZERO, ENCLAVE_PUBLIC_KEY2))
        .hasMessage("Privacy group must contain the enclave public key");
  }

  @Test
  public void verifyPrivacyGroupMatchesEnclaveKeySucceeds() {
    multiTenancyPrivacyController.verifyPrivacyGroupContainsPrivacyUserId(
        PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY1);
  }

  @Test
  public void verifyPrivacyGroupDoesNotMatchEnclaveKeyThrowsException() {
    doThrow(MultiTenancyValidationException.class)
        .when(privacyController)
        .verifyPrivacyGroupContainsPrivacyUserId(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY2);
    assertThatThrownBy(
            () ->
                multiTenancyPrivacyController.verifyPrivacyGroupContainsPrivacyUserId(
                    PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY2))
        .isInstanceOf(MultiTenancyValidationException.class);
  }
}

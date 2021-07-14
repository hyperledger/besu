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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.eea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RestrictedOnChainEeaSendRawTransactionTest extends BaseEeaSendRawTransaction {
  static final String ENCLAVE_PUBLIC_KEY = "S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXo=";
  final String MOCK_ORION_KEY = "";

  final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  RestrictedOnChainEeaSendRawTransaction method;

  @Before
  public void before() {
    method =
        new RestrictedOnChainEeaSendRawTransaction(
            transactionPool, privacyController, privacyIdProvider);
  }

  @Test
  public void validOnChainTransactionPrivacyGroupIsSentToTransactionPool() {
    when(privacyController.createPrivateMarkerTransactionPayload(any(), any(), any()))
        .thenReturn(MOCK_ORION_KEY);
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivateMarkerTransaction(
            any(String.class), any(PrivateTransaction.class), any(Address.class)))
        .thenReturn(PUBLIC_TRANSACTION);
    when(transactionPool.addLocalTransaction(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    final Optional<PrivacyGroup> onChainPrivacyGroup =
        Optional.of(
            new PrivacyGroup(
                "", PrivacyGroup.Type.ONCHAIN, "", "", Arrays.asList(ENCLAVE_PUBLIC_KEY)));

    when(privacyController.findOnChainPrivacyGroupAndAddNewMembers(any(), any(), any()))
        .thenReturn(onChainPrivacyGroup);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            "0x221e930a2c18d91fca4d509eaa3512f3e01fef266f660e32473de67474b36c15");

    final JsonRpcResponse actualResponse = method.response(validPrivacyGroupTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addLocalTransaction(PUBLIC_TRANSACTION);
    verify(privacyController)
        .createPrivateMarkerTransaction(any(), any(), eq(Address.ONCHAIN_PRIVACY));
  }

  @Test
  public void transactionFailsForLegacyPrivateTransaction() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());

    method =
        new RestrictedOnChainEeaSendRawTransaction(
            transactionPool, privacyController, privacyIdProvider);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivateForTransactionRequest.getRequest().getId(),
            JsonRpcError.ONCHAIN_PRIVACY_GROUP_ID_NOT_AVAILABLE);

    final JsonRpcResponse actualResponse = method.response(validPrivateForTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void offChainPrivacyGroupTransactionFailsWhenOnchainPrivacyGroupFeatureIsEnabled() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());

    method =
        new RestrictedOnChainEeaSendRawTransaction(
            transactionPool, privacyController, privacyIdProvider);

    when(privacyController.findOnChainPrivacyGroupAndAddNewMembers(any(), any(), any()))
        .thenReturn(Optional.empty());

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            JsonRpcError.ONCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST);

    final JsonRpcResponse actualResponse = method.response(validPrivacyGroupTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void onChainPrivacyGroupTransactionFailsWhenGroupDoesNotExist() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());

    method =
        new RestrictedOnChainEeaSendRawTransaction(
            transactionPool, privacyController, privacyIdProvider);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            JsonRpcError.ONCHAIN_PRIVACY_GROUP_DOES_NOT_EXIST);

    final JsonRpcResponse actualResponse = method.response(validPrivacyGroupTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void
      transactionWithUnrestrictedTransactionTypeShouldReturnUnimplementedTransactionTypeError() {
    final JsonRpcResponse actualResponse =
        method.response(validUnrestrictedPrivacyGroupTransactionRequest);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void
      transactionWithUnsupportedTransactionTypeShouldReturnUnimplementedTransactionTypeError() {
    final JsonRpcResponse actualResponse =
        method.response(validUnsuportedPrivacyGroupTransactionRequest);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}

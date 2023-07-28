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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RestrictedFlexibleEeaSendRawTransactionTest extends BaseEeaSendRawTransaction {
  static final String ENCLAVE_PUBLIC_KEY = "S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXo=";

  final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  RestrictedFlexibleEeaSendRawTransaction method;

  @BeforeEach
  public void before() {
    method =
        new RestrictedFlexibleEeaSendRawTransaction(
            transactionPool,
            privacyIdProvider,
            privateMarkerTransactionFactory,
            address -> 0,
            privacyController);
  }

  @Test
  public void validFlexibleTransactionPrivacyGroupIsSentToTransactionPool() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());
    when(transactionPool.addTransactionViaApi(any(Transaction.class)))
        .thenReturn(ValidationResult.valid());

    when(privacyController.createPrivateMarkerTransactionPayload(any(), any(), any()))
        .thenReturn(MOCK_ORION_KEY);

    final Optional<PrivacyGroup> flexiblePrivacyGroup =
        Optional.of(
            new PrivacyGroup(
                "", PrivacyGroup.Type.FLEXIBLE, "", "", Arrays.asList(ENCLAVE_PUBLIC_KEY)));

    when(privacyController.findPrivacyGroupByGroupId(any(), any()))
        .thenReturn(flexiblePrivacyGroup);

    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            "0x5af919ad2926e1cf98292dc0f3f8f74dbc446dd96debdd97e224e4695e662ff0");

    final JsonRpcResponse actualResponse = method.response(validPrivacyGroupTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addTransactionViaApi(PUBLIC_FLEXIBLE_TRANSACTION);
  }

  @Test
  public void transactionFailsForLegacyPrivateTransaction() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivateForTransactionRequest.getRequest().getId(),
            RpcErrorType.FLEXIBLE_PRIVACY_GROUP_ID_NOT_AVAILABLE);

    final JsonRpcResponse actualResponse = method.response(validPrivateForTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void offchainPrivacyGroupTransactionFailsWhenFlexiblePrivacyGroupFeatureIsEnabled() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());

    when(privacyController.findPrivacyGroupByGroupId(any(), any())).thenReturn(Optional.empty());

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            RpcErrorType.FLEXIBLE_PRIVACY_GROUP_DOES_NOT_EXIST);

    final JsonRpcResponse actualResponse = method.response(validPrivacyGroupTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void flexiblePrivacyGroupTransactionFailsWhenGroupDoesNotExist() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            RpcErrorType.FLEXIBLE_PRIVACY_GROUP_DOES_NOT_EXIST);

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
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            RpcErrorType.UNSUPPORTED_PRIVATE_TRANSACTION_TYPE);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void
      transactionWithUnsupportedTransactionTypeShouldReturnUnimplementedTransactionTypeError() {
    final JsonRpcResponse actualResponse =
        method.response(validUnsuportedPrivacyGroupTransactionRequest);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            RpcErrorType.UNSUPPORTED_PRIVATE_TRANSACTION_TYPE);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}

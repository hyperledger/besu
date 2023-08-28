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

import static java.util.Collections.singletonList;
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
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RestrictedOffchainEeaSendRawTransactionTest extends BaseEeaSendRawTransaction {
  static final String ENCLAVE_PUBLIC_KEY = "S28yYlZxRCtuTmxOWUw1RUU3eTNJZE9udmlmdGppaXo=";

  final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  RestrictedOffchainEeaSendRawTransaction method;

  @BeforeEach
  public void before() {
    method =
        new RestrictedOffchainEeaSendRawTransaction(
            transactionPool,
            privacyIdProvider,
            privateMarkerTransactionFactory,
            address -> 0,
            privacyController);
  }

  @Test
  public void validLegacyTransactionIsSentToTransactionPool() {
    when(privacyController.createPrivateMarkerTransactionPayload(any(), any(), any()))
        .thenReturn(MOCK_ORION_KEY);
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());
    when(transactionPool.addTransactionViaApi(any())).thenReturn(ValidationResult.valid());

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            validPrivateForTransactionRequest.getRequest().getId(),
            "0x7f14b1aaa2fdddf918350d99801bf00a0eb1a1441b21b8c147f42db5ea675590");

    final JsonRpcResponse actualResponse = method.response(validPrivateForTransactionRequest);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(transactionPool).addTransactionViaApi(PUBLIC_OFF_CHAIN_TRANSACTION);
  }

  @Test
  public void validPantheonPrivacyGroupTransactionIsSentToTransactionPool() {
    when(privacyController.validatePrivateTransaction(any(), any()))
        .thenReturn(ValidationResult.valid());
    when(privacyController.createPrivateMarkerTransactionPayload(any(), any(), any()))
        .thenReturn(MOCK_ORION_KEY);

    final Optional<PrivacyGroup> pantheonPrivacyGroup =
        Optional.of(
            new PrivacyGroup(
                "", PrivacyGroup.Type.PANTHEON, "", "", singletonList(ENCLAVE_PUBLIC_KEY)));

    when(privacyController.findPrivacyGroupByGroupId(any(), any()))
        .thenReturn(pantheonPrivacyGroup);
    when(transactionPool.addTransactionViaApi(any())).thenReturn(ValidationResult.valid());

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            validPrivacyGroupTransactionRequest.getRequest().getId(),
            "0x7f14b1aaa2fdddf918350d99801bf00a0eb1a1441b21b8c147f42db5ea675590");

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

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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.VALID_BASE64_ENCLAVE_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionGroupResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionLegacyResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.PrivateTransactionResult;
import org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.ExecutedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PrivGetPrivateTransactionTest {

  @Mock private PrivacyController privacyController;

  private final User user =
      new UserImpl(
          new JsonObject().put("privacyPublicKey", VALID_BASE64_ENCLAVE_KEY.toBase64String()),
          new JsonObject());
  private final PrivacyIdProvider privacyIdProvider =
      (user) -> VALID_BASE64_ENCLAVE_KEY.toBase64String();

  private PrivGetPrivateTransaction privGetPrivateTransaction;
  private Transaction markerTransaction;

  @BeforeEach
  public void before() {
    privGetPrivateTransaction = new PrivGetPrivateTransaction(privacyController, privacyIdProvider);

    markerTransaction = PrivateTransactionDataFixture.privateMarkerTransaction();
  }

  @Test
  public void returnsPrivateTransactionLegacy() {
    final PrivateTransaction legacyPrivateTransaction =
        PrivateTransactionDataFixture.privateTransactionLegacy();
    final ExecutedPrivateTransaction executedPrivateTransaction =
        createExecutedPrivateTransaction(legacyPrivateTransaction);

    when(privacyController.findPrivateTransactionByPmtHash(any(), any()))
        .thenReturn(Optional.of(executedPrivateTransaction));

    final PrivateTransactionLegacyResult expectedResult =
        new PrivateTransactionLegacyResult(legacyPrivateTransaction);

    final JsonRpcRequestContext request = createRequestContext();
    final PrivateTransactionResult result = makeRequest(request);

    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
  }

  @Test
  public void returnsPrivateTransactionGroup() {
    final PrivateTransaction privateTransaction =
        PrivateTransactionDataFixture.privateTransactionBesu();
    final ExecutedPrivateTransaction executedPrivateTransaction =
        createExecutedPrivateTransaction(privateTransaction);

    when(privacyController.findPrivateTransactionByPmtHash(any(), any()))
        .thenReturn(Optional.of(executedPrivateTransaction));

    final PrivateTransactionGroupResult expectedResult =
        new PrivateTransactionGroupResult(privateTransaction);

    final JsonRpcRequestContext request = createRequestContext();
    final PrivateTransactionResult result = makeRequest(request);

    assertThat(result).usingRecursiveComparison().isEqualTo(expectedResult);
  }

  @Test
  public void returnNullWhenPrivateMarkerTransactionDoesNotExist() {
    when(privacyController.findPrivateTransactionByPmtHash(any(), any()))
        .thenReturn(Optional.empty());

    final JsonRpcRequestContext request = createRequestContext();

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);

    assertThat(response.getResult()).isNull();
  }

  @Test
  public void failsWithEnclaveErrorOnEnclaveError() {
    final JsonRpcRequestContext request = createRequestContext();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.ENCLAVE_ERROR);

    when(privacyController.findPrivateTransactionByPmtHash(any(), any()))
        .thenThrow(new EnclaveClientException(500, "enclave failure"));

    final JsonRpcResponse response = privGetPrivateTransaction.response(request);

    assertThat(response).isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext createRequestContext() {
    final Object[] params = new Object[] {markerTransaction.getHash()};
    return new JsonRpcRequestContext(
        new JsonRpcRequest("1", "priv_getTransactionReceipt", params), user);
  }

  private PrivateTransactionResult makeRequest(final JsonRpcRequestContext request) {
    final PrivGetPrivateTransaction privGetPrivateTransaction =
        new PrivGetPrivateTransaction(privacyController, privacyIdProvider);
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetPrivateTransaction.response(request);
    return (PrivateTransactionResult) response.getResult();
  }

  private ExecutedPrivateTransaction createExecutedPrivateTransaction(
      final PrivateTransaction legacyPrivateTransaction) {
    return new ExecutedPrivateTransaction(
        Hash.EMPTY, 0L, Hash.EMPTY, 0, "", legacyPrivateTransaction);
  }
}

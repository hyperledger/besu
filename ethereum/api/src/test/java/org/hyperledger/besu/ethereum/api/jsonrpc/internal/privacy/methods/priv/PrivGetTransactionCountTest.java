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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.impl.JWTUser;
import org.junit.Before;
import org.junit.Test;

public class PrivGetTransactionCountTest {

  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);
  private final PrivacyController privacyController = mock(PrivacyController.class);

  private static final String PRIVACY_GROUP_ID = "DyAOiF/ynpc+JXa2YAGB0bCitSlOMNm+ShmB/7M6C4w=";

  private final Address senderAddress =
      Address.fromHexString("0x627306090abab3a6e1400e9345bc60c78a8bef57");
  private final long NONCE = 5;
  private final User user =
      new JWTUser(new JsonObject().put("privacyPublicKey", ENCLAVE_PUBLIC_KEY), "");
  private final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  @Before
  public void before() {
    when(privacyParameters.isEnabled()).thenReturn(true);
    when(privacyController.determineBesuNonce(senderAddress, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY))
        .thenReturn(NONCE);
  }

  @Test
  public void verifyTransactionCount() {
    final PrivGetTransactionCount privGetTransactionCount =
        new PrivGetTransactionCount(privacyController, privacyIdProvider);

    final Object[] params = new Object[] {senderAddress, PRIVACY_GROUP_ID};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_getTransactionCount", params), user);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privGetTransactionCount.response(request);

    assertThat(response.getResult()).isEqualTo(String.format("0x%X", NONCE));
    verify(privacyController)
        .determineBesuNonce(senderAddress, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void failsWithNonceErrorIfExceptionIsThrown() {
    final PrivGetTransactionCount privGetTransactionCount =
        new PrivGetTransactionCount(privacyController, privacyIdProvider);

    when(privacyController.determineBesuNonce(senderAddress, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY))
        .thenThrow(EnclaveClientException.class);

    final Object[] params = new Object[] {senderAddress, PRIVACY_GROUP_ID};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_getTransactionCount", params), user);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
    final JsonRpcResponse response = privGetTransactionCount.response(request);
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  public void failsWithUnauthorizedErrorIfMultiTenancyValidationFails() {
    final PrivGetTransactionCount privGetTransactionCount =
        new PrivGetTransactionCount(privacyController, privacyIdProvider);

    when(privacyController.determineBesuNonce(senderAddress, PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY))
        .thenThrow(new MultiTenancyValidationException("validation failed"));

    final Object[] params = new Object[] {senderAddress, PRIVACY_GROUP_ID};
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_getTransactionCount", params), user);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
    final JsonRpcResponse response = privGetTransactionCount.response(request);
    assertThat(response).isEqualTo(expectedResponse);
  }
}

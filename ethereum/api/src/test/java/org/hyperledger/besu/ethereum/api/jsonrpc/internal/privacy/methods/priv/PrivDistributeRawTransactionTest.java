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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;

import java.util.Base64;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PrivDistributeRawTransactionTest {

  private static final String VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP =
      "0xf8ac800182520894095e7baea6a6c7c4c2dfeb977efac326af552d87"
          + "80801ba048b55bfa915ac795c431978d8a6a992b628d557da5ff"
          + "759b307d495a36649353a01fffd310ac743f371de3b9f7f9cb56"
          + "c0b28ad43601b4ab949f53faa07bd2c804a0035695b4cc4b0941"
          + "e60551d7a19cf30603db5bfc23e5ac43a56f57f25f75486aa00f"
          + "200e885ff29e973e2576b6600181d1b0a2b5294e30d9be4a1981"
          + "ffb33a0b8c8a72657374726963746564";
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  private final User user =
      new UserImpl(
          new JsonObject().put("privacyPublicKey", ENCLAVE_PUBLIC_KEY), new JsonObject()) {};
  private final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;

  private PrivDistributeRawTransaction method;
  @Mock private PrivacyController privacyController;

  @BeforeEach
  public void before() {
    method = new PrivDistributeRawTransaction(privacyController, privacyIdProvider, false);
  }

  @Test
  public void validTransactionHashReturnedAfterDistribute() {
    final String enclavePublicKey = "93Ky7lXwFkMc7+ckoFgUMku5bpr9tz4zhmWmk9RlNng=";
    when(privacyController.createPrivateMarkerTransactionPayload(
            any(PrivateTransaction.class), any(), any()))
        .thenReturn(enclavePublicKey);
    when(privacyController.validatePrivateTransaction(any(PrivateTransaction.class), anyString()))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "priv_distributeRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}),
            user);

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(),
            Bytes.wrap(Base64.getDecoder().decode(enclavePublicKey)).toString());

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(privacyController)
        .createPrivateMarkerTransactionPayload(
            any(PrivateTransaction.class), eq(ENCLAVE_PUBLIC_KEY), any());
    verify(privacyController)
        .validatePrivateTransaction(any(PrivateTransaction.class), eq(ENCLAVE_PUBLIC_KEY));
  }

  @Test
  public void invalidTransactionFailingWithMultiTenancyValidationErrorReturnsUnauthorizedError() {
    when(privacyController.createPrivateMarkerTransactionPayload(
            any(PrivateTransaction.class), any(), any()))
        .thenThrow(new MultiTenancyValidationException("validation failed"));
    when(privacyController.validatePrivateTransaction(any(PrivateTransaction.class), anyString()))
        .thenReturn(ValidationResult.valid());

    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "priv_distributeRawTransaction",
                new String[] {VALID_PRIVATE_TRANSACTION_RLP_PRIVACY_GROUP}),
            user);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), RpcErrorType.ENCLAVE_ERROR);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}

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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyValidationException;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PrivDeletePrivacyGroupTest {
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String PRIVACY_GROUP_ID = "privacyGroupId";

  private final PrivacyController privacyController = mock(PrivacyController.class);
  private final User user =
      new UserImpl(new JsonObject().put("privacyPublicKey", ENCLAVE_PUBLIC_KEY), new JsonObject());
  private final PrivacyIdProvider privacyIdProvider = (user) -> ENCLAVE_PUBLIC_KEY;
  private JsonRpcRequestContext request;

  @BeforeEach
  public void setUp() {
    request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("1", "priv_deletePrivacyGroup", new Object[] {PRIVACY_GROUP_ID}),
            user);
  }

  @Test
  public void deletesPrivacyGroupWithValidGroupId() {
    when(privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY))
        .thenReturn(PRIVACY_GROUP_ID);

    final PrivDeletePrivacyGroup privDeletePrivacyGroup =
        new PrivDeletePrivacyGroup(privacyController, privacyIdProvider);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) privDeletePrivacyGroup.response(request);
    final String result = (String) response.getResult();
    assertThat(result).isEqualTo(PRIVACY_GROUP_ID);
    verify(privacyController).deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void failsWithDeletePrivacyGroupErrorIfEnclaveFails() {
    when(privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY))
        .thenThrow(new EnclaveClientException(500, "some failure"));

    final PrivDeletePrivacyGroup privDeletePrivacyGroup =
        new PrivDeletePrivacyGroup(privacyController, privacyIdProvider);

    final JsonRpcErrorResponse response =
        (JsonRpcErrorResponse) privDeletePrivacyGroup.response(request);
    assertThat(response.getErrorType()).isEqualTo(RpcErrorType.DELETE_PRIVACY_GROUP_ERROR);
    verify(privacyController).deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY);
  }

  @Test
  public void failsWithUnauthorizedErrorIfMultiTenancyValidationFails() {
    when(privacyController.deletePrivacyGroup(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY))
        .thenThrow(new MultiTenancyValidationException("validation failed"));

    final PrivDeletePrivacyGroup privDeletePrivacyGroup =
        new PrivDeletePrivacyGroup(privacyController, privacyIdProvider);

    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.DELETE_PRIVACY_GROUP_ERROR);

    final JsonRpcResponse response = privDeletePrivacyGroup.response(request);
    assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);

    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse).isEqualTo(expectedResponse);
  }
}

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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.PrivateSubscribeRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import io.vertx.core.json.Json;
import io.vertx.ext.auth.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivSubscribeTest {

  private final String ENCLAVE_KEY = "enclave_key";
  private final String PRIVACY_GROUP_ID = "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";

  @Mock private SubscriptionManager subscriptionManagerMock;
  @Mock private SubscriptionRequestMapper mapperMock;
  @Mock private PrivacyController privacyController;
  @Mock private PrivacyIdProvider privacyIdProvider;

  private PrivSubscribe privSubscribe;

  @Before
  public void before() {
    privSubscribe =
        new PrivSubscribe(
            subscriptionManagerMock, mapperMock, privacyController, privacyIdProvider);
  }

  @Test
  public void expectedMethodName() {
    assertThat(privSubscribe.getName()).isEqualTo("priv_subscribe");
  }

  @Test
  public void responseContainsSubscriptionId() {
    final WebSocketRpcRequest webSocketRequest = createWebSocketRpcRequest();
    final JsonRpcRequestContext jsonRpcrequestContext = new JsonRpcRequestContext(webSocketRequest);

    final PrivateSubscribeRequest subscribeRequest =
        new PrivateSubscribeRequest(
            SubscriptionType.LOGS,
            null,
            null,
            webSocketRequest.getConnectionId(),
            PRIVACY_GROUP_ID,
            "public_key");

    when(mapperMock.mapPrivateSubscribeRequest(eq(jsonRpcrequestContext), any()))
        .thenReturn(subscribeRequest);
    when(subscriptionManagerMock.subscribe(eq(subscribeRequest))).thenReturn(1L);

    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(
            jsonRpcrequestContext.getRequest().getId(), Quantity.create((1L)));

    assertThat(privSubscribe.response(jsonRpcrequestContext)).isEqualTo(expectedResponse);
  }

  @Test
  public void invalidSubscribeRequestRespondsInvalidRequestResponse() {
    final WebSocketRpcRequest webSocketRequest = createWebSocketRpcRequest();
    final JsonRpcRequestContext jsonRpcrequestContext = new JsonRpcRequestContext(webSocketRequest);

    when(mapperMock.mapPrivateSubscribeRequest(any(), any()))
        .thenThrow(new InvalidSubscriptionRequestException());

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(
            jsonRpcrequestContext.getRequest().getId(), JsonRpcError.INVALID_REQUEST);

    assertThat(privSubscribe.response(jsonRpcrequestContext)).isEqualTo(expectedResponse);
  }

  @Test
  public void multiTenancyCheckSuccess() {
    final User user = mock(User.class);
    final WebSocketRpcRequest webSocketRequest = createWebSocketRpcRequest();
    final JsonRpcRequestContext jsonRpcrequestContext =
        new JsonRpcRequestContext(webSocketRequest, user);

    final PrivateSubscribeRequest subscribeRequest =
        new PrivateSubscribeRequest(
            SubscriptionType.LOGS,
            null,
            null,
            webSocketRequest.getConnectionId(),
            PRIVACY_GROUP_ID,
            ENCLAVE_KEY);

    when(mapperMock.mapPrivateSubscribeRequest(any(), any())).thenReturn(subscribeRequest);
    when(privacyIdProvider.getPrivacyUserId(any())).thenReturn(ENCLAVE_KEY);

    // This should pass if a MultiTenancyMultiTenancyValidationException isn't thrown

    final JsonRpcResponse response = privSubscribe.response(jsonRpcrequestContext);
    assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
  }

  private WebSocketRpcRequest createWebSocketRpcRequest() {
    return Json.decodeValue(
        "{\"id\": 1, \"method\": \"priv_subscribe\", \"params\": [\""
            + PRIVACY_GROUP_ID
            + "\", \"logs\"], \"connectionId\": \"1\"}",
        WebSocketRpcRequest.class);
  }
}

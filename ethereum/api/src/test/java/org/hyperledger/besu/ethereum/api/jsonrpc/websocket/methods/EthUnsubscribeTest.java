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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionNotFoundException;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.InvalidSubscriptionRequestException;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionRequestMapper;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.UnsubscribeRequest;

import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;

public class EthUnsubscribeTest {

  private EthUnsubscribe ethUnsubscribe;
  private SubscriptionManager subscriptionManagerMock;
  private SubscriptionRequestMapper mapperMock;
  private final String CONNECTION_ID = "test-connection-id";

  @Before
  public void before() {
    subscriptionManagerMock = mock(SubscriptionManager.class);
    mapperMock = mock(SubscriptionRequestMapper.class);
    ethUnsubscribe = new EthUnsubscribe(subscriptionManagerMock, mapperMock);
  }

  @Test
  public void nameIsEthUnsubscribe() {
    assertThat(ethUnsubscribe.getName()).isEqualTo("eth_unsubscribe");
  }

  @Test
  public void responseContainsUnsubscribeStatus() {
    final JsonRpcRequestContext request = createJsonRpcRequest();
    final UnsubscribeRequest unsubscribeRequest = new UnsubscribeRequest(1L, CONNECTION_ID);
    when(mapperMock.mapUnsubscribeRequest(eq(request))).thenReturn(unsubscribeRequest);
    when(subscriptionManagerMock.unsubscribe(eq(unsubscribeRequest))).thenReturn(true);

    final JsonRpcSuccessResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), true);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  @Test
  public void invalidUnsubscribeRequestReturnsInvalidRequestResponse() {
    final JsonRpcRequestContext request = createJsonRpcRequest();
    when(mapperMock.mapUnsubscribeRequest(any()))
        .thenThrow(new InvalidSubscriptionRequestException());

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_REQUEST);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  @Test
  public void whenSubscriptionNotFoundReturnError() {
    final JsonRpcRequestContext request = createJsonRpcRequest();
    when(mapperMock.mapUnsubscribeRequest(any())).thenReturn(mock(UnsubscribeRequest.class));
    when(subscriptionManagerMock.unsubscribe(any()))
        .thenThrow(new SubscriptionNotFoundException(1L));

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.SUBSCRIPTION_NOT_FOUND);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  @Test
  public void uncaughtErrorOnSubscriptionManagerReturnsInternalErrorResponse() {
    final JsonRpcRequestContext request = createJsonRpcRequest();
    when(mapperMock.mapUnsubscribeRequest(any())).thenReturn(mock(UnsubscribeRequest.class));
    when(subscriptionManagerMock.unsubscribe(any())).thenThrow(new RuntimeException());

    final JsonRpcErrorResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INTERNAL_ERROR);

    assertThat(ethUnsubscribe.response(request)).isEqualTo(expectedResponse);
  }

  private JsonRpcRequestContext createJsonRpcRequest() {
    return new JsonRpcRequestContext(
        Json.decodeValue(
            "{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": [\"0x0\"]}",
            JsonRpcRequest.class));
  }
}

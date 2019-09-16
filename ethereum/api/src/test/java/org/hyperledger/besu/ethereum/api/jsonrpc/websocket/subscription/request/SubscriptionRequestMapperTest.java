/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.internal.matchers.ThrowableMessageMatcher.hasMessage;

import org.hyperledger.besu.ethereum.api.TopicsParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketRpcRequest;

import java.util.Arrays;
import java.util.Collections;

import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SubscriptionRequestMapperTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private SubscriptionRequestMapper mapper;
  // These tests aren't passing through WebSocketRequestHandler, so connectionId is null.
  private final String CONNECTION_ID = null;

  @Before
  public void before() {
    mapper = new SubscriptionRequestMapper(new JsonRpcParameter());
  }

  @Test
  public void mapRequestToUnsubscribeRequest() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": [\"0x1\"]}");
    final UnsubscribeRequest expectedUnsubscribeRequest = new UnsubscribeRequest(1L, CONNECTION_ID);

    final UnsubscribeRequest unsubscribeRequest = mapper.mapUnsubscribeRequest(jsonRpcRequest);

    assertThat(unsubscribeRequest).isEqualTo(expectedUnsubscribeRequest);
  }

  @Test
  public void mapRequestToUnsubscribeRequestIgnoresSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": [\"0x1\", {\"foo\": \"bar\"}]}");
    final UnsubscribeRequest expectedUnsubscribeRequest = new UnsubscribeRequest(1L, CONNECTION_ID);

    final UnsubscribeRequest unsubscribeRequest = mapper.mapUnsubscribeRequest(jsonRpcRequest);

    assertThat(unsubscribeRequest).isEqualTo(expectedUnsubscribeRequest);
  }

  @Test
  public void mapRequestToUnsubscribeRequestMissingSubscriptionIdFails() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest("{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": []}");

    thrown.expect(InvalidSubscriptionRequestException.class);
    thrown.expectCause(
        both(hasMessage(equalTo("Missing required json rpc parameter at index 0")))
            .and(instanceOf(InvalidJsonRpcParameters.class)));

    mapper.mapUnsubscribeRequest(jsonRpcRequest);
  }

  @Test
  public void mapRequestToNewHeadsSubscribeIncludeTransactionsTrue() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"includeTransactions\": true}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewHeadsSubscribeIncludeTransactionsFalse() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"includeTransactions\": false}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewHeadsSubscribeOmittingIncludeTransactions() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\"]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewHeadsWithInvalidSecondParamFails() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"foo\": \"bar\"}]}");

    thrown.expect(InvalidSubscriptionRequestException.class);
    thrown.expectCause(
        both(hasMessage(equalTo("Invalid json rpc parameter at index 1")))
            .and(instanceOf(InvalidJsonRpcParameters.class)));

    mapper.mapSubscribeRequest(jsonRpcRequest);
  }

  @Test
  public void mapRequestToNewHeadsIgnoresThirdParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"includeTransactions\": true}, {\"foo\": \"bar\"}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestWithSingleAddress() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\"}]}");

    final FilterParameter expectedFilterParam =
        new FilterParameter(
            null,
            null,
            Arrays.asList("0x8320fe7702b96808f7bbc0d4a888ed1468216cfd"),
            new TopicsParameter(Collections.emptyList()),
            null);
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.LOGS, expectedFilterParam, null, null);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest)
        .isEqualToComparingFieldByFieldRecursively(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestWithMultipleAddresses() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": [\"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\", \"0xf17f52151EbEF6C7334FAD080c5704D77216b732\"], \"topics\": [\"0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902\"]}]}");

    final FilterParameter expectedFilterParam =
        new FilterParameter(
            null,
            null,
            Arrays.asList(
                "0x8320fe7702b96808f7bbc0d4a888ed1468216cfd",
                "0xf17f52151EbEF6C7334FAD080c5704D77216b732"),
            new TopicsParameter(
                Arrays.asList(
                    Arrays.asList(
                        "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902"))),
            null);
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.LOGS, expectedFilterParam, null, null);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest)
        .isEqualToComparingFieldByFieldRecursively(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestWithMultipleTopics() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\", \"topics\": [\"0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902\", \"0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab901\"]}]}");

    final FilterParameter expectedFilterParam =
        new FilterParameter(
            null,
            null,
            Arrays.asList("0x8320fe7702b96808f7bbc0d4a888ed1468216cfd"),
            new TopicsParameter(
                Arrays.asList(
                    Arrays.asList(
                        "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902",
                        "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab901"))),
            null);
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.LOGS, expectedFilterParam, null, null);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest)
        .isEqualToComparingFieldByFieldRecursively(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToLogsWithoutTopics() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\"}]}");

    final FilterParameter expectedFilterParam =
        new FilterParameter(
            null,
            null,
            Arrays.asList("0x8320fe7702b96808f7bbc0d4a888ed1468216cfd"),
            new TopicsParameter(Collections.emptyList()),
            null);
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.LOGS, expectedFilterParam, null, null);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest)
        .isEqualToComparingFieldByFieldRecursively(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToLogsWithInvalidTopicInFilter() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x0\", \"topics\": [\"0x1\"]}]}");

    thrown.expect(InvalidSubscriptionRequestException.class);
    thrown.expectCause(
        both(hasMessage(equalTo("Invalid odd-length hex binary representation 0x1")))
            .and(instanceOf(IllegalArgumentException.class)));

    mapper.mapSubscribeRequest(jsonRpcRequest);
  }

  @Test
  public void mapRequestToLogsWithInvalidSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"foo\": \"bar\"}]}");

    thrown.expect(InvalidSubscriptionRequestException.class);
    thrown.expectCause(
        both(hasMessage(equalTo("Invalid json rpc parameter at index 1")))
            .and(instanceOf(InvalidJsonRpcParameters.class)));

    mapper.mapSubscribeRequest(jsonRpcRequest);
  }

  @Test
  public void mapRequestToNewPendingTransactions() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newPendingTransactions\"]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_PENDING_TRANSACTIONS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewPendingTransactionsParsesSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newPendingTransactions\", {\"includeTransactions\": false}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_PENDING_TRANSACTIONS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToSyncingSubscribe() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToSyncingSubscribeParsesSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\", {\"includeTransactions\": true}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, true, CONNECTION_ID);

    final SubscribeRequest subscribeRequest = mapper.mapSubscribeRequest(jsonRpcRequest);

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapAbsentSubscriptionTypeRequestFails() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"foo\"]}");

    thrown.expect(InvalidSubscriptionRequestException.class);
    thrown.expectCause(
        both(hasMessage(equalTo("Invalid json rpc parameter at index 0")))
            .and(instanceOf(InvalidJsonRpcParameters.class)));

    mapper.mapSubscribeRequest(jsonRpcRequest);
  }

  private WebSocketRpcRequest parseWebSocketRpcRequest(final String json) {
    return Json.decodeValue(json, WebSocketRpcRequest.class);
  }
}

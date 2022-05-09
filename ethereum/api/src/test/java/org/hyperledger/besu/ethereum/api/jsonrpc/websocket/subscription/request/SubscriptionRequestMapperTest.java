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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.FilterParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketRpcRequest;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.List;
import java.util.stream.Stream;

import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;

public class SubscriptionRequestMapperTest {

  private SubscriptionRequestMapper mapper;
  // These tests aren't passing through WebSocketRequestHandler, so connectionId is null.
  private final String CONNECTION_ID = null;
  private static final String ENCLAVE_PUBLIC_KEY = "enclave_public_key";

  @Before
  public void before() {
    mapper = new SubscriptionRequestMapper();
  }

  @Test
  public void mapRequestToUnsubscribeRequest() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": [\"0x1\"]}");
    final UnsubscribeRequest expectedUnsubscribeRequest = new UnsubscribeRequest(1L, CONNECTION_ID);

    final UnsubscribeRequest unsubscribeRequest =
        mapper.mapUnsubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(unsubscribeRequest).isEqualTo(expectedUnsubscribeRequest);
  }

  @Test
  public void mapRequestToUnsubscribeRequestIgnoresSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": [\"0x1\", {\"foo\": \"bar\"}]}");
    final UnsubscribeRequest expectedUnsubscribeRequest = new UnsubscribeRequest(1L, CONNECTION_ID);

    final UnsubscribeRequest unsubscribeRequest =
        mapper.mapUnsubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(unsubscribeRequest).isEqualTo(expectedUnsubscribeRequest);
  }

  @Test
  public void mapRequestToUnsubscribeRequestMissingSubscriptionIdFails() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest("{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": []}");

    assertThatThrownBy(() -> mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest)))
        .isInstanceOf(InvalidSubscriptionRequestException.class)
        .getCause()
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void mapRequestToNewHeadsSubscribeIncludeTransactionsTrue() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"includeTransactions\": true}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewHeadsSubscribeIncludeTransactionsFalse() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"includeTransactions\": false}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewHeadsSubscribeOmittingIncludeTransactions() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\"]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewHeadsWithInvalidSecondParamFails() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"foo\": \"bar\"}]}");

    assertThatThrownBy(() -> mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest)))
        .isInstanceOf(InvalidSubscriptionRequestException.class)
        .getCause()
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 1");
  }

  @Test
  public void mapRequestToNewHeadsIgnoresThirdParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\", {\"includeTransactions\": true}, {\"foo\": \"bar\"}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_BLOCK_HEADERS, null, true, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestWithSingleAddress() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\"}]}");

    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(
            SubscriptionType.LOGS,
            new FilterParameter(
                BlockParameter.LATEST,
                BlockParameter.LATEST,
                null,
                null,
                singletonList(Address.fromHexString("0x8320fe7702b96808f7bbc0d4a888ed1468216cfd")),
                emptyList(),
                null,
                null,
                null),
            null,
            null);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).usingRecursiveComparison().isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestWithMultipleAddresses() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": [\"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\", \"0xf17f52151EbEF6C7334FAD080c5704D77216b732\"], \"topics\": [\"0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902\"]}]}");

    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(
            SubscriptionType.LOGS,
            new FilterParameter(
                BlockParameter.LATEST,
                BlockParameter.LATEST,
                null,
                null,
                Stream.of(
                        "0x8320fe7702b96808f7bbc0d4a888ed1468216cfd",
                        "0xf17f52151EbEF6C7334FAD080c5704D77216b732")
                    .map(Address::fromHexString)
                    .collect(toUnmodifiableList()),
                singletonList(
                    singletonList(
                        LogTopic.fromHexString(
                            "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902"))),
                null,
                null,
                null),
            null,
            null);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).usingRecursiveComparison().isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestWithMultipleTopics() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\", \"topics\": [\"0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902\", \"0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab901\"]}]}");

    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(
            SubscriptionType.LOGS,
            new FilterParameter(
                BlockParameter.LATEST,
                BlockParameter.LATEST,
                null,
                null,
                singletonList(Address.fromHexString("0x8320fe7702b96808f7bbc0d4a888ed1468216cfd")),
                List.of(
                    singletonList(
                        LogTopic.fromHexString(
                            "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab902")),
                    singletonList(
                        LogTopic.fromHexString(
                            "0xd78a0cb8bb633d06981248b816e7bd33c2a35a6089241d099fa519e361cab901"))),
                null,
                null,
                null),
            null,
            null);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).usingRecursiveComparison().isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToLogsWithoutTopics() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\"}]}");

    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(
            SubscriptionType.LOGS,
            new FilterParameter(
                BlockParameter.LATEST,
                BlockParameter.LATEST,
                null,
                null,
                singletonList(Address.fromHexString("0x8320fe7702b96808f7bbc0d4a888ed1468216cfd")),
                emptyList(),
                null,
                null,
                null),
            null,
            null);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).usingRecursiveComparison().isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToLogsWithInvalidTopicInFilter() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"address\": \"0x0\", \"topics\": [\"0x1\"]}]}");

    assertThatThrownBy(() -> mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest)))
        .isInstanceOf(InvalidSubscriptionRequestException.class)
        .getCause()
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 1");
  }

  @Test
  public void mapRequestToLogsWithInvalidSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"logs\", {\"foo\": \"bar\"}]}");

    assertThatThrownBy(() -> mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest)))
        .isInstanceOf(InvalidSubscriptionRequestException.class)
        .getCause()
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 1");
  }

  @Test
  public void mapRequestToNewPendingTransactions() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newPendingTransactions\"]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_PENDING_TRANSACTIONS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToNewPendingTransactionsParsesSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newPendingTransactions\", {\"includeTransactions\": false}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.NEW_PENDING_TRANSACTIONS, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToSyncingSubscribe() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, false, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToSyncingSubscribeParsesSecondParam() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\", {\"includeTransactions\": true}]}");
    final SubscribeRequest expectedSubscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, true, CONNECTION_ID);

    final SubscribeRequest subscribeRequest =
        mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(subscribeRequest).isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapAbsentSubscriptionTypeRequestFails() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"foo\"]}");

    assertThatThrownBy(() -> mapper.mapSubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest)))
        .isInstanceOf(InvalidSubscriptionRequestException.class)
        .getCause()
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Invalid json rpc parameter at index 0");
  }

  @Test
  public void mapRequestToPrivateLogsSubscription() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"priv_subscribe\", \"params\": [\"B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=\", \"logs\", {\"address\": \"0x8320fe7702b96808f7bbc0d4a888ed1468216cfd\"}]}");

    final PrivateSubscribeRequest expectedSubscribeRequest =
        new PrivateSubscribeRequest(
            SubscriptionType.LOGS,
            new FilterParameter(
                BlockParameter.LATEST,
                BlockParameter.LATEST,
                null,
                null,
                singletonList(Address.fromHexString("0x8320fe7702b96808f7bbc0d4a888ed1468216cfd")),
                emptyList(),
                null,
                null,
                null),
            null,
            null,
            "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=",
            ENCLAVE_PUBLIC_KEY);

    final PrivateSubscribeRequest subscribeRequest =
        mapper.mapPrivateSubscribeRequest(
            new JsonRpcRequestContext(jsonRpcRequest), ENCLAVE_PUBLIC_KEY);

    assertThat(subscribeRequest).usingRecursiveComparison().isEqualTo(expectedSubscribeRequest);
  }

  @Test
  public void mapRequestToPrivateSubscriptionWithInvalidType() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"priv_subscribe\", \"params\": [\"B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=\", \"syncing\", {\"includeTransactions\": true}]}");

    assertThatThrownBy(
            () ->
                mapper.mapPrivateSubscribeRequest(
                    new JsonRpcRequestContext(jsonRpcRequest), ENCLAVE_PUBLIC_KEY))
        .isInstanceOf(InvalidSubscriptionRequestException.class)
        .hasMessage("Invalid subscribe request. Invalid private subscription type.");
  }

  @Test
  public void mapRequestToPrivateUnsubscribeRequest() {
    final JsonRpcRequest jsonRpcRequest =
        parseWebSocketRpcRequest(
            "{\"id\": 1, \"method\": \"priv_unsubscribe\", \"params\": [\"B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=\", \"0x1\"]}");
    final PrivateUnsubscribeRequest expectedUnsubscribeRequest =
        new PrivateUnsubscribeRequest(
            1L, CONNECTION_ID, "B1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

    final PrivateUnsubscribeRequest unsubscribeRequest =
        mapper.mapPrivateUnsubscribeRequest(new JsonRpcRequestContext(jsonRpcRequest));

    assertThat(unsubscribeRequest).isEqualTo(expectedUnsubscribeRequest);
  }

  private WebSocketRpcRequest parseWebSocketRpcRequest(final String json) {
    return Json.decodeValue(json, WebSocketRpcRequest.class);
  }
}

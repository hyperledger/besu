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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketRequestHandler;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscribeRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.HashMap;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class EthUnsubscribeIntegrationTest {

  private Vertx vertx;
  private WebSocketRequestHandler webSocketRequestHandler;
  private SubscriptionManager subscriptionManager;
  private WebSocketMethodsFactory webSocketMethodsFactory;
  private final int ASYNC_TIMEOUT = 5000;
  private final String CONNECTION_ID = "test-connection-id-1";

  @Before
  public void before() {
    vertx = Vertx.vertx();
    subscriptionManager = new SubscriptionManager(new NoOpMetricsSystem());
    webSocketMethodsFactory = new WebSocketMethodsFactory(subscriptionManager, new HashMap<>());
    webSocketRequestHandler = new WebSocketRequestHandler(vertx, webSocketMethodsFactory.methods());
  }

  @Test
  public void shouldRemoveConnectionWithSingleSubscriptionFromMap(final TestContext context) {
    final Async async = context.async();

    // Add the subscription we'd like to remove
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);
    final Long subscriptionId = subscriptionManager.subscribe(subscribeRequest);
    assertThat(subscriptionManager.getSubscriptionById(subscriptionId)).isNotNull();

    final JsonRpcRequest unsubscribeRequest =
        createEthUnsubscribeRequest(subscriptionId, CONNECTION_ID);

    vertx
        .eventBus()
        .consumer(CONNECTION_ID)
        .handler(
            msg -> {
              assertThat(subscriptionManager.getSubscriptionById(subscriptionId)).isNull();
              async.complete();
            })
        .completionHandler(
            v ->
                webSocketRequestHandler.handle(
                    CONNECTION_ID, Buffer.buffer(Json.encode(unsubscribeRequest))));

    async.awaitSuccess(ASYNC_TIMEOUT);
  }

  @Test
  public void shouldRemoveSubscriptionAndKeepConnection(final TestContext context) {
    final Async async = context.async();

    // Add the subscriptions we'd like to remove
    final SubscribeRequest subscribeRequest =
        new SubscribeRequest(SubscriptionType.SYNCING, null, null, CONNECTION_ID);
    final Long subscriptionId1 = subscriptionManager.subscribe(subscribeRequest);
    final Long subscriptionId2 = subscriptionManager.subscribe(subscribeRequest);

    assertThat(subscriptionManager.getSubscriptionById(subscriptionId1)).isNotNull();
    assertThat(subscriptionManager.getSubscriptionById(subscriptionId2)).isNotNull();

    final JsonRpcRequest unsubscribeRequest =
        createEthUnsubscribeRequest(subscriptionId2, CONNECTION_ID);

    vertx
        .eventBus()
        .consumer(CONNECTION_ID)
        .handler(
            msg -> {
              assertThat(subscriptionManager.getSubscriptionById(subscriptionId1)).isNotNull();
              assertThat(subscriptionManager.getSubscriptionById(subscriptionId2)).isNull();
              async.complete();
            })
        .completionHandler(
            v ->
                webSocketRequestHandler.handle(
                    CONNECTION_ID, Buffer.buffer(Json.encode(unsubscribeRequest))));

    async.awaitSuccess(ASYNC_TIMEOUT);
  }

  private WebSocketRpcRequest createEthUnsubscribeRequest(
      final Long subscriptionId, final String connectionId) {
    return Json.decodeValue(
        "{\"id\": 1, \"method\": \"eth_unsubscribe\", \"params\": [\""
            + subscriptionId
            + "\"], \"connectionId\": \""
            + connectionId
            + "\"}",
        WebSocketRpcRequest.class);
  }
}

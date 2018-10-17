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
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket.methods;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketRequestHandler;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class EthSubscribeIntegrationTest {

  private Vertx vertx;
  private WebSocketRequestHandler webSocketRequestHandler;
  private SubscriptionManager subscriptionManager;
  private WebSocketMethodsFactory webSocketMethodsFactory;
  private final int ASYNC_TIMEOUT = 5000;
  private final String CONNECTION_ID_1 = "test-connection-id-1";
  private final String CONNECTION_ID_2 = "test-connection-id-2";

  @Before
  public void before() {
    vertx = Vertx.vertx();
    subscriptionManager = new SubscriptionManager();
    webSocketMethodsFactory = new WebSocketMethodsFactory(subscriptionManager, new HashMap<>());
    webSocketRequestHandler = new WebSocketRequestHandler(vertx, webSocketMethodsFactory.methods());
  }

  @Test
  public void shouldAddConnectionToMap(final TestContext context) {
    final Async async = context.async();

    final JsonRpcRequest subscribeRequest = createEthSubscribeRequest(CONNECTION_ID_1);

    vertx
        .eventBus()
        .consumer(CONNECTION_ID_1)
        .handler(
            msg -> {
              final Map<String, List<Long>> connectionSubscriptionsMap =
                  subscriptionManager.getConnectionSubscriptionsMap();
              assertThat(connectionSubscriptionsMap.size()).isEqualTo(1);
              assertThat(connectionSubscriptionsMap.containsKey(CONNECTION_ID_1)).isTrue();
              async.complete();
            })
        .completionHandler(
            v ->
                webSocketRequestHandler.handle(
                    CONNECTION_ID_1, Buffer.buffer(Json.encode(subscribeRequest))));

    async.awaitSuccess(ASYNC_TIMEOUT);
  }

  @Test
  public void shouldAddMultipleConnectionsToMap(final TestContext context) {
    final Async async = context.async(2);

    final JsonRpcRequest subscribeRequest1 = createEthSubscribeRequest(CONNECTION_ID_1);
    final JsonRpcRequest subscribeRequest2 = createEthSubscribeRequest(CONNECTION_ID_2);

    vertx
        .eventBus()
        .consumer(CONNECTION_ID_1)
        .handler(
            msg -> {
              assertThat(subscriptionManager.getConnectionSubscriptionsMap().size()).isEqualTo(1);
              assertThat(
                      subscriptionManager
                          .getConnectionSubscriptionsMap()
                          .containsKey(CONNECTION_ID_1))
                  .isTrue();
              async.countDown();

              vertx
                  .eventBus()
                  .consumer(CONNECTION_ID_2)
                  .handler(
                      msg2 -> {
                        assertThat(subscriptionManager.getConnectionSubscriptionsMap().size())
                            .isEqualTo(2);
                        assertThat(
                                subscriptionManager
                                    .getConnectionSubscriptionsMap()
                                    .containsKey(CONNECTION_ID_1))
                            .isTrue();
                        assertThat(
                                subscriptionManager
                                    .getConnectionSubscriptionsMap()
                                    .containsKey(CONNECTION_ID_2))
                            .isTrue();
                        async.countDown();
                      })
                  .completionHandler(
                      v ->
                          webSocketRequestHandler.handle(
                              CONNECTION_ID_2, Buffer.buffer(Json.encode(subscribeRequest2))));
            })
        .completionHandler(
            v ->
                webSocketRequestHandler.handle(
                    CONNECTION_ID_1, Buffer.buffer(Json.encode(subscribeRequest1))));

    async.awaitSuccess(ASYNC_TIMEOUT);
  }

  private WebSocketRpcRequest createEthSubscribeRequest(final String connectionId) {
    return Json.decodeValue(
        "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"], \"connectionId\": \""
            + connectionId
            + "\"}",
        WebSocketRpcRequest.class);
  }
}

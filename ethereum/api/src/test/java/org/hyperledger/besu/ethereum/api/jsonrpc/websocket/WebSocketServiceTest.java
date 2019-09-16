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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketBase;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class WebSocketServiceTest {

  private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;

  private Vertx vertx;
  private WebSocketConfiguration websocketConfiguration;
  private WebSocketRequestHandler webSocketRequestHandlerSpy;
  private WebSocketService websocketService;
  private HttpClient httpClient;

  @Before
  public void before() {
    vertx = Vertx.vertx();

    websocketConfiguration = WebSocketConfiguration.createDefault();
    websocketConfiguration.setPort(0);
    websocketConfiguration.setHostsWhitelist(Collections.singleton("*"));

    final Map<String, JsonRpcMethod> websocketMethods =
        new WebSocketMethodsFactory(
                new SubscriptionManager(new NoOpMetricsSystem()), new HashMap<>())
            .methods();
    webSocketRequestHandlerSpy = spy(new WebSocketRequestHandler(vertx, websocketMethods));

    websocketService =
        new WebSocketService(vertx, websocketConfiguration, webSocketRequestHandlerSpy);
    websocketService.start().join();

    websocketConfiguration.setPort(websocketService.socketAddress().getPort());

    final HttpClientOptions httpClientOptions =
        new HttpClientOptions()
            .setDefaultHost(websocketConfiguration.getHost())
            .setDefaultPort(websocketConfiguration.getPort());

    httpClient = vertx.createHttpClient(httpClientOptions);
  }

  @After
  public void after() {
    reset(webSocketRequestHandlerSpy);
    websocketService.stop();
  }

  @Test
  public void websocketServiceExecutesHandlerOnMessage(final TestContext context) {
    final Async async = context.async();

    final String request = "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}";
    final String expectedResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x1\"}";

    httpClient.websocket(
        "/",
        webSocket -> {
          webSocket.write(Buffer.buffer(request));

          webSocket.handler(
              buffer -> {
                context.assertEquals(expectedResponse, buffer.toString());
                async.complete();
              });
        });

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void websocketServiceRemoveSubscriptionOnConnectionClose(final TestContext context) {
    final Async async = context.async();

    vertx
        .eventBus()
        .consumer(SubscriptionManager.EVENTBUS_REMOVE_SUBSCRIPTIONS_ADDRESS)
        .handler(
            m -> {
              context.assertNotNull(m.body());
              async.complete();
            })
        .completionHandler(v -> httpClient.websocket("/", WebSocketBase::close));

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void websocketServiceCloseConnectionOnUnrecoverableError(final TestContext context) {
    final Async async = context.async();

    final byte[] bigMessage = new byte[HttpServerOptions.DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE + 1];
    Arrays.fill(bigMessage, (byte) 1);

    httpClient.websocket(
        "/",
        webSocket -> {
          webSocket.write(Buffer.buffer(bigMessage));
          webSocket.closeHandler(v -> async.complete());
        });

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @SuppressWarnings("deprecation") // No alternative available in vertx 3.
  @Test
  public void websocketServiceMustReturnErrorOnHttpRequest(final TestContext context) {
    final Async async = context.async();

    httpClient
        .post(
            websocketConfiguration.getPort(),
            websocketConfiguration.getHost(),
            "/",
            response ->
                response.bodyHandler(
                    b -> {
                      context
                          .assertEquals(400, response.statusCode())
                          .assertEquals(
                              "Websocket endpoint can't handle HTTP requests", b.toString());
                      async.complete();
                    }))
        .end();

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void handleLoginRequestWithAuthDisabled() {
    final HttpClientRequest request =
        httpClient.post(
            websocketConfiguration.getPort(),
            websocketConfiguration.getHost(),
            "/login",
            response -> {
              assertThat(response.statusCode()).isEqualTo(400);
              assertThat(response.statusMessage()).isEqualTo("Authentication not enabled");
            });
    request.putHeader("Content-Type", "application/json; charset=utf-8");
    request.end("{\"username\":\"user\",\"password\":\"pass\"}");
  }
}

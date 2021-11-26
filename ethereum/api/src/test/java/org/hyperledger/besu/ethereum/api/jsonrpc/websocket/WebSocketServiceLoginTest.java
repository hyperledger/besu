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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.list;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class WebSocketServiceLoginTest {
  private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;

  private Vertx vertx;
  private WebSocketConfiguration websocketConfiguration;
  private WebSocketRequestHandler webSocketRequestHandlerSpy;
  private WebSocketService websocketService;
  private HttpClient httpClient;
  protected static JWTAuth jwtAuth;

  @Before
  public void before() throws URISyntaxException {
    vertx = Vertx.vertx();

    final String authTomlPath =
        Paths.get(ClassLoader.getSystemResource("JsonRpcHttpService/auth.toml").toURI())
            .toAbsolutePath()
            .toString();

    websocketConfiguration = WebSocketConfiguration.createDefault();
    websocketConfiguration.setPort(0);
    websocketConfiguration.setAuthenticationEnabled(true);
    websocketConfiguration.setAuthenticationCredentialsFile(authTomlPath);
    websocketConfiguration.setHostsAllowlist(Collections.singletonList("*"));

    final Map<String, JsonRpcMethod> websocketMethods =
        new WebSocketMethodsFactory(
                new SubscriptionManager(new NoOpMetricsSystem()), new HashMap<>())
            .methods();
    webSocketRequestHandlerSpy =
        spy(
            new WebSocketRequestHandler(
                vertx,
                websocketMethods,
                mock(EthScheduler.class),
                TimeoutOptions.defaultOptions().getTimeoutSeconds()));

    websocketService =
        new WebSocketService(vertx, websocketConfiguration, webSocketRequestHandlerSpy);
    websocketService.start().join();
    jwtAuth = websocketService.authenticationService.get().getJwtAuthProvider();

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
  public void loginWithBadCredentials(final TestContext context) {
    final Async async = context.async();
    final HttpClientRequest request =
        httpClient.post(
            websocketConfiguration.getPort(),
            websocketConfiguration.getHost(),
            "/login",
            response -> {
              assertThat(response.statusCode()).isEqualTo(401);
              assertThat(response.statusMessage()).isEqualTo("Unauthorized");
              async.complete();
            });
    request.putHeader("Content-Type", "application/json; charset=utf-8");
    request.end("{\"username\":\"user\",\"password\":\"pass\"}");
    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void loginWithGoodCredentials(final TestContext context) {
    final Async async = context.async();
    final HttpClientRequest request =
        httpClient.post(
            websocketConfiguration.getPort(),
            websocketConfiguration.getHost(),
            "/login",
            response -> {
              assertThat(response.statusCode()).isEqualTo(200);
              assertThat(response.statusMessage()).isEqualTo("OK");
              assertThat(response.getHeader("Content-Type")).isNotNull();
              assertThat(response.getHeader("Content-Type")).isEqualTo("application/json");
              response.bodyHandler(
                  buffer -> {
                    final String body = buffer.toString();
                    assertThat(body).isNotBlank();

                    final JsonObject respBody = new JsonObject(body);
                    final String token = respBody.getString("token");
                    assertThat(token).isNotNull();

                    websocketService
                        .authenticationService
                        .get()
                        .getJwtAuthProvider()
                        .authenticate(
                            new JsonObject().put("jwt", token),
                            (r) -> {
                              Assertions.assertThat(r.succeeded()).isTrue();
                              final User user = r.result();
                              user.isAuthorized(
                                  "noauths",
                                  (authed) -> {
                                    assertThat(authed.succeeded()).isTrue();
                                    assertThat(authed.result()).isFalse();
                                  });
                              user.isAuthorized(
                                  "fakePermission",
                                  (authed) -> {
                                    assertThat(authed.succeeded()).isTrue();
                                    assertThat(authed.result()).isTrue();
                                  });
                              user.isAuthorized(
                                  "eth:subscribe",
                                  (authed) -> {
                                    assertThat(authed.succeeded()).isTrue();
                                    assertThat(authed.result()).isTrue();
                                    async.complete();
                                  });
                            });
                  });
            });
    request.putHeader("Content-Type", "application/json; charset=utf-8");
    request.end("{\"username\":\"user\",\"password\":\"pegasys\"}");

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  @Test
  public void websocketServiceWithBadHeaderAuthenticationToken(final TestContext context) {
    final Async async = context.async();

    final String request = "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}";
    final String expectedResponse =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-40100,\"message\":\"Unauthorized\"}}";

    RequestOptions options = new RequestOptions();
    options.setURI("/");
    options.setHost(websocketConfiguration.getHost());
    options.setPort(websocketConfiguration.getPort());
    final MultiMap headers = new VertxHttpHeaders();
    String badtoken = "badtoken";
    headers.add("Authorization", "Bearer " + badtoken);

    final StringBuilder response = new StringBuilder(expectedResponse.length());

    WebSocketConnectOptions connectionOptions =
        new WebSocketConnectOptions(options).setHeaders(headers);

    httpClient.websocket(
        connectionOptions,
        webSocket -> {
          webSocket.writeTextMessage(request);

          webSocket.frameHandler(
              frame -> {
                response.append(frame.textData());
                if (frame.isFinal()) {
                  async.complete();
                }
              });
        });

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
    context.assertEquals(expectedResponse, response.toString());
  }

  @Test
  public void websocketServiceWithGoodHeaderAuthenticationToken(final TestContext context) {
    final Async async = context.async();

    final JWTOptions jwtOptions = new JWTOptions().setExpiresInMinutes(5).setAlgorithm("RS256");
    final JsonObject jwtContents =
        new JsonObject().put("permissions", Lists.newArrayList("eth:*")).put("username", "user");
    final String goodToken = jwtAuth.generateToken(jwtContents, jwtOptions);

    final String requestSub =
        "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}";
    final String expectedResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x1\"}";

    RequestOptions options = new RequestOptions();
    options.setURI("/");
    options.setHost(websocketConfiguration.getHost());
    options.setPort(websocketConfiguration.getPort());
    final MultiMap headers = new VertxHttpHeaders();
    if (goodToken != null) {
      headers.add("Authorization", "Bearer " + goodToken);
    }

    final StringBuilder response = new StringBuilder(expectedResponse.length());

    httpClient.websocket(
        options,
        headers,
        webSocket -> {
          webSocket.writeTextMessage(requestSub);

          webSocket.frameHandler(
              frame -> {
                response.append(frame.textData());
                if (frame.isFinal()) {
                  async.complete();
                }
              });
        });

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
    context.assertEquals(expectedResponse, response.toString());
  }

  @Test
  public void loginPopulatesJWTPayloadWithRequiredValues(final TestContext context) {
    final Async async = context.async();
    final HttpClientRequest request =
        httpClient.post(
            websocketConfiguration.getPort(),
            websocketConfiguration.getHost(),
            "/login",
            response -> {
              response.bodyHandler(
                  buffer -> {
                    final String body = buffer.toString();
                    assertThat(body).isNotBlank();

                    final JsonObject respBody = new JsonObject(body);
                    final String token = respBody.getString("token");

                    final JsonObject jwtPayload = decodeJwtPayload(token);
                    assertThat(jwtPayload.getString("username")).isEqualTo("user");
                    assertThat(jwtPayload.getJsonArray("permissions"))
                        .isEqualTo(
                            new JsonArray(
                                list(
                                    "fakePermission",
                                    "eth:blockNumber",
                                    "eth:subscribe",
                                    "web3:*")));
                    assertThat(jwtPayload.getString("privacyPublicKey"))
                        .isEqualTo("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
                    assertThat(jwtPayload.containsKey("iat")).isTrue();
                    assertThat(jwtPayload.containsKey("exp")).isTrue();
                    final long tokenExpiry = jwtPayload.getLong("exp") - jwtPayload.getLong("iat");
                    assertThat(tokenExpiry).isEqualTo(MINUTES.toSeconds(5));

                    async.complete();
                  });
            });
    request.putHeader("Content-Type", "application/json; charset=utf-8");
    request.end("{\"username\":\"user\",\"password\":\"pegasys\"}");

    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }

  private JsonObject decodeJwtPayload(final String token) {
    final List<String> tokenParts = Splitter.on('.').splitToList(token);
    final String payload = tokenParts.get(1);
    return new JsonObject(new String(Base64.getUrlDecoder().decode(payload), UTF_8));
  }
}

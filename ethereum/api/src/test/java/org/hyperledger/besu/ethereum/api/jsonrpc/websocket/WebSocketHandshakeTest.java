/*
 * Copyright contributors to Besu.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationService;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.DefaultAuthenticationService;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.BaseJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class WebSocketHandshakeTest {

  private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;
  private static final List<String> NO_AUTH_METHODS = Arrays.asList("net_services");

  private Vertx vertx;
  private VertxTestContext testContext;
  private WebSocketConfiguration websocketConfiguration;
  private WebSocketMessageHandler webSocketMessageHandler;
  private Map<String, JsonRpcMethod> websocketMethods;
  private WebSocketService websocketService;
  private WebSocketClient websocketClient;
  private JWTAuth jwtAuth;

  @BeforeEach
  public void before() throws URISyntaxException {
    vertx = Vertx.vertx();
    testContext = new VertxTestContext();

    final String authTomlPath =
        Paths.get(ClassLoader.getSystemResource("JsonRpcHttpService/auth.toml").toURI())
            .toAbsolutePath()
            .toString();

    websocketConfiguration = WebSocketConfiguration.createDefault();
    websocketConfiguration.setPort(0);
    websocketConfiguration.setHostsAllowlist(Collections.singletonList("localhost"));
    websocketConfiguration.setAuthenticationEnabled(true);
    websocketConfiguration.setAuthenticationCredentialsFile(authTomlPath);
    websocketConfiguration.setRpcApisNoAuth(new ArrayList<>(NO_AUTH_METHODS));

    websocketMethods =
        new WebSocketMethodsFactory(
                new SubscriptionManager(new NoOpMetricsSystem()), new HashMap<>())
            .methods();

    // Create authentication service
    final Optional<AuthenticationService> authService =
        DefaultAuthenticationService.create(vertx, websocketConfiguration);
    assertThat(authService).isPresent();
    jwtAuth = authService.get().getJwtAuthProvider();

    webSocketMessageHandler =
        new WebSocketMessageHandler(
            vertx,
            new JsonRpcExecutor(new BaseJsonRpcProcessor(), websocketMethods),
            mock(EthScheduler.class),
            TimeoutOptions.defaultOptions().getTimeoutSeconds());

    websocketService =
        new WebSocketService(
            vertx, websocketConfiguration, webSocketMessageHandler, new NoOpMetricsSystem());
    websocketService.start().join();

    websocketConfiguration.setPort(websocketService.socketAddress().getPort());

    websocketClient =
        vertx.createWebSocketClient(
            new WebSocketClientOptions()
                .setDefaultHost(websocketConfiguration.getHost())
                .setDefaultPort(websocketConfiguration.getPort()));
  }

  @AfterEach
  public void after() {
    websocketService.stop();
  }

  @Test
  public void hostNotInAllowlistIsRejectedDuringHandshake() throws InterruptedException {
    // Create WebSocket connection options with a host not in the allowlist
    WebSocketConnectOptions options = new WebSocketConnectOptions();
    options.setURI("/");
    options.setHost(websocketConfiguration.getHost());
    options.setPort(websocketConfiguration.getPort());
    options.addHeader("Host", "not-in-allowlist.com");

    // Connection should be rejected
    websocketClient.connect(options, websocket -> testContext.failingThenComplete());

    testContext.awaitCompletion(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  @Test
  public void hostInAllowlistIsAcceptedDuringHandshake() throws InterruptedException {
    // Create WebSocket connection options with a host in the allowlist
    WebSocketConnectOptions options = new WebSocketConnectOptions();
    options.setURI("/");
    options.setHost(websocketConfiguration.getHost());
    options.setPort(websocketConfiguration.getPort());
    options.addHeader("Host", "localhost");

    // Connection should be accepted
    websocketClient.connect(options, webSocket -> testContext.succeedingThenComplete());

    testContext.awaitCompletion(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  @Test
  public void authenticationFailsDuringHandshake() throws InterruptedException {
    // Create WebSocket connection options with an invalid token
    WebSocketConnectOptions options = new WebSocketConnectOptions();
    options.setURI("/");
    options.setHost(websocketConfiguration.getHost());
    options.setPort(websocketConfiguration.getPort());
    options.addHeader("Authorization", "Bearer invalidtoken");

    // Connection should be rejected
    websocketClient.connect(options, webSocket -> testContext.failingThenComplete());

    testContext.awaitCompletion(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  @Test
  public void authenticationSucceedsDuringHandshake() throws InterruptedException {
    // Create a valid JWT token
    final JWTOptions jwtOptions = new JWTOptions().setExpiresInMinutes(5).setAlgorithm("RS256");
    final JsonObject jwtContents =
        new JsonObject().put("permissions", Arrays.asList("eth:*")).put("username", "user");
    final String validToken = jwtAuth.generateToken(jwtContents, jwtOptions);

    // Create WebSocket connection options with a valid token
    WebSocketConnectOptions options = new WebSocketConnectOptions();
    options.setURI("/");
    options.setHost(websocketConfiguration.getHost());
    options.setPort(websocketConfiguration.getPort());
    options.addHeader("Authorization", "Bearer " + validToken);

    // Connection should be accepted
    websocketClient.connect(
        options,
        webSocket ->
            testContext.succeeding(
                nextHandler -> {
                  webSocket.result().close();
                  testContext.completeNow();
                }));

    testContext.awaitCompletion(VERTX_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }
}

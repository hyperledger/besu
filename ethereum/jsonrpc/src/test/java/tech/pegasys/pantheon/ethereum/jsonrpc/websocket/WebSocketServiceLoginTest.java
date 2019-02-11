/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.subscription.SubscriptionManager;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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

    final Map<String, JsonRpcMethod> websocketMethods =
        new WebSocketMethodsFactory(new SubscriptionManager(), new HashMap<>()).methods();
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
  public void loginWithBadCredentials() {
    final HttpClientRequest request =
        httpClient.post(
            websocketConfiguration.getPort(),
            websocketConfiguration.getHost(),
            "/login",
            response -> {
              assertThat(response.statusCode()).isEqualTo(401);
              assertThat(response.statusMessage()).isEqualTo("Unauthorized");
            });
    request.putHeader("Content-Type", "application/json; charset=utf-8");
    request.end("{\"username\":\"user\",\"password\":\"pass\"}");
  }

  @Test
  public void loginWithGoodCredentials() {
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
                              assertThat(r.succeeded()).isTrue();
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
                            });
                  });
            });
    request.putHeader("Content-Type", "application/json; charset=utf-8");
    request.end("{\"username\":\"user\",\"password\":\"pegasys\"}");
  }
}

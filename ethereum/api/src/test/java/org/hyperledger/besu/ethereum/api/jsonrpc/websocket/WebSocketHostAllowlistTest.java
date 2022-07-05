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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.BaseJsonRpcProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.execution.JsonRpcExecutor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.methods.WebSocketMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class WebSocketHostAllowlistTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  protected static Vertx vertx;

  private final List<String> hostsAllowlist = Arrays.asList("ally", "friend");

  private final WebSocketConfiguration webSocketConfiguration =
      WebSocketConfiguration.createDefault();
  private static WebSocketMessageHandler webSocketMessageHandlerSpy;
  private WebSocketService websocketService;
  private HttpClient httpClient;
  private static final int VERTX_AWAIT_TIMEOUT_MILLIS = 10000;
  private int websocketPort;

  @Before
  public void initServerAndClient() {
    webSocketConfiguration.setPort(0);
    vertx = Vertx.vertx();

    final Map<String, JsonRpcMethod> websocketMethods =
        new WebSocketMethodsFactory(
                new SubscriptionManager(new NoOpMetricsSystem()), new HashMap<>())
            .methods();
    webSocketMessageHandlerSpy =
        spy(
            new WebSocketMessageHandler(
                vertx,
                new JsonRpcExecutor(new BaseJsonRpcProcessor(), websocketMethods),
                mock(EthScheduler.class),
                TimeoutOptions.defaultOptions().getTimeoutSeconds()));

    websocketService =
        new WebSocketService(
            vertx, webSocketConfiguration, webSocketMessageHandlerSpy, new NoOpMetricsSystem());
    websocketService.start().join();
    final InetSocketAddress inetSocketAddress = websocketService.socketAddress();

    websocketPort = inetSocketAddress.getPort();
    final HttpClientOptions httpClientOptions =
        new HttpClientOptions()
            .setDefaultHost(webSocketConfiguration.getHost())
            .setDefaultPort(websocketPort);

    httpClient = vertx.createHttpClient(httpClientOptions);
  }

  @After
  public void after() {
    reset(webSocketMessageHandlerSpy);
    websocketService.stop();
  }

  @Test
  public void websocketRequestWithDefaultHeaderAndDefaultConfigIsAccepted() {
    boolean result = websocketService.hasAllowedHostnameHeader(Optional.of("localhost:50012"));
    assertThat(result).isTrue();
  }

  @Test
  public void httpRequestWithDefaultHeaderAndDefaultConfigIsAccepted(final TestContext context) {
    doHttpRequestAndVerify(context, "localhost:50012", 400);
  }

  @Test
  public void websocketRequestWithEmptyHeaderAndDefaultConfigIsRejected() {
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of(""))).isFalse();
  }

  @Test
  public void httpRequestWithEmptyHeaderAndDefaultConfigIsRejected(final TestContext context) {
    doHttpRequestAndVerify(context, "", 403);
  }

  @Test
  public void websocketRequestWithAnyHostnameAndWildcardConfigIsAccepted() {
    webSocketConfiguration.setHostsAllowlist(Collections.singletonList("*"));
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("ally"))).isTrue();
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("foe"))).isTrue();
  }

  @Test
  public void httpRequestWithAnyHostnameAndWildcardConfigIsAccepted(final TestContext context) {
    webSocketConfiguration.setHostsAllowlist(Collections.singletonList("*"));
    doHttpRequestAndVerify(context, "ally", 400);
    doHttpRequestAndVerify(context, "foe", 400);
  }

  @Test
  public void websocketRequestWithAllowedHostIsAccepted() {
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("ally"))).isTrue();
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("ally:12345"))).isTrue();
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("friend"))).isTrue();
  }

  @Test
  public void httpRequestWithAllowedHostIsAccepted(final TestContext context) {
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    doHttpRequestAndVerify(context, "ally", 400);
    doHttpRequestAndVerify(context, "ally:12345", 400);
    doHttpRequestAndVerify(context, "friend", 400);
  }

  @Test
  public void websocketRequestWithUnknownHostIsRejected() {
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("foe"))).isFalse();
  }

  @Test
  public void httpRequestWithUnknownHostIsRejected(final TestContext context) {
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    doHttpRequestAndVerify(context, "foe", 403);
  }

  @Test
  public void websocketRequestWithMalformedHostIsRejected() {
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("ally:friend"))).isFalse();
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("ally:123456"))).isFalse();
    assertThat(websocketService.hasAllowedHostnameHeader(Optional.of("ally:friend:1234")))
        .isFalse();
  }

  @Test
  public void httpRequestWithMalformedHostIsRejected(final TestContext context) {
    webSocketConfiguration.setAuthenticationEnabled(false);
    webSocketConfiguration.setHostsAllowlist(hostsAllowlist);
    doHttpRequestAndVerify(context, "ally:friend", 400);
    doHttpRequestAndVerify(context, "ally:123456", 403);
    doHttpRequestAndVerify(context, "ally:friend:1234", 403);
  }

  private void doHttpRequestAndVerify(
      final TestContext context, final String hostname, final int expectedResponse) {
    final Async async = context.async();

    httpClient.request(
        HttpMethod.POST,
        websocketPort,
        webSocketConfiguration.getHost(),
        "/",
        request -> {
          request.result().putHeader("Host", hostname);
          request.result().end();
          request
              .result()
              .send(
                  response -> {
                    assertThat(response.result().statusCode()).isEqualTo(expectedResponse);
                    async.complete();
                  });
        });
    async.awaitSuccess(VERTX_AWAIT_TIMEOUT_MILLIS);
  }
}

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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import tech.pegasys.pantheon.config.StubGenesisConfigOptions;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.health.HealthService;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.network.DefaultP2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.permissioning.AccountLocalConfigPermissioningController;
import tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Lists;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JsonRpcHttpServiceRpcApisTest {
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private final Vertx vertx = Vertx.vertx();
  private final OkHttpClient client = new OkHttpClient();
  private JsonRpcHttpService service;
  private static String baseUrl;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
  private static final BigInteger NETWORK_ID = BigInteger.valueOf(123);
  private JsonRpcConfiguration configuration;
  private static final List<String> netServices =
      new ArrayList<>(Arrays.asList("jsonrpc", "ws", "p2p", "metrics"));

  @Mock protected static BlockchainQueries blockchainQueries;

  private final JsonRpcTestHelper testHelper = new JsonRpcTestHelper();

  @Before
  public void before() {
    configuration = JsonRpcConfiguration.createDefault();
    configuration.setPort(0);
  }

  @After
  public void after() {
    service.stop().join();
  }

  @Test
  public void requestWithNetMethodShouldSucceedWhenDefaultApisEnabled() throws Exception {
    service = createJsonRpcHttpServiceWithRpcApis(configuration);
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_version\"}");

    try (final Response resp = client.newCall(buildRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
    }
  }

  @Test
  public void requestWithNetMethodShouldSucceedWhenNetApiIsEnabled() throws Exception {
    service = createJsonRpcHttpServiceWithRpcApis(RpcApis.NET);
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_version\"}");

    try (final Response resp = client.newCall(buildRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
    }
  }

  @Test
  public void requestWithNetMethodShouldFailWhenNetApiIsNotEnabled() throws Exception {
    service = createJsonRpcHttpServiceWithRpcApis(RpcApis.WEB3);
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_version\"}");

    try (final Response resp = client.newCall(buildRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.METHOD_NOT_ENABLED;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void requestWithNetMethodShouldSucceedWhenNetApiAndOtherIsEnabled() throws Exception {
    service = createJsonRpcHttpServiceWithRpcApis(RpcApis.NET, RpcApis.WEB3);
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_version\"}");

    try (final Response resp = client.newCall(buildRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
    }
  }

  private JsonRpcConfiguration createJsonRpcConfigurationWithRpcApis(final RpcApi... rpcApis) {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setCorsAllowedDomains(singletonList("*"));
    config.setPort(0);
    if (rpcApis != null) {
      config.setRpcApis(Lists.newArrayList(rpcApis));
    }
    return config;
  }

  private JsonRpcHttpService createJsonRpcHttpServiceWithRpcApis(final RpcApi... rpcApis)
      throws Exception {
    return createJsonRpcHttpServiceWithRpcApis(createJsonRpcConfigurationWithRpcApis(rpcApis));
  }

  private JsonRpcHttpService createJsonRpcHttpServiceWithRpcApis(final JsonRpcConfiguration config)
      throws Exception {
    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    final Map<String, JsonRpcMethod> rpcMethods =
        spy(
            new JsonRpcMethodsFactory()
                .methods(
                    CLIENT_VERSION,
                    NETWORK_ID,
                    new StubGenesisConfigOptions(),
                    mock(P2PNetwork.class),
                    blockchainQueries,
                    mock(Synchronizer.class),
                    MainnetProtocolSchedule.create(),
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(EthHashMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                    Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                    config.getRpcApis(),
                    mock(PrivacyParameters.class),
                    mock(JsonRpcConfiguration.class),
                    mock(WebSocketConfiguration.class),
                    mock(MetricsConfiguration.class)));
    final JsonRpcHttpService jsonRpcHttpService =
        new JsonRpcHttpService(
            vertx,
            folder.newFolder().toPath(),
            config,
            new NoOpMetricsSystem(),
            Optional.empty(),
            rpcMethods,
            HealthService.ALWAYS_HEALTHY,
            HealthService.ALWAYS_HEALTHY);
    jsonRpcHttpService.start().join();

    baseUrl = jsonRpcHttpService.url();
    return jsonRpcHttpService;
  }

  private Request buildRequest(final RequestBody body) {
    return new Request.Builder().post(body).url(baseUrl).build();
  }

  private JsonRpcConfiguration createJsonRpcConfiguration() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setEnabled(true);
    return config;
  }

  private WebSocketConfiguration createWebSocketConfiguration() {
    final WebSocketConfiguration config = WebSocketConfiguration.createDefault();
    config.setEnabled(true);
    return config;
  }

  private P2PNetwork createP2pNetwork() {
    final NetworkingConfiguration config =
        NetworkingConfiguration.create()
            .setRlpx(RlpxConfiguration.create().setBindPort(0))
            .setDiscovery(DiscoveryConfiguration.create().setBindPort(0));

    final P2PNetwork p2pNetwork =
        DefaultP2PNetwork.builder()
            .supportedCapabilities(Capability.create("eth", 63))
            .keyPair(SECP256K1.KeyPair.generate())
            .vertx(vertx)
            .config(config)
            .metricsSystem(new NoOpMetricsSystem())
            .build();

    p2pNetwork.start();
    return p2pNetwork;
  }

  private MetricsConfiguration createMetricsConfiguration() {
    return MetricsConfiguration.builder().enabled(true).port(0).build();
  }

  private JsonRpcHttpService createJsonRpcHttpService(
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final P2PNetwork p2pNetwork,
      final MetricsConfiguration metricsConfiguration)
      throws Exception {
    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);
    jsonRpcConfiguration.setPort(0);
    webSocketConfiguration.setPort(0);

    final Map<String, JsonRpcMethod> rpcMethods =
        spy(
            new JsonRpcMethodsFactory()
                .methods(
                    CLIENT_VERSION,
                    NETWORK_ID,
                    new StubGenesisConfigOptions(),
                    p2pNetwork,
                    blockchainQueries,
                    mock(Synchronizer.class),
                    MainnetProtocolSchedule.create(),
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(EthHashMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                    Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                    jsonRpcConfiguration.getRpcApis(),
                    mock(PrivacyParameters.class),
                    jsonRpcConfiguration,
                    webSocketConfiguration,
                    metricsConfiguration));
    final JsonRpcHttpService jsonRpcHttpService =
        new JsonRpcHttpService(
            vertx,
            folder.newFolder().toPath(),
            jsonRpcConfiguration,
            new NoOpMetricsSystem(),
            Optional.empty(),
            rpcMethods,
            HealthService.ALWAYS_HEALTHY,
            HealthService.ALWAYS_HEALTHY);
    jsonRpcHttpService.start().join();

    baseUrl = jsonRpcHttpService.url();
    return jsonRpcHttpService;
  }

  @Test
  public void netServicesTestWhenJsonrpcWebsocketP2pNetworkAndMatricesIsEnabled() throws Exception {
    final boolean[] servicesStates = new boolean[netServices.size()];
    Arrays.fill(servicesStates, Boolean.TRUE); // All services are enabled
    service = getJsonRpcHttpService(servicesStates);

    final RequestBody body = createNetServicesRequestBody();

    try (final Response resp = client.newCall(buildRequest(body)).execute()) {

      final JsonObject responseBody = new JsonObject(resp.body().string());
      for (int j = 0; j < netServices.size(); j++) {
        assertNetService(servicesStates, responseBody, netServices.get(j));
      }
    }
  }

  @Test
  public void netServicesTestWhenOneIsEnabled() throws Exception {

    for (int i = 0; i < netServices.size(); i++) {

      final boolean[] servicesStates = new boolean[netServices.size()];
      final int enabledServiceIndex = i % netServices.size();
      servicesStates[enabledServiceIndex] = true; // enable only one service at a time
      service = getJsonRpcHttpService(servicesStates);

      final RequestBody body = createNetServicesRequestBody();
      try (final Response resp = client.newCall(buildRequest(body)).execute()) {
        final JsonObject responseBody = new JsonObject(resp.body().string());

        for (int j = 0; j < netServices.size(); j++) {
          assertNetService(servicesStates, responseBody, netServices.get(j));
        }
      }
      service.stop().join();
    }
  }

  private void assertNetService(
      final boolean[] servicesStates, final JsonObject jsonBody, final String serviceName) {

    final boolean isAssertTrue = servicesStates[netServices.indexOf(serviceName)];

    final JsonObject result = jsonBody.getJsonObject("result");
    final JsonObject serviceElement = result.getJsonObject(serviceName);
    if (isAssertTrue) {
      assertTrue(
          serviceElement != null
              && serviceElement.containsKey("host")
              && serviceElement.containsKey("port"));
    } else {
      assertFalse(
          serviceElement != null
              && serviceElement.containsKey("host")
              && serviceElement.containsKey("port"));
    }
  }

  public RequestBody createNetServicesRequestBody() {
    final String id = "123";
    return RequestBody.create(
        JSON, "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_services\"}");
  }

  public JsonRpcHttpService getJsonRpcHttpService(final boolean[] enabledNetServices)
      throws Exception {

    JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    P2PNetwork p2pNetwork = mock(P2PNetwork.class);
    MetricsConfiguration metricsConfiguration = MetricsConfiguration.builder().build();

    if (enabledNetServices[netServices.indexOf("jsonrpc")]) {
      jsonRpcConfiguration = createJsonRpcConfiguration();
    }

    if (enabledNetServices[netServices.indexOf("ws")]) {
      webSocketConfiguration = createWebSocketConfiguration();
    }
    if (enabledNetServices[netServices.indexOf("p2p")]) {
      p2pNetwork = createP2pNetwork();
    }
    if (enabledNetServices[netServices.indexOf("metrics")]) {
      metricsConfiguration = createMetricsConfiguration();
    }

    return createJsonRpcHttpService(
        jsonRpcConfiguration, webSocketConfiguration, p2pNetwork, metricsConfiguration);
  }

  @Test
  public void netServicesTestWhenJsonrpcWebsocketP2pNetworkAndMatricesIsDisabled()
      throws Exception {
    service =
        createJsonRpcHttpService(
            JsonRpcConfiguration.createDefault(),
            WebSocketConfiguration.createDefault(),
            mock(P2PNetwork.class),
            MetricsConfiguration.builder().build());
    final RequestBody body = createNetServicesRequestBody();

    try (final Response resp = client.newCall(buildRequest(body)).execute()) {
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonObject result = json.getJsonObject("result");
      assertTrue(result.isEmpty());
    }
  }
}

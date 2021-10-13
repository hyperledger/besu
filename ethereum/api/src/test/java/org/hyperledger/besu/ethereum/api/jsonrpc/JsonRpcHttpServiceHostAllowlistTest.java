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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatService;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JsonRpcHttpServiceHostAllowlistTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  protected static final Vertx vertx = Vertx.vertx();

  private static Map<String, JsonRpcMethod> rpcMethods;
  private static JsonRpcHttpService service;
  private static OkHttpClient client;
  private static String baseUrl;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
  private static final BigInteger CHAIN_ID = BigInteger.valueOf(123);

  private final JsonRpcConfiguration jsonRpcConfig = createJsonRpcConfig();
  private final NatService natService = new NatService(Optional.empty());

  private final List<String> hostsAllowlist = Arrays.asList("ally", "friend");

  @Before
  public void initServerAndClient() throws Exception {
    final P2PNetwork peerDiscoveryMock = mock(P2PNetwork.class);
    final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
    final Synchronizer synchronizer = mock(Synchronizer.class);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    rpcMethods =
        spy(
            new JsonRpcMethodsFactory()
                .methods(
                    CLIENT_VERSION,
                    CHAIN_ID,
                    new StubGenesisConfigOptions(),
                    peerDiscoveryMock,
                    blockchainQueries,
                    synchronizer,
                    MainnetProtocolSchedule.fromConfig(
                        new StubGenesisConfigOptions().constantinopleBlock(0).chainId(CHAIN_ID)),
                    mock(ProtocolContext.class),
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(PoWMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                    Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                    DEFAULT_RPC_APIS,
                    mock(PrivacyParameters.class),
                    mock(JsonRpcConfiguration.class),
                    mock(WebSocketConfiguration.class),
                    mock(MetricsConfiguration.class),
                    natService,
                    new HashMap<>(),
                    folder.getRoot().toPath(),
                    mock(EthPeers.class)));
    service = createJsonRpcHttpService();
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url();
  }

  private JsonRpcHttpService createJsonRpcHttpService() throws Exception {
    return new JsonRpcHttpService(
        vertx,
        folder.newFolder().toPath(),
        jsonRpcConfig,
        new NoOpMetricsSystem(),
        natService,
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  private static JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    return config;
  }

  @After
  public void shutdownServer() {
    service.stop().join();
  }

  @Test
  public void requestWithDefaultHeaderAndDefaultConfigIsAccepted() throws IOException {
    assertThat(doRequest("localhost:50012")).isEqualTo(200);
  }

  @Test
  public void requestWithEmptyHeaderAndDefaultConfigIsRejected() throws IOException {
    assertThat(doRequest("")).isEqualTo(403);
  }

  @Test
  public void requestWithAnyHostnameAndWildcardConfigIsAccepted() throws IOException {
    jsonRpcConfig.setHostsAllowlist(Collections.singletonList("*"));
    assertThat(doRequest("ally")).isEqualTo(200);
    assertThat(doRequest("foe")).isEqualTo(200);
  }

  @Test
  public void requestWithAllowlistedHostIsAccepted() throws IOException {
    jsonRpcConfig.setHostsAllowlist(hostsAllowlist);
    assertThat(doRequest("ally")).isEqualTo(200);
    assertThat(doRequest("ally:12345")).isEqualTo(200);
    assertThat(doRequest("friend")).isEqualTo(200);
  }

  @Test
  public void requestWithUnknownHostIsRejected() throws IOException {
    jsonRpcConfig.setHostsAllowlist(hostsAllowlist);
    assertThat(doRequest("foe")).isEqualTo(403);
  }

  private int doRequest(final String hostname) throws IOException {
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode("123") + ",\"method\":\"net_version\"}");

    final Request build =
        new Request.Builder().post(body).url(baseUrl).addHeader("Host", hostname).build();
    return client.newCall(build).execute().code();
  }

  @Test
  public void requestWithMalformedHostIsRejected() throws IOException {
    jsonRpcConfig.setHostsAllowlist(hostsAllowlist);
    assertThat(doRequest("ally:friend")).isEqualTo(403);
    assertThat(doRequest("ally:123456")).isEqualTo(403);
    assertThat(doRequest("ally:friend:1234")).isEqualTo(403);
  }
}

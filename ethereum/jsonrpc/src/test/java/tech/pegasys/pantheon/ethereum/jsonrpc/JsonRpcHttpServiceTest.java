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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.config.StubGenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogsBloomFilter;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.SyncStatus;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.health.HealthService;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.network.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.Capability;
import tech.pegasys.pantheon.ethereum.permissioning.AccountLocalConfigPermissioningController;
import tech.pegasys.pantheon.ethereum.permissioning.NodeLocalConfigPermissioningController;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatchers;

public class JsonRpcHttpServiceTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static final Vertx vertx = Vertx.vertx();

  protected static Map<String, JsonRpcMethod> rpcMethods;
  protected static JsonRpcHttpService service;
  protected static OkHttpClient client;
  protected static String baseUrl;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
  protected static final BigInteger CHAIN_ID = BigInteger.valueOf(123);
  protected static P2PNetwork peerDiscoveryMock;
  protected static BlockchainQueries blockchainQueries;
  protected static Synchronizer synchronizer;
  protected static final Collection<RpcApi> JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3, RpcApis.ADMIN);
  protected final JsonRpcTestHelper testHelper = new JsonRpcTestHelper();

  @BeforeClass
  public static void initServerAndClient() throws Exception {
    peerDiscoveryMock = mock(P2PNetwork.class);
    blockchainQueries = mock(BlockchainQueries.class);
    synchronizer = mock(Synchronizer.class);

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
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(EthHashMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                    Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                    JSON_RPC_APIS,
                    mock(PrivacyParameters.class),
                    mock(JsonRpcConfiguration.class),
                    mock(WebSocketConfiguration.class),
                    mock(MetricsConfiguration.class)));
    service = createJsonRpcHttpService();
    service.start().join();

    // Build an OkHttp client.
    client = new OkHttpClient();
    baseUrl = service.url();
  }

  private static JsonRpcHttpService createJsonRpcHttpService(final JsonRpcConfiguration config)
      throws Exception {
    return new JsonRpcHttpService(
        vertx,
        folder.newFolder().toPath(),
        config,
        new NoOpMetricsSystem(),
        Optional.empty(),
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  private static JsonRpcHttpService createJsonRpcHttpService() throws Exception {
    return new JsonRpcHttpService(
        vertx,
        folder.newFolder().toPath(),
        createJsonRpcConfig(),
        new NoOpMetricsSystem(),
        Optional.empty(),
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  private static JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    config.setHostsWhitelist(Collections.singletonList("*"));
    return config;
  }

  /** Tears down the HTTP server. */
  @AfterClass
  public static void shutdownServer() {
    service.stop().join();
  }

  @Test
  public void handleLoginRequestWithAuthDisabled() throws Exception {
    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pass\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      assertThat(resp.message()).isEqualTo("Authentication not enabled");
    }
  }

  @Test
  public void invalidCallToStart() {
    service
        .start()
        .whenComplete(
            (unused, exception) -> assertThat(exception).isInstanceOf(IllegalStateException.class));
  }

  @Test
  public void http404() throws Exception {
    try (final Response resp = client.newCall(buildGetRequest("/foo")).execute()) {
      assertThat(resp.code()).isEqualTo(404);
    }
  }

  @Test
  public void handleEmptyRequest() throws Exception {
    try (final Response resp = client.newCall(buildGetRequest("")).execute()) {
      assertThat(resp.code()).isEqualTo(201);
    }
  }

  @Test
  public void handleUnknownRequestFields() throws Exception {
    final String id = "123";
    // Create a request with an extra "beta" param
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"net_version\", \"beta\":true}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(String.valueOf(CHAIN_ID));
    }
  }

  @Test
  public void getSocketAddressWhenActive() {
    final InetSocketAddress socketAddress = service.socketAddress();
    assertThat("127.0.0.1").isEqualTo(socketAddress.getAddress().getHostAddress());
    assertThat(socketAddress.getPort() > 0).isTrue();
  }

  @Test
  public void getSocketAddressWhenStoppedIsEmpty() throws Exception {
    final JsonRpcHttpService service = createJsonRpcHttpService();

    final InetSocketAddress socketAddress = service.socketAddress();
    assertThat("0.0.0.0").isEqualTo(socketAddress.getAddress().getHostAddress());
    assertThat(0).isEqualTo(socketAddress.getPort());
    assertThat("").isEqualTo(service.url());
  }

  @Test
  public void getSocketAddressWhenBindingToAllInterfaces() throws Exception {
    final JsonRpcConfiguration config = createJsonRpcConfig();
    config.setHost("0.0.0.0");
    final JsonRpcHttpService service = createJsonRpcHttpService(config);
    service.start().join();

    try {
      final InetSocketAddress socketAddress = service.socketAddress();
      assertThat("0.0.0.0").isEqualTo(socketAddress.getAddress().getHostAddress());
      assertThat(socketAddress.getPort() > 0).isTrue();
      assertThat(!service.url().contains("0.0.0.0")).isTrue();
    } finally {
      service.stop().join();
    }
  }

  @Test
  public void responseContainsJsonContentTypeHeader() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.header("Content-Type")).isEqualTo("application/json");
    }
  }

  @Test
  public void web3ClientVersionSuccessful() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void netVersionSuccessful() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_version\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(String.valueOf(CHAIN_ID));
    }
  }

  @Test
  public void ethAccountsSuccessful() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"eth_accounts\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonArray result = json.getJsonArray("result");
      assertThat(result.size()).isEqualTo(0);
    }
  }

  @Test
  public void netPeerCountSuccessful() throws Exception {
    when(peerDiscoveryMock.getPeerCount()).thenReturn(3);

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_peerCount\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String expectedResult = "0x3";
      assertThat(json.getString("result")).isEqualTo(expectedResult);
    }
  }

  @Test
  public void ethGetUncleCountByBlockHash() throws Exception {
    final int uncleCount = 2;
    final Hash blockHash = Hash.hash(BytesValue.of(1));
    when(blockchainQueries.getOmmerCount(eq(blockHash))).thenReturn(Optional.of(uncleCount));

    final String id = "123";
    final String params = "\"params\": [\"" + blockHash + "\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockHash\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String jsonStr = resp.body().string();
      final JsonObject json = new JsonObject(jsonStr);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String expectedResult = "0x2";
      assertThat(json.getString("result")).isEqualTo(expectedResult);
    }
  }

  @Test
  public void ethGetUncleCountByBlockHashNoData() throws Exception {
    final Hash blockHash = Hash.hash(BytesValue.of(1));
    when(blockchainQueries.getOmmerCount(eq(blockHash))).thenReturn(Optional.empty());

    final String id = "123";
    final String params = "\"params\": [\"" + blockHash + "\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockHash\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String jsonStr = resp.body().string();
      final JsonObject json = new JsonObject(jsonStr);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      assertThat(json.getString("result")).isNull();
    }
  }

  @Test
  public void ethGetUncleCountByBlockNumber() throws Exception {
    final int uncleCount = 2;
    final String number = "0x567";
    final long blockNumber = Long.decode(number);
    when(blockchainQueries.getOmmerCount(eq(blockNumber))).thenReturn(Optional.of(uncleCount));

    final String id = "123";
    final String params = "\"params\": [\"" + number + "\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockNumber\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String expectedResult = "0x2";
      assertThat(json.getString("result")).isEqualTo(expectedResult);
    }
  }

  @Test
  public void ethGetUncleCountByBlockNumberNoData() throws Exception {
    final String number = "0x567";
    final long blockNumber = Long.decode(number);
    when(blockchainQueries.getOmmerCount(eq(blockNumber))).thenReturn(Optional.empty());

    final String id = "123";
    final String params = "\"params\": [\"" + number + "\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockNumber\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      assertThat(json.getString("result")).isNull();
    }
  }

  @Test
  public void ethGetUncleCountByBlockNumberEarliest() throws Exception {
    final int uncleCount = 2;
    when(blockchainQueries.getOmmerCount(eq(BlockHeader.GENESIS_BLOCK_NUMBER)))
        .thenReturn(Optional.of(uncleCount));

    final String id = "123";
    final String params = "\"params\": [\"earliest\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockNumber\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String expectedResult = "0x2";
      assertThat(json.getString("result")).isEqualTo(expectedResult);
    }
  }

  @Test
  public void ethGetUncleCountByBlockNumberLatest() throws Exception {
    final int uncleCount = 0;
    when(blockchainQueries.headBlockNumber()).thenReturn(0L);
    when(blockchainQueries.getOmmerCount(eq(0L))).thenReturn(Optional.of(uncleCount));

    final String id = "123";
    final String params = "\"params\": [\"latest\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockNumber\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String expectedResult = "0x0";
      assertThat(json.getString("result")).isEqualTo(expectedResult);
    }
  }

  @Test
  public void ethGetUncleCountByBlockNumberPending() throws Exception {
    final String id = "123";
    final String params = "\"params\": [\"pending\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockNumber\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      assertThat(json.getString("result")).isEqualTo("0x0");
    }
  }

  @Test
  public void ethGetUncleCountByBlockNumberPendingNoData() throws Exception {
    final String id = "123";
    final String params = "\"params\": [\"pending\"]";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ","
                + params
                + ",\"method\":\"eth_getUncleCountByBlockNumber\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      assertThat(json.getString("result")).isEqualTo("0x0");
    }
  }

  @Test
  public void netPeerCountOfZero() throws Exception {
    when(peerDiscoveryMock.getPeers()).thenReturn(Collections.emptyList());
    when(peerDiscoveryMock.getPeerCount()).thenReturn(0);

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_peerCount\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String expectedResult = "0x0";
      assertThat(json.getString("result")).isEqualTo(expectedResult);
    }
  }

  @Test
  public void getBalanceForLatest() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final String mockBalance = "0x35";
    when(blockchainQueries.headBlockNumber()).thenReturn(0L);
    when(blockchainQueries.accountBalance(eq(address), eq(0L)))
        .thenReturn(Optional.of(Wei.fromHexString(mockBalance)));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBalance\", \"params\": [\""
                + address
                + "\",\"latest\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(mockBalance).isEqualTo(result);
    }
  }

  @Test
  public void getBalanceForLatestWithZeroBalance() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final Wei mockBalance = Wei.of(0);
    when(blockchainQueries.headBlockNumber()).thenReturn(0L);
    when(blockchainQueries.accountBalance(eq(address), eq(0L)))
        .thenReturn(Optional.of(mockBalance));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBalance\", \"params\": [\""
                + address
                + "\",\"latest\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat("0x0").isEqualTo(result);
    }
  }

  @Test
  public void getBalanceForEarliest() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final String mockBalance = "0x33";
    when(blockchainQueries.accountBalance(eq(address), eq(0L)))
        .thenReturn(Optional.of(Wei.fromHexString(mockBalance)));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBalance\", \"params\": [\""
                + address
                + "\",\"earliest\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(mockBalance).isEqualTo(result);
    }
  }

  @Test
  public void getBalanceByBlockNumber() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final String mockBalance = "0x32";
    final long blockNumber = 13L;
    when(blockchainQueries.accountBalance(eq(address), eq(blockNumber)))
        .thenReturn(Optional.of(Wei.fromHexString(mockBalance)));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBalance\", \"params\": [\""
                + address
                + "\",\"0x"
                + Long.toString(blockNumber, 16)
                + "\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(mockBalance).isEqualTo(result);
    }
  }

  @Test
  public void getBlockByHashForUnknownBlock() throws Exception {
    final String id = "123";
    final String blockHashString =
        "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d15273321";
    final Hash blockHash = Hash.fromHexString(blockHashString);
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [\""
                + blockHashString
                + "\",true]}");

    // Setup mocks
    when(blockchainQueries.blockByHash(eq(blockHash))).thenReturn(Optional.empty());

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final Object result = json.getValue("result");
      // For now, no block will be returned so we should get null
      assertThat(result).isNull();
    }
  }

  @Test
  public void getBlockByHashWithTransactions() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.block();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWMetadata =
        blockWithMetadata(block);
    final Hash blockHash = block.getHeader().getHash();
    when(blockchainQueries.blockByHash(eq(blockHash))).thenReturn(Optional.of(blockWMetadata));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [\""
                + blockHash
                + "\",true]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      verifyBlockResult(block, blockWMetadata.getTotalDifficulty(), result, false);
    }
  }

  @Test
  public void getBlockByHashWithTransactionHashes() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.block();
    final BlockWithMetadata<Hash, Hash> blockWMetadata = blockWithMetadataAndTxHashes(block);
    final Hash blockHash = block.getHeader().getHash();
    when(blockchainQueries.blockByHashWithTxHashes(eq(blockHash)))
        .thenReturn(Optional.of(blockWMetadata));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [\""
                + blockHash
                + "\",false]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      verifyBlockResult(block, blockWMetadata.getTotalDifficulty(), result, true);
    }
  }

  @Test
  public void getBlockByHashWithMissingHashParameter() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [true]}");

    // Setup mocks
    when(blockchainQueries.blockByHash(ArgumentMatchers.isA(Hash.class)))
        .thenReturn(Optional.empty());

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByHashWithMissingBooleanParameter() throws Exception {
    final String id = "123";
    final String blockHashString =
        "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d15273321";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [\""
                + blockHashString
                + "\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByHashWithInvalidHashParameterWithOddLength() throws Exception {
    final String id = "123";
    final String blockHashString = "0xe";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [\""
                + blockHashString
                + "\",true]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByHashWithInvalidHashParameterThatIsTooShort() throws Exception {
    final String id = "123";
    final String blockHashString = "0xe670";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [\""
                + blockHashString
                + "\",true]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByHashWithInvalidBooleanParameter() throws Exception {
    final String id = "123";
    final String blockHashString =
        "0xe670ec64341771606e55d6b4ca35a1a6b75ee3d5145a99d05921026d15273321";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": [\""
                + blockHashString
                + "\",{}]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByHashWithAllParametersMissing() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\", \"params\": []}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByHashWithNoParameters() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByHash\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByNumberWithTransactions() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.block();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
        blockWithMetadata(block);
    final long number = block.getHeader().getNumber();
    when(blockchainQueries.blockByNumber(eq(number))).thenReturn(Optional.of(blockWithMetadata));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByNumber\", \"params\": [\"0x"
                + Long.toString(number, 16)
                + "\",true]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      verifyBlockResult(block, blockWithMetadata.getTotalDifficulty(), result, false);
    }
  }

  @Test
  public void getBlockByNumberWithTransactionHashes() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.block();
    final BlockWithMetadata<Hash, Hash> blockWithMetadata = blockWithMetadataAndTxHashes(block);
    final long number = block.getHeader().getNumber();
    when(blockchainQueries.blockByNumberWithTxHashes(eq(number)))
        .thenReturn(Optional.of(blockWithMetadata));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByNumber\", \"params\": [\"0x"
                + Long.toString(number, 16)
                + "\",false]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      verifyBlockResult(block, blockWithMetadata.getTotalDifficulty(), result, true);
    }
  }

  @Test
  public void getBlockByNumberForInvalidBlockParameter() throws Exception {
    final String id = "123";

    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByNumber\", \"params\": [\"bla\",false]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void getBlockByNumberForEarliest() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.genesisBlock();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
        blockWithMetadata(block);
    when(blockchainQueries.blockByNumber(eq(BlockHeader.GENESIS_BLOCK_NUMBER)))
        .thenReturn(Optional.of(blockWithMetadata));

    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByNumber\", \"params\": [\"earliest\",true]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      verifyBlockResult(block, blockWithMetadata.getTotalDifficulty(), result, false);
    }
  }

  @Test
  public void getBlockByNumberForBlockNumberZero() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByNumber\", \"params\": [\"0x0\",true]}");

    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.genesisBlock();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
        blockWithMetadata(block);
    when(blockchainQueries.blockByNumber(eq(0L))).thenReturn(Optional.of(blockWithMetadata));

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      verifyBlockResult(block, blockWithMetadata.getTotalDifficulty(), result, false);
    }
  }

  @Test
  public void getBlockByNumberForLatest() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByNumber\", \"params\": [\"latest\",true]}");

    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Block block = gen.genesisBlock();
    final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata =
        blockWithMetadata(block);
    when(blockchainQueries.headBlockNumber()).thenReturn(0L);
    when(blockchainQueries.blockByNumber(eq(0L))).thenReturn(Optional.of(blockWithMetadata));

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      verifyBlockResult(block, blockWithMetadata.getTotalDifficulty(), result, false);
    }
  }

  @Test
  public void getBlockByNumberForPending() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getBlockByNumber\", \"params\": [\"pending\",true]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final JsonObject result = json.getJsonObject("result");
      assertThat(result).isNull();
    }
  }

  @Test
  public void extraneousParameters() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\", \"params\": [1,2,3]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void requestMissingVersionFieldShouldSucceed() throws Exception {
    final String id = "456";
    final RequestBody body =
        RequestBody.create(
            JSON, "{\"id\":" + Json.encode(id) + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void notification() throws Exception {
    // No id field is present - marking this as a notification
    final RequestBody body =
        RequestBody.create(JSON, "{\"jsonrpc\":\"2.0\",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      // Notifications return an empty response
      assertThat(resp.code()).isEqualTo(200);
      final String resBody = resp.body().string();
      assertThat(resBody).isEqualTo("");
    }
  }

  @Test
  public void nullId() throws Exception {
    // Be lenient - allow explicit null id fields
    final RequestBody body =
        RequestBody.create(
            JSON, "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, null);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void emptyStringIdField() throws Exception {
    final String id = "";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      // An empty string is still a string, so should be a valid id
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void negativeNumericId() throws Exception {
    final int id = -1;
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void largeNumericId() throws Exception {
    final BigInteger id =
        new BigInteger(
            "234567899875432345679098765323457892345678998754323456790987653234578923456789987543234567909876532345789");

    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void largeStringId() throws Exception {
    final StringBuilder idBuilder = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      idBuilder.append(i);
    }
    final String id = idBuilder.toString();

    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void fractionalNumericId() throws Exception {
    final double id = 1.5;
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void objectId() throws Exception {
    final RequestBody body =
        RequestBody.create(
            JSON, "{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_REQUEST;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void arrayId() throws Exception {
    final RequestBody body =
        RequestBody.create(
            JSON, "{\"jsonrpc\":\"2.0\",\"id\":[],\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_REQUEST;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void missingMethodField() throws Exception {
    final Integer id = 2;
    final RequestBody body =
        RequestBody.create(JSON, "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + "}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_REQUEST;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void invalidJson() throws Exception {
    final RequestBody body = RequestBody.create(JSON, "{bla");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.PARSE_ERROR;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void wrongJsonType() throws Exception {
    final RequestBody body = RequestBody.create(JSON, "\"a string\"");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.PARSE_ERROR;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void requestWithWrongVersionShouldSucceed() throws Exception {
    final String id = "234";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"1.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      final String result = json.getString("result");
      assertThat(result).isEqualTo(CLIENT_VERSION);
    }
  }

  @Test
  public void unknownMethod() throws Exception {
    final String id = "234";
    final RequestBody body =
        RequestBody.create(
            JSON, "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"bla\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.METHOD_NOT_FOUND;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void exceptionallyHandleJsonSingleRequest() throws Exception {
    final JsonRpcMethod jsonRpcMethod = mock(JsonRpcMethod.class);
    when(jsonRpcMethod.getName()).thenReturn("foo");
    when(jsonRpcMethod.response(ArgumentMatchers.any()))
        .thenThrow(new RuntimeException("test exception"));

    doReturn(Optional.of(jsonRpcMethod)).when(rpcMethods).get("foo");

    final RequestBody body =
        RequestBody.create(JSON, "{\"jsonrpc\":\"2.0\",\"id\":\"666\",\"method\":\"foo\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(500);
    }
  }

  @Test
  public void exceptionallyHandleJsonBatchRequest() throws Exception {
    final JsonRpcMethod jsonRpcMethod = mock(JsonRpcMethod.class);
    when(jsonRpcMethod.getName()).thenReturn("foo");
    when(jsonRpcMethod.response(ArgumentMatchers.any()))
        .thenThrow(new RuntimeException("test exception"));
    doReturn(Optional.of(jsonRpcMethod)).when(rpcMethods).get("foo");

    final RequestBody body =
        RequestBody.create(
            JSON,
            "[{\"jsonrpc\":\"2.0\",\"id\":\"000\",\"method\":\"web3_clientVersion\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":\"111\",\"method\":\"foo\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":\"222\",\"method\":\"net_version\"}]");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(500);
    }
  }

  @Test
  public void batchRequest() throws Exception {
    final int clientVersionRequestId = 2;
    final int brokenRequestId = 3;
    final int netVersionRequestId = 4;
    final RequestBody body =
        RequestBody.create(
            JSON,
            "[{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(clientVersionRequestId)
                + ",\"method\":\"web3_clientVersion\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(brokenRequestId)
                + ",\"method\":\"bla\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(netVersionRequestId)
                + ",\"method\":\"net_version\"}]");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonArray json = new JsonArray(resp.body().string());
      final int requestCount = 3;
      assertThat(json.size()).isEqualTo(requestCount);
      final Map<Integer, JsonObject> responses = new HashMap<>();
      for (int i = 0; i < requestCount; ++i) {
        final JsonObject response = json.getJsonObject(i);
        responses.put(response.getInteger("id"), response);
      }

      // Check result web3_clientVersion
      final JsonObject jsonClientVersion = responses.get(clientVersionRequestId);
      testHelper.assertValidJsonRpcResult(jsonClientVersion, clientVersionRequestId);
      assertThat(jsonClientVersion.getString("result")).isEqualTo(CLIENT_VERSION);

      // Check result unknown method
      final JsonObject jsonError = responses.get(brokenRequestId);
      final JsonRpcError expectedError = JsonRpcError.METHOD_NOT_FOUND;
      testHelper.assertValidJsonRpcError(
          jsonError, brokenRequestId, expectedError.getCode(), expectedError.getMessage());

      // Check result net_version
      final JsonObject jsonNetVersion = responses.get(netVersionRequestId);
      testHelper.assertValidJsonRpcResult(jsonNetVersion, netVersionRequestId);
      assertThat(jsonNetVersion.getString("result")).isEqualTo(String.valueOf(CHAIN_ID));
    }
  }

  @Test
  public void batchRequestContainingInvalidRequest() throws Exception {
    final int clientVersionRequestId = 2;
    final int invalidId = 3;
    final int netVersionRequestId = 4;
    final String[] reqs = new String[3];
    reqs[0] =
        "{\"jsonrpc\":\"2.0\",\"id\":"
            + Json.encode(clientVersionRequestId)
            + ",\"method\":\"web3_clientVersion\"}";
    reqs[1] = "5";
    reqs[2] =
        "{\"jsonrpc\":\"2.0\",\"id\":"
            + Json.encode(netVersionRequestId)
            + ",\"method\":\"net_version\"}";
    final String batchRequest = "[" + String.join(", ", reqs) + "]";
    final RequestBody body = RequestBody.create(JSON, batchRequest);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String jsonStr = resp.body().string();
      final JsonArray json = new JsonArray(jsonStr);
      final int requestCount = 3;
      assertThat(json.size()).isEqualTo(requestCount);

      // Organize results for inspection
      final Map<Integer, JsonObject> responses = new HashMap<>();
      for (int i = 0; i < requestCount; ++i) {
        final JsonObject response = json.getJsonObject(i);
        Integer identifier = response.getInteger("id");
        if (identifier == null) {
          identifier = invalidId;
        }
        responses.put(identifier, response);
      }

      // Check result web3_clientVersion
      final JsonObject jsonClientVersion = responses.get(clientVersionRequestId);
      testHelper.assertValidJsonRpcResult(jsonClientVersion, clientVersionRequestId);
      assertThat(jsonClientVersion.getString("result")).isEqualTo(CLIENT_VERSION);

      // Check invalid request
      final JsonObject jsonError = responses.get(invalidId);
      final JsonRpcError expectedError = JsonRpcError.INVALID_REQUEST;
      testHelper.assertValidJsonRpcError(
          jsonError, null, expectedError.getCode(), expectedError.getMessage());

      // Check result net_version
      final JsonObject jsonNetVersion = responses.get(netVersionRequestId);
      testHelper.assertValidJsonRpcResult(jsonNetVersion, netVersionRequestId);
      assertThat(jsonNetVersion.getString("result")).isEqualTo(String.valueOf(CHAIN_ID));
    }
  }

  @Test
  public void batchRequestParseError() throws Exception {
    final String req =
        "[\n"
            + "  {\"jsonrpc\": \"2.0\", \"method\": \"net_version\", \"id\": \"1\"},\n"
            + "  {\"jsonrpc\": \"2.0\", \"method\"\n"
            + "]";

    final RequestBody body = RequestBody.create(JSON, req);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.PARSE_ERROR;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void batchRequestWithNotifications() throws Exception {
    final int clientVersionRequestId = 2;
    final int netVersionRequestId = 3;
    final RequestBody body =
        RequestBody.create(
            JSON,
            "[{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(clientVersionRequestId)
                + ",\"method\":\"web3_clientVersion\"},"
                + "{\"jsonrpc\":\"2.0\", \"method\":\"web3_clientVersion\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(netVersionRequestId)
                + ",\"method\":\"net_version\"}]");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final JsonArray json = new JsonArray(resp.body().string());
      // 2 Responses since the notification is ignored
      final int responseCount = 2;
      assertThat(json.size()).isEqualTo(responseCount);
      final Map<Integer, JsonObject> responses = new HashMap<>();
      for (int i = 0; i < responseCount; ++i) {
        final JsonObject response = json.getJsonObject(i);
        responses.put(response.getInteger("id"), response);
      }

      // Check result web3_clientVersion
      final JsonObject jsonClientVersion = responses.get(clientVersionRequestId);
      testHelper.assertValidJsonRpcResult(jsonClientVersion, clientVersionRequestId);
      assertThat(jsonClientVersion.getString("result")).isEqualTo(CLIENT_VERSION);

      // Check result net_version
      final JsonObject jsonNetVersion = responses.get(netVersionRequestId);
      testHelper.assertValidJsonRpcResult(jsonNetVersion, netVersionRequestId);
      assertThat(jsonNetVersion.getString("result")).isEqualTo(String.valueOf(CHAIN_ID));
    }
  }

  /**
   * Tests that empty batch requests are treated as invalid requests as per
   * http://www.jsonrpc.org/specification#batch.
   */
  @Test
  public void emptyBatchRequest() throws Exception {
    final RequestBody body = RequestBody.create(JSON, "[]");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_REQUEST;
      testHelper.assertValidJsonRpcError(
          json, null, expectedError.getCode(), expectedError.getMessage());
    }
  }

  private void verifyBlockResult(
      final Block block,
      final UInt256 td,
      final JsonObject result,
      final boolean shouldTransactionsBeHashed) {
    assertBlockResultMatchesBlock(result, block);

    if (td == null) {
      assertThat(result.getJsonObject("totalDifficulty")).isNull();
    } else {
      assertThat(UInt256.fromHexString(result.getString("totalDifficulty"))).isEqualTo(td);
    }

    // Check ommers
    final JsonArray ommersResult = result.getJsonArray("uncles");
    assertThat(ommersResult.size()).isEqualTo(block.getBody().getOmmers().size());
    for (int i = 0; i < block.getBody().getOmmers().size(); i++) {
      final BlockHeader ommer = block.getBody().getOmmers().get(i);
      final Hash ommerHash = ommer.getHash();
      assertThat(Hash.fromHexString(ommersResult.getString(i))).isEqualTo(ommerHash);
    }

    // Check transactions
    final JsonArray transactionsResult = result.getJsonArray("transactions");
    assertThat(transactionsResult.size()).isEqualTo(block.getBody().getTransactions().size());
    for (int i = 0; i < block.getBody().getTransactions().size(); i++) {
      final Transaction transaction = block.getBody().getTransactions().get(i);
      if (shouldTransactionsBeHashed) {
        assertThat(Hash.fromHexString(transactionsResult.getString(i)))
            .isEqualTo(transaction.hash());
      } else {
        final JsonObject transactionResult = transactionsResult.getJsonObject(i);
        final Integer expectedIndex = i;
        final Hash expectedBlockHash = block.getHeader().getHash();
        final long expectedBlockNumber = block.getHeader().getNumber();
        assertTransactionResultMatchesTransaction(
            transactionResult, transaction, expectedIndex, expectedBlockHash, expectedBlockNumber);
      }
    }
  }

  private void assertTransactionResultMatchesTransaction(
      final JsonObject result,
      final Transaction transaction,
      final Integer index,
      final Hash blockHash,
      final Long blockNumber) {
    assertThat(Hash.fromHexString(result.getString("hash"))).isEqualTo(transaction.hash());
    assertThat(Long.decode(result.getString("nonce"))).isEqualByComparingTo(transaction.getNonce());
    if (blockHash != null) {
      assertThat(Hash.fromHexString(result.getString("blockHash"))).isEqualTo(blockHash);
    } else {
      assertThat(result.getValue("blockHash")).isNull();
    }
    if (blockNumber != null) {
      assertThat(Long.decode(result.getString("blockNumber"))).isEqualTo(blockNumber);
    } else {
      assertThat(result.getValue("blockNumber")).isNull();
    }
    if (index != null) {
      assertThat(UInt256.fromHexString(result.getString("transactionIndex")).toInt())
          .isEqualTo(index);
    } else {
      assertThat(result.getValue("transactionIndex")).isNull();
    }
    assertThat(Address.fromHexString(result.getString("from"))).isEqualTo(transaction.getSender());
    if (transaction.getTo().isPresent()) {
      assertThat(Address.fromHexString(result.getString("to")))
          .isEqualTo(transaction.getTo().get());
    } else {
      assertThat(result.getValue("to")).isNull();
    }
    assertThat(Wei.fromHexString(result.getString("value"))).isEqualTo(transaction.getValue());
    assertThat(Wei.fromHexString(result.getString("gasPrice")))
        .isEqualTo(transaction.getGasPrice());
    assertThat(Long.decode(result.getString("gas"))).isEqualTo(transaction.getGasLimit());
    assertThat(BytesValue.fromHexString(result.getString("input")))
        .isEqualTo(transaction.getPayload());
  }

  private void assertBlockResultMatchesBlock(final JsonObject result, final Block block) {
    final BlockHeader header = block.getHeader();
    assertThat(Hash.fromHexString(result.getString("parentHash")))
        .isEqualTo(header.getParentHash());
    assertThat(Hash.fromHexString(result.getString("sha3Uncles")))
        .isEqualTo(header.getOmmersHash());
    assertThat(Hash.fromHexString(result.getString("transactionsRoot")))
        .isEqualTo(header.getTransactionsRoot());
    assertThat(Hash.fromHexString(result.getString("stateRoot"))).isEqualTo(header.getStateRoot());
    assertThat(Hash.fromHexString(result.getString("receiptsRoot")))
        .isEqualTo(header.getReceiptsRoot());
    assertThat(Address.fromHexString(result.getString("miner"))).isEqualTo(header.getCoinbase());
    assertThat(UInt256.fromHexString(result.getString("difficulty")))
        .isEqualTo(header.getDifficulty());
    assertThat(BytesValue.fromHexString(result.getString("extraData")))
        .isEqualTo(header.getExtraData());
    assertThat(hexStringToInt(result.getString("size"))).isEqualTo(block.calculateSize());
    assertThat(Long.decode(result.getString("gasLimit"))).isEqualTo(header.getGasLimit());
    assertThat(Long.decode(result.getString("gasUsed"))).isEqualTo(header.getGasUsed());
    assertThat(Long.decode(result.getString("timestamp"))).isEqualTo(header.getTimestamp());
    assertThat(Long.decode(result.getString("number"))).isEqualTo(header.getNumber());
    // Nonce is a data field and should represent 8 bytes exactly
    final String nonceResult = result.getString("nonce").toLowerCase();
    assertThat(nonceResult.length() == 18 && nonceResult.startsWith("0x")).isTrue();
    assertThat(Long.parseUnsignedLong(nonceResult.substring(2), 16)).isEqualTo(header.getNonce());
    assertThat(Hash.fromHexString(result.getString("hash"))).isEqualTo(header.getHash());
    assertThat(LogsBloomFilter.fromHexString(result.getString("logsBloom")))
        .isEqualTo(header.getLogsBloom());
  }

  private int hexStringToInt(final String hexString) {
    return BytesValues.extractInt(BytesValue.fromHexStringLenient(hexString));
  }

  @Test
  public void ethSyncingFalse() throws Exception {
    final String id = "007";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"eth_syncing\"}");
    when(synchronizer.getSyncStatus()).thenReturn(Optional.empty());

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Verify general result format.
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Evaluate result.
      assertThat(json.getBoolean("result")).isFalse();
    }
  }

  @Test
  public void ethSyncingResultIsPresent() throws Exception {
    final SyncStatus testResult = new SyncStatus(1L, 8L, 7L);
    when(synchronizer.getSyncStatus()).thenReturn(Optional.of(testResult));
    final String id = "999";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"eth_syncing\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      final JsonObject result = json.getJsonObject("result");
      final long startingBlock = Long.decode(result.getString("startingBlock"));
      assertThat(startingBlock).isEqualTo(1L);
      final long currentBlock = Long.decode(result.getString("currentBlock"));
      assertThat(currentBlock).isEqualTo(8L);
      final long highestBlock = Long.decode(result.getString("highestBlock"));
      assertThat(highestBlock).isEqualTo(7L);
    }
  }

  public BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetadata(final Block block) {
    final UInt256 td = block.getHeader().getDifficulty().plus(10L);
    final int size = block.calculateSize();

    final List<Transaction> txs = block.getBody().getTransactions();
    final List<TransactionWithMetadata> formattedTxs = new ArrayList<>(txs.size());
    for (int i = 0; i < txs.size(); i++) {
      formattedTxs.add(
          new TransactionWithMetadata(
              txs.get(i), block.getHeader().getNumber(), block.getHash(), i));
    }
    final List<Hash> ommers =
        block.getBody().getOmmers().stream().map(BlockHeader::getHash).collect(Collectors.toList());
    return new BlockWithMetadata<>(block.getHeader(), formattedTxs, ommers, td, size);
  }

  public BlockWithMetadata<Hash, Hash> blockWithMetadataAndTxHashes(final Block block) {
    final UInt256 td = block.getHeader().getDifficulty().plus(10L);
    final int size = block.calculateSize();

    final List<Hash> txs =
        block.getBody().getTransactions().stream()
            .map(Transaction::hash)
            .collect(Collectors.toList());
    final List<Hash> ommers =
        block.getBody().getOmmers().stream().map(BlockHeader::getHash).collect(Collectors.toList());
    return new BlockWithMetadata<>(block.getHeader(), txs, ommers, td, size);
  }

  @Test
  public void ethGetStorageLatestAtIndexZero() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final String mockStorage = "0x0000000000000000000000000000000000000000000000000000000000000001";
    when(blockchainQueries.headBlockNumber()).thenReturn(0L);
    when(blockchainQueries.storageAt(eq(address), eq(UInt256.ZERO), eq(0L)))
        .thenReturn(Optional.of(UInt256.fromHexString(mockStorage)));

    final String id = "88";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getStorageAt\", \"params\": [\""
                + address
                + "\",\""
                + UInt256.ZERO
                + "\",\"latest\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat("0x0000000000000000000000000000000000000000000000000000000000000001")
          .isEqualTo(result);
    }
  }

  @Test
  public void ethGetStorageLatestAtIndexOne() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final String mockStorage = "0x0000000000000000000000000000000000000000000000000000000000000006";
    when(blockchainQueries.headBlockNumber()).thenReturn(0L);
    when(blockchainQueries.storageAt(eq(address), eq(UInt256.ONE), eq(0L)))
        .thenReturn(Optional.of(UInt256.fromHexString(mockStorage)));

    final String id = "88";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getStorageAt\", \"params\": [\""
                + address
                + "\",\""
                + UInt256.ONE
                + "\",\"latest\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat("0x0000000000000000000000000000000000000000000000000000000000000006")
          .isEqualTo(result);
    }
  }

  @Test
  public void ethGetStorageAtEarliest() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final String mockStorage = "0x0000000000000000000000000000000000000000000000000000000000000006";
    when(blockchainQueries.storageAt(address, UInt256.ONE, 0L))
        .thenReturn(Optional.of(UInt256.fromHexString(mockStorage)));

    final String id = "88";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getStorageAt\", \"params\": [\""
                + address
                + "\",\""
                + UInt256.ONE
                + "\",\"earliest\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat("0x0000000000000000000000000000000000000000000000000000000000000006")
          .isEqualTo(result);
    }
  }

  @Test
  public void ethGetStorageAtBlockNumber() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();
    final String mockStorage = "0x0000000000000000000000000000000000000000000000000000000000000002";
    when(blockchainQueries.storageAt(address, UInt256.ZERO, 0L))
        .thenReturn(Optional.of(UInt256.fromHexString(mockStorage)));

    final String id = "999";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getStorageAt\", \"params\": [\""
                + address
                + "\",\""
                + UInt256.ZERO
                + "\",\""
                + 0L
                + "\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      // Check general format of result
      final String respBody = resp.body().string();
      final JsonObject json = new JsonObject(respBody);
      testHelper.assertValidJsonRpcResult(json, id);

      // Check result
      final Object result = json.getString("result");
      assertThat("0x0000000000000000000000000000000000000000000000000000000000000002")
          .isEqualTo(result);
    }
  }

  @Test
  public void ethGetStorageAtInvalidParameterStorageIndex() throws Exception {
    // Setup mocks to return a block
    final BlockDataGenerator gen = new BlockDataGenerator();
    final Address address = gen.address();

    final String id = "88";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"eth_getStorageAt\", \"params\": [\""
                + address
                + "\",\""
                + "blah"
                + "\",\"latest\"]}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(400);
      // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      final JsonRpcError expectedError = JsonRpcError.INVALID_PARAMS;
      testHelper.assertValidJsonRpcError(
          json, id, expectedError.getCode(), expectedError.getMessage());
    }
  }

  @Test
  public void assertThatLivenessProbeWorks() throws Exception {
    try (final Response resp = client.newCall(buildGetRequest("/liveness")).execute()) {
      assertThat(resp.code()).isEqualTo(200);
    }
  }

  @Test
  public void assertThatReadinessProbeWorks() throws Exception {
    try (final Response resp = client.newCall(buildGetRequest("/readiness")).execute()) {
      assertThat(resp.code()).isEqualTo(200);
    }
  }

  private Request buildPostRequest(final RequestBody body) {
    return new Request.Builder().post(body).url(baseUrl).build();
  }

  private Request buildGetRequest(final String path) {
    return new Request.Builder().get().url(baseUrl + path).build();
  }
}

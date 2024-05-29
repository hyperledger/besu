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
package org.hyperledger.besu.ethereum.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import graphql.GraphQL;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class GraphQLHttpServiceTest {

  // this tempDir is deliberately static
  @TempDir private static Path folder;

  private static final Vertx vertx = Vertx.vertx();

  private static GraphQLHttpService service;
  private static OkHttpClient client;
  private static String baseUrl;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");
  private static BlockchainQueries blockchainQueries;
  private static GraphQL graphQL;
  private static Map<GraphQLContextType, Object> graphQlContextMap;
  private static PoWMiningCoordinator miningCoordinatorMock;

  private final GraphQLTestHelper testHelper = new GraphQLTestHelper();

  @BeforeAll
  public static void initServerAndClient() throws Exception {
    blockchainQueries = Mockito.mock(BlockchainQueries.class);
    final Synchronizer synchronizer = Mockito.mock(Synchronizer.class);
    graphQL = Mockito.mock(GraphQL.class);

    miningCoordinatorMock = Mockito.mock(PoWMiningCoordinator.class);
    graphQlContextMap =
        Map.of(
            GraphQLContextType.BLOCKCHAIN_QUERIES,
            blockchainQueries,
            GraphQLContextType.TRANSACTION_POOL,
            Mockito.mock(TransactionPool.class),
            GraphQLContextType.MINING_COORDINATOR,
            miningCoordinatorMock,
            GraphQLContextType.SYNCHRONIZER,
            synchronizer);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);
    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers(supportedCapabilities);
    graphQL = GraphQLProvider.buildGraphQL(dataFetchers);
    service = createGraphQLHttpService();
    service.start().join();
    // Build an OkHttp client.
    client = new OkHttpClient.Builder().followRedirects(false).build();

    baseUrl = service.url() + "/graphql/";
  }

  private static GraphQLHttpService createGraphQLHttpService(final GraphQLConfiguration config)
      throws Exception {
    return new GraphQLHttpService(
        vertx, folder, config, graphQL, graphQlContextMap, Mockito.mock(EthScheduler.class));
  }

  private static GraphQLHttpService createGraphQLHttpService() throws Exception {
    return new GraphQLHttpService(
        vertx,
        folder,
        createGraphQLConfig(),
        graphQL,
        graphQlContextMap,
        Mockito.mock(EthScheduler.class));
  }

  private static GraphQLConfiguration createGraphQLConfig() {
    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();
    config.setPort(0);
    return config;
  }

  @BeforeAll
  public static void setupConstants() {
    final URL blocksUrl = BlockTestUtil.getTestBlockchainUrl();

    final URL genesisJsonUrl = BlockTestUtil.getTestGenesisUrl();

    Assertions.assertThat(blocksUrl).isNotNull();
    Assertions.assertThat(genesisJsonUrl).isNotNull();
  }

  /** Tears down the HTTP server. */
  @AfterAll
  public static void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
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
      Assertions.assertThat(resp.code()).isEqualTo(404);
    }
  }

  @Test
  public void handleEmptyRequestAndRedirect_post() throws Exception {
    final RequestBody body = RequestBody.create("", null);
    try (final Response resp =
        client.newCall(new Request.Builder().post(body).url(service.url()).build()).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(HttpResponseStatus.PERMANENT_REDIRECT.code());
      final String location = resp.header("Location");
      Assertions.assertThat(location).isNotEmpty().isNotNull();
      final HttpUrl redirectUrl = resp.request().url().resolve(location);
      Assertions.assertThat(redirectUrl).isNotNull();
      final Request.Builder redirectBuilder = resp.request().newBuilder();
      redirectBuilder.post(resp.request().body());
      resp.body().close();
      try (final Response redirectResp =
          client.newCall(redirectBuilder.url(redirectUrl).build()).execute()) {
        Assertions.assertThat(redirectResp.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code());
      }
    }
  }

  @Test
  public void handleEmptyRequestAndRedirect_get() throws Exception {
    try (final Response resp =
        client.newCall(new Request.Builder().get().url(service.url()).build()).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(HttpResponseStatus.PERMANENT_REDIRECT.code());
      final String location = resp.header("Location");
      Assertions.assertThat(location).isNotEmpty().isNotNull();
      final HttpUrl redirectUrl = resp.request().url().resolve(location);
      Assertions.assertThat(redirectUrl).isNotNull();
      final Request.Builder redirectBuilder = resp.request().newBuilder();
      redirectBuilder.get();
      // resp.body().close();
      try (final Response redirectResp =
          client.newCall(redirectBuilder.url(redirectUrl).build()).execute()) {
        Assertions.assertThat(redirectResp.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code());
      }
    }
  }

  @Test
  public void handleInvalidQuerySchema() throws Exception {
    final RequestBody body = RequestBody.create("{gasPrice1}", GRAPHQL);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLError(json);
      Assertions.assertThat(resp.code()).isEqualTo(400);
    }
  }

  @Test
  public void query_get() throws Exception {
    final Wei price = Wei.of(16);
    Mockito.when(blockchainQueries.gasPrice()).thenReturn(price);
    Mockito.when(miningCoordinatorMock.getMinTransactionGasPrice()).thenReturn(price);

    try (final Response resp = client.newCall(buildGetRequest("?query={gasPrice}")).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200);
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("gasPrice");
      Assertions.assertThat(result).isEqualTo("0x10");
    }
  }

  @Test
  public void query_jsonPost() throws Exception {
    final RequestBody body = RequestBody.create("{\"query\":\"{gasPrice}\"}", JSON);
    final Wei price = Wei.of(16);
    Mockito.when(miningCoordinatorMock.getMinTransactionGasPrice()).thenReturn(price);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200); // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("gasPrice");
      Assertions.assertThat(result).isEqualTo("0x10");
    }
  }

  @Test
  public void query_graphqlPost() throws Exception {
    final RequestBody body = RequestBody.create("{gasPrice}", GRAPHQL);
    final Wei price = Wei.of(16);
    Mockito.when(miningCoordinatorMock.getMinTransactionGasPrice()).thenReturn(price);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200); // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("gasPrice");
      Assertions.assertThat(result).isEqualTo("0x10");
    }
  }

  @Test
  public void query_untypedPost() throws Exception {
    final RequestBody body = RequestBody.create("{gasPrice}", null);
    final Wei price = Wei.of(16);
    Mockito.when(miningCoordinatorMock.getMinTransactionGasPrice()).thenReturn(price);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200); // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("gasPrice");
      Assertions.assertThat(result).isEqualTo("0x10");
    }
  }

  @Test
  public void getSocketAddressWhenActive() {
    final InetSocketAddress socketAddress = service.socketAddress();
    Assertions.assertThat(socketAddress.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
    Assertions.assertThat(socketAddress.getPort()).isPositive();
  }

  @Test
  public void getSocketAddressWhenStoppedIsEmpty() throws Exception {
    final GraphQLHttpService service = createGraphQLHttpService();

    final InetSocketAddress socketAddress = service.socketAddress();
    Assertions.assertThat(socketAddress.getAddress().getHostAddress()).isEqualTo("0.0.0.0");
    Assertions.assertThat(socketAddress.getPort()).isZero();
    Assertions.assertThat(service.url()).isEmpty();
  }

  @Test
  public void getSocketAddressWhenBindingToAllInterfaces() throws Exception {
    final GraphQLConfiguration config = createGraphQLConfig();
    config.setHost("0.0.0.0");
    final GraphQLHttpService service = createGraphQLHttpService(config);
    service.start().join();

    try {
      final InetSocketAddress socketAddress = service.socketAddress();
      Assertions.assertThat(socketAddress.getAddress().getHostAddress()).isEqualTo("0.0.0.0");
      Assertions.assertThat(socketAddress.getPort()).isPositive();
      Assertions.assertThat(!service.url().contains("0.0.0.0")).isTrue();
    } finally {
      service.stop().join();
    }
  }

  @Test
  public void responseContainsJsonContentTypeHeader() throws Exception {

    final RequestBody body = RequestBody.create("{gasPrice}", GRAPHQL);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.header("Content-Type")).isEqualTo(JSON.toString());
    }
  }

  @Test
  public void ethGetUncleCountByBlockHash() throws Exception {
    final int uncleCount = 4;
    final Hash blockHash = Hash.hash(Bytes.of(1));
    @SuppressWarnings("unchecked")
    final BlockWithMetadata<TransactionWithMetadata, Hash> block =
        Mockito.mock(BlockWithMetadata.class);
    @SuppressWarnings("unchecked")
    final List<Hash> list = Mockito.mock(List.class);

    Mockito.when(blockchainQueries.blockByHash(blockHash)).thenReturn(Optional.of(block));
    Mockito.when(block.getOmmers()).thenReturn(list);
    Mockito.when(list.size()).thenReturn(uncleCount);

    final String query = "{block(hash:\"" + blockHash + "\") {ommerCount}}";

    final RequestBody body = RequestBody.create(query, GRAPHQL);
    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200);
      final String jsonStr = resp.body().string();
      final JsonObject json = new JsonObject(jsonStr);
      testHelper.assertValidGraphQLResult(json);
      final String result =
          json.getJsonObject("data").getJsonObject("block").getString("ommerCount");
      Assertions.assertThat(Bytes.fromHexStringLenient(result).toInt()).isEqualTo(uncleCount);
    }
  }

  @Test
  public void ethGetUncleCountByBlockNumber() throws Exception {
    final int uncleCount = 5;
    @SuppressWarnings("unchecked")
    final BlockWithMetadata<TransactionWithMetadata, Hash> block =
        Mockito.mock(BlockWithMetadata.class);
    @SuppressWarnings("unchecked")
    final List<Hash> list = Mockito.mock(List.class);
    Mockito.when(blockchainQueries.blockByNumber(ArgumentMatchers.anyLong()))
        .thenReturn(Optional.of(block));
    Mockito.when(block.getOmmers()).thenReturn(list);
    Mockito.when(list.size()).thenReturn(uncleCount);

    final String query = "{block(number:\"3\") {ommerCount}}";

    final RequestBody body = RequestBody.create(query, GRAPHQL);
    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200);
      final String jsonStr = resp.body().string();
      final JsonObject json = new JsonObject(jsonStr);
      testHelper.assertValidGraphQLResult(json);
      final String result =
          json.getJsonObject("data").getJsonObject("block").getString("ommerCount");
      Assertions.assertThat(Bytes.fromHexStringLenient(result).toInt()).isEqualTo(uncleCount);
    }
  }

  @Test
  public void ethGetUncleCountByBlockLatest() throws Exception {
    final int uncleCount = 5;
    @SuppressWarnings("unchecked")
    final BlockWithMetadata<TransactionWithMetadata, Hash> block =
        Mockito.mock(BlockWithMetadata.class);
    @SuppressWarnings("unchecked")
    final List<Hash> list = Mockito.mock(List.class);
    Mockito.when(blockchainQueries.latestBlock()).thenReturn(Optional.of(block));
    Mockito.when(block.getOmmers()).thenReturn(list);
    Mockito.when(list.size()).thenReturn(uncleCount);

    final String query = "{block {ommerCount}}";

    final RequestBody body = RequestBody.create(query, GRAPHQL);
    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200);
      final String jsonStr = resp.body().string();
      final JsonObject json = new JsonObject(jsonStr);
      testHelper.assertValidGraphQLResult(json);
      final String result =
          json.getJsonObject("data").getJsonObject("block").getString("ommerCount");
      Assertions.assertThat(Bytes.fromHexStringLenient(result).toInt()).isEqualTo(uncleCount);
    }
  }

  private Request buildPostRequest(final RequestBody body) {
    return new Request.Builder().post(body).url(baseUrl).build();
  }

  private Request buildGetRequest(final String path) {
    return new Request.Builder().get().url(baseUrl + path).build();
  }
}

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
package tech.pegasys.pantheon.ethereum.graphqlrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.ethereum.graphqlrpc.internal.BlockchainQuery;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import graphql.GraphQL;
import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GraphQLRpcHttpServiceHostWhitelistTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  protected static Vertx vertx;

  private static GraphQLRpcHttpService service;
  private static OkHttpClient client;
  private static String baseUrl;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private final GraphQLRpcConfiguration graphQLRpcConfig = createGraphQLRpcConfig();
  private final List<String> hostsWhitelist = Arrays.asList("ally", "friend");

  @Before
  public void initServerAndClient() throws Exception {
    vertx = Vertx.vertx();

    service = createGraphQLRpcHttpService();
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url();
  }

  private GraphQLRpcHttpService createGraphQLRpcHttpService() throws Exception {
    final BlockchainQuery blockchainQueries = mock(BlockchainQuery.class);
    final Synchronizer synchronizer = mock(Synchronizer.class);

    final EthHashMiningCoordinator miningCoordinatorMock = mock(EthHashMiningCoordinator.class);

    final GraphQLDataFetcherContext dataFetcherContext = mock(GraphQLDataFetcherContext.class);
    when(dataFetcherContext.getBlockchainQuery()).thenReturn(blockchainQueries);
    when(dataFetcherContext.getMiningCoordinator()).thenReturn(miningCoordinatorMock);

    when(dataFetcherContext.getTransactionPool()).thenReturn(mock(TransactionPool.class));
    when(dataFetcherContext.getSynchronizer()).thenReturn(synchronizer);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);
    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers(supportedCapabilities);
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    return new GraphQLRpcHttpService(
        vertx, folder.newFolder().toPath(), graphQLRpcConfig, graphQL, dataFetcherContext);
  }

  private static GraphQLRpcConfiguration createGraphQLRpcConfig() {
    final GraphQLRpcConfiguration config = GraphQLRpcConfiguration.createDefault();
    config.setPort(0);
    return config;
  }

  @After
  public void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
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
    graphQLRpcConfig.setHostsWhitelist(Collections.singletonList("*"));
    assertThat(doRequest("ally")).isEqualTo(200);
    assertThat(doRequest("foe")).isEqualTo(200);
  }

  @Test
  public void requestWithWhitelistedHostIsAccepted() throws IOException {
    graphQLRpcConfig.setHostsWhitelist(hostsWhitelist);
    assertThat(doRequest("ally")).isEqualTo(200);
    assertThat(doRequest("ally:12345")).isEqualTo(200);
    assertThat(doRequest("friend")).isEqualTo(200);
  }

  @Test
  public void requestWithUnknownHostIsRejected() throws IOException {
    graphQLRpcConfig.setHostsWhitelist(hostsWhitelist);
    assertThat(doRequest("foe")).isEqualTo(403);
  }

  private int doRequest(final String hostname) throws IOException {
    final RequestBody body = RequestBody.create(JSON, "{protocolVersion}");

    final Request build =
        new Request.Builder()
            .post(body)
            .url(baseUrl + "/graphql")
            .addHeader("Host", hostname)
            .build();
    return client.newCall(build).execute().code();
  }

  @Test
  public void requestWithMalformedHostIsRejected() throws IOException {
    graphQLRpcConfig.setHostsWhitelist(hostsWhitelist);
    assertThat(doRequest("ally:friend")).isEqualTo(403);
    assertThat(doRequest("ally:123456")).isEqualTo(403);
    assertThat(doRequest("ally:friend:1234")).isEqualTo(403);
  }
}

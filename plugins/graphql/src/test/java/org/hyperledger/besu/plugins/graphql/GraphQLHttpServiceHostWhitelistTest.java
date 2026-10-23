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
package org.hyperledger.besu.plugins.graphql;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.services.BlockchainQueryService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import graphql.GraphQL;
import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GraphQLHttpServiceHostWhitelistTest {

  @TempDir private Path folder;

  protected static Vertx vertx;

  private static GraphQLHttpService service;
  private static OkHttpClient client;
  private static String baseUrl;
  protected static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");
  private final GraphQLConfiguration graphQLConfig = createGraphQLConfig();
  private final List<String> hostsWhitelist = Arrays.asList("ally", "friend");

  @BeforeEach
  public void initServerAndClient() throws Exception {
    vertx = Vertx.vertx();

    service = createGraphQLHttpService();
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url();
  }

  private GraphQLHttpService createGraphQLHttpService() throws Exception {
    final BlockchainQueryService blockchainQueries = mock(BlockchainQueryService.class);
    when(blockchainQueries.getOracleMaxPriorityFeePerGas()).thenReturn(Wei.ONE);
    final SynchronizationService synchronizer = mock(SynchronizationService.class);

    final Map<GraphQLContextType, Object> graphQLContextMap =
        Map.of(
            GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE,
            blockchainQueries,
            GraphQLContextType.TRANSACTION_POOL_SERVICE,
            mock(TransactionPoolService.class),
            GraphQLContextType.SYNCHRONIZATION_SERVICE,
            synchronizer);

    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers();
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    return new GraphQLHttpService(vertx, folder, graphQLConfig, graphQL, graphQLContextMap);
  }

  private static GraphQLConfiguration createGraphQLConfig() {
    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();
    config.setPort(0);
    return config;
  }

  @AfterEach
  public void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
  }

  @Test
  public void requestWithDefaultHeaderAndDefaultConfigIsAccepted() throws IOException {
    Assertions.assertThat(doRequest("localhost:50012")).isEqualTo(200);
  }

  @Test
  public void requestWithEmptyHeaderAndDefaultConfigIsRejected() throws IOException {
    Assertions.assertThat(doRequest("")).isEqualTo(403);
  }

  @Test
  public void requestWithAnyHostnameAndWildcardConfigIsAccepted() throws IOException {
    graphQLConfig.setHostsAllowlist(Collections.singletonList("*"));
    Assertions.assertThat(doRequest("ally")).isEqualTo(200);
    Assertions.assertThat(doRequest("foe")).isEqualTo(200);
  }

  @Test
  public void requestWithWhitelistedHostIsAccepted() throws IOException {
    graphQLConfig.setHostsAllowlist(hostsWhitelist);
    Assertions.assertThat(doRequest("ally")).isEqualTo(200);
    Assertions.assertThat(doRequest("ally:12345")).isEqualTo(200);
    Assertions.assertThat(doRequest("friend")).isEqualTo(200);
  }

  @Test
  public void requestWithUnknownHostIsRejected() throws IOException {
    graphQLConfig.setHostsAllowlist(hostsWhitelist);
    Assertions.assertThat(doRequest("foe")).isEqualTo(403);
  }

  private int doRequest(final String hostname) throws IOException {
    final RequestBody body = RequestBody.create("{maxPriorityFeePerGas}", GRAPHQL);

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
    graphQLConfig.setHostsAllowlist(hostsWhitelist);
    Assertions.assertThat(doRequest("ally:friend")).isEqualTo(400);
    Assertions.assertThat(doRequest("ally:123456")).isEqualTo(400);
    Assertions.assertThat(doRequest("ally:friend:1234")).isEqualTo(400);
  }
}

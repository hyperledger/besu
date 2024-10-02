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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import graphql.GraphQL;
import io.vertx.core.Vertx;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GraphQLHttpServiceCorsTest {
  @TempDir private Path folder;

  private final Vertx vertx = Vertx.vertx();
  private final OkHttpClient client = new OkHttpClient();
  private GraphQLHttpService graphQLHttpService;

  @BeforeEach
  public void before() {
    final GraphQLConfiguration configuration = GraphQLConfiguration.createDefault();
    configuration.setPort(0);
  }

  @AfterEach
  public void after() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    graphQLHttpService.stop().join();
    vertx.close();
  }

  @Test
  public void requestWithNonAcceptedOriginShouldFail() throws Exception {
    graphQLHttpService = createGraphQLHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithAcceptedOriginShouldSucceed() throws Exception {
    graphQLHttpService = createGraphQLHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .header("Origin", "http://foo.io")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      System.out.println(response.body().string());
      Assertions.assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithOneOfMultipleAcceptedOriginsShouldSucceed() throws Exception {
    graphQLHttpService =
        createGraphQLHttpServiceWithAllowedDomains("http://foo.io", "http://bar.me");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithNoneOfMultipleAcceptedOriginsShouldFail() throws Exception {
    graphQLHttpService =
        createGraphQLHttpServiceWithAllowedDomains("http://foo.io", "http://bar.me");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .header("Origin", "http://hel.lo")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithNoOriginShouldSucceedWhenNoCorsConfigSet() throws Exception {
    graphQLHttpService = createGraphQLHttpServiceWithAllowedDomains();

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithNoOriginShouldSucceedWhenCorsIsSet() throws Exception {
    graphQLHttpService = createGraphQLHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithAnyOriginShouldNotSucceedWhenCorsIsEmpty() throws Exception {
    graphQLHttpService = createGraphQLHttpServiceWithAllowedDomains("");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.isSuccessful()).isFalse();
    }
  }

  @Test
  public void requestWithAnyOriginShouldSucceedWhenCorsIsStart() throws Exception {
    graphQLHttpService = createGraphQLHttpServiceWithAllowedDomains("*");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .header("Origin", "http://bar.me")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.isSuccessful()).isTrue();
    }
  }

  @Test
  public void requestWithAccessControlRequestMethodShouldReturnAllowedHeaders() throws Exception {
    graphQLHttpService = createGraphQLHttpServiceWithAllowedDomains("http://foo.io");

    final Request request =
        new Request.Builder()
            .url(graphQLHttpService.url() + "/graphql?query={maxPriorityFeePerGas}")
            .method("OPTIONS", null)
            .header("Access-Control-Request-Method", "OPTIONS")
            .header("Origin", "http://foo.io")
            .build();

    try (final Response response = client.newCall(request).execute()) {
      Assertions.assertThat(response.header("Access-Control-Allow-Headers"))
          .contains("*", "content-type");
    }
  }

  private GraphQLHttpService createGraphQLHttpServiceWithAllowedDomains(
      final String... corsAllowedDomains) throws Exception {
    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();
    config.setPort(0);
    if (corsAllowedDomains != null) {
      config.setCorsAllowedDomains(Lists.newArrayList(corsAllowedDomains));
    }

    final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
    when(blockchainQueries.gasPriorityFee()).thenReturn(Wei.ONE);
    final Synchronizer synchronizer = mock(Synchronizer.class);

    final PoWMiningCoordinator miningCoordinatorMock = mock(PoWMiningCoordinator.class);

    // mock graphql context
    final Map<GraphQLContextType, Object> graphQLContextMap =
        Map.of(
            GraphQLContextType.BLOCKCHAIN_QUERIES,
            blockchainQueries,
            GraphQLContextType.TRANSACTION_POOL,
            mock(TransactionPool.class),
            GraphQLContextType.MINING_COORDINATOR,
            miningCoordinatorMock,
            GraphQLContextType.SYNCHRONIZER,
            synchronizer);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);
    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers(supportedCapabilities);
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    final GraphQLHttpService graphQLHttpService =
        new GraphQLHttpService(
            vertx, folder, config, graphQL, graphQLContextMap, mock(EthScheduler.class));
    graphQLHttpService.start().join();

    return graphQLHttpService;
  }
}

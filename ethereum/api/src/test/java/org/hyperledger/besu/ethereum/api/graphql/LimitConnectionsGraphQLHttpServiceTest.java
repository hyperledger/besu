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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import graphql.GraphQL;
import io.vertx.core.Vertx;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

public class LimitConnectionsGraphQLHttpServiceTest {

  @TempDir private static Path folder;

  protected static Vertx vertx;

  private static GraphQLHttpService service;
  private static String baseUrl;
  private static final GraphQLConfiguration graphQLConfig = createGraphQLConfig();
  private static final int maxConnections = 2;

  @BeforeAll
  public static void initServerAndClient() throws Exception {
    vertx = Vertx.vertx();

    service = createGraphQLHttpService();
    service.start().join();

    baseUrl = service.url() + "/graphql?query={maxPriorityFeePerGas}";
  }

  private static GraphQLHttpService createGraphQLHttpService() throws Exception {
    final BlockchainQueries blockchainQueries = Mockito.mock(BlockchainQueries.class);
    final Synchronizer synchronizer = Mockito.mock(Synchronizer.class);

    final PoWMiningCoordinator miningCoordinatorMock = Mockito.mock(PoWMiningCoordinator.class);

    final Map<GraphQLContextType, Object> graphQLContextMap =
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
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    return new GraphQLHttpService(vertx, folder, graphQLConfig, graphQL, graphQLContextMap);
  }

  private static GraphQLConfiguration createGraphQLConfig() {
    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();
    config.setPort(0);
    config.setMaxActiveConnections(maxConnections);
    return config;
  }

  @AfterAll
  public static void shutdownServer() {
    service.stop().join();
    vertx.close();
  }

  @Test
  public void limitActiveConnections() throws Exception {

    OkHttpClient newClient = new OkHttpClient();
    int i;
    for (i = 0; i < maxConnections; i++) {
      // create a new client for each request because we want to test the limit
      try (final Response resp = newClient.newCall(buildRequest()).execute()) {
        assertThat(resp.code()).isEqualTo(200);
      }
      // new client for each request so that connection does NOT get reused
      newClient = new OkHttpClient();
    }
    // now we should get a rejected connection because we have hit the limit
    assertThat(i).isEqualTo(maxConnections);
    final OkHttpClient newClient2 = new OkHttpClient();

    // end of stream gets wrapped locally by ConnectException with message "Connection refused"
    // but in CI it comes through as is
    assertThatThrownBy(() -> newClient2.newCall(buildRequest()).execute())
        .isInstanceOf(IOException.class)
        .hasStackTraceContaining("unexpected end of stream");
  }

  private Request buildRequest() {
    return new Request.Builder().get().url(baseUrl).build();
  }
}

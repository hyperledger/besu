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

import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.nio.file.Path;

import graphql.GraphQL;
import io.vertx.core.Vertx;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Serves GraphQL over the real plugin service implementations of Besu, backed by the same in-memory
 * test chain, transaction pool mock and sync status as when GraphQL was part of Besu's core.
 */
public abstract class AbstractEthGraphQLHttpServiceTest {
  @TempDir private Path tempDir;

  private static TestChainServices testChainServices;

  private final Vertx vertx = Vertx.vertx();

  private GraphQLHttpService service;

  OkHttpClient client;

  String baseUrl;

  final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");

  @BeforeAll
  public static void setupConstants() {
    testChainServices = TestChainServices.forHiveTesting(DataStorageFormat.BONSAI);
  }

  @BeforeEach
  public void setupTest() throws Exception {
    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();

    config.setPort(0);
    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers();
    final GraphQL graphQL = GraphQLProvider.buildGraphQL(dataFetchers);

    service =
        new GraphQLHttpService(vertx, tempDir, config, graphQL, testChainServices.graphQLContext());
    service.start().join();

    client = new OkHttpClient();
    baseUrl = service.url() + "/graphql/";
  }

  @AfterEach
  public void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
  }
}

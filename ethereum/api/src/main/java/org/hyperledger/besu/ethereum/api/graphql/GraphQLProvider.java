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

import org.hyperledger.besu.ethereum.api.graphql.internal.Scalars;

import java.io.IOException;
import java.net.URL;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.analysis.FieldComplexityEnvironment;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

public class GraphQLProvider {

  public static final int MAX_COMPLEXITY = 200;

  private GraphQLProvider() {}

  public static GraphQL buildGraphQL(final GraphQLDataFetchers graphQLDataFetchers)
      throws IOException {
    final URL url = Resources.getResource("schema.graphqls");
    final String sdl = Resources.toString(url, Charsets.UTF_8);
    final GraphQLSchema graphQLSchema = buildSchema(sdl, graphQLDataFetchers);
    return GraphQL.newGraphQL(graphQLSchema)
        .instrumentation(
            new MaxQueryComplexityInstrumentation(
                MAX_COMPLEXITY, GraphQLProvider::calculateFieldCost))
        .build();
  }

  private static GraphQLSchema buildSchema(
      final String sdl, final GraphQLDataFetchers graphQLDataFetchers) {
    final TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
    final RuntimeWiring runtimeWiring = buildWiring(graphQLDataFetchers);
    final SchemaGenerator schemaGenerator = new SchemaGenerator();
    return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
  }

  private static RuntimeWiring buildWiring(final GraphQLDataFetchers graphQLDataFetchers) {
    return RuntimeWiring.newRuntimeWiring()
        .scalar(Scalars.addressScalar())
        .scalar(Scalars.bigIntScalar())
        .scalar(Scalars.bytesScalar())
        .scalar(Scalars.bytes32Scalar())
        .scalar(Scalars.longScalar())
        .type(
            TypeRuntimeWiring.newTypeWiring("Query")
                .dataFetcher("account", graphQLDataFetchers.getAccountDataFetcher())
                .dataFetcher("block", graphQLDataFetchers.getBlockDataFetcher())
                .dataFetcher("blocks", graphQLDataFetchers.getRangeBlockDataFetcher())
                .dataFetcher("logs", graphQLDataFetchers.getLogsDataFetcher())
                .dataFetcher("transaction", graphQLDataFetchers.getTransactionDataFetcher())
                .dataFetcher("gasPrice", graphQLDataFetchers.getGasPriceDataFetcher())
                .dataFetcher("syncing", graphQLDataFetchers.getSyncingDataFetcher())
                .dataFetcher("pending", graphQLDataFetchers.getPendingStateDataFetcher())
                .dataFetcher(
                    "protocolVersion", graphQLDataFetchers.getProtocolVersionDataFetcher()))
        .type(
            TypeRuntimeWiring.newTypeWiring("Mutation")
                .dataFetcher(
                    "sendRawTransaction", graphQLDataFetchers.getSendRawTransactionDataFetcher()))
        .build();
  }

  private static int calculateFieldCost(
      final FieldComplexityEnvironment environment, final int childComplexity) {
    final String childTypeName = environment.getParentType().getName();
    final String fieldName = environment.getField().getName();

    if (childTypeName.equals("Transaction") && fieldName.equals("block")) {
      return childComplexity + 100;
    } else if (childTypeName.equals("__Type") && fieldName.equals("fields")) {
      return childComplexity + 100;
    } else {
      return childComplexity + 1;
    }
  }
}

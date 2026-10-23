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

import static org.mockito.Mockito.lenient;

import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.BlockchainQueryService;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.WorldStateService;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.Mockito;

public abstract class AbstractDataFetcherTest {

  GraphQLDataFetchers fetchers;

  @Mock protected DataFetchingEnvironment environment;

  @Mock protected BlockchainService blockchainService;

  @Mock protected BlockchainQueryService blockchainQueryService;

  @Mock protected WorldStateService worldStateService;

  @Mock protected GraphQLContext graphQLContext;

  @Mock protected BlockHeader header;

  @Mock protected BlockBody body;

  @Mock protected BlockContext blockContext;

  @Mock protected Transaction transaction;

  @Mock protected TransactionReceipt transactionReceipt;

  @BeforeEach
  public void before() {
    fetchers = new GraphQLDataFetchers();
    Mockito.when(environment.getGraphQlContext()).thenReturn(graphQLContext);
    // The services every data fetcher may read from the context. Lenient, as each test exercises
    // only some of them.
    lenient()
        .when(graphQLContext.get(GraphQLContextType.BLOCKCHAIN_SERVICE))
        .thenReturn(blockchainService);
    lenient()
        .when(graphQLContext.get(GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE))
        .thenReturn(blockchainQueryService);
    lenient()
        .when(graphQLContext.get(GraphQLContextType.WORLD_STATE_SERVICE))
        .thenReturn(worldStateService);
    lenient().when(graphQLContext.get(GraphQLContextType.BONSAI_WORLD_STATE)).thenReturn(true);
    lenient().when(graphQLContext.get(GraphQLContextType.IS_ALIVE_HANDLER)).thenReturn(null);
  }
}

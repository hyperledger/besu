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

import org.hyperledger.besu.ethereum.api.graphql.internal.BlockchainQuery;
import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.NormalBlockAdapter;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;

import java.util.Optional;
import java.util.Set;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;

public abstract class AbstractDataFetcherTest {

  DataFetcher<Optional<NormalBlockAdapter>> fetcher;
  private GraphQLDataFetchers fetchers;

  @Mock protected Set<Capability> supportedCapabilities;

  @Mock protected DataFetchingEnvironment environment;

  @Mock protected GraphQLDataFetcherContext context;

  @Mock protected BlockchainQuery query;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void before() {
    fetchers = new GraphQLDataFetchers(supportedCapabilities);
    fetcher = fetchers.getBlockDataFetcher();
    Mockito.when(environment.getContext()).thenReturn(context);
    Mockito.when(context.getBlockchainQuery()).thenReturn(query);
  }
}

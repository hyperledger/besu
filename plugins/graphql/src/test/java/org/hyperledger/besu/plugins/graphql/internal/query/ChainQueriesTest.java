/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.plugins.graphql.internal.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.WorldStateService;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ChainQueriesTest {

  private final WorldStateService worldStateService = mock(WorldStateService.class);
  private final ChainQueries chainQueries =
      new ChainQueries(mock(BlockchainService.class), worldStateService, true);

  @Test
  void worldStateMappingIsDelegated() {
    when(worldStateService.mapWorldView(eq(Hash.ZERO), any())).thenReturn(Optional.of(7));

    assertThat(chainQueries.getAndMapWorldState(Hash.ZERO, ws -> Optional.of(1))).contains(7);
  }

  @Test
  void failingWorldStateMappingIsEmpty() {
    // the plugin service propagates a failing mapper; the GraphQL queries keep treating it as
    // "no value", as they did when GraphQL was served by Besu's core
    when(worldStateService.mapWorldView(eq(Hash.ZERO), any()))
        .thenThrow(new IllegalStateException("missing trie node"));

    assertThat(chainQueries.getAndMapWorldState(Hash.ZERO, ws -> Optional.of(1))).isEmpty();
  }
}

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
package org.hyperledger.besu.ethereum.api.graphql;

import org.hyperledger.besu.ethereum.api.BlockWithMetadata;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BlockDataFetcherTest extends AbstractDataFetcherTest {

  @Test
  public void bothNumberAndHashThrows() throws Exception {
    final Hash fakedHash = Hash.hash(BytesValue.of(1));
    Mockito.when(environment.getArgument(ArgumentMatchers.eq("number"))).thenReturn(1L);
    Mockito.when(environment.getArgument(ArgumentMatchers.eq("hash"))).thenReturn(fakedHash);

    thrown.expect(GraphQLException.class);
    fetcher.get(environment);
  }

  @Test
  public void onlyNumber() throws Exception {

    Mockito.when(environment.getArgument(ArgumentMatchers.eq("number"))).thenReturn(1L);
    Mockito.when(environment.getArgument(ArgumentMatchers.eq("hash"))).thenReturn(null);

    Mockito.when(environment.getContext()).thenReturn(context);
    Mockito.when(context.getBlockchainQuery()).thenReturn(query);
    Mockito.when(query.blockByNumber(ArgumentMatchers.anyLong()))
        .thenReturn(Optional.of(new BlockWithMetadata<>(null, null, null, null, 0)));

    fetcher.get(environment);
  }
}

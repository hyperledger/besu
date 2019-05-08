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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BlockDataFetcherTest extends AbstractDataFetcherTest {

  @Test
  public void bothNumberAndHashThrows() throws Exception {
    final Hash fakedHash = Hash.hash(BytesValue.of(1));
    when(environment.getArgument(eq("number"))).thenReturn(1L);
    when(environment.getArgument(eq("hash"))).thenReturn(fakedHash);

    thrown.expect(GraphQLRpcException.class);
    fetcher.get(environment);
  }

  @Test
  public void onlyNumber() throws Exception {

    when(environment.getArgument(eq("number"))).thenReturn(1L);
    when(environment.getArgument(eq("hash"))).thenReturn(null);

    when(environment.getContext()).thenReturn(context);
    when(context.getBlockchainQuery()).thenReturn(query);

    fetcher.get(environment);
  }
}

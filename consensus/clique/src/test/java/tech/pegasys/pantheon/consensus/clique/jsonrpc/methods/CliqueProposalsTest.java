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
package tech.pegasys.pantheon.consensus.clique.jsonrpc.methods;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteType;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

public class CliqueProposalsTest {

  private final VoteProposer voteProposer = mock(VoteProposer.class);
  private final String METHOD_NAME = "clique_proposals";
  private final String JSON_RPC_VERSION = "2.0";
  private CliqueProposals method;

  @Before
  public void setup() {
    method = new CliqueProposals(voteProposer);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(METHOD_NAME);
  }

  @Test
  public void testConversionFromVoteTypeToBoolean() {
    final JsonRpcRequest request =
        new JsonRpcRequest(JSON_RPC_VERSION, METHOD_NAME, new Object[] {});

    when(voteProposer.getProposals())
        .thenReturn(
            ImmutableMap.of(
                Address.fromHexString("1"),
                VoteType.ADD,
                Address.fromHexString("2"),
                VoteType.DROP));

    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getId(),
            ImmutableMap.of(
                "0x0000000000000000000000000000000000000001",
                true,
                "0x0000000000000000000000000000000000000002",
                false));

    final JsonRpcResponse response = method.response(request);

    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }
}

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
package org.hyperledger.besu.consensus.clique.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;

import java.util.Optional;

import org.junit.Test;

public class DiscardTest {
  private final String JSON_RPC_VERSION = "2.0";
  private final String METHOD = "clique_discard";

  @Test
  public void discardEmpty() {
    final VoteProposer proposer = new VoteProposer();
    final Discard discard = new Discard(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    final JsonRpcResponse response = discard.response(requestWithParams(a0));

    assertThat(proposer.get(a0)).isEqualTo(Optional.empty());
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void discardAuth() {
    final VoteProposer proposer = new VoteProposer();
    final Discard discard = new Discard(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    proposer.auth(a0);

    final JsonRpcResponse response = discard.response(requestWithParams(a0));

    assertThat(proposer.get(a0)).isEqualTo(Optional.empty());
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void discardDrop() {
    final VoteProposer proposer = new VoteProposer();
    final Discard discard = new Discard(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    proposer.drop(a0);

    final JsonRpcResponse response = discard.response(requestWithParams(a0));

    assertThat(proposer.get(a0)).isEqualTo(Optional.empty());
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void discardIsolation() {
    final VoteProposer proposer = new VoteProposer();
    final Discard discard = new Discard(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");
    final Address a1 = Address.fromHexString("1");

    proposer.auth(a0);
    proposer.auth(a1);

    final JsonRpcResponse response = discard.response(requestWithParams(a0));

    assertThat(proposer.get(a0)).isEqualTo(Optional.empty());
    assertThat(proposer.get(a1)).isEqualTo(Optional.of(VoteType.ADD));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void discardWithoutAddress() {
    final VoteProposer proposer = new VoteProposer();
    final Discard discard = new Discard(proposer, new JsonRpcParameter());

    assertThatThrownBy(() -> discard.response(requestWithParams()))
        .hasMessage("Missing required json rpc parameter at index 0")
        .isInstanceOf(InvalidJsonRpcParameters.class);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, METHOD, params);
  }
}

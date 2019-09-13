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

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteType;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcResponseType;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.core.Address;

import java.util.Optional;

import org.junit.Test;

public class ProposeTest {
  private final String JSON_RPC_VERSION = "2.0";
  private final String METHOD = "clique_propose";

  @Test
  public void testAuth() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a1 = Address.fromHexString("1");

    final JsonRpcResponse response = propose.response(requestWithParams(a1, true));

    assertThat(proposer.get(a1)).isEqualTo(Optional.of(VoteType.ADD));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testAuthWithAddressZeroResultsInError() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    final JsonRpcResponse response = propose.response(requestWithParams(a0, true));

    assertThat(proposer.get(a0)).isEqualTo(Optional.empty());
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.INVALID_REQUEST);
  }

  @Test
  public void testDrop() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a1 = Address.fromHexString("1");

    final JsonRpcResponse response = propose.response(requestWithParams(a1, false));

    assertThat(proposer.get(a1)).isEqualTo(Optional.of(VoteType.DROP));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testDropWithAddressZeroResultsInError() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    final JsonRpcResponse response = propose.response(requestWithParams(a0, false));

    assertThat(proposer.get(a0)).isEqualTo(Optional.empty());
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.ERROR);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.INVALID_REQUEST);
  }

  @Test
  public void testRepeatAuth() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a1 = Address.fromHexString("1");

    proposer.auth(a1);
    final JsonRpcResponse response = propose.response(requestWithParams(a1, true));

    assertThat(proposer.get(a1)).isEqualTo(Optional.of(VoteType.ADD));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testRepeatDrop() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a1 = Address.fromHexString("1");

    proposer.drop(a1);
    final JsonRpcResponse response = propose.response(requestWithParams(a1, false));

    assertThat(proposer.get(a1)).isEqualTo(Optional.of(VoteType.DROP));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testChangeToAuth() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a1 = Address.fromHexString("1");

    proposer.drop(a1);
    final JsonRpcResponse response = propose.response(requestWithParams(a1, true));

    assertThat(proposer.get(a1)).isEqualTo(Optional.of(VoteType.ADD));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testChangeToDrop() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("1");

    proposer.auth(a0);
    final JsonRpcResponse response = propose.response(requestWithParams(a0, false));

    assertThat(proposer.get(a0)).isEqualTo(Optional.of(VoteType.DROP));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, METHOD, params);
  }
}

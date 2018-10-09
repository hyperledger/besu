package net.consensys.pantheon.consensus.clique.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.consensus.common.VoteProposer.Vote;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponseType;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

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
    assertThat(proposer.get(a1)).isEqualTo(Optional.of(Vote.AUTH));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void discardWithoutAddress() {
    final VoteProposer proposer = new VoteProposer();
    final Discard discard = new Discard(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    assertThatThrownBy(() -> discard.response(requestWithParams()))
        .hasMessage("Missing required json rpc parameter at index 0")
        .isInstanceOf(InvalidJsonRpcParameters.class);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, METHOD, params);
  }
}

package tech.pegasys.pantheon.consensus.clique.jsonrpc.methods;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteType;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponseType;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Optional;

import org.junit.Test;

public class ProposeTest {
  private final String JSON_RPC_VERSION = "2.0";
  private final String METHOD = "clique_propose";

  @Test
  public void testAuth() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    final JsonRpcResponse response = propose.response(requestWithParams(a0, true));

    assertThat(proposer.get(a0)).isEqualTo(Optional.of(VoteType.ADD));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testDrop() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    final JsonRpcResponse response = propose.response(requestWithParams(a0, false));

    assertThat(proposer.get(a0)).isEqualTo(Optional.of(VoteType.DROP));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testRepeatAuth() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    proposer.auth(a0);
    final JsonRpcResponse response = propose.response(requestWithParams(a0, true));

    assertThat(proposer.get(a0)).isEqualTo(Optional.of(VoteType.ADD));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testRepeatDrop() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    proposer.drop(a0);
    final JsonRpcResponse response = propose.response(requestWithParams(a0, false));

    assertThat(proposer.get(a0)).isEqualTo(Optional.of(VoteType.DROP));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testChangeToAuth() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

    proposer.drop(a0);
    final JsonRpcResponse response = propose.response(requestWithParams(a0, true));

    assertThat(proposer.get(a0)).isEqualTo(Optional.of(VoteType.ADD));
    assertThat(response.getType()).isEqualTo(JsonRpcResponseType.SUCCESS);
    final JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) response;
    assertThat(successResponse.getResult()).isEqualTo(true);
  }

  @Test
  public void testChangeToDrop() {
    final VoteProposer proposer = new VoteProposer();
    final Propose propose = new Propose(proposer, new JsonRpcParameter());
    final Address a0 = Address.fromHexString("0");

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

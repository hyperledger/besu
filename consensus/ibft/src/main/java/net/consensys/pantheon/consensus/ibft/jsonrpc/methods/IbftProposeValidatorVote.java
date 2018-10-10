package net.consensys.pantheon.consensus.ibft.jsonrpc.methods;

import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class IbftProposeValidatorVote implements JsonRpcMethod {
  private final VoteProposer voteProposer;
  private final JsonRpcParameter parameters;

  public IbftProposeValidatorVote(
      final VoteProposer voteProposer, final JsonRpcParameter parameters) {
    this.voteProposer = voteProposer;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "ibft_proposeValidatorVote";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {

    Address validatorAddress = parameters.required(req.getParams(), 0, Address.class);
    Boolean add = parameters.required(req.getParams(), 1, Boolean.class);

    if (add) {
      voteProposer.auth(validatorAddress);
    } else {
      voteProposer.drop(validatorAddress);
    }

    return new JsonRpcSuccessResponse(req.getId(), true);
  }
}

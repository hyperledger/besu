package tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class IbftDiscardValidatorVote implements JsonRpcMethod {
  private final VoteProposer voteProposer;
  private final JsonRpcParameter parameters;

  public IbftDiscardValidatorVote(
      final VoteProposer voteProposer, final JsonRpcParameter parameters) {
    this.voteProposer = voteProposer;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "ibft_discardValidatorVote";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest req) {

    final Address validatorAddress = parameters.required(req.getParams(), 0, Address.class);

    voteProposer.discard(validatorAddress);

    return new JsonRpcSuccessResponse(req.getId(), true);
  }
}

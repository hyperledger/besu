package net.consensys.pantheon.consensus.clique.jsonrpc.methods;

import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class Propose implements JsonRpcMethod {
  private final VoteProposer proposer;
  private final JsonRpcParameter parameters;

  public Propose(final VoteProposer proposer, final JsonRpcParameter parameters) {
    this.proposer = proposer;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "clique_propose";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Address address = parameters.required(request.getParams(), 0, Address.class);
    final Boolean auth = parameters.required(request.getParams(), 1, Boolean.class);
    if (auth) {
      proposer.auth(address);
    } else {
      proposer.drop(address);
    }
    // Return true regardless, the vote is always recorded
    return new JsonRpcSuccessResponse(request.getId(), true);
  }
}

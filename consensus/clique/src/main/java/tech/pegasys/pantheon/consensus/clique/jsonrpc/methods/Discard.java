package net.consensys.pantheon.consensus.clique.jsonrpc.methods;

import net.consensys.pantheon.consensus.common.VoteProposer;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class Discard implements JsonRpcMethod {
  private static final String CLIQUE_DISCARD = "clique_discard";
  private final VoteProposer proposer;
  private final JsonRpcParameter parameters;

  public Discard(final VoteProposer proposer, final JsonRpcParameter parameters) {
    this.proposer = proposer;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return CLIQUE_DISCARD;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Address address = parameters.required(request.getParams(), 0, Address.class);
    proposer.discard(address);
    return new JsonRpcSuccessResponse(request.getId(), true);
  }
}

package tech.pegasys.pantheon.consensus.ibft.jsonrpc;

import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftDiscardValidatorVote;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftProposeValidatorVote;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;

import java.util.HashMap;
import java.util.Map;

public class IbftJsonRpcMethodsFactory {

  private final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();

  public Map<String, JsonRpcMethod> methods(final ProtocolContext<IbftContext> context) {

    final Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();
    // @formatter:off
    addMethods(
        rpcMethods,
        new IbftProposeValidatorVote(
            context.getConsensusState().getVoteProposer(), jsonRpcParameter),
        new IbftDiscardValidatorVote(
            context.getConsensusState().getVoteProposer(), jsonRpcParameter));

    return rpcMethods;
  }

  private void addMethods(
      final Map<String, JsonRpcMethod> methods, final JsonRpcMethod... rpcMethods) {
    for (JsonRpcMethod rpcMethod : rpcMethods) {
      methods.put(rpcMethod.getName(), rpcMethod);
    }
  }
}

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
package tech.pegasys.pantheon.consensus.ibft.jsonrpc;

import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftDiscardValidatorVote;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftProposeValidatorVote;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IbftJsonRpcMethodsFactory {

  private final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();

  public Map<String, JsonRpcMethod> methods(
      final ProtocolContext<IbftContext> context, final Collection<RpcApi> jsonRpcApis) {

    final Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();

    if (jsonRpcApis.contains(IbftRpcApis.IBFT)) {
      // @formatter:off
      addMethods(
          rpcMethods,
          new IbftProposeValidatorVote(
              context.getConsensusState().getVoteProposer(), jsonRpcParameter),
          new IbftDiscardValidatorVote(
              context.getConsensusState().getVoteProposer(), jsonRpcParameter));
    }

    return rpcMethods;
  }

  private void addMethods(
      final Map<String, JsonRpcMethod> methods, final JsonRpcMethod... rpcMethods) {
    for (final JsonRpcMethod rpcMethod : rpcMethods) {
      methods.put(rpcMethod.getName(), rpcMethod);
    }
  }
}

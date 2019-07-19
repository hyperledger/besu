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

import tech.pegasys.pantheon.consensus.common.BlockInterface;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftDiscardValidatorVote;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftGetPendingVotes;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftGetSignerMetrics;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftGetValidatorsByBlockHash;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftGetValidatorsByBlockNumber;
import tech.pegasys.pantheon.consensus.ibft.jsonrpc.methods.IbftProposeValidatorVote;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethodFactory;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IbftJsonRpcMethodsFactory implements JsonRpcMethodFactory {

  private final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();
  private final ProtocolContext<IbftContext> context;

  public IbftJsonRpcMethodsFactory(final ProtocolContext<IbftContext> context) {
    this.context = context;
  }

  @Override
  public Map<String, JsonRpcMethod> createJsonRpcMethods(final Collection<RpcApi> jsonRpcApis) {
    final Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();

    if (jsonRpcApis.contains(IbftRpcApis.IBFT)) {
      final BlockchainQueries blockchainQueries =
          new BlockchainQueries(context.getBlockchain(), context.getWorldStateArchive());
      final VoteProposer voteProposer = context.getConsensusState().getVoteProposer();
      final BlockInterface blockInterface = new IbftBlockInterface();

      addMethods(
          rpcMethods,
          new IbftProposeValidatorVote(voteProposer, jsonRpcParameter),
          new IbftGetValidatorsByBlockNumber(blockchainQueries, blockInterface, jsonRpcParameter),
          new IbftDiscardValidatorVote(voteProposer, jsonRpcParameter),
          new IbftGetValidatorsByBlockHash(
              context.getBlockchain(), blockInterface, jsonRpcParameter),
          new IbftGetSignerMetrics(blockInterface, blockchainQueries, jsonRpcParameter),
          new IbftGetPendingVotes(voteProposer));
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

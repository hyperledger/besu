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
package org.hyperledger.besu.consensus.ibft.jsonrpc;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.ibft.IbftBlockInterface;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.jsonrpc.methods.IbftDiscardValidatorVote;
import org.hyperledger.besu.consensus.ibft.jsonrpc.methods.IbftGetPendingVotes;
import org.hyperledger.besu.consensus.ibft.jsonrpc.methods.IbftGetSignerMetrics;
import org.hyperledger.besu.consensus.ibft.jsonrpc.methods.IbftGetValidatorsByBlockHash;
import org.hyperledger.besu.consensus.ibft.jsonrpc.methods.IbftGetValidatorsByBlockNumber;
import org.hyperledger.besu.consensus.ibft.jsonrpc.methods.IbftProposeValidatorVote;
import org.hyperledger.besu.crosschain.core.CrosschainController;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossBlockchainPublicKey;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossCheckUnlock;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.EthIsLockable;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.EthIsLocked;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.EthProcessSubordinateView;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.EthSendRawCrosschainTransaction;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethodFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;

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

    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(context.getBlockchain(), context.getWorldStateArchive());
    final VoteProposer voteProposer = context.getConsensusState().getVoteProposer();
    final BlockInterface blockInterface = new IbftBlockInterface();

    if (jsonRpcApis.contains(IbftRpcApis.IBFT)) {
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

    if (jsonRpcApis.contains(RpcApis.CROSSCHAIN)) {

      final CrosschainController crosschainController =
          context.getConsensusState().getCrosschainController();

      addMethods(
          rpcMethods,
          new EthSendRawCrosschainTransaction(crosschainController, jsonRpcParameter),
          new EthProcessSubordinateView(blockchainQueries, crosschainController, jsonRpcParameter),
          new EthIsLockable(blockchainQueries, jsonRpcParameter),
          new EthIsLocked(blockchainQueries, jsonRpcParameter),
          new CrossCheckUnlock(crosschainController, jsonRpcParameter),
          new CrossBlockchainPublicKey(crosschainController));
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

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
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossActivateKey;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossAddCoordinationContract;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossCheckUnlock;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossGetActiveKeyVersion;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossGetBlockchainPublicKey;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossGetKeyActiveNodes;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossGetKeyGenFailureReason;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossGetKeyGenNodesDroppedOutOfKeyGeneration;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossGetKeyStatus;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossIsLockable;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossIsLocked;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossListCoordinationContracts;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossProcessSubordinateView;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossRemoveCoordinationContract;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossSendRawCrosschainTransaction;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossSetKeyGenerationContractAddress;
import org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods.CrossStartThresholdKeyGeneration;
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
          new CrossAddCoordinationContract(crosschainController, jsonRpcParameter),
          new CrossActivateKey(crosschainController, jsonRpcParameter),
          new CrossCheckUnlock(crosschainController, jsonRpcParameter),
          new CrossGetActiveKeyVersion(crosschainController),
          new CrossGetBlockchainPublicKey(crosschainController, jsonRpcParameter),
          new CrossGetKeyActiveNodes(crosschainController, jsonRpcParameter),
          new CrossGetKeyGenFailureReason(crosschainController, jsonRpcParameter),
          new CrossGetKeyGenNodesDroppedOutOfKeyGeneration(crosschainController, jsonRpcParameter),
          new CrossGetKeyStatus(crosschainController, jsonRpcParameter),
          new CrossIsLockable(blockchainQueries, jsonRpcParameter),
          new CrossIsLocked(blockchainQueries, jsonRpcParameter),
          new CrossListCoordinationContracts(crosschainController),
          new CrossProcessSubordinateView(
              blockchainQueries, crosschainController, jsonRpcParameter),
          new CrossRemoveCoordinationContract(crosschainController, jsonRpcParameter),
          new CrossSendRawCrosschainTransaction(crosschainController, jsonRpcParameter),
          new CrossSetKeyGenerationContractAddress(crosschainController, jsonRpcParameter),
          new CrossStartThresholdKeyGeneration(crosschainController, jsonRpcParameter));
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

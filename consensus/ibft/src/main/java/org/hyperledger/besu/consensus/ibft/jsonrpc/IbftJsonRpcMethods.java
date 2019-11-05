/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
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
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.ApiGroupJsonRpcMethods;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.Collection;
import java.util.Map;

public class IbftJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();
  private final ProtocolContext<IbftContext> context;

  public IbftJsonRpcMethods(final ProtocolContext<IbftContext> context) {
    this.context = context;
  }

  @Override
  protected boolean isWithin(Collection<RpcApi> apis) {
    return apis.contains(IbftRpcApis.IBFT);
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(context.getBlockchain(), context.getWorldStateArchive());
    final VoteProposer voteProposer = context.getConsensusState().getVoteProposer();
    final BlockInterface blockInterface = new IbftBlockInterface();

    return mapOf(
        new IbftProposeValidatorVote(voteProposer, jsonRpcParameter),
        new IbftGetValidatorsByBlockNumber(blockchainQueries, blockInterface, jsonRpcParameter),
        new IbftDiscardValidatorVote(voteProposer, jsonRpcParameter),
        new IbftGetValidatorsByBlockHash(context.getBlockchain(), blockInterface, jsonRpcParameter),
        new IbftGetSignerMetrics(blockInterface, blockchainQueries, jsonRpcParameter),
        new IbftGetPendingVotes(voteProposer));
  }
}

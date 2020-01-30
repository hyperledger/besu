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
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.common.VoteTallyUpdater;
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
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.ApiGroupJsonRpcMethods;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;

import java.util.Map;

public class IbftJsonRpcMethods extends ApiGroupJsonRpcMethods {

  private final ProtocolContext<IbftContext> context;

  public IbftJsonRpcMethods(final ProtocolContext<IbftContext> context) {
    this.context = context;
  }

  @Override
  protected RpcApi getApiGroup() {
    return IbftRpcApis.IBFT;
  }

  @Override
  protected Map<String, JsonRpcMethod> create() {
    final MutableBlockchain mutableBlockchain = context.getBlockchain();
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(context.getBlockchain(), context.getWorldStateArchive());
    final VoteProposer voteProposer = context.getConsensusState().getVoteProposer();
    final BlockInterface blockInterface = new IbftBlockInterface();

    final VoteTallyCache voteTallyCache = createVoteTallyCache(context, mutableBlockchain);

    return mapOf(
        new IbftProposeValidatorVote(voteProposer),
        new IbftGetValidatorsByBlockNumber(blockchainQueries, blockInterface),
        new IbftDiscardValidatorVote(voteProposer),
        new IbftGetValidatorsByBlockHash(context.getBlockchain(), blockInterface),
        new IbftGetSignerMetrics(voteTallyCache, blockInterface, blockchainQueries),
        new IbftGetPendingVotes(voteProposer));
  }

  private VoteTallyCache createVoteTallyCache(
      final ProtocolContext<IbftContext> context, final MutableBlockchain blockchain) {
    final EpochManager epochManager = context.getConsensusState().getEpochManager();
    final IbftBlockInterface ibftBlockInterface = new IbftBlockInterface();
    final VoteTallyUpdater voteTallyUpdater =
        new VoteTallyUpdater(epochManager, ibftBlockInterface);
    return new VoteTallyCache(blockchain, voteTallyUpdater, epochManager, ibftBlockInterface);
  }
}

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
package org.hyperledger.besu.consensus.clique.jsonrpc;

import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueGetSignerMetrics;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueGetSigners;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueGetSignersAtHash;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.CliqueProposals;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.Discard;
import org.hyperledger.besu.consensus.clique.jsonrpc.methods.Propose;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.VoteProposer;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.consensus.common.VoteTallyUpdater;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethodFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CliqueJsonRpcMethodsFactory implements JsonRpcMethodFactory {

  private final ProtocolContext<CliqueContext> context;

  public CliqueJsonRpcMethodsFactory(final ProtocolContext<CliqueContext> context) {
    this.context = context;
  }

  @Override
  public Map<String, JsonRpcMethod> createJsonRpcMethods(final Collection<RpcApi> jsonRpcApis) {
    final Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();
    if (!jsonRpcApis.contains(CliqueRpcApis.CLIQUE)) {
      return rpcMethods;
    }
    final MutableBlockchain blockchain = context.getBlockchain();
    final WorldStateArchive worldStateArchive = context.getWorldStateArchive();
    final BlockchainQueries blockchainQueries =
        new BlockchainQueries(blockchain, worldStateArchive);
    final VoteProposer voteProposer = context.getConsensusState().getVoteProposer();
    final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();
    // Must create our own voteTallyCache as using this would pollute the main voteTallyCache
    final VoteTallyCache voteTallyCache = createVoteTallyCache(context, blockchain);

    final CliqueGetSigners cliqueGetSigners =
        new CliqueGetSigners(blockchainQueries, voteTallyCache, jsonRpcParameter);
    final CliqueGetSignersAtHash cliqueGetSignersAtHash =
        new CliqueGetSignersAtHash(blockchainQueries, voteTallyCache, jsonRpcParameter);
    final Propose proposeRpc = new Propose(voteProposer, jsonRpcParameter);
    final Discard discardRpc = new Discard(voteProposer, jsonRpcParameter);
    final CliqueProposals cliqueProposals = new CliqueProposals(voteProposer);
    final CliqueGetSignerMetrics cliqueGetSignerMetrics =
        new CliqueGetSignerMetrics(new CliqueBlockInterface(), blockchainQueries, jsonRpcParameter);

    rpcMethods.put(cliqueGetSigners.getName(), cliqueGetSigners);
    rpcMethods.put(cliqueGetSignersAtHash.getName(), cliqueGetSignersAtHash);
    rpcMethods.put(proposeRpc.getName(), proposeRpc);
    rpcMethods.put(discardRpc.getName(), discardRpc);
    rpcMethods.put(cliqueProposals.getName(), cliqueProposals);
    rpcMethods.put(cliqueGetSignerMetrics.getName(), cliqueGetSignerMetrics);
    return rpcMethods;
  }

  private VoteTallyCache createVoteTallyCache(
      final ProtocolContext<CliqueContext> context, final MutableBlockchain blockchain) {
    final EpochManager epochManager = context.getConsensusState().getEpochManager();
    final VoteTallyUpdater voteTallyUpdater =
        new VoteTallyUpdater(epochManager, new CliqueBlockInterface());
    return new VoteTallyCache(
        blockchain, voteTallyUpdater, epochManager, new CliqueBlockInterface());
  }
}

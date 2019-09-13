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
package tech.pegasys.pantheon.consensus.clique.jsonrpc;

import tech.pegasys.pantheon.consensus.clique.CliqueBlockInterface;
import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueGetSignerMetrics;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueGetSigners;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueGetSignersAtHash;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueProposals;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.Discard;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.Propose;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.methods.JsonRpcMethodFactory;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;

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

package tech.pegasys.pantheon.consensus.clique.jsonrpc;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueVoteTallyUpdater;
import tech.pegasys.pantheon.consensus.clique.VoteTallyCache;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueGetSigners;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueGetSignersAtHash;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.Discard;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.Propose;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;

import java.util.HashMap;
import java.util.Map;

public class CliqueJsonRpcMethodsFactory {

  public Map<String, JsonRpcMethod> methods(final ProtocolContext<CliqueContext> context) {
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

    final Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();
    rpcMethods.put(cliqueGetSigners.getName(), cliqueGetSigners);
    rpcMethods.put(cliqueGetSignersAtHash.getName(), cliqueGetSignersAtHash);
    rpcMethods.put(proposeRpc.getName(), proposeRpc);
    rpcMethods.put(discardRpc.getName(), discardRpc);
    return rpcMethods;
  }

  private VoteTallyCache createVoteTallyCache(
      final ProtocolContext<CliqueContext> context, final MutableBlockchain blockchain) {
    final EpochManager epochManager = context.getConsensusState().getEpochManager();
    final CliqueVoteTallyUpdater cliqueVoteTallyUpdater = new CliqueVoteTallyUpdater(epochManager);
    return new VoteTallyCache(blockchain, cliqueVoteTallyUpdater, epochManager);
  }
}

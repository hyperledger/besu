package tech.pegasys.pantheon.consensus.clique.jsonrpc;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueGetSigners;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.CliqueGetSignersAtHash;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.Discard;
import tech.pegasys.pantheon.consensus.clique.jsonrpc.methods.Propose;
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
    final JsonRpcParameter jsonRpcParameter = new JsonRpcParameter();

    final CliqueGetSigners cliqueGetSigners =
        new CliqueGetSigners(blockchainQueries, jsonRpcParameter);
    final CliqueGetSignersAtHash cliqueGetSignersAtHash =
        new CliqueGetSignersAtHash(blockchainQueries, jsonRpcParameter);
    final Propose proposeRpc =
        new Propose(context.getConsensusState().getVoteProposer(), jsonRpcParameter);
    final Discard discardRpc =
        new Discard(context.getConsensusState().getVoteProposer(), jsonRpcParameter);

    final Map<String, JsonRpcMethod> rpcMethods = new HashMap<>();
    rpcMethods.put(cliqueGetSigners.getName(), cliqueGetSigners);
    rpcMethods.put(cliqueGetSignersAtHash.getName(), cliqueGetSignersAtHash);
    rpcMethods.put(proposeRpc.getName(), proposeRpc);
    rpcMethods.put(discardRpc.getName(), discardRpc);
    return rpcMethods;
  }
}

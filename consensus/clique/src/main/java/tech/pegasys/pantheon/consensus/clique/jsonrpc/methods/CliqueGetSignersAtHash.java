package tech.pegasys.pantheon.consensus.clique.jsonrpc.methods;

import static tech.pegasys.pantheon.consensus.clique.CliqueHelpers.getValidatorsOfBlock;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Optional;

public class CliqueGetSignersAtHash implements JsonRpcMethod {
  public static final String CLIQUE_GET_SIGNERS_AT_HASH = "clique_getSignersAtHash";
  private final BlockchainQueries blockchainQueries;
  private final JsonRpcParameter parameters;

  public CliqueGetSignersAtHash(
      final BlockchainQueries blockchainQueries, final JsonRpcParameter parameter) {
    this.blockchainQueries = blockchainQueries;
    this.parameters = parameter;
  }

  @Override
  public String getName() {
    return CLIQUE_GET_SIGNERS_AT_HASH;
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Optional<BlockHeader> blockHeader = blockHeader(request);
    return blockHeader
        .<JsonRpcResponse>map(
            bh -> new JsonRpcSuccessResponse(request.getId(), getValidatorsOfBlock(bh)))
        .orElse(new JsonRpcErrorResponse(request.getId(), JsonRpcError.INTERNAL_ERROR));
  }

  private Optional<BlockHeader> blockHeader(final JsonRpcRequest request) {
    final Hash hash = parameters.required(request.getParams(), 0, Hash.class);
    return blockchainQueries.blockByHash(hash).map(BlockWithMetadata::getHeader);
  }
}

package tech.pegasys.pantheon.consensus.clique.jsonrpc.methods;

import static tech.pegasys.pantheon.consensus.clique.CliqueHelpers.getValidatorsOfBlock;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Optional;

public class CliqueGetSigners implements JsonRpcMethod {
  public static final String CLIQUE_GET_SIGNERS = "clique_getSigners";
  private final BlockchainQueries blockchainQueries;
  private final JsonRpcParameter parameters;

  public CliqueGetSigners(
      final BlockchainQueries blockchainQueries, final JsonRpcParameter parameter) {
    this.blockchainQueries = blockchainQueries;
    this.parameters = parameter;
  }

  @Override
  public String getName() {
    return CLIQUE_GET_SIGNERS;
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
    final Optional<BlockParameter> blockParameter =
        parameters.optional(request.getParams(), 0, BlockParameter.class);
    final long latest = blockchainQueries.headBlockNumber();
    final long blockNumber = blockParameter.map(b -> b.getNumber().orElse(latest)).orElse(latest);
    return blockchainQueries.blockByNumber(blockNumber).map(BlockWithMetadata::getHeader);
  }
}

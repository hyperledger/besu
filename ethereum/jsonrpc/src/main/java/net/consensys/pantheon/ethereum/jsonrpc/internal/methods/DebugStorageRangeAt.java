package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import net.consensys.pantheon.ethereum.core.Account;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.BlockReplay;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.DebugStorageRangeAtResult;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.uint.UInt256;

import java.util.NavigableMap;

public class DebugStorageRangeAt implements JsonRpcMethod {

  private final JsonRpcParameter parameters;
  private final BlockchainQueries blockchainQueries;
  private final BlockReplay blockReplay;

  public DebugStorageRangeAt(
      final JsonRpcParameter parameters,
      final BlockchainQueries blockchainQueries,
      final BlockReplay blockReplay) {
    this.parameters = parameters;
    this.blockchainQueries = blockchainQueries;
    this.blockReplay = blockReplay;
  }

  @Override
  public String getName() {
    return "debug_storageRangeAt";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    final Hash blockHash = parameters.required(request.getParams(), 0, Hash.class);
    final int transactionIndex = parameters.required(request.getParams(), 1, Integer.class);
    final Address accountAddress = parameters.required(request.getParams(), 2, Address.class);
    final Hash startKey = parameters.required(request.getParams(), 3, Hash.class);
    final int limit = parameters.required(request.getParams(), 4, Integer.class);

    final TransactionWithMetadata transactionWithMetadata =
        blockchainQueries.transactionByBlockHashAndIndex(blockHash, transactionIndex);

    return blockReplay
        .afterTransactionInBlock(
            blockHash,
            transactionWithMetadata.getTransaction().hash(),
            (transaction, blockHeader, blockchain, worldState, transactionProcessor) ->
                extractStorageAt(request, accountAddress, startKey, limit, worldState))
        .orElseGet(() -> new JsonRpcSuccessResponse(request.getId(), null));
  }

  private JsonRpcSuccessResponse extractStorageAt(
      final JsonRpcRequest request,
      final Address accountAddress,
      final Hash startKey,
      final int limit,
      final MutableWorldState worldState) {
    final Account account = worldState.get(accountAddress);
    final NavigableMap<Bytes32, UInt256> entries = account.storageEntriesFrom(startKey, limit + 1);

    Bytes32 nextKey = null;
    if (entries.size() == limit + 1) {
      nextKey = entries.lastKey();
      entries.remove(nextKey);
    }
    return new JsonRpcSuccessResponse(
        request.getId(), new DebugStorageRangeAtResult(entries, nextKey));
  }
}

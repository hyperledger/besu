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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.DebugStorageRangeAtResult;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes32;

public class DebugStorageRangeAt implements JsonRpcMethod {

  private final Supplier<BlockchainQueries> blockchainQueries;
  private final Supplier<BlockReplay> blockReplay;
  private final boolean shortValues;

  public DebugStorageRangeAt(
      final BlockchainQueries blockchainQueries, final BlockReplay blockReplay) {
    this(Suppliers.ofInstance(blockchainQueries), Suppliers.ofInstance(blockReplay), false);
  }

  public DebugStorageRangeAt(
      final Supplier<BlockchainQueries> blockchainQueries,
      final Supplier<BlockReplay> blockReplay,
      final boolean shortValues) {
    this.blockchainQueries = blockchainQueries;
    this.blockReplay = blockReplay;
    this.shortValues = shortValues;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_STORAGE_RANGE_AT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final BlockParameterOrBlockHash blockParameterOrBlockHash =
        requestContext.getRequiredParameter(0, BlockParameterOrBlockHash.class);
    final int transactionIndex = requestContext.getRequiredParameter(1, Integer.class);
    final Address accountAddress = requestContext.getRequiredParameter(2, Address.class);
    final Hash startKey =
        Hash.fromHexStringLenient(requestContext.getRequiredParameter(3, String.class));
    final int limit = requestContext.getRequiredParameter(4, Integer.class);

    final Optional<Hash> blockHashOptional = hashFromParameter(blockParameterOrBlockHash);
    if (blockHashOptional.isEmpty()) {
      return emptyResponse(requestContext);
    }
    final Hash blockHash = blockHashOptional.get();
    final Optional<BlockHeader> blockHeaderOptional =
        blockchainQueries.get().blockByHash(blockHash).map(BlockWithMetadata::getHeader);
    if (blockHeaderOptional.isEmpty()) {
      return emptyResponse(requestContext);
    }

    final Optional<TransactionWithMetadata> optional =
        blockchainQueries.get().transactionByBlockHashAndIndex(blockHash, transactionIndex);

    return optional
        .map(
            transactionWithMetadata ->
                (blockReplay
                    .get()
                    .afterTransactionInBlock(
                        blockHash,
                        transactionWithMetadata.getTransaction().getHash(),
                        (transaction, blockHeader, blockchain, worldState, transactionProcessor) ->
                            extractStorageAt(
                                requestContext, accountAddress, startKey, limit, worldState))
                    .orElseGet(() -> emptyResponse(requestContext))))
        .orElseGet(
            () ->
                blockchainQueries
                    .get()
                    .getWorldState(blockHeaderOptional.get().getNumber())
                    .map(
                        worldState ->
                            extractStorageAt(
                                requestContext, accountAddress, startKey, limit, worldState))
                    .orElseGet(() -> emptyResponse(requestContext)));
  }

  private Optional<Hash> hashFromParameter(final BlockParameterOrBlockHash blockParameter) {
    if (blockParameter.isEarliest()) {
      return blockchainQueries.get().getBlockHashByNumber(0);
    } else if (blockParameter.isLatest() || blockParameter.isPending()) {
      return blockchainQueries
          .get()
          .latestBlockWithTxHashes()
          .map(block -> block.getHeader().getHash());
    } else if (blockParameter.isNumeric()) {
      return blockchainQueries.get().getBlockHashByNumber(blockParameter.getNumber().getAsLong());
    } else {
      return blockParameter.getHash();
    }
  }

  private JsonRpcSuccessResponse extractStorageAt(
      final JsonRpcRequestContext requestContext,
      final Address accountAddress,
      final Hash startKey,
      final int limit,
      final WorldState worldState) {
    final Account account = worldState.get(accountAddress);
    final NavigableMap<Bytes32, AccountStorageEntry> entries =
        account.storageEntriesFrom(startKey, limit + 1);

    Bytes32 nextKey = null;
    if (entries.size() == limit + 1) {
      nextKey = entries.lastKey();
      entries.remove(nextKey);
    }
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        new DebugStorageRangeAtResult(entries, nextKey, shortValues));
  }

  private JsonRpcSuccessResponse emptyResponse(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        new DebugStorageRangeAtResult(Collections.emptyNavigableMap(), null, shortValues));
  }
}
